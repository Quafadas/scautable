package io.github.quafadas.scautable

import scala.NamedTuple.*

import io.github.quafadas.table.*
import io.github.quafadas.table.Excel.BadTableException

class ExcelSuite extends munit.FunSuite:

  test("excel provider compiles and typechecks") {
    def csv = Excel.resource("SimpleTable.xlsx", "Sheet1", "", TypeInferrer.StringType)

    assertEquals(csv.column["Column 1"].toList.head, "Row 1, Col 1")
    assertEquals(csv.column["Column 1"].toList.last, "Row 3, Col 1")

  }

  test("excel provider with explicit StringType TypeInferrer") {
    def csv = Excel.resource("SimpleTable.xlsx", "Sheet1", "", TypeInferrer.StringType)

    assertEquals(csv.column["Column 1"].toList.head, "Row 1, Col 1")
    assertEquals(csv.column["Column 1"].toList.last, "Row 3, Col 1")

  }

  test("excel provider throws on duplicated header") {
    assert(
      compileErrors(
        """Excel.resource("SimpleDup.xlsx", "Sheet1", "", TypeInferrer.StringType)"""
      ).contains("Duplicate header found: Column 3")
    )

  }

  test("excel provider compiles but throws on malformed table") {
    val csv = Excel.resource("SimpleTableWithExtendedRow.xlsx", "Sheet1", "", TypeInferrer.StringType)

    intercept[BadTableException] {
      println(csv.toList)
    }

  }

  test("ExcelIterator with colStart parameter") {
    def csv = Excel.resource("SimpleTableColOffset.xlsx", "Sheet1", "D1:F4", TypeInferrer.StringType)

    val csvSeq = csv.toSeq
    assertEquals(csvSeq.column["Column 1"].toList.head, "Row 1, Col 1")
    assertEquals(csvSeq.column["Column 1"].toList.last, "Row 3, Col 1")
    assertEquals(csvSeq.column["Column 3"].toList.last, "Row 3, Col 3")

  }

  test("ExcelIterator range") {
    def csv = Excel.resource("Offset.xlsx", "Sheet1", "E3:G6", TypeInferrer.StringType)
    val csvSeq = csv.toSeq
    // csvSeq.ptbln
    assertEquals(csv.column["Column 2"].toList.head, "Row 1, Col 2")
    assertEquals(csv.column["Column 2"].toList.last, "Row 3, Col 2")
  }

  test("ExcelIterator Missing and blank values") {
    // Checks that we've set the Missing cell policy correctly
    def csv = Excel.resource("Missing.xlsx", "Sheet1", "A1:C4", TypeInferrer.StringType)
    val csvSeq = csv.toSeq
    // csvSeq.ptbln
    assertEquals(csv.column["Column 2"].toList.drop(1).head, "") // blank
    assertEquals(csv.column["Column 3"].toList.drop(2).head, "") // missing

  }

  test("excel provider rejects unsupported TypeInferrer at compile time") {
    assert(
      compileErrors(
        """Excel.resource("SimpleTable.xlsx", "Sheet1", "", TypeInferrer.FirstRow)"""
      ).contains("TypeInferrer FirstRow is not yet supported for Excel")
    )

    assert(
      compileErrors(
        """Excel.resource("SimpleTable.xlsx", "Sheet1", "", TypeInferrer.FromAllRows)"""
      ).contains("TypeInferrer FromAllRows is not yet supported for Excel")
    )
    
    // FirstN falls back to the general case
    val firstNError = compileErrors(
        """Excel.resource("SimpleTable.xlsx", "Sheet1", "", TypeInferrer.FirstN(5))"""
    )
    assert(firstNError.contains("Only TypeInferrer.StringType is currently supported for Excel"))
  }

end ExcelSuite
