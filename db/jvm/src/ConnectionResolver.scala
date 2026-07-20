package io.github.quafadas.scautable.db

import java.sql.{Connection, DriverManager}
import scala.jdk.CollectionConverters.*

/** Resolves JDBC connection parameters from the environment.
  *
  * Priority order:
  *   1. Explicit parameters passed as arguments.
  *   2. Environment variables (`SCAUTABLE_DB_URL`, `SCAUTABLE_DB_USER`, `SCAUTABLE_DB_PASSWORD`).
  *
  * Credentials are **never** embedded in generated code — only the env-var names are emitted.
  * Connection objects are created fresh at runtime from the env-var values so that rotating
  * credentials requires no recompilation.
  */
object ConnectionResolver:

  val urlEnvVar: String      = "SCAUTABLE_DB_URL"
  val userEnvVar: String     = "SCAUTABLE_DB_USER"
  val passwordEnvVar: String = "SCAUTABLE_DB_PASSWORD"
  val snapshotEnvVar: String = "SCAUTABLE_DB_SNAPSHOT"

  /** Resolve connection parameters at *macro expansion time* (compile time).
    *
    * Returns `Some((url, user, password))` if an env-var URL is found, `None` otherwise.
    */
  def resolveAtCompileTime(env: String => Option[String] = sys.env.get): Option[(String, Option[String], Option[String])] =
    env(urlEnvVar).map { url =>
      (url, env(userEnvVar), env(passwordEnvVar))
    }
  end resolveAtCompileTime

  /** Resolve the snapshot file path at *macro expansion time*.
    *
    * Looks for `SCAUTABLE_DB_SNAPSHOT` env var; falls back to [[SchemaSnapshot.defaultSnapshotPath]].
    */
  def snapshotPath(env: String => Option[String] = sys.env.get): String =
    env(snapshotEnvVar).getOrElse(SchemaSnapshot.defaultSnapshotPath)
  end snapshotPath

  /** Open a JDBC connection at **runtime** using env vars.
    *
    * Called from the generated code inside [[DbIterator]] construction.
    */
  def openConnection(): Connection =
    val url = sys.env.getOrElse(urlEnvVar, throw new IllegalStateException(
      s"Environment variable '$urlEnvVar' is not set. " +
        s"Set it to a JDBC URL such as 'jdbc:h2:mem:test;DB_CLOSE_DELAY=-1'."
    ))
    openConnectionWith(url, sys.env.get(userEnvVar), sys.env.get(passwordEnvVar))
  end openConnection

  /** Open a JDBC connection, bypassing [[DriverManager]] so that drivers on child
    * classloaders are found correctly.
    *
    * `DriverManager.getConnection` discovers drivers via `ServiceLoader` using the
    * **system classloader**.  In build-tool environments (Mill, sbt) and the Scala
    * REPL, JDBC driver JARs are typically placed on a *child* classloader, making
    * them invisible to `DriverManager`.  This method uses the thread context
    * classloader instead — which Mill/sbt always set to the project's classloader —
    * and falls back to `DriverManager` if no driver is found there.
    */
  private[db] def openConnectionWith(
      url: String,
      user: Option[String],
      pass: Option[String]
  ): Connection =
    val ctxCl = Thread.currentThread().getContextClassLoader
    val props  = new java.util.Properties()
    user.foreach(props.setProperty("user", _))
    pass.foreach(props.setProperty("password", _))

    // Try drivers visible to the context classloader first.
    val ctxConn: Option[Connection] =
      java.util.ServiceLoader
        .load(classOf[java.sql.Driver], ctxCl)
        .asScala
        .find(_.acceptsURL(url))
        .flatMap(d => Option(d.connect(url, props)))

    ctxConn.getOrElse:
      // Fallback: DriverManager (works when the driver is on the system classpath,
      // e.g. in forked JVM runs or when the user has added the JAR to the boot cp).
      user match
        case Some(u) => DriverManager.getConnection(url, u, pass.getOrElse(""))
        case None    => DriverManager.getConnection(url)
  end openConnectionWith

end ConnectionResolver
