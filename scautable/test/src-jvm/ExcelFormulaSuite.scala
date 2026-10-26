package io.github.quafadas.scautable

import scala.NamedTuple.*

import io.github.quafadas.table.{*, given}

/** Formula cells have to read the same at compile time, where their types are inferred, as at runtime, where they are decoded.
  *
  * "Formulas.xlsx" is written by POI without cached results, so nothing can be read back without evaluating:
  *
  * {{{
  *    A     B        C               D        E        F                   G
  * 1  Base  Doubled  ="Lab"&"el"     Flag     Ratio    Day                 PlainDay
  * 2  1     =A2*2    ="item"&A2      =A2>1    =A2/4    =DATE(2024,1,A2)    2024-01-01
  * 3  2     =A3*2    ="item"&A3      =A3>1    =A3/4    =DATE(2024,1,A3)    2024-01-02
  * 4  3     =A4*2    ="item"&A4      =A4>1    =A4/4    =DATE(2024,1,A4)    2024-01-03
  * }}}
  *
  * F and G share a yyyy-mm-dd date format.
  */
class ExcelFormulaSuite extends munit.FunSuite:

  test("formula cells are inferred from, and decoded as, their evaluated results") {
    val rows: ExcelIterator[
      ("Base", "Doubled", "Label", "Flag", "Ratio", "Day", "PlainDay"),
      (Int, Int, String, Boolean, Double, String, String)
    ] = Excel.resource("Formulas.xlsx", "Sheet1", "", TypeInferrer.FromAllRows)

    val all = rows.toList
    assertEquals(all.map(_.Doubled), List(2, 4, 6))
    assertEquals(all.map(_.Label), List("item1", "item2", "item3"))
    assertEquals(all.map(_.Flag), List(false, true, true))
    assertEquals(all.map(_.Ratio), List(0.25, 0.5, 0.75))
  }

  test("a date-formatted formula reads like a date cell with the same format") {
    val all = Excel.resource("Formulas.xlsx", "Sheet1", "", TypeInferrer.FromAllRows).toList
    assertEquals(all.map(_.Day), all.map(_.PlainDay))
  }

  test("StringType reads the evaluated result, not the formula text") {
    val all = Excel.resource("Formulas.xlsx", "Sheet1", "", TypeInferrer.StringType).toList
    assertEquals(all.map(_.Doubled), List("2.0", "4.0", "6.0"))
    assertEquals(all.map(_.Label), List("item1", "item2", "item3"))
  }

  test("formula cells are evaluated within a range too") {
    val all = Excel.resource("Formulas.xlsx", "Sheet1", "A1:C4", TypeInferrer.FromAllRows).toList
    assertEquals(all.map(_.Doubled), List(2, 4, 6))
    assertEquals(all.map(_.Label), List("item1", "item2", "item3"))
  }

end ExcelFormulaSuite
