# ScalaSql integration

The `scalasql` module lets you point [scalasql](https://github.com/com-lihaoyi/scalasql) at a live JDBC database and get a fully typed, query-pushdown-capable table — schema inferred at *compile time*, no case class or codegen step required.

Add both the `db` and `scalasql` modules, and bring both packages into scope:

```scala
import io.github.quafadas.scautable.db.*
import io.github.quafadas.scautable.scalasql.*
```

## Connection configuration

Schema inference (compile time) and the runtime `DbApi` are both configured from the same three environment variables:

`SCAUTABLE_DB_URL`, `SCAUTABLE_DB_USER`, `SCAUTABLE_DB_PASSWORD`.

You select a dialect via a `DbFlavour` marker type (`H2`, `Postgres`, ...) — this determines both the scalasql dialect used to build queries and which `TypeMapper`s are summoned for each column.

## `DB.sqlTable` — infer a table's schema

`DB.sqlTable[F]("tableName")` reads the schema of `tableName` from the database at compile time and returns a `NamedTupleTable` — a scalasql `Table` whose row type is an anonymous `NamedTuple`. `tableName` may include a schema qualifier: `"schema.table"` or just `"table"`.

```scala
import io.github.quafadas.scautable.db.*
import io.github.quafadas.scautable.scalasql.*

@main def run(): Unit = {
  val db = DB.connection[Postgres]("boo")
  val country  = DB.sqlTable[Postgres]("country")
  // country: NamedTupleTable[("iso3", "name", "population", "area_km2", "is_island"),
//                            (String, String, Option[Long], Double, Boolean)]
  val city  = DB.sqlTable[Postgres]("city")
  val countrylanguage  = DB.sqlTable[Postgres]("countrylanguage")

  db.run(country.select.take(10)).ptbln

  db.run(city.select.take(10)).ptbln
  db.run(countrylanguage.select.take(10)).ptbln

  db.run(city.select.sumBy(_.population))
}


```

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
