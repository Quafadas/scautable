package io.github.quafadas.scautable

import scala.NamedTuple.*

import io.github.quafadas.table.{*, given}

/** The cell contents of "Numbers.xlsx"
  *
  * Doubles Int Longs Strings 1.10 1.00 1.00 blah 2.20 2.00 3.00 blah
  */
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
    val csv2 = Excel.resource("Numbers.xlsx", "Sheet1", "A1:D3", TypeInferrer.FromAllRows)
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

  /** "Missing.xlsx" spans A1:C4. Its blanks are staggered, one row apart, which is what makes it a usable probe for how far `FirstN` actually samples:
    *
    * Column 1 Column 2 Column 3 Row 1, Col 1 Row 1, Col 2 Row 1, Col 3 Row 2, Col 1 <blank> Row 2, Col 3 Row 3, Col 1 Row 3, Col 2 <blank>
    *
    * So the second column only becomes optional once two data rows have been read, and the third only once three have. The type ascriptions below are the assertion - a regression
    * here is a compile error, not a failed `assertEquals`.
    */
  test("an anchored range is resolved at compile time and readable off the iterator") {
    // The macro bakes the *resolved* range into the generated iterator, so `getColRange` is how you
    // see what an anchor turned into without recompiling under SCAUTABLE_RANGE.
    def anchored = Excel.resource("Missing.xlsx", "Sheet1", "A1", TypeInferrer.StringType)
    assertEquals(anchored.getColRange, Some("A1:C4"))

    // A pinned range is passed through untouched.
    def pinned = Excel.resource("Missing.xlsx", "Sheet1", "A1:C4", TypeInferrer.StringType)
    assertEquals(pinned.getColRange, Some("A1:C4"))

    // No range at all resolves to None rather than Some(""), and the iterator reads the whole sheet.
    def whole = Excel.resource("Missing.xlsx", "Sheet1", TypeInferrer.StringType)
    assertEquals(whole.getColRange, None)
  }

  test("excel TypeInferrer.FirstN stops sampling at N rows when a range is given") {
    // `def`, not `val` - an ExcelIterator is an Iterator, so each use needs a fresh one.

    // One data row read, none of it blank, so nothing is optional.
    def one: ExcelIterator[("Column 1", "Column 2", "Column 3"), (String, String, String)] =
      Excel.resource("Missing.xlsx", "Sheet1", "A1:C4", TypeInferrer.FirstN(1))

    // Two data rows read, which reaches the blank in column 2 but not the one in column 3.
    def two: ExcelIterator[("Column 1", "Column 2", "Column 3"), (String, Option[String], String)] =
      Excel.resource("Missing.xlsx", "Sheet1", "A1:C4", TypeInferrer.FirstN(2))

    // All three data rows read, so both blanks are seen.
    def three: ExcelIterator[("Column 1", "Column 2", "Column 3"), (String, Option[String], Option[String])] =
      Excel.resource("Missing.xlsx", "Sheet1", "A1:C4", TypeInferrer.FirstN(3))

    // Sampling more rows than the range holds is not an error, it just saturates.
    def more: ExcelIterator[("Column 1", "Column 2", "Column 3"), (String, Option[String], Option[String])] =
      Excel.resource("Missing.xlsx", "Sheet1", "A1:C4", TypeInferrer.FirstN(1000))

    // FromAllRows arrives at the macro as FirstN(Int.MaxValue) - the row cap must not overflow.
    def all: ExcelIterator[("Column 1", "Column 2", "Column 3"), (String, Option[String], Option[String])] =
      Excel.resource("Missing.xlsx", "Sheet1", "A1:C4", TypeInferrer.FromAllRows)

    // FirstRow is FirstN(1), and must agree with it.
    def first: ExcelIterator[("Column 1", "Column 2", "Column 3"), (String, String, String)] =
      Excel.resource("Missing.xlsx", "Sheet1", "A1:C4", TypeInferrer.FirstRow)

    // Narrow sampling only narrows the *types* - every row of the range is still read back.
    assertEquals(one.size, 3)
    assertEquals(two.size, 3)
    assertEquals(three.size, 3)

    // The staggered blanks, read back under each inferred type. An optional column decodes a blank
    // to None; a column that was not sampled far enough to become optional decodes it to "".
    assertEquals(two.column["Column 2"].toList(1), None)
    assertEquals(two.column["Column 3"].toList(2), "")
    assertEquals(three.column["Column 3"].toList(2), None)
    assertEquals(all.column["Column 3"].toList(2), None)
    assertEquals(more.column["Column 2"].toList(1), None)
    assertEquals(first.column["Column 2"].toList(1), "")

    // Populated cells are unaffected by how far the sampling went.
    assertEquals(two.column["Column 2"].toList(0), Some("Row 1, Col 2"))
    assertEquals(one.column["Column 2"].toList(0), "Row 1, Col 2")
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
    val csv = Excel.resource("Numbers.xlsx", "Sheet1", "A1:D3", TypeInferrer.FirstRow)

    // Verify that we can read the data and it compiles with inferred types
    val rows: List[(Doubles: Double, Int: Int, Longs: Int, Strings: String)] = csv.toList
    assertEquals(rows.size, 2)

    // Test access to columns with the inferred types (should be same as FirstN(1))
    assertEquals(rows.column["Doubles"].toList.head, 1.1) // Double
    assertEquals(rows.column["Strings"].toList.head, "blah") // String
    assertEquals(rows.column["Int"].toList.head, 1) // Apache POI correctly identifies as Int
    assertEquals(rows.column["Longs"].toList.head, 1) // Apache POI correctly identifies as Int
  }

  test("excel provider with TypeInferrer.FromAllRows should infer types from all rows") {
    // Test FromAllRows with Numbers.xlsx - should consider all data rows for type inference
    val csv = Excel.resource("Numbers.xlsx", "Sheet1", "", TypeInferrer.FromAllRows)

    // Verify that we can read the data and it compiles with inferred types
    val rows = csv.toList
    assertEquals(rows.size, 2)

    // Test access to columns with the inferred types
    assertEquals(rows.column["Doubles"].toList.head, 1.1) // Double
    assertEquals(rows.column["Strings"].toList.head, "blah") // String
    // Apache POI correctly identifies these as integers
    assertEquals(rows.column["Int"].toList.head, 1) // Apache POI correctly identifies as Int
    assertEquals(rows.column["Longs"].toList.head, 1) // Apache POI correctly identifies as Int
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
    val csv = Excel.resource("Numbers.xlsx", "Sheet1", "", TypeInferrer.FirstN(2))

    // Verify that we can read the data and it compiles with inferred types
    val rows = csv.toList
    assertEquals(rows.size, 2)

    // Test access to columns with the actually inferred types
    assertEquals(rows.column["Doubles"].toList.head, 1.1) // Double
    assertEquals(rows.column["Strings"].toList.head, "blah") // String

    // Apache POI correctly identifies these as integers based on cell type
    assertEquals(rows.column["Int"].toList.head, 1) // Apache POI correctly identifies as Int
    assertEquals(rows.column["Longs"].toList.head, 1) // Apache POI correctly identifies as Int
  }

  test("excel provider with TypeInferrer.FirstN and preferIntToBoolean parameter") {
    // Test FirstN with custom preferIntToBoolean setting - just verify compilation and basic access
    val csv: ExcelIterator[("Doubles", "Int", "Longs", "Strings"), (Double, Int, Int, String)] = Excel.resource("Numbers.xlsx", "Sheet1", "A1:D3", TypeInferrer.FirstN(1, false))

    // Verify that we can read the data and it compiles with inferred types
    val rows = csv.toList
    assertEquals(rows.size, 2)

    // Basic functionality test - verify we can access data (don't assert specific types due to preferIntToBoolean differences)
    assertEquals(rows.column["Doubles"].toList.head, 1.1)
    assertEquals(rows.column["Strings"].toList.head, "blah")
    // Note: Int/Longs columns may be inferred differently with preferIntToBoolean=false
  }

  /** Data in cells B6:G11 of Bands.xlsx Country < 1% 1% to 2% 2% to 3% 3% to 4% > 4% Japan 5.0% 15.0% 30.0% 0.0% 0.0% UK 0.0% 0.0% 30.0% 0.0% 0.0% Europe 0.0% 0.0% 30.0% 0.0% 0.0%
    * LatAm 0.0% 0.0% 0.0% 0.0% 0.0% Australia 0.0% 0.0% 0.0% 0.0% 0.0%
    */

  test("Bands - sheet1") {
    // Deliberately compiles the same table in the same workbook multiple times to probe workbook caching
    val data2 = Excel.resource("Bands.xlsx", "Sheet1", "B6:G11", TypeInferrer.FromAllRows)
    val data1 = Excel.resource("Bands.xlsx", "Sheet1", "B6:G11", TypeInferrer.FromAllRows)
    val data = Excel.resource("Bands.xlsx", "Sheet1", "B6:G11", TypeInferrer.FromAllRows)
    val rows = data.toList
    assertEquals(rows.size, 5)

    assertEqualsDouble(rows.column["2% to 3%"].head, 0.3, 0.00001)
  }

  test("range inferred from a top left anchor") {
    // The real table lives at E3:G6, surrounded by decoy cells. Naming its top left corner is enough.
    val data = Excel.resource("Offset.xlsx", "Sheet1", "E3", TypeInferrer.StringType)
    assertEquals(data.getColRange, Some("E3:G6"))

    val rows = data.toSeq
    assertEquals(rows.size, 3)
    assertEquals(rows.column["Column 2"].toList.head, "Row 1, Col 2")
    assertEquals(rows.column["Column 3"].toList.last, "Row 3, Col 3")
  }

  test("anchor inferred range types identically to the explicit range") {
    val inferred = Excel.resource("Numbers.xlsx", "Sheet1", "A1", TypeInferrer.FromAllRows)
    val explicit: ExcelIterator[("Doubles", "Int", "Longs", "Strings"), (Double, Int, Int, String)] =
      Excel.resource("Numbers.xlsx", "Sheet1", "A1:D3", TypeInferrer.FromAllRows)

    val check: ExcelIterator[("Doubles", "Int", "Longs", "Strings"), (Double, Int, Int, String)] = inferred
    assertEquals(inferred.toList, explicit.toList)
  }

  test("anchor with a pinned column edge infers only the last row") {
    val data = Excel.resource("Bands.xlsx", "Sheet1", "B6:D", TypeInferrer.FromAllRows)
    assertEquals(data.getColRange, Some("B6:D11"))

    val rows = data.toList
    assertEquals(rows.size, 5)
    assertEquals(rows.column["Country"].head, "Japan")
    assert(
      compileErrors("""Excel.resource("Bands.xlsx", "Sheet1", "B6:D", TypeInferrer.FromAllRows).column["> 4%"]""").nonEmpty
    )
  }

  test("anchor with a pinned row edge infers only the last column") {
    val data = Excel.resource("Bands.xlsx", "Sheet1", "B6:8", TypeInferrer.FromAllRows)
    assertEquals(data.getColRange, Some("B6:G8"))
    assertEquals(data.toList.size, 2)
  }

  test("anchor on an empty cell fails at compile time") {
    assert(
      compileErrors("""Excel.resource("Offset.xlsx", "Sheet1", "C3", TypeInferrer.StringType)""").contains("No data at C3")
    )
  }

  test("an unreadable range fails at compile time") {
    assert(
      compileErrors("""Excel.resource("Offset.xlsx", "Sheet1", "banana", TypeInferrer.StringType)""").contains("Could not read \"banana\" as a cell range")
    )
  }

end ExcelSuite
