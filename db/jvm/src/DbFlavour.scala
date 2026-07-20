package io.github.quafadas.scautable.db

/** Marker traits used as type parameters to `DB.table[F]` and `DB.query[F]` to select the
  * database flavour (dialect + connection-resolution strategy).
  *
  * Built-in flavours:
  *   - [[Postgres]] — PostgreSQL via any JDBC driver
  *   - [[H2]] — H2 in-memory / file database (useful for tests and local dev)
  *
  * Connection parameters are read from environment variables at macro-expansion time:
  *   - `SCAUTABLE_DB_URL` — JDBC URL (required)
  *   - `SCAUTABLE_DB_USER` — username (optional)
  *   - `SCAUTABLE_DB_PASSWORD` — password (optional)
  *
  * At runtime the same env-var names are used to open the connection, so credentials are never
  * baked into generated code.
  */
sealed trait DbFlavour

/** PostgreSQL flavour marker. */
trait Postgres extends DbFlavour

/** H2 flavour marker (in-memory or file). */
trait H2 extends DbFlavour

/** Generic / unknown flavour — uses standard JDBC type mapping without dialect-specific overrides.
  */
trait GenericJdbc extends DbFlavour
