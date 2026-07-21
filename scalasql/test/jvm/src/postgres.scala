package io.github.quafadas.scautable.scalasql

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.munit.TestContainerForAll
import munit.FunSuite
import org.testcontainers.containers.wait.strategy.Wait
import java.net.URL
import scala.io.Source
import java.sql.DriverManager
import org.testcontainers.utility.DockerImageName

class PgSpec extends FunSuite with TestContainerForAll:

  override val containerDef = PostgreSQLContainer.Def(
    dockerImageName = DockerImageName.parse("postgres:15.1"),
    databaseName = "testcontainer-scala",
    username = sys.env("SCAUTABLE_DB_USER"),
    password = sys.env("SCAUTABLE_DB_PASSWORD")
  )

  test("PostgreSQL container should get connection") {
    withContainers { case pgContainer: PostgreSQLContainer =>
      Class.forName(pgContainer.driverClassName)
      val connection = DriverManager.getConnection(pgContainer.jdbcUrl, sys.env("SCAUTABLE_DB_USER"), sys.env("SCAUTABLE_DB_PASSWORD"))
      println(pgContainer.jdbcUrl)
      assert(!connection.isClosed())

      val statement = connection.createStatement()
      val createTable = "CREATE TABLE test_table (id SERIAL PRIMARY KEY, name VARCHAR(50), town text, something double precision);"
      statement.execute(createTable)





    }
  }
end PgSpec
