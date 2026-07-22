# ScalaSql integration

The `scalasql` module lets you point [scalasql](https://github.com/com-lihaoyi/scalasql) at a live JDBC database and get a fully typed, query-pushdown-capable table — schema inferred at *compile time*, no case class or codegen step required.

Assuming you have a postgres database running locally (e.g. [world](https://www.postgresql.org/ftp/projects/pgFoundry/dbsamples/world/)), that is identified by the following three variables;

```bash
"SCAUTABLE_DB_USER": "testuser",
"SCAUTABLE_DB_PASSWORD": "testpass",
"SCAUTABLE_DB_URL": "jdbc:postgresql://localhost:5432/testdb"
```

If you are using scala-cli, you should put these in `ide-envs.json`. Restart bloop - `scala-cli bloop exit`...

```scala
//> using dep io.github.quafadas::scautable-scalasql:0.0.37-5-22d216
//> using compileOnly.dep io.github.quafadas::scautable-scalasql:0.0.37-5-22d216

//> using dep org.postgresql:postgresql:42.7.13
//> using compileOnly.dep org.postgresql:postgresql:42.7.13

import io.github.quafadas.scautable.scalasql.*
import io.github.quafadas.scautable.db.DB
import io.github.quafadas.scautable.db.Postgres
import scalasql.PostgresDialect.*
import scalasql.simple.{*, given}
import io.github.quafadas.table.*


@main def run(): Unit = {
  val db = DB.connection[Postgres]
  val country  = DB.sqlTable[Postgres]("country")
  val city  = DB.sqlTable[Postgres]("city")
  val countrylanguage  = DB.sqlTable[Postgres]("countrylanguage")

  db.run(country.select.take(10)).ptbln

  db.run(city.select.take(10)).ptbln
  db.run(countrylanguage.select.take(10)).ptbln

  db.run(city.select.sumBy(_.population))
}

// TO CHECK IF YOUR CONNECTION IS WORKING, you can also use a raw JDBC connection:
// val dataSource = new org.postgresql.ds.PGSimpleDataSource
//   dataSource.setURL("jdbc:postgresql://localhost:5432/testdb");

//   dataSource.setUser("testuser");
//   dataSource.setPassword("testpass");

// lazy val postgresClient = new scalasql.DbClient.DataSource(
//   dataSource,
//   config = new scalasql.Config {}
// )

// postgresClient.transaction { db =>
//   db.runRaw[Int]("SELECT 1")
// }
```

## Connection configuration

Schema inference (compile time) and the runtime `DbApi` are both configured from the same three environment variables:

`SCAUTABLE_DB_URL`, `SCAUTABLE_DB_USER`, `SCAUTABLE_DB_PASSWORD`.

You select a dialect via a `DbFlavour` marker type (`H2`, `Postgres`, `MsSqlServer`, ...) — this determines both the scalasql dialect used to build queries and which `TypeMapper`s are summoned for each column.

## `DB.sqlTable` — infer a table's schema

`DB.sqlTable[F]("tableName")` reads the schema of `tableName` from the database at compile time and returns a `NamedTupleTable` — a scalasql `Table` whose row type is an anonymous `NamedTuple`. `tableName` may include a schema qualifier: `"schema.table"` or just `"table"`.


Constructing `countries` never opens a network connection — it only runs at compile time against the schema-inference connection (or a snapshot).

## `DB.connection` — get a matching `DbApi`

`DB.connection[F]` gives you a lazily-connecting scalasql `DbApi`, wired from the same `SCAUTABLE_DB_URL` / `_USER` / `_PASSWORD` names, for the dialect `F`. It is not table-specific, so it takes no table name — call it once and reuse it across tables:

```scala
val db = DB.connection[H2]

val big = db.run(countries.select.filter(_.population > 1_000_000))
```

The underlying JDBC connection is opened lazily on first use (e.g. the first `db.run(...)` call), not when `db` is constructed.

## `DB.sqlConnectionAndTable` — both at once

If you want the table and a matching connection together, `DB.sqlConnectionAndTable[F]("tableName")` returns a `(DbApi, NamedTupleTable)` pair in one call:

```scala
val (db, countries) = DB.sqlConnectionAndTable[H2]("country")

db.run(countries.select.filter(_.population > 1_000_000))
```

## Full query DSL

Because `NamedTupleTable` is a genuine scalasql `Table`, the whole query DSL is available: `select`, `filter`, `map`, `join`, `sumBy` / `avgBy` / `minBy` / `maxBy` / `countBy`, `insert`, `update`, `delete`, and so on. Column identifiers are exactly the DB column names — no camelCase mapping is applied.

## Identifier handling

Table names are always quoted via the dialect's `escape` mechanism, and column names are passed through unmodified. If you configure your own `scalasql.Config`, use `columnNameMapper = identity` to avoid any camelCase-to-snake_case conversion.
