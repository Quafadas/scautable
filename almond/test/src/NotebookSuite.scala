package io.github.quafadas.scautable

import scala.collection.mutable

import almond.interpreter.api.DisplayData
import almond.interpreter.api.OutputHandler
import io.github.quafadas.table.*
import io.github.quafadas.scautable.notebook.{*, given}

class NotebookSuite extends munit.FunSuite:

  /** Captures what would be sent to the notebook. */
  class Recorder extends OutputHandler:
    val displayed = mutable.ArrayBuffer.empty[DisplayData]
    def stdout(s: String): Unit = ()
    def stderr(s: String): Unit = ()
    def display(displayData: DisplayData): Unit = displayed += displayData
    def updateDisplay(displayData: DisplayData): Unit = ()
    def canOutput(): Boolean = true
    def messageIdOpt: Option[String] = None
    def addPayload(payload: String): Unit = ()
  end Recorder

  type Row = (id: Int, name: String, price: Double)

  def rows(n: Int): Vector[Row] = Vector.tabulate(n)(i => (id = i, name = s"n$i", price = i * 1.5))

  test("display publishes html and plain text, with headers from the field names") {
    given out: Recorder = Recorder()
    rows(3).display()
    assertEquals(out.displayed.size, 1)
    val data = out.displayed.head.data
    assertEquals(data.keySet, Set("text/html", "text/plain"))
    for h <- Seq("id", "name", "price") do
      assert(data("text/html").contains(s"<th>$h</th>"), h)
      assert(data("text/plain").contains(h), h)
    end for
    assert(data("text/html").contains("<td>n2</td>"))
    assert(!data("text/html").contains("more rows"))
  }

  test("an empty collection still renders its headers") {
    val t = Vector.empty[Row].notebookTable()
    assertEquals(t.headers, Seq("id", "name", "price"))
    assertEquals(t.totalRows, 0)
    assert(t.html.contains("<th>price</th>"))
    assert(t.text.contains("price"))
  }

  test("tables above maxRows show head and tail with a marker") {
    val t = rows(100).notebookTable()
    assertEquals(t.head.map(_.head), Seq("0", "1", "2", "3", "4"))
    assertEquals(t.tail.map(_.head), Seq("95", "96", "97", "98", "99"))
    assertEquals(t.omittedRows, 90)
    assert(t.html.contains("… 90 more rows"))
    assert(t.html.contains("<th>99</th>"), "tail rows keep their original index")
    assert(t.text.contains("… 90 more rows [100 rows × 3 columns]"))
    assert(!t.text.contains("\u001b"), "plain text has no ANSI escapes")
  }

  test("tables at maxRows are not truncated") {
    val t = rows(60).notebookTable()
    assertEquals(t.head.size, 60)
    assert(!t.isTruncated)
  }

  test("maxRows and edgeRows are configurable, and displayAll shows everything") {
    val t = rows(10).notebookTable(maxRows = 4, edgeRows = 2)
    assertEquals(t.head.map(_.head), Seq("0", "1"))
    assertEquals(t.tail.map(_.head), Seq("8", "9"))

    given out: Recorder = Recorder()
    rows(100).displayAll
    val html = out.displayed.head.data("text/html")
    assert(!html.contains("more rows"))
    assert(html.contains("<td>n50</td>"))
  }

  test("truncation always hides at least one row") {
    val t = rows(5).notebookTable(maxRows = 4, edgeRows = 5)
    assertEquals(t.omittedRows, 1)
    assertEquals(t.head.size + t.tail.size, 4)
  }

  test("format tags apply, as in ptbln") {
    val t = rows(2).formatColumn["price", Currency["$", 2]].notebookTable()
    assertEquals(t.head(1)(2), "$1.50")
  }

  test("cell content is html-escaped") {
    val t = Vector((a = "<b>&</b>")).notebookTable()
    assert(t.html.contains("&lt;b&gt;&amp;&lt;/b&gt;"))
  }

  test("ptbln on an Iterable publishes to the notebook in preference to the console") {
    given out: Recorder = Recorder()
    rows(2).ptbln
    assertEquals(out.displayed.size, 1)
  }

  test("dataresource is opt-in and is valid table schema json") {
    given out: Recorder = Recorder()
    Vector((n = 1, x = Some(2.5), s = "q\"uote"), (n = 2, x = None, s = "b")).display(dataResource = true)
    val detailed = out.displayed.head.detailedData
    assert(detailed.contains(NotebookTable.DataResourceMime))
    val json = rows(1).notebookTable(dataResource = true).dataResource.get
    assertEquals(
      json,
      """{"schema":{"fields":[{"name":"id","type":"integer"},{"name":"name","type":"string"},{"name":"price","type":"number"}]},"data":[{"id":0,"name":"n0","price":0.0}]}"""
    )
    val opt = Vector((n = 1, x = Some(2.5), s = "q\"uote"), (n = 2, x = None, s = "b")).notebookTable(dataResource = true).dataResource.get
    assert(opt.contains("""{"name":"x","type":"number"}"""), opt)
    assert(opt.contains(""""x":null"""), opt)
    assert(opt.contains(""""s":"q\"uote""""), opt)
    assert(!rows(1).notebookTable().dataResource.isDefined)
  }

end NotebookSuite
