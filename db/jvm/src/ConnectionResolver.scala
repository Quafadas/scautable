package io.github.quafadas.scautable.db

import java.sql.{Connection, DriverManager}
import scala.jdk.CollectionConverters.*

/** Resolves JDBC connection parameters from the environment.
  *
  * Priority order:
  *   1. Explicit parameters passed as arguments.
  *   2. JVM system properties (`-DSCAUTABLE_DB_URL=...`, `-DSCAUTABLE_DB_USER=...`, `-DSCAUTABLE_DB_PASSWORD=...`).
  *   3. Environment variables (`SCAUTABLE_DB_URL`, `SCAUTABLE_DB_USER`, `SCAUTABLE_DB_PASSWORD`).
  *
  * Credentials are **never** embedded in generated code — only the variable/property names are
  * emitted. Connection objects are created fresh at runtime from those values so that rotating
  * credentials requires no recompilation.
  */
object ConnectionResolver:

  val urlEnvVar: String      = "SCAUTABLE_DB_URL"
  val userEnvVar: String     = "SCAUTABLE_DB_USER"
  val passwordEnvVar: String = "SCAUTABLE_DB_PASSWORD"
  val snapshotEnvVar: String = "SCAUTABLE_DB_SNAPSHOT"

  /** Look up `name`, preferring the JVM system property (`-D$name=...`) over the environment
    * variable of the same name.
    */
  def lookup(name: String): Option[String] =
    sys.props.get(name).orElse(sys.env.get(name))
  end lookup

  /** Resolve connection parameters at *macro expansion time* (compile time).
    *
    * Returns `Some((url, user, password))` if a URL is found (system property or env var),
    * `None` otherwise.
    */
  def resolveAtCompileTime(lookup: String => Option[String] = lookup): Option[(String, Option[String], Option[String])] =
    lookup(urlEnvVar).map { url =>
      (url, lookup(userEnvVar), lookup(passwordEnvVar))
    }
  end resolveAtCompileTime

  /** Resolve the snapshot file path at *macro expansion time*.
    *
    * Looks for `SCAUTABLE_DB_SNAPSHOT` (system property or env var); falls back to
    * [[SchemaSnapshot.defaultSnapshotPath]].
    */
  def snapshotPath(lookup: String => Option[String] = lookup): String =
    lookup(snapshotEnvVar).getOrElse(SchemaSnapshot.defaultSnapshotPath)
  end snapshotPath

  /** Open a JDBC connection at **runtime** using system properties or env vars.
    *
    * Called from the generated code inside [[DbIterator]] construction.
    */
  def openConnection(): Connection =
    val url = lookup(urlEnvVar).getOrElse(throw new IllegalStateException(
      s"Neither system property nor environment variable '$urlEnvVar' is set. " +
        s"Set it to a JDBC URL such as 'jdbc:h2:mem:test;DB_CLOSE_DELAY=-1'."
    ))
    openConnectionWith(url, lookup(userEnvVar), lookup(passwordEnvVar))
  end openConnection

  /** Open a JDBC connection, bypassing [[DriverManager]] so that drivers on child
    * classloaders are found correctly.
    *
    * `DriverManager.getConnection` discovers drivers via `ServiceLoader` using the
    * system classloader.  Build tools and REPL environments use layered classloaders
    * where JDBC driver JARs land on a *child* classloader:
    *
    *   - Mill (REPL/test/run): thread context CL == project CL  →  `ctxCl` works.
    *   - scala-cli (macro expansion): context CL is the scala-cli app CL, NOT the
    *     compile-classpath CL; but `getClass.getClassLoader` IS the compile-classpath
    *     URLClassLoader that has both our JAR and the driver JAR on it.
    *   - Forked JVM (mill runMain / sbt run): driver on system CP  →  DriverManager fallback.
    *
    * We walk candidate classloaders in priority order, find the first that can see
    * a driver for `url`, and connect directly — bypassing `DriverManager` entirely.
    */
  private[db] def openConnectionWith(
      url: String,
      user: Option[String],
      pass: Option[String]
  ): Connection =
    val props = new java.util.Properties()
    user.foreach(props.setProperty("user", _))
    pass.foreach(props.setProperty("password", _))

    // Candidate classloaders, deduplicated and without nulls (bootstrap CL = null).
    val classLoaders: List[ClassLoader] =
      List(
        Thread.currentThread().getContextClassLoader, // Mill REPL/tests
        getClass.getClassLoader                        // scala-cli macro expansion
      ).filter(_ != null).distinct

    classLoaders.iterator
      .flatMap(cl => java.util.ServiceLoader.load(classOf[java.sql.Driver], cl).asScala)
      .find(_.acceptsURL(url))
      .flatMap(d => Option(d.connect(url, props)))
      .getOrElse:
        // Last resort: DriverManager (driver on system/boot classpath, e.g. forked JVM).
        user match
          case Some(u) => DriverManager.getConnection(url, u, pass.getOrElse(""))
          case None    => DriverManager.getConnection(url)
  end openConnectionWith

end ConnectionResolver
