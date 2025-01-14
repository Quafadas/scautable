package io.github.quafadas.scautable

import scalatags.Text.all.*
import java.time.LocalDate

import scala.annotation.experimental
import NamedTuple.*
import CSV.*
import scala.compiletime.ops.int.S
import ConsoleFormat.*
import Excel.BadTableException

@experimental
class ExcelSuite extends munit.FunSuite:

  test("excel provider compiles and typechecks") {
    def csv: ExcelIterator[("Column 1", "Column 2", "Column 3")] = Excel.absolutePath(Generated.resourceDir0 + "SimpleTable.xlsx", "Sheet1")

    assertEquals(csv.column[("Column 1")].toList.head, "Row 1, Col 1")
    assertEquals(csv.column[("Column 1")].toList.last, "Row 3, Col 1")

  }

  test("excel provider throws on duplicated header") {
    assert(
     compileErrors(
        """Excel.absolutePath(Generated.resourceDir0 + "SimpleDup.xlsx", "Sheet1")"""
      ).contains("Duplicate header found: Column 3")
    )
    
  }

  test("excel provider compiles but throws on malformed table") {
    val csv: ExcelIterator[("Column 1", "Column 2", "Column 3")] = Excel.absolutePath(Generated.resourceDir0 + "SimpleTableWithExtendedRow.xlsx", "Sheet1")

    intercept[BadTableException] {
      println(csv.toList)
    }

  }

end ExcelSuite
