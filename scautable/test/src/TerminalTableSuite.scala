package io.github.quafadas.scautable

import munit.FunSuite

class TerminalTableSuite extends FunSuite:

  test("allocateWidths: all columns fit comfortably - no shrinking") {
    val widths = TerminalTable.allocateWidths(Seq(3, 5, 10), 100)
    assertEquals(widths, Seq(3, 5, 10))
  }

  test("allocateWidths: narrow column untouched, long column squeezed") {
    // narrow column has natural width 3, long column natural width 50
    // budget only allows 20 total content chars
    val widths = TerminalTable.allocateWidths(Seq(3, 50), 20)
    assertEquals(widths(0), 3)
    assertEquals(widths(1), 17)
  }

  test("allocateWidths: multiple wide columns share remaining budget equally with fair remainder") {
    // three columns all naturally wide (20 each), budget only 17
    // no column fits into average on first pass since 17/3 = 5 < 20 for all
    val widths = TerminalTable.allocateWidths(Seq(20, 20, 20), 17, minColumnWidth = 3)
    assertEquals(widths.sum, 17)
    // 17 / 3 = 5 remainder 2 -> first two columns get 6, last gets 5
    assertEquals(widths, Seq(6, 6, 5))
  }

  test("allocateWidths: floor enforced when budget too small for all columns") {
    val widths = TerminalTable.allocateWidths(Seq(20, 20, 20), 6, minColumnWidth = 3)
    assertEquals(widths, Seq(3, 3, 3))
  }

  test("allocateWidths: floor enforced with uneven leftover") {
    // budget 10, 3 columns, min 3 -> each gets floor 3, 1 leftover but all frozen at floor already
    val widths = TerminalTable.allocateWidths(Seq(20, 20, 20), 10, minColumnWidth = 3)
    assertEquals(widths.sum <= 10, true)
    assert(widths.forall(_ >= 3))
  }

  test("allocateWidths: empty input") {
    assertEquals(TerminalTable.allocateWidths(Seq.empty, 100), Seq.empty)
  }

  test("truncateCell: content within width unchanged") {
    assertEquals(TerminalTable.truncateCell("abc", 5), "abc")
    assertEquals(TerminalTable.truncateCell("abc", 3), "abc")
  }

  test("truncateCell: content truncated with ellipsis") {
    assertEquals(TerminalTable.truncateCell("abcdef", 4), "abc…")
    assertEquals(TerminalTable.truncateCell("abcdef", 1), "…")
  }

  test("truncateCell: zero width") {
    assertEquals(TerminalTable.truncateCell("abcdef", 0), "")
  }

  test("render: fits comfortably, no truncation") {
    val out = TerminalTable.render(
      Seq("id", "name"),
      Seq(TableRow(Seq("1", "Alice")), TableRow(Seq("2", "Bob"))),
      widthOverride = Some(80)
    )
    assert(out.contains("Alice"))
    assert(out.contains("Bob"))
    assert(!out.contains("…"))
    // no line should exceed the requested width
    assert(out.linesIterator.forall(_.length <= 80))
  }

  test("render: long column truncated, narrow column untouched") {
    val longText = "x" * 200
    val out = TerminalTable.render(
      Seq("id", "description"),
      Seq(TableRow(Seq("1", longText))),
      widthOverride = Some(40)
    )
    assert(out.contains("…"))
    assert(out.linesIterator.forall(_.length <= 40))
    // id column header/values should never be truncated since they're short/narrow
    assert(out.contains(" id "))
  }

  test("render: never wraps - each row is a single line") {
    val longText = "y" * 500
    val out = TerminalTable.render(
      Seq("col"),
      Seq(TableRow(Seq(longText))),
      widthOverride = Some(30)
    )
    val dataLines = out.linesIterator.filterNot(l => l.startsWith("+") || l.contains("col")).toList
    assertEquals(dataLines.length, 1)
  }

  test("render: empty rows, headers only") {
    val out = TerminalTable.render(Seq("a", "b"), Seq.empty, widthOverride = Some(80))
    assert(out.contains("a"))
    assert(out.contains("b"))
  }

  test("render: empty headers") {
    val out = TerminalTable.render(Seq.empty, Seq.empty, widthOverride = Some(80))
    assertEquals(out, "")
  }

  test("render: single column table") {
    val out = TerminalTable.render(Seq("only"), Seq(TableRow(Seq("value"))), widthOverride = Some(80))
    assert(out.contains("only"))
    assert(out.contains("value"))
  }

  test("render: header longer than any cell content is itself truncated") {
    val out = TerminalTable.render(
      Seq("a-very-long-header-name"),
      Seq(TableRow(Seq("x"))),
      widthOverride = Some(10)
    )
    assert(out.contains("…"))
  }

  test("render: unicode content does not crash") {
    val out = TerminalTable.render(
      Seq("emoji"),
      Seq(TableRow(Seq("😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀"))),
      widthOverride = Some(20)
    )
    assert(out.nonEmpty)
  }

  test("render: widthOverride is reproducible") {
    val out1 = TerminalTable.render(Seq("a", "b"), Seq(TableRow(Seq("1", "2"))), widthOverride = Some(50))
    val out2 = TerminalTable.render(Seq("a", "b"), Seq(TableRow(Seq("1", "2"))), widthOverride = Some(50))
    assertEquals(out1, out2)
  }

  test("detectWidth falls back when no tty is attached") {
    // In CI this typically isn't a tty, so it should fall back to the provided fallback.
    val w = TerminalTable.detectWidth(123)
    assert(w > 0)
  }

end TerminalTableSuite
