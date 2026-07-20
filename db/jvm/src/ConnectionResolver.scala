package io.github.quafadas.scautable.db

import java.sql.{Connection, DriverManager}

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
    sys.env.get(userEnvVar) match
      case Some(user) => DriverManager.getConnection(url, user, sys.env.getOrElse(passwordEnvVar, ""))
      case None       => DriverManager.getConnection(url)
  end openConnection

end ConnectionResolver
