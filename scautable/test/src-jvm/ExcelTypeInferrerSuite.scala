package io.github.quafadas.scautable
import scala.NamedTuple.*

import io.github.quafadas.table.*

class ExcelTypeInferrerSuite extends munit.FunSuite:

  // ---------------------------
  // TypeInferrer.StringType
  // ---------------------------

  test("Excel TypeInferrer.StringType should treat all columns as String") {
    val excel: TypedExcelIterator[("Column 1", "Column 2", "Column 3"), (String, String, String)] =
      Excel.resource(
        "SimpleTable.xlsx",
        "Sheet1",
        TypeInferrer.StringType
      )

    assertEquals(excel.headers, List("Column 1", "Column 2", "Column 3"))

    val rows = excel.toArray
    assertEquals(rows.length, 3)

    assertEquals(rows(0).`Column 1`, "Row 1, Col 1")
    assertEquals(rows(0).`Column 2`, "Row 1, Col 2")           
    assertEquals(rows(0).`Column 3`, "Row 1, Col 3")

    assertEquals(rows(1).`Column 1`, "Row 2, Col 1")
    assertEquals(rows(2).`Column 1`, "Row 3, Col 1")
  }

  // ---------------------------
  // TypeInferrer.FirstRow
  // ---------------------------

  test("Excel TypeInferrer.FirstRow should automatically detect column types") {
    val excel = Excel.resource(
      "SimpleTable.xlsx",
      "Sheet1",
      TypeInferrer.FirstRow
    )

    assertEquals(excel.headers, List("Column 1", "Column 2", "Column 3"))

    val rows = excel.toArray
    assertEquals(rows.length, 3)

    // All values should be Strings since Excel test data contains text
    assertEquals(rows(0).`Column 1`, "Row 1, Col 1")
    assertEquals(rows(0).`Column 2`, "Row 1, Col 2")
    assertEquals(rows(0).`Column 3`, "Row 1, Col 3")

    assertEquals(rows(1).`Column 1`, "Row 2, Col 1")
    assertEquals(rows(2).`Column 1`, "Row 3, Col 1")
  }

  // ---------------------------
  // TypeInferrer.FirstN
  // ---------------------------

  test("Excel TypeInferrer.FirstN should detect types from multiple rows") {
    val excel = Excel.resource(
      "SimpleTable.xlsx",
      "Sheet1",
      TypeInferrer.FirstN(2)
    )

    val rows = excel.toArray
    assertEquals(rows.length, 3) 

    assertEquals(rows(0).`Column 1`, "Row 1, Col 1")
    assertEquals(rows(1).`Column 1`, "Row 2, Col 1")
    assertEquals(rows(2).`Column 1`, "Row 3, Col 1")
  }

  // ---------------------------
  // TypeInferrer.FromAllRows
  // ---------------------------

  test("Excel TypeInferrer.FromAllRows should inspect all data for type inference") {
    val excel = Excel.resource(
      "SimpleTable.xlsx",
      "Sheet1",
      TypeInferrer.FromAllRows
    )

    val rows = excel.toArray
    assertEquals(rows.length, 3)

    assertEquals(rows(0).`Column 1`, "Row 1, Col 1")
    assertEquals(rows(1).`Column 1`, "Row 2, Col 1")
    assertEquals(rows(2).`Column 1`, "Row 3, Col 1")
  }
  
  // ---------------------------
  // TypeInferrer.FromTuple[T]()
  // ---------------------------

  test("Excel TypeInferrer.fromTuple should apply provided column types explicitly") {
    
    val excel: TypedExcelIterator[("Column 1", "Column 2", "Column 3"), (String, String, String)] =
      Excel.resource(
        "SimpleTable.xlsx",
        "Sheet1",
        TypeInferrer.FromTuple[(String, String, String)]()
      )

    assertEquals(excel.headers, List("Column 1", "Column 2", "Column 3"))

    val rows = excel.toArray
    assertEquals(rows.length, 3)

    assertEquals(rows(0).`Column 1`, "Row 1, Col 1")
    assertEquals(rows(0).`Column 2`, "Row 1, Col 2")            
    assertEquals(rows(0).`Column 3`, "Row 1, Col 3")

    assertEquals(rows(1).`Column 1`, "Row 2, Col 1")
    assertEquals(rows(2).`Column 1`, "Row 3, Col 1")
  }

  // ---------------------------
  // Range-specific tests
  // ---------------------------

  test("Excel TypeInferrer with range should work correctly") {
    val excel = Excel.resource(
      "SimpleTableColOffset.xlsx",
      "Sheet1", 
      "D1:F4",
      TypeInferrer.StringType
    )

    val rows = excel.toArray
    assertEquals(rows.length, 3)
    assertEquals(excel.headers, List("Column 1", "Column 2", "Column 3"))
  }

  test("Excel TypeInferrer with absolutePath should work") {
    val resourcePath = this.getClass.getClassLoader.getResource("SimpleTable.xlsx")
    if resourcePath != null then
      val excel = Excel.absolutePath(
        resourcePath.getPath,
        "Sheet1",
        TypeInferrer.StringType
      )

      val rows = excel.toArray
      assertEquals(rows.length, 3)
      assertEquals(excel.headers, List("Column 1", "Column 2", "Column 3"))
  }

  // ---------------------------
  // Missing.xlsx - Testing numeric and missing values
  // ---------------------------

  test("Excel TypeInferrer should handle missing values correctly") {
    val excel = Excel.resource(
      "Missing.xlsx",
      "Sheet1",
      "A1:C4",
      TypeInferrer.FirstRow
    )

    val rows = excel.toArray
    assertEquals(rows.length, 3)
    assertEquals(excel.headers, List("Column 1", "Column 2", "Column 3"))
    
    // Test that blank/missing cells are handled properly
    assert(rows(1).`Column 2` == "") // blank cell
    assert(rows(2).`Column 3` == "") // missing cell
  }

end ExcelTypeInferrerSuite