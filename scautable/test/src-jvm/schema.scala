import io.github.quafadas.table.*
import NamedTuple.*
import scala.quoted.Quotes

final val rd = CsvReadOptions2(
  ';'
)

class CSVSchemaSuite extends munit.FunSuite:

  test("Drop column by number") {
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.resource("simple.csv")

    val drp = csv.dropColumn[csv.Col[1]]

    println(csv.schemaGen)

  }

  test("Drop column by number") {

    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.resource(
      "simple.csv",
      CsvReadOptions2(
        ';'
      )
    )

    println(csv.schemaGen)

  }
end CSVSchemaSuite
