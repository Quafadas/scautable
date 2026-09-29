package io.github.quafadas.scautable

import scala.NamedTuple.*

import io.github.quafadas.table.{*, given}

/** `ExcelOpts`, and the `skipFooter` it exists to carry.
  *
  * "FooterNote.xlsx" is the case this is for. Its "Price Sheet" holds a clean four column table anchored at A5, and immediately under the last data row - with no blank row to
  * separate it - a note in column A:
  *
  * {{{
  * A1  Q3 Price Sheet - INTERNAL
  * A5  Product     Region  Price  Units
  * A6  Widget      EMEA    12.5   100
  * A7  Gadget      APAC     8.25  250
  * A8  Doohickey   AMER    31.0    40
  * A9  Thingamy    EMEA     4.75  900
  * A10 Source: Bloomberg, retrieved 2026-09-29
  * }}}
  *
  * The downward walk cannot tell that note from data, so it swallows row 10. Every column but the first then sees a blank cell and becomes optional - one stray row makes the whole
  * table Option. `skipFooter = 1` is how the reader says what the sheet does not.
  */
class ExcelOptsSuite extends munit.FunSuite:

  test("a footer note under the table makes every other column optional") {
    // The problem, stated as a type. Nothing here is wrong given what is on the sheet - the walk
    // has no way to know row 10 is a note rather than data.
    def swallowed: ExcelIterator[
      ("Product", "Region", "Price", "Units"),
      (String, Option[String], Option[Double], Option[Int])
    ] = Excel.resource("FooterNote.xlsx", "Price Sheet", "A5", TypeInferrer.FromAllRows)

    assertEquals(swallowed.getColRange, Some("A5:D10"))
    assertEquals(swallowed.size, 5) // four real rows, plus the note
  }

  test("skipFooter trims the note, and the types come back clean") {
    def clean: ExcelIterator[
      ("Product", "Region", "Price", "Units"),
      (String, String, Double, Int)
    ] = Excel.resource("FooterNote.xlsx", "Price Sheet", ExcelOpts("A5", TypeInferrer.FromAllRows, skipFooter = 1))

    // The trim happens at compile time, so it is the resolved range that shrinks.
    assertEquals(clean.getColRange, Some("A5:D9"))
    assertEquals(clean.size, 4)

    val rows = clean.toList
    assertEquals(rows.column["Product"].toList, List("Widget", "Gadget", "Doohickey", "Thingamy"))
    assertEquals(rows.column["Price"].toList, List(12.5, 8.25, 31.0, 4.75))
    assertEquals(rows.column["Units"].toList, List(100, 250, 40, 900))
  }

  test("skipFooter applies to a pinned range too, so it means the same thing everywhere") {
    def pinned: ExcelIterator[("Product", "Region", "Price", "Units"), (String, String, Double, Int)] =
      Excel.resource("FooterNote.xlsx", "Price Sheet", ExcelOpts("A5:D10", TypeInferrer.FromAllRows, skipFooter = 1))

    assertEquals(pinned.getColRange, Some("A5:D9"))
    assertEquals(pinned.size, 4)
  }

  test("skipFooter composes with the type inferrer rather than replacing it") {
    // Missing.xlsx has staggered blanks - column 2 is blank in the second data row, column 3 in the
    // third. Trimming from the bottom therefore un-Options the columns in reverse order.
    def none: ExcelIterator[("Column 1", "Column 2", "Column 3"), (String, Option[String], Option[String])] =
      Excel.resource("Missing.xlsx", "Sheet1", ExcelOpts("A1:C4", TypeInferrer.FromAllRows))

    def one: ExcelIterator[("Column 1", "Column 2", "Column 3"), (String, Option[String], String)] =
      Excel.resource("Missing.xlsx", "Sheet1", ExcelOpts("A1:C4", TypeInferrer.FromAllRows, skipFooter = 1))

    def two: ExcelIterator[("Column 1", "Column 2", "Column 3"), (String, String, String)] =
      Excel.resource("Missing.xlsx", "Sheet1", ExcelOpts("A1:C4", TypeInferrer.FromAllRows, skipFooter = 2))

    assertEquals(none.size, 3)
    assertEquals(one.size, 2)
    assertEquals(two.size, 1)
  }

  test("ExcelOpts reads the same whichever way it is written") {
    // Positional, named, out of order, and defaulted all have to reach the macro intact - the
    // compiler produces a different tree shape for each.
    def positional = Excel.resource("FooterNote.xlsx", "Price Sheet", ExcelOpts("A5", TypeInferrer.FromAllRows, 1))
    def named = Excel.resource("FooterNote.xlsx", "Price Sheet", ExcelOpts(range = "A5", typeInferrer = TypeInferrer.FromAllRows, skipFooter = 1))
    def reordered = Excel.resource("FooterNote.xlsx", "Price Sheet", ExcelOpts(skipFooter = 1, range = "A5"))
    def partial = Excel.resource("FooterNote.xlsx", "Price Sheet", ExcelOpts("A5", skipFooter = 1))

    assertEquals(positional.getColRange, Some("A5:D9"))
    assertEquals(named.getColRange, Some("A5:D9"))
    assertEquals(reordered.getColRange, Some("A5:D9"))
    assertEquals(partial.getColRange, Some("A5:D9"))

    // Defaults: no skipFooter, and FromAllRows inference like CsvOpts.
    def defaulted: ExcelIterator[("Product", "Region", "Price", "Units"), (String, Option[String], Option[Double], Option[Int])] =
      Excel.resource("FooterNote.xlsx", "Price Sheet", ExcelOpts("A5"))
    assertEquals(defaulted.getColRange, Some("A5:D10"))

    // The single argument companion apply, for a type inferrer over the whole sheet.
    def inferrerOnly = Excel.resource("Numbers.xlsx", "Sheet1", ExcelOpts(TypeInferrer.FromAllRows))
    assertEquals(inferrerOnly.getColRange, None)
  }

  test("skipFooter is rejected when it cannot mean anything") {
    // No range, so there is no bottom edge to count back from.
    assert(
      compileErrors("""Excel.resource("FooterNote.xlsx", "Price Sheet", ExcelOpts(skipFooter = 1))""")
        .contains("skipFooter needs a range to trim"),
      compileErrors("""Excel.resource("FooterNote.xlsx", "Price Sheet", ExcelOpts(skipFooter = 1))""")
    )

    // Trimming everything leaves no table, which is a mistake rather than an empty result.
    assert(
      compileErrors("""Excel.resource("FooterNote.xlsx", "Price Sheet", ExcelOpts("A5", skipFooter = 5))""")
        .contains("would leave no data rows"),
      compileErrors("""Excel.resource("FooterNote.xlsx", "Price Sheet", ExcelOpts("A5", skipFooter = 5))""")
    )

    assert(
      compileErrors("""Excel.resource("FooterNote.xlsx", "Price Sheet", ExcelOpts("A5", skipFooter = -1))""")
        .contains("skipFooter must not be negative"),
      compileErrors("""Excel.resource("FooterNote.xlsx", "Price Sheet", ExcelOpts("A5", skipFooter = -1))""")
    )
  }

end ExcelOptsSuite
