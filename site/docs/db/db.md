# Database schemas without credentials at compile time

`DB.table`, `DB.query` and `DB.sqlTable` infer their types by reading the database schema *while the compiler runs*. By default that means the compiler needs a live connection: `SCAUTABLE_DB_URL`, `_USER` and `_PASSWORD` have to be visible to the JVM doing the compiling.

That JVM is often not the one you think. With scala-cli it is the long-running Bloop daemon, and in an editor it is whatever Metals connected to. Neither sees variables you export in your shell after it started, and putting credentials into their environment exposes them to every project the daemon compiles. In CI the build may not be able to reach the database at all.

The DB module already supports a better split. Read the schema once, with credentials, and write it to a snapshot file. Compile from that file, without credentials. Supply credentials again only when the program actually runs.

| Phase | Needs credentials | Needs network | How often |
|---|---|---|---|
| 1. Snapshot the schema | yes | yes | when the schema changes |
| 2. Compile | no | no | every edit |
| 3. Run | yes | yes | every run |

## How the macro finds a schema

At expansion time, the DB macros resolve a schema in this order:

1. **Live database.** Used only if `SCAUTABLE_DB_URL` is set as a system property or environment variable *of the compiler's JVM*. If it is set but the connection or schema read fails, compilation fails; it does not fall back.
2. **Snapshot file on disk.** The path in `SCAUTABLE_DB_SNAPSHOT`, or `scautable-db-schema.json` relative to the compiler's working directory. Under Bloop that working directory is the daemon's own directory, not your project, so the default path rarely helps.
3. **Snapshot on the classpath.** A resource named `scautable-db-schema.json` at the classpath root.

The two-phase approach relies on step 3. Keep `SCAUTABLE_DB_URL` out of the compiler's environment, and put the snapshot in a resource directory.

Generated code never contains credentials. At runtime, the connection is opened from the same three names, read from the running program's system properties or environment.

## Phase 1: write the snapshot

The snapshot is a JSON file keyed by what you pass to the macros:

- `table:<name>` for `DB.table` and `DB.sqlTable`, using the name exactly as written, including any `schema.` or `catalog.schema.` prefix. On SQL Server, `DB.table[MsSqlServer]("OtherDb.dbo.Customers")` reads another database on the same server through the one configured connection.
- `query:<sql>` for `DB.query`, using the SQL text exactly as written.

`SchemaSnapshot.write` replaces the whole file, so list every table and query your code uses. A small standalone script works well:

```scala
//> using dep io.github.quafadas::scautable-db:@VERSION@
//> using dep org.postgresql:postgresql:42.7.13   // your JDBC driver

import io.github.quafadas.scautable.db.*

val tables = List("country", "city", "public.countrylanguage")
val queries = List("SELECT name, population FROM country WHERE population > 1000000")

@main def snapshot(): Unit =
  val conn = ConnectionResolver.openConnection() // reads SCAUTABLE_DB_* at runtime
  try
    val tableSnaps = tables.map(name => s"table:$name" -> SchemaSnapshot.fromTable(conn, name))
    val querySnaps = queries.map(sql => s"query:$sql" -> SchemaSnapshot.fromQuery(conn, sql))
    SchemaSnapshot.write((tableSnaps ++ querySnaps).toMap, "schemas/scautable-db-schema.json")
  finally conn.close()
```

This script runs as an ordinary program, so it takes credentials from its own environment, and you can hand them over in whatever way suits you. With a secrets manager you can avoid ever writing the password to disk, for example with the 1Password CLI:

```bash
op run --env-file=.env.op -- scala-cli run tools/snapshot.scala
```

where `.env.op` holds secret references rather than values:

```bash
SCAUTABLE_DB_URL=jdbc:postgresql://db.example.com:5432/app
SCAUTABLE_DB_USER=op://Vault/app-db/username
SCAUTABLE_DB_PASSWORD=op://Vault/app-db/password
```

Keep the script out of the main build. Otherwise the main build cannot compile without a snapshot, and you need the main build to compile to produce one. With scala-cli:

```scala
//> using exclude tools/*
```

The snapshot contains column names and types only, not data or credentials, so it is usually safe to commit. Committing it lets CI compile with no database access.

## Phase 2: compile from the snapshot

Put the snapshot directory on the **compile** classpath. The macro looks it up through the compiler's classloader, so a runtime-only resource directory is not enough.

**scala-cli.** Resource directories are visible to macros:

```scala
//> using resourceDirs schemas
```

**Mill.** Ordinary `resources` are not on the compile classpath. Add them to `compileResources`, which is what scautable's own tests do:

```scala
override def compileResources = super.compileResources() ++ resources()
```

Then compile as usual, with no `SCAUTABLE_*` variables in the compiler's environment:

```scala
import io.github.quafadas.scautable.scalasql.*
import io.github.quafadas.scautable.db.*
import scalasql.PostgresDialect.*
import scalasql.simple.{*, given}

val country = DB.sqlTable[Postgres]("country") // types come from the snapshot
```

Metals, Bloop, `scala-cli compile` and CI all read the same file, so they agree on the types.

## Phase 3: run with credentials

The generated code opens its connection lazily, from `SCAUTABLE_DB_URL`, `_USER` and `_PASSWORD` in the running program's environment:

```bash
op run --env-file=.env.op -- scala-cli run . --main-class app
```

For VS Code launch configurations, the Scala debugger accepts an `envFile`. You can generate that file just before launch and delete it afterwards:

```json
// .vscode/tasks.json
{
  "version": "2.0.0",
  "tasks": [
    { "label": "db-env", "type": "shell",
      "command": "umask 077 && op inject -f -i .env.op -o .env.local" },
    { "label": "db-env-clean", "type": "shell", "command": "rm -f .env.local" }
  ]
}
```

```json
// in each .vscode/launch.json configuration
"preLaunchTask": "db-env",
"postDebugTask": "db-env-clean",
"envFile": "${workspaceFolder}/.env.local"
```

Add `.env.local` to `.gitignore`.

## Refreshing the snapshot

Re-run phase 1 whenever the schema changes. The incremental compiler does not track the snapshot file, so code that was already compiled keeps its old types until you force a rebuild:

```bash
scala-cli clean .   # or "Metals: Clean compile workspace", or ./mill clean
```

## Troubleshooting

**`Cannot infer DB schema for 'table:…': no live connection and no snapshot found`.** No live URL was set, and the classpath has no `scautable-db-schema.json` with that key. Check three things: the file is at the root of a directory on the compile classpath; it is named exactly `scautable-db-schema.json`; and the key matches the string you passed to the macro character for character.

**`Schema read failed: …` when you expected the snapshot to be used.** `SCAUTABLE_DB_URL` is set in the compiler's environment, so the live path won. Remove it from that environment and restart the build server (`scala-cli --power bloop exit`), or fix the credentials.

**The types don't reflect a schema change.** Clean and rebuild, as described under *Refreshing the snapshot*.

**`No suitable driver found` when running the snapshot script.** The JDBC driver is missing from the script's dependencies, or `SCAUTABLE_DB_URL` is not a full JDBC URL. For example, SQL Server needs `jdbc:sqlserver://host:1433;databaseName=…`.
