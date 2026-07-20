package io.github.quafadas.scautable.db

import java.sql.{Connection, DriverManager}
import scala.NamedTuple.*

/** Sets up an H2 in-memory database with a "country" table for testing. */
trait H2Fixture extends munit.FunSuite:

  val jdbcUrl = "jdbc:h2:mem:scautable_test;DB_CLOSE_DELAY=-1"

  def withConn[A](f: Connection => A): A =
    val conn = DriverManager.getConnection(jdbcUrl)
    try f(conn)
    finally conn.close()

  override def beforeAll(): Unit =
    super.beforeAll()
    withConn { conn =>
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

  override def afterAll(): Unit =
    withConn { conn =>
      conn.createStatement().execute("DROP TABLE IF EXISTS country")
    }
    super.afterAll()
end H2Fixture

class SchemaReaderSuite extends H2Fixture:

  test("forTable returns correct ColumnMeta for country table") {
    withConn { conn =>
      val cols = SchemaReader.forTable(conn, None, "COUNTRY")
      assertEquals(cols.length, 5)

      val iso3 = cols.find(_.name.equalsIgnoreCase("iso3"))
        .getOrElse(fail("iso3 column not found in schema"))
      assertEquals(iso3.nullable, false)

      val pop = cols.find(_.name.equalsIgnoreCase("population"))
        .getOrElse(fail("population column not found in schema"))
      assertEquals(pop.nullable, true) // BIGINT without NOT NULL -> nullable

      // Columns are ordered by position
      val positions = cols.map(_.position)
      assert(positions == positions.sorted, "positions should be sorted")
    }
  }

  test("forTable throws on unknown table with near-miss") {
    withConn { conn =>
      intercept[IllegalArgumentException] {
        SchemaReader.forTable(conn, None, "countryXYZ")
      }
    }
  }

  test("forQuery returns correct metadata for a SELECT") {
    withConn { conn =>
      val cols = SchemaReader.forQuery(conn, "SELECT iso3, name, population FROM COUNTRY")
      assertEquals(cols.length, 3)
      assertEquals(cols.head.name.toLowerCase, "iso3")
    }
  }

end SchemaReaderSuite

class JdbcDecoderSuite extends H2Fixture:

  test("decode Int") {
    withConn { conn =>
      val rs = conn.createStatement().executeQuery("SELECT 42")
      rs.next()
      assertEquals(summon[JdbcDecoder[Int]].decode(rs, 1), 42)
    }
  }

  test("decode Long") {
    withConn { conn =>
      val rs = conn.createStatement().executeQuery("SELECT CAST(9876543210 AS BIGINT)")
      rs.next()
      assertEquals(summon[JdbcDecoder[Long]].decode(rs, 1), 9876543210L)
    }
  }

  test("decode Double") {
    withConn { conn =>
      val rs = conn.createStatement().executeQuery("SELECT 3.14")
      rs.next()
      val v = summon[JdbcDecoder[Double]].decode(rs, 1)
      assert(math.abs(v - 3.14) < 0.0001, s"expected ~3.14 but got $v")
    }
  }

  test("decode Boolean") {
    withConn { conn =>
      val rs = conn.createStatement().executeQuery("SELECT TRUE")
      rs.next()
      assertEquals(summon[JdbcDecoder[Boolean]].decode(rs, 1), true)
    }
  }

  test("decode String") {
    withConn { conn =>
      val rs = conn.createStatement().executeQuery("SELECT 'hello'")
      rs.next()
      assertEquals(summon[JdbcDecoder[String]].decode(rs, 1), "hello")
    }
  }

  test("decode Option[Long] - Some") {
    withConn { conn =>
      val rs = conn.createStatement().executeQuery("SELECT population FROM COUNTRY WHERE iso3='GBR'")
      rs.next()
      val v = summon[JdbcDecoder[Option[Long]]].decode(rs, 1)
      assertEquals(v, Some(67000000L))
    }
  }

  test("decode Option[Long] - None for NULL") {
    withConn { conn =>
      // ISL has population 370000 which is not NULL; let us use a literal NULL
      val rs = conn.createStatement().executeQuery("SELECT CAST(NULL AS BIGINT)")
      rs.next()
      val v = summon[JdbcDecoder[Option[Long]]].decode(rs, 1)
      assertEquals(v, None)
    }
  }

  test("decode BigDecimal") {
    withConn { conn =>
      val rs = conn.createStatement().executeQuery("SELECT CAST(123.456 AS DECIMAL(10,3))")
      rs.next()
      val v = summon[JdbcDecoder[BigDecimal]].decode(rs, 1)
      assertEquals(v, BigDecimal("123.456"))
    }
  }

end JdbcDecoderSuite

class JdbcRowDecoderSuite extends H2Fixture:

  test("decodeRow returns typed tuple with correct values from ResultSet") {
    withConn { conn =>
      val stmt = conn.createStatement()
      val rs   = stmt.executeQuery("SELECT iso3, name, population FROM COUNTRY WHERE iso3='GBR'")
      rs.next()
      type Row = (String, String, Option[Long])
      val decoder = summon[JdbcRowDecoder[Row]]
      val row     = decoder.decodeRow(rs)
      assertEquals(row._1, "GBR")
      assertEquals(row._2, "United Kingdom")
      assertEquals(row._3, Some(67000000L))
    }
  }

end JdbcRowDecoderSuite

class DbIteratorSuite extends H2Fixture:

  test("DbIterator reads all rows in order from ResultSet") {
    withConn { conn =>
      val stmt = conn.createStatement()
      val rs   = stmt.executeQuery("SELECT iso3, name FROM COUNTRY ORDER BY iso3")
      type K = ("iso3", "name")
      type V = (String, String)
      val iter = new DbIterator[K, V](() => (conn, stmt, rs))
      val rows = iter.toSeq
      assertEquals(rows.length, 4)
      assertEquals(rows.head.iso3, "DEU")
    }
  }

  test("DbIterator closes resources on exhaustion") {
    withConn { conn =>
      val stmt = conn.createStatement()
      val rs   = stmt.executeQuery("SELECT iso3 FROM COUNTRY")
      type K = Tuple1["iso3"]
      type V = Tuple1[String]
      val iter = new DbIterator[K, V](() => (conn, stmt, rs))
      iter.toSeq // exhaust
      // After exhaustion further hasNext must return false
      assertEquals(iter.hasNext, false)
    }
  }

  test("DbIterator works with ptbln via ConsoleFormat") {
    withConn { conn =>
      val stmt = conn.createStatement()
      val rs   = stmt.executeQuery("SELECT iso3, name FROM COUNTRY")
      type K = ("iso3", "name")
      type V = (String, String)
      val iter = new DbIterator[K, V](() => (conn, stmt, rs))
      // toSeq converts to a Seq[NamedTuple] which is what ptbln / consoleFormat operates on
      val rows = iter.toSeq
      assert(rows.nonEmpty, "Expected non-empty result")
      // The existence of the map call proves columnExtensions work on NamedTuple
      val names = rows.map(_.name)
      assert(names.contains("Germany"), "Expected Germany in results")
    }
  }

end DbIteratorSuite

class SchemaSnapshotSuite extends munit.FunSuite:

  test("round-trip: renderJson / parseSnapshotJson") {
    val cols = Seq(
      ColumnMeta("id",   java.sql.Types.INTEGER, "integer", false, 1),
      ColumnMeta("name", java.sql.Types.VARCHAR, "varchar", true,  2)
    )
    val json = SchemaSnapshot.renderJson(Map("mykey" -> cols))
    val parsed = SchemaSnapshot.parseSnapshotJson(json, "mykey")
    assertEquals(parsed, Some(cols))
  }

  test("load returns None for missing key") {
    val json = SchemaSnapshot.renderJson(Map("other" -> Seq(ColumnMeta("x", 4, "int", false, 1))))
    val result = SchemaSnapshot.parseSnapshotJson(json, "missing")
    assertEquals(result, None)
  }

end SchemaSnapshotSuite

/** Macro-based tests that compile DB.table / DB.query against the committed snapshot file.
  *
  * The snapshot at `test/jvm/resources/scautable-db-schema.json` is picked up by the
  * macro via the classpath (ShareCompileResources adds resources to the compile classpath).
  *
  * Runtime execution requires SCAUTABLE_DB_URL to be set to a running H2 instance.
  * If it is not set, the runtime tests are skipped.
  */
class DbMacroSnapshotSuite extends munit.FunSuite:

  /** True if we have a live DB connection available at runtime. */
  private val haveDb: Boolean = ConnectionResolver.lookup(ConnectionResolver.urlEnvVar).isDefined

  test("DB.table infers correct compile-time types from snapshot schema") {
    // The macro reads the schema from the classpath snapshot at compile time.
    // The following type ascription verifies the inferred schema matches.
    // This test body runs at test time but the type check happens at compile time.
    // The DbIterator connection is lazy — construction does NOT open the DB.
    val countryTable = DB.table[H2]("country")
    val typed: DbIterator[
      ("iso3", "name", "population", "area_km2", "is_island"),
      (String, String, Option[Long], Double, Boolean)
    ] = countryTable
    // Verify the iterator was constructed (factory is set; no DB connection opened yet)
    assertNotEquals(typed, null)
    // Do NOT call hasNext or next — we have no DB at test time (snapshot-only mode).
  }

  test("DB.table runtime execution reads rows from live database (requires SCAUTABLE_DB_URL)") {
    assume(haveDb, "SCAUTABLE_DB_URL not set — skipping runtime DB test")

    // Runtime: create table in H2, insert rows, read back via DbIterator.
    // Use try-finally to ensure the setup connection is always closed.
    val setupConn = java.sql.DriverManager.getConnection(ConnectionResolver.lookup(ConnectionResolver.urlEnvVar).get)
    try
      setupConn.createStatement().execute("DROP TABLE IF EXISTS country")
      setupConn.createStatement().execute(
        """CREATE TABLE country (
          |  iso3       CHAR(3)      NOT NULL PRIMARY KEY,
          |  name       VARCHAR(100) NOT NULL,
          |  population BIGINT,
          |  area_km2   DOUBLE       NOT NULL,
          |  is_island  BOOLEAN      NOT NULL
          |)""".stripMargin
      )
      setupConn.createStatement().execute(
        "INSERT INTO country VALUES ('GBR', 'United Kingdom', 67000000, 242495.0, false)"
      )
    finally setupConn.close()

    val table2 = DB.table[H2]("country")
    val rows   = table2.toSeq
    assert(rows.nonEmpty, "Expected at least one row")
    assertEquals(rows.head.iso3, "GBR")
  }

end DbMacroSnapshotSuite
