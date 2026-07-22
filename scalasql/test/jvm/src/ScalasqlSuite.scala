package io.github.quafadas.scautable.scalasql

import scalasql.H2Dialect
import scalasql.H2Dialect.*
import scalasql.DbClient
import java.sql.DriverManager
import scala.NamedTuple.*
import io.github.quafadas.scautable.db.H2 as ScautableH2

/** Sets up an H2 in-memory database with a "country" table for scalasql tests. */
trait H2FixtureScalasql extends munit.FunSuite:

  val jdbcUrl = "jdbc:h2:mem:scautable_scalasql_test;DB_CLOSE_DELAY=-1"

  def withConn[A](f: java.sql.Connection => A): A =
    val conn = DriverManager.getConnection(jdbcUrl)
    try f(conn)
    finally conn.close()
    end try
  end withConn

  /** A scalasql DbClient using the test H2 database.
    *
    * Configuration: identity columnNameMapper so exact DB column names are preserved.
    */
  lazy val dbClient: DbClient.Connection =
    DbClient.Connection(
      java.sql.DriverManager.getConnection(jdbcUrl, "", ""),
      new scalasql.Config:
        override def columnNameMapper(v: String): String = v
        override def tableNameMapper(v: String): String = v
        override def logSql(sql: String, file: String, line: Int): Unit = ()
    )(using H2Dialect)

  lazy val db = dbClient.getAutoCommitClientConnection

  override def beforeAll(): Unit =
    super.beforeAll()
    withConn { conn =>
      conn.createStatement().execute("DROP TABLE IF EXISTS country")
      conn
        .createStatement()
        .execute(
          """CREATE TABLE IF NOT EXISTS country (
            |  iso3       CHAR(3)      NOT NULL,
            |  name       VARCHAR(100) NOT NULL,
            |  population BIGINT,
            |  area_km2   DOUBLE       NOT NULL,
            |  is_island  BOOLEAN      NOT NULL
            |)""".stripMargin
        )
      conn
        .createStatement()
        .execute(
          """INSERT INTO country (iso3, name, population, area_km2, is_island) VALUES
            |  ('GBR', 'United Kingdom',  67000000, 242495.0, false),
            |  ('ISL', 'Iceland',            370000,  103000.0, true),
            |  ('NZL', 'New Zealand',       5000000,  268000.0, true),
            |  ('DEU', 'Germany',          83000000,  357114.0, false)
            |""".stripMargin
        )
    }
  end beforeAll

  override def afterAll(): Unit =
    db.close()
    withConn { conn =>
      conn.createStatement().execute("DROP TABLE IF EXISTS country")
    }
    super.afterAll()
  end afterAll
end H2FixtureScalasql

// ---------------------------------------------------------------------------
// Phase 2 spike: hard-coded country table
// Risk coverage:
//   R1 — TableQueryable works (now via NTQueryableRow, no <: Product bound)
//   R2 — Generic combinators: select, filter, map, sortBy, take, drop
//   R3 — Write path: insert.values, update, delete
//   R4 — Identifier fidelity: exact DB column names, no camelCase mapping
//   R6 — Arity: 25-column table
// ---------------------------------------------------------------------------

class NamedTupleTableSuite extends H2FixtureScalasql:

  // Hard-coded table object — Phase 2 spike (no macro)
  type CountryNames = ("iso3", "name", "population", "area_km2", "is_island")
  type CountryVals = (String, String, Option[Long], Double, Boolean)

  object country extends NamedTupleTable[CountryNames, CountryVals]("country")

  // --- R1: basic select ---

  test("R1: select all returns 4 rows") {
    val rows = db.run(country.select)
    assertEquals(rows.length, 4)
  }

  test("R1: row fields accessible by name") {
    val rows = db.run(country.select)
    val firstIso: String = rows.head.iso3
    val firstPop: Option[Long] = rows.head.population
    assert(firstIso.nonEmpty, "iso3 should be non-empty")
    assert(firstPop.isDefined || firstPop.isEmpty, "population can be Some or None")
  }

  // --- R2: generic combinators ---

  test("R2: filter by boolean column") {
    val islands = db.run(country.select.filter(_.is_island === true))
    assertEquals(islands.length, 2)
    assert(islands.forall(_.is_island), "all rows should have is_island = true")
  }

  test("R2: filter by String column") {
    val result = db.run(country.select.filter(_.iso3 === "GBR"))
    assertEquals(result.length, 1)
    assertEquals(result.head.name, "United Kingdom")
  }

  test("R2: filter by nullable Long column (Option[Long])") {
    val result = db.run(country.select.filter(_.population.get > 1_000_000L))
    assert(result.nonEmpty)
    assert(result.forall(_.population.exists(_ > 1_000_000L)))
  }

  test("R2: map to single column projection") {
    val result = db.run(country.select.map(c => c.name))
    assertEquals(result.length, 4)
    assert(result.contains("Germany"), s"Expected Germany in $result")
  }

  test("R2: sortBy + take") {
    val result = db.run(country.select.sortBy(_.iso3).take(2))
    assertEquals(result.length, 2)
    assertEquals(result.head.iso3, "DEU") // DEU is first alphabetically
  }

  // --- R3: write path ---

  test("R3: insert.columns then select") {
    db.run(
      country.insert.columns(
        _.iso3 := "AUS",
        _.name := "Australia",
        _.population := Some(26_000_000L),
        _.area_km2 := 7_692_024.0,
        _.is_island := true
      )
    )
    val result = db.run(country.select.filter(_.iso3 === "AUS"))
    assertEquals(result.length, 1)
    assertEquals(result.head.name, "Australia")
    // cleanup
    db.run(country.delete(_.iso3 === "AUS"))
  }

  test("R3: update single column") {
    db.run(country.update(_.iso3 === "ISL").set(_.population := Some(400_000L)))
    val result = db.run(country.select.filter(_.iso3 === "ISL"))
    assertEquals(result.head.population, Some(400_000L))
    // restore
    db.run(country.update(_.iso3 === "ISL").set(_.population := Some(370_000L)))
  }

  test("R3: delete with filter") {
    db.run(
      country.insert.columns(
        _.iso3 := "TST",
        _.name := "TestLand",
        _.population := None,
        _.area_km2 := 1.0,
        _.is_island := false
      )
    )
    val countBefore = db.run(country.select).length
    db.run(country.delete(_.iso3 === "TST"))
    val countAfter = db.run(country.select).length
    assertEquals(countAfter, countBefore - 1)
  }

  // --- R4: identifier fidelity ---

  test("R4: underscore column names preserved (area_km2, is_island)") {
    val result = db.run(country.select)
    assert(result.nonEmpty)
    assert(result.exists(_.area_km2 > 0.0), "area_km2 field should be accessible and non-zero")
    assert(result.exists(_.is_island), "is_island field should be accessible")
  }

  // --- R6: arity check ---

  test("R6: 25-column table compiles and reads correctly") {
    withConn { conn =>
      conn.createStatement().execute("DROP TABLE IF EXISTS wide")
      conn
        .createStatement()
        .execute(
          """CREATE TABLE wide (
          |  c01 INT, c02 INT, c03 INT, c04 INT, c05 INT,
          |  c06 INT, c07 INT, c08 INT, c09 INT, c10 INT,
          |  c11 INT, c12 INT, c13 INT, c14 INT, c15 INT,
          |  c16 INT, c17 INT, c18 INT, c19 INT, c20 INT,
          |  c21 INT, c22 INT, c23 INT, c24 INT, c25 INT
          |)""".stripMargin
        )
      conn
        .createStatement()
        .execute(
          "INSERT INTO wide VALUES (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25)"
        )
    }
    type WN = (
        "c01",
        "c02",
        "c03",
        "c04",
        "c05",
        "c06",
        "c07",
        "c08",
        "c09",
        "c10",
        "c11",
        "c12",
        "c13",
        "c14",
        "c15",
        "c16",
        "c17",
        "c18",
        "c19",
        "c20",
        "c21",
        "c22",
        "c23",
        "c24",
        "c25"
    )
    type WV = (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)
    object wide extends NamedTupleTable[WN, WV]("wide")
    val rows = db.run(wide.select)
    assertEquals(rows.length, 1)
    assertEquals(rows.head.c01, 1)
    assertEquals(rows.head.c25, 25)
    withConn(conn => conn.createStatement().execute("DROP TABLE IF EXISTS wide"))
  }

end NamedTupleTableSuite

// ---------------------------------------------------------------------------
// Phase 3: DB.sqlTable macro test (uses committed snapshot for compile-time schema)
// ---------------------------------------------------------------------------

class DbSqlTableSuite extends H2FixtureScalasql:
  import io.github.quafadas.scautable.db.DB
  import io.github.quafadas.scautable.db.H2 as DbH2

  // The macro reads schema from the classpath snapshot at compile time.
  // This import makes `DB.sqlTable` extension available:
  // (it's defined in DBExtension.scala in this same package)

  test("DB.sqlTable compile-time types match expected schema") {
    // macro expands at compile time using the snapshot; constructing the table never connects
    val countries = DB.sqlTable[DbH2]("country")
    val liveDb = DB.connection[DbH2]
    // Verify compile-time type ascription
    val typed: NamedTupleTable[
      ("iso3", "name", "population", "area_km2", "is_island"),
      (String, String, Option[Long], Double, Boolean)
    ] = countries
    assertNotEquals(typed, null)
    assertNotEquals(liveDb, null)
  }

  test("DB.sqlTable runtime execution (requires SCAUTABLE_DB_URL)") {
    assume(sys.env.contains("SCAUTABLE_DB_URL"), "SCAUTABLE_DB_URL not set — skipping")
    val countries = DB.sqlTable[DbH2]("country")
    val liveDb = DB.connection[DbH2]
    val rows = liveDb.run(countries.select)
    assert(rows.nonEmpty, "Expected rows from runtime DB")
  }

end DbSqlTableSuite

// ---------------------------------------------------------------------------
// FlavourDialect resolution: each DbFlavour marker resolves to the expected
// scalasql dialect object.
// ---------------------------------------------------------------------------

class FlavourDialectSuite extends munit.FunSuite:
  import io.github.quafadas.scautable.db.{H2 as DbH2, Postgres as DbPostgres, MsSqlServer as DbMsSqlServer}

  test("H2 flavour resolves to scalasql.H2Dialect") {
    assertEquals(summon[FlavourDialect[DbH2]].dialect, scalasql.H2Dialect)
  }

  test("Postgres flavour resolves to scalasql.PostgresDialect") {
    assertEquals(summon[FlavourDialect[DbPostgres]].dialect, scalasql.PostgresDialect)
  }

  test("MsSqlServer flavour resolves to scalasql.MsSqlDialect") {
    assertEquals(summon[FlavourDialect[DbMsSqlServer]].dialect, scalasql.MsSqlDialect)
  }
end FlavourDialectSuite
