# Several databases on one SQL Server

A SQL Server instance usually hosts several databases, and one login can often read more than one of them. Scautable reaches all of them through a **single configured connection**: write the table name in three parts, `Database.schema.table`, and the database part selects which one you read.

This works with all three entry points:

| Entry point | Example |
|---|---|
| `DB.table` | `DB.table[MsSqlServer]("Crm.dbo.Customers")` |
| `DB.sqlTable` | `DB.sqlTable[MsSqlServer]("Crm.dbo.Customers")` |
| `DB.query` | `DB.query[MsSqlServer]("SELECT ... FROM Crm.dbo.Customers")` |

## The setup

The examples below use a server with three databases:

- `Sales`, which holds `dbo.Orders`. This is the database the connection opens on.
- `Crm`, which holds `dbo.Customers`.
- `Reporting`, which holds `finance.MonthlyTargets`.

Point the connection at your "home" database. The other databases are reached by name, so they don't appear in the URL:

```bash
export SCAUTABLE_DB_URL="jdbc:sqlserver://sql.example.com:1433;databaseName=Sales;encrypt=true"
export SCAUTABLE_DB_USER="report_reader"
export SCAUTABLE_DB_PASSWORD="..."
```

The login needs a user in *every* database you read (or a server-level permission that covers them). If it can see `Sales` but has no user in `Crm`, schema inference for `Crm.dbo.Customers` fails at compile time with a "table not found" style error. It does not fall back to a table of the same name in `Sales`.

## Iterating tables from several databases

`DB.table` gives you a lazily-evaluated `Iterator` of named tuples. Each one opens its own JDBC connection when you first pull from it, and the generated `SELECT` carries the full `"Crm"."dbo"."Customers"` name:

```scala
//> using dep io.github.quafadas::scautable-db:@VERSION@
//> using dep com.microsoft.sqlserver:mssql-jdbc:12.8.1.jre11

import io.github.quafadas.scautable.db.*
import io.github.quafadas.table.*

@main def browse(): Unit =
  // Home database: a two-part name is enough
  val orders    = DB.table[MsSqlServer]("dbo.Orders")
  // Other databases on the same server: three-part names
  val customers = DB.table[MsSqlServer]("Crm.dbo.Customers")
  val targets   = DB.table[MsSqlServer]("Reporting.finance.MonthlyTargets")

  orders.take(5).toSeq.ptbln
  customers.take(5).toSeq.ptbln
  targets.take(5).toSeq.ptbln
```

Each value's column names and types are inferred at compile time from its own database, so `customers.column["email"]` is checked by the compiler just as `orders.column["total"]` is.

## Joining across databases with scalasql

The scalasql integration is the better fit for this case. Every `NamedTupleTable` runs through the same `DbApi`, so a join between `Sales` and `Crm` is rendered as one SQL statement and runs on the server:

```scala
//> using dep io.github.quafadas::scautable-scalasql:@VERSION@
//> using dep com.microsoft.sqlserver:mssql-jdbc:12.8.1.jre11

import io.github.quafadas.scautable.scalasql.*
import io.github.quafadas.scautable.db.{DB, MsSqlServer}
import io.github.quafadas.table.*
import scalasql.MsSqlDialect.*
import scalasql.simple.{*, given}

@main def crossDb(): Unit =
  val db = DB.connection[MsSqlServer]          // one lazily-opened connection to Sales

  val orders    = DB.sqlTable[MsSqlServer]("dbo.Orders")
  val customers = DB.sqlTable[MsSqlServer]("Crm.dbo.Customers")
  val targets   = DB.sqlTable[MsSqlServer]("Reporting.finance.MonthlyTargets")

  // Orders (Sales) joined to their customer (Crm), filtered and projected on the server
  val bigOrders = db.run(
    orders.select
      .join(customers)(_.customer_id === _.id)
      .filter { case (o, _) => o.total > BigDecimal(10_000) }
      .map { case (o, c) => (o.id, c.name, c.region, o.total) }
      .take(20)
  )
  bigOrders.foreach(println)

  // Aggregates work the same way on any of the databases
  val totalTarget = db.run(targets.select.sumBy(_.target_amount))
  println(s"Total target: $totalTarget")
```

The generated SQL names `Orders` by its schema alone (so it resolves in `Sales`, the connection's database) and `Customers` as `Crm.dbo.Customers`, so SQL Server resolves both within the one session.

## Raw SQL across databases

`DB.query` takes any SQL text, so you can write three-part names yourself. That is useful for joins or window functions you would rather not express in the DSL. The query is not executed at compile time: SQL Server only describes its result set.

```scala
val regionRevenue = DB.query[MsSqlServer](
  """SELECT c.region, SUM(o.total) AS revenue, t.target_amount
    |FROM Sales.dbo.Orders o
    |JOIN Crm.dbo.Customers c ON c.id = o.customer_id
    |JOIN Reporting.finance.MonthlyTargets t ON t.region = c.region
    |GROUP BY c.region, t.target_amount""".stripMargin
)

regionRevenue.toSeq.ptbln
```

## Compiling from a snapshot

If you compile from a schema snapshot rather than a live connection (see [Database schemas without credentials at compile time](db.md)), the keys use the names exactly as written in your code, database prefix included:

```scala
val conn = ConnectionResolver.openConnection()
try
  val tables = List("dbo.Orders", "Crm.dbo.Customers", "Reporting.finance.MonthlyTargets")
  val snaps  = tables.map(t => s"table:$t" -> SchemaSnapshot.fromTable(conn, t))
  SchemaSnapshot.write(snaps.toMap, "schemas/scautable-db-schema.json")
finally conn.close()
```

`"table:Crm.dbo.Customers"` and `"table:dbo.Customers"` are different keys, even if they happen to name the same table.

## Things to watch for

- **One server only.** The connection settings are global (`SCAUTABLE_DB_URL` and friends), so every table in a program comes from the same server. Four-part names such as `OtherServer.Db.dbo.T` are rejected by `DB.table` and `DB.sqlTable`. To read from a second server, the simplest route is a view in one of your databases that selects from the linked server. `DB.table` can then read that view like any other table.
- **Keep database and schema names plain in `DB.sqlTable`.** `DB.table` quotes every part of the name, but `DB.sqlTable` passes the `Database.schema` prefix to scalasql without quoting it (only the table name is escaped). Names with spaces, hyphens or reserved words need a view or `DB.query` instead.
- **Types SQL Server reports specially.** `datetimeoffset`, `sql_variant`, `geography` and similar columns have no default Scala mapping, so compilation stops and names the column. Use `DB.query` with a `CAST`, for example `CAST(created_at AS datetime2) AS created_at`, which becomes `LocalDateTime`.
- **Permissions are checked at compile time too.** With a live connection, the compiler's login must be able to read every database you reference, not just the one in the URL.
