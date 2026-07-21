package io.github.quafadas.scautable.scalasql

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.munit.TestContainerForAll
import munit.FunSuite
import org.testcontainers.containers.wait.strategy.Wait
import java.net.URL
import scala.io.Source
import java.sql.DriverManager
import org.testcontainers.utility.DockerImageName

class PgSpec extends FunSuite with TestContainerForAll {

  override val containerDef = PostgreSQLContainer.Def(
    dockerImageName = DockerImageName.parse("postgres:15.1"),
    databaseName = "testcontainer-scala",
    username = "scala",
    password = "scala"
  )

  test("PostgreSQL container should get connection") {
    withContainers { case pgContainer: PostgreSQLContainer =>
      Class.forName(pgContainer.driverClassName)
      val connection = DriverManager.getConnection(pgContainer.jdbcUrl, pgContainer.username, pgContainer.password)
      assert(!connection.isClosed())
    }
  }
}