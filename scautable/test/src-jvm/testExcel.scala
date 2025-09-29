package io.github.quafadas.scautable


import scala.NamedTuple.*

import io.github.quafadas.table.{*, given}
class ExcelSuite extends munit.FunSuite:

  test("excel provider compiles and typechecks") {
    def csv = Excel.resource("SimpleTable.xlsx", "Sheet1", "", TypeInferrer.StringType)

    assertEquals(csv.column["Column 1"].toList.head, "Row 1, Col 1")
    assertEquals(csv.column["Column 1"].toList.last, "Row 3, Col 1")

  }

  test("Excel simple with default StringType inference") {
    val csv = Excel.resource("SimpleTable.xlsx", "Sheet1")
    val csv2 = Excel.resource("SimpleTable.xlsx", "Sheet1", TypeInferrer.StringType)
    val seq = csv.toSeq

    assertEquals(seq, csv2.toSeq)
    assertEquals(seq.size, 3)
    assertEquals(seq.column["Column 1"].toList.head, "Row 1, Col 1")
    assertEquals(seq.column["Column 1"].toList.last, "Row 3, Col 1")
  }

  test("Numbers") {
    val csv2 = Excel.resource("Numbers.xlsx", "Sheet1", TypeInferrer.FromAllRows)
    csv2.toSeq.ptbln
    val csv = Excel.resource("Numbers.xlsx", "Sheet1", "A1:C3", TypeInferrer.FromAllRows)
    val seq = csv.toSeq

    assertEquals(seq.size, 2)
    assertEquals(seq.column["Doubles"].toList.head, 1.1)
    assertEquals(seq.column["Int"].toList.head, 1)
    assertEquals(seq.column["Longs"].toList.head, 1)
    assert(
      compileErrors("""assertEquals(seq.column["Strings"].toList.head, "blah")""").contains("""Column ("Strings" : String) not found""")
    )
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

  test("excel provider with FromTuple TypeInferrer enforces specific types") {
    // Force specific types using FromTuple - all columns are actually strings in SimpleTable.xlsx
    def csv = Excel.resource("SimpleTable.xlsx", "Sheet1", "", TypeInferrer.FromTuple[(String, String, String)]())

    // Test that we can access the data with correct types
    assertEquals(csv.column["Column 1"].toList.head, "Row 1, Col 1") // String column
    assertEquals(csv.column["Column 2"].toList.head, "Row 1, Col 2") // String column
    assertEquals(csv.column["Column 3"].toList.head, "Row 1, Col 3") // String column

    // Verify we can access the data
    assertEquals(csv.size, 3) // 3 data rows
  }

  test("excel provider with FromTuple TypeInferrer (Double, Int, Long String)") {
    def csv = Excel.resource("Numbers.xlsx", "Sheet1", "", TypeInferrer.FromTuple[(Double, Int, Long, String)]())

    // Test that we can access the data with correct types
    assertEquals(csv.column["Doubles"].toList.head, 1.1)
    assertEquals(csv.column["Int"].toList.head, 1)
    assertEquals(csv.column["Longs"].toList.head, 1L)

    // Verify we can access the data
    assertEquals(csv.size, 2) // 3 data rows
  }

  test("excel provider with TypeInferrer.FirstRow should infer types from first row") {
    // Test FirstRow with Numbers.xlsx - should be equivalent to FirstN(1)
    def csv = Excel.resource("Numbers.xlsx", "Sheet1", "", TypeInferrer.FirstRow)

    // Verify that we can read the data and it compiles with inferred types
    val rows = csv.toList
    assertEquals(rows.size, 2)

    // Test access to columns with the inferred types (should be same as FirstN(1))
    assertEquals(csv.column["Doubles"].toList.head, 1.1) // Double
    assertEquals(csv.column["Strings"].toList.head, "blah") // String
    assertEquals(csv.column["Int"].toList.head, 1) // Apache POI correctly identifies as Int
    assertEquals(csv.column["Longs"].toList.head, 1) // Apache POI correctly identifies as Int
  }

  test("excel provider with TypeInferrer.FromAllRows should infer types from all rows") {
    // Test FromAllRows with Numbers.xlsx - should consider all data rows for type inference
    def csv = Excel.resource("Numbers.xlsx", "Sheet1", "", TypeInferrer.FromAllRows)

    // Verify that we can read the data and it compiles with inferred types
    val rows = csv.toList
    assertEquals(rows.size, 2)

    // Test access to columns with the inferred types
    assertEquals(csv.column["Doubles"].toList.head, 1.1) // Double
    assertEquals(csv.column["Strings"].toList.head, "blah") // String
    // Apache POI correctly identifies these as integers
    assertEquals(csv.column["Int"].toList.head, 1) // Apache POI correctly identifies as Int
    assertEquals(csv.column["Longs"].toList.head, 1) // Apache POI correctly identifies as Int
  }

  test("excel provider all TypeInferrer variants now supported") {
    // Just test compilation - no runtime assertions needed
    def csvFirstRow = Excel.resource("Numbers.xlsx", "Sheet1", "", TypeInferrer.FirstRow)
    def csvFromAllRows = Excel.resource("Numbers.xlsx", "Sheet1", "", TypeInferrer.FromAllRows)
    def csvFirstN = Excel.resource("Numbers.xlsx", "Sheet1", "", TypeInferrer.FirstN(2))
    def csvString = Excel.resource("Numbers.xlsx", "Sheet1", "", TypeInferrer.StringType)
    def csvFromTuple = Excel.resource("Numbers.xlsx", "Sheet1", "", TypeInferrer.FromTuple[(Double, Double, Double, String)]())

  }

  test("excel provider with TypeInferrer.FirstN should infer types automatically") {
    // Test FirstN with Numbers.xlsx - based on error message, types are inferred as:
    // (Doubles : Double, Int : Double, Longs : Double, Strings : String)
    def csv = Excel.resource("Numbers.xlsx", "Sheet1", "", TypeInferrer.FirstN(2))

    // Verify that we can read the data and it compiles with inferred types
    val rows = csv.toList
    assertEquals(rows.size, 2)

    // Test access to columns with the actually inferred types
    assertEquals(csv.column["Doubles"].toList.head, 1.1) // Double
    assertEquals(csv.column["Strings"].toList.head, "blah") // String

    // Apache POI correctly identifies these as integers based on cell type
    assertEquals(csv.column["Int"].toList.head, 1) // Apache POI correctly identifies as Int
    assertEquals(csv.column["Longs"].toList.head, 1) // Apache POI correctly identifies as Int
  }

  test("excel provider with TypeInferrer.FirstN and preferIntToBoolean parameter") {
    // Test FirstN with custom preferIntToBoolean setting - just verify compilation and basic access
    def csv = Excel.resource("Numbers.xlsx", "Sheet1", "", TypeInferrer.FirstN(1, false))

    // Verify that we can read the data and it compiles with inferred types
    val rows = csv.toList
    assertEquals(rows.size, 2)

    // Basic functionality test - verify we can access data (don't assert specific types due to preferIntToBoolean differences)
    assertEquals(csv.column["Doubles"].toList.head, 1.1)
    assertEquals(csv.column["Strings"].toList.head, "blah")
    // Note: Int/Longs columns may be inferred differently with preferIntToBoolean=false
  }

end ExcelSuite
