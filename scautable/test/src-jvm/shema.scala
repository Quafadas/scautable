import scala.NamedTuple.*

import io.github.quafadas.table.*

class CSVSchemaSuite extends munit.FunSuite:
  test("Drop column by number") {

    val csv: CsvIterator[("col1", "col2", "col3"), (Int, Int, Int)] = CSV.resource("simple.csv", TypeInferrer.FirstRow)
    csv.dropColumn[csv.Col[1]]
    println(csv.schemaGen)
  }
end CSVSchemaSuite
