package io.github.quafadas.scautable

import org.apache.poi.ss.usermodel.Sheet

/** Unit tests for the range inference which lets a caller name only the top left corner of a table. */
class ExcelRangeSuite extends munit.FunSuite:

  private def sheet(resource: String, sheetName: String = "Sheet1"): Sheet =
    val url = getClass.getClassLoader.getResource(resource)
    assert(url != null, s"Test resource $resource not found")
    val path = new java.io.File(url.toURI).getAbsolutePath
    ExcelWorkbookCache.getOrCreate(path).get.getSheet(sheetName)
  end sheet

  test("a fully specified range is passed through untouched") {
    assertEquals(ExcelRange.resolve(sheet("SimpleTable.xlsx"), "A1:C4"), "A1:C4")
    assertEquals(ExcelRange.resolve(sheet("Offset.xlsx"), "E3:G6"), "E3:G6")
  }

  test("an anchor infers both edges") {
    assertEquals(ExcelRange.resolve(sheet("SimpleTable.xlsx"), "A1"), "A1:C4")
    assertEquals(ExcelRange.resolve(sheet("Numbers.xlsx"), "A1"), "A1:D3")
    assertEquals(ExcelRange.resolve(sheet("Bands.xlsx"), "B6"), "B6:G11")
  }

  test("an anchor ignores data outside the table") {
    // Offset.xlsx is littered with decoy cells - A1, B2, A4, I1, J7 - around the real table at E3:G6
    assertEquals(ExcelRange.resolve(sheet("Offset.xlsx"), "E3"), "E3:G6")
    assertEquals(ExcelRange.resolve(sheet("SimpleTableColOffset.xlsx"), "D1"), "D1:F4")
  }

  test("an anchor is not truncated by holes inside the table") {
    // Missing.xlsx has blank cells in the middle of both its second and third columns
    assertEquals(ExcelRange.resolve(sheet("Missing.xlsx"), "A1"), "A1:C4")
  }

  test("a trailing colon is the same as an anchor") {
    assertEquals(ExcelRange.resolve(sheet("Offset.xlsx"), "E3:"), "E3:G6")
  }

  test("a pinned column edge only infers the last row") {
    assertEquals(ExcelRange.resolve(sheet("Offset.xlsx"), "E3:F"), "E3:F6")
    assertEquals(ExcelRange.resolve(sheet("Numbers.xlsx"), "A1:B"), "A1:B3")
  }

  test("a pinned row edge only infers the last column") {
    assertEquals(ExcelRange.resolve(sheet("Offset.xlsx"), "E3:5"), "E3:G5")
    assertEquals(ExcelRange.resolve(sheet("SimpleTable.xlsx"), "A1:3"), "A1:C3")
  }

  test("absolute references and whitespace are tolerated") {
    assertEquals(ExcelRange.resolve(sheet("Offset.xlsx"), " $E$3 "), "E3:G6")
    assertEquals(ExcelRange.resolve(sheet("Offset.xlsx"), "$E$3:$F"), "E3:F6")
  }

  test("an anchor on an empty cell is an error") {
    val ex = intercept[BadTableException](ExcelRange.resolve(sheet("Offset.xlsx"), "C3"))
    assert(ex.getMessage.contains("No data at C3"), ex.getMessage)
  }

  test("an unreadable range is an error") {
    intercept[BadTableException](ExcelRange.resolve(sheet("SimpleTable.xlsx"), "not a range"))
    intercept[BadTableException](ExcelRange.resolve(sheet("SimpleTable.xlsx"), "A1:B2:C3"))
    intercept[BadTableException](ExcelRange.resolve(sheet("SimpleTable.xlsx"), "A1:%%"))
  }

end ExcelRangeSuite
