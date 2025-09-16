import scala.NamedTuple.*

import io.github.quafadas.table.*

class CSVSchemaSuite extends munit.FunSuite:
    test("Drop column by number") {
        inline val csvContent = "col1,col2,col3\n1,2,3\n4,5,6"
        val csv: CsvIterator[("col1", "col2", "col3"), (Int, Int, Int)] = CSV.resource("simple.csv", TypeInferrer.FirstRow)
        val drp = csv.dropColumn[csv.Col[1]]
        println(csv.schemaGen)
    }
end CSVSchemaSuite

