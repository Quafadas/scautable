package io.github.quafadas.scautable

import io.github.quafadas.table.*

import munit.FunSuite

class StylerSuite extends FunSuite:

  type Quote = (name: String, region: String, el: Double, bid: Double)

  val quotes: Seq[Quote] = Seq(
    (name = "A", region = "EU", el = 0.01, bid = 1.0),
    (name = "B", region = "US", el = 0.08, bid = 2.0),
    (name = "C", region = "EU", el = 0.10, bid = 3.0)
  )

  val red = CellStyle.fg(Colour.Red)
  val grey = CellStyle.bg(Colour.Grey)

  /** The styles the renderers would see, row by row. */
  def stylesOf(s: Styler[?, ?]): Seq[IndexedSeq[CellStyle]] = quotes.map(q => s.stylesOf(q.toTuple))

  test("cell styles only matching cells of the named column, with a typed predicate") {
    val out = stylesOf(quotes.styler.cell["el"](_ > 0.05, red))
    assertEquals(out.map(_(2)), Seq(CellStyle.empty, red, red))
    assert(out.forall(r => r(0).isEmpty && r(1).isEmpty && r(3).isEmpty))
  }

  test("row styles every cell of matching rows") {
    val out = stylesOf(quotes.styler.row(_.region == "EU", grey))
    assertEquals(out(0), IndexedSeq.fill(4)(grey))
    assertEquals(out(1), IndexedSeq.fill(4)(CellStyle.empty))
  }

  test("cells styles chosen columns of matching rows") {
    val out = stylesOf(quotes.styler.cells[("name", "bid")](_.bid > 1.5, CellStyle.bold))
    assertEquals(out(2), IndexedSeq(CellStyle.bold, CellStyle.empty, CellStyle.empty, CellStyle.bold))
    assert(out(0).forall(_.isEmpty))
  }

  test("later rules win property by property") {
    val out = stylesOf(quotes.styler.row(_.region == "EU", grey ++ CellStyle.bold).cell["el"](_ > 0.05, red))
    val elOfC = out(2)(2)
    assertEquals(elOfC.fg, Some(Colour.Red))
    assertEquals(elOfC.bg, Some(Colour.Grey))
    assert(elOfC.bold)
  }

  test("predicates see format-tagged columns as their underlying type, and formats still apply") {
    val s = quotes.formatColumn["el", Percent[1]].styler.cell["el"](_ > 0.05, red)
    val html = s.html
    assert(html.contains("8.0%"), html)
    assert(html.contains("""style="color:rgb(214,39,40)""""), html)
  }

  test("html puts an inline style only on styled cells, with a readable text colour on a background") {
    val html = quotes.styler.cell["name"](_ == "A", CellStyle.bg(Colour.Yellow)).html
    assert(html.contains("""<td style="color:rgb(0,0,0);background-color:rgb(255,221,87)">A</td>"""), html)
    assert(html.contains("<td>B</td>"), html)
  }

  test("the terminal renders styled cells with ANSI escapes, and drops them with colour off") {
    val s = quotes.styler.cell["name"](_ == "B", red)
    val coloured = s.consoleFormatNt()
    assert(coloured.contains(fansi.Color.Red.escape), coloured)
    val plain = s.consoleFormatNt(colour = false)
    assert(!plain.contains("\u001b["), plain)
    assert(plain.contains("| B "), plain)
  }

  test("an unstyled styler renders unstyled cells, without the per-column rainbow") {
    val out = quotes.styler.consoleFormatNt()
    assert(!out.contains("\u001b["), out)
  }

  test("works on an Iterator") {
    val html = quotes.iterator.styler.cell["el"](_ > 0.05, red).html
    assertEquals(html.split("rgb\\(214,39,40\\)").length - 1, 2)
  }

  test("naming a missing column does not compile") {
    val errs = compileErrors("""quotes.styler.cell["nope"](_ => true, red)""")
    assert(errs.contains("\"nope\" : String) is not a column"), errs)
    val errs2 = compileErrors("""quotes.styler.cells[("name", "nope")](_ => true, red)""")
    assert(errs2.contains("must be a column"), errs2)
  }

  test("a predicate of the wrong type does not compile") {
    val errs = compileErrors("""quotes.styler.cell["name"](_ > 0.05, red)""")
    assert(errs.nonEmpty)
  }

  test("the docs example compiles and renders") {
    def csv = CSV.fromString("product,region,price,margin\nwidget,EU,1234.5678,0.0425\ngizmo,US,99.5,0.113\nsprocket,EU,12.0,-0.02")
    val out = csv.toSeq
      .style[(price: Currency["$", 2], margin: Percent[2])]
      .styler
      .row(_.region == "EU", CellStyle.bg(Colour.Grey))
      .cell["margin"](_ < 0, CellStyle.fg(Colour.Red) ++ CellStyle.bold)
      .cells[("product", "price")](_.price > 1000, CellStyle.underline)
      .html
    assert(out.contains("$1,234.57"), out)
    assert(out.contains("""<td style="color:rgb(214,39,40);background-color:rgb(190,190,190);font-weight:bold">-2.00%</td>"""), out)
  }

end StylerSuite
