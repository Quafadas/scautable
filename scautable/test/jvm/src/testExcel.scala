package io.github.quafadas.scautable

import scalatags.Text.all.*
import java.time.LocalDate

import scala.annotation.experimental
import NamedTuple.*
import CSV.*
import scala.compiletime.ops.int.S
import ConsoleFormat.*

@experimental
class ExcelSuite extends munit.FunSuite:

  test("excel provider compiles and typechecks") {
    def csv: ExcelIterator[("Column 1", "Column 2", "Column 3")] = Excel.absolutePath(Generated.resourceDir0 + "SimpleTable.xlsx", "Sheet1")

    assertEquals(csv.column[("Column 1")].toList.head, "Row 1, Col 1")
    assertEquals(csv.column[("Column 1")].toList.last, "Row 3, Col 1")

  }

  test("excel provider compiles and typechecks with a blank row") {
    val csv: ExcelIterator[("Column 1", "Column 2", "Column 3")] = Excel.absolutePath(Generated.resourceDir0 + "SimpleTableWithExtendedRow.xlsx", "Sheet1")

    intercept[Exception] {
      println(csv.toList)
    }

  }
end ExcelSuite
