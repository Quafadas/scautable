package io.github.quafadas.scautable

import io.github.quafadas.table.*
import NamedTuple.*
import scala.quoted.Quotes
import java.awt.Window.Type

class CSVSchemaSuite extends munit.FunSuite:

  test("Drop column by number") {
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.resource("simple.csv")

    val drp = csv.dropColumn[csv.Col[1]]

    println(csv.schemaGen)

  }

  test("Drop column by number") {

    val csv2 = CSV.resource("simple.csv", CsvReadOptions(';', TypeInferenceStrategy.StringsOnly))

    println(csv2.getOpts)

    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.resource(
      "simple.csv",
      CsvReadOptions(
        ',',
        TypeInferenceStrategy.AutoType
      )
    )

    println(csv.getOpts)

  }
end CSVSchemaSuite
