package io.github.quafadas.scautable

import io.github.quafadas.table.*
import java.time.LocalDate

import NamedTuple.*
import scala.compiletime.ops.int.S

import Excel.BadTableException

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

  test("ExcelIterator with colStart parameter") {    
    def csv: ExcelIterator[("Column 1", "Column 2", "Column 3")] = Excel.absolutePath(Generated.resourceDir0 + "SimpleTableColOffset.xlsx", "Sheet1", "D1:F4")
     
    val csvSeq = csv.toSeq
    assertEquals(csvSeq.column["Column 1"].toList.head, "Row 1, Col 1")
    assertEquals(csvSeq.column["Column 1"].toList.last, "Row 3, Col 1")
    assertEquals(csvSeq.column["Column 3"].toList.last, "Row 3, Col 3")

  }

  test("ExcelIterator range") {
    def csv: ExcelIterator[("Column 1", "Column 2", "Column 3")] = Excel.absolutePath(Generated.resourceDir0 + "Offset.xlsx", "Sheet1", "E3:G6")    
    val csvSeq = csv.toSeq
    // csvSeq.ptbln
    assertEquals(csv.column["Column 2"].toList.head, "Row 1, Col 2")
    assertEquals(csv.column["Column 2"].toList.last, "Row 3, Col 2")

  }

end ExcelSuite