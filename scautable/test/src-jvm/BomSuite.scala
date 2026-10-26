package io.github.quafadas.scautable

import io.github.quafadas.table.*
import io.github.quafadas.scautable.HeaderOptionsProcessing.headers

/** Excel and many bank / broker exports prefix CSVs with a UTF-8 byte order mark (`EF BB BF`). `scala.io.Source` keeps it, so it has to be stripped before header parsing, and the
  * compile-time headers and runtime iterator have to agree on that.
  */
class BomSuite extends munit.FunSuite:

  private val bom = 0xfeff.toChar.toString

  test("BOM-prefixed CSV with quoted headers: first column is selectable by name") {
    val csv: CsvIterator[("Asset class", "Qty/nom.", "Market price"), (String, Int, Double)] =
      CSV.resource("bom_quoted_semicolon.csv", CsvOpts(delimiter = ';'))

    assertEquals(csv.headers, Seq("Asset class", "Qty/nom.", "Market price"))
    val rows = LazyList.from(csv)
    assertEquals(rows.column["Asset class"].toList, List("Equity", "Bond"))
    assertEquals(rows.column["Qty/nom."].toList, List(10, 5))
  }

  test("BOM-prefixed CSV with unquoted headers") {
    val csv: CsvIterator[("col1", "col2", "col3"), (Int, Int, Int)] = CSV.resource("bom_unquoted.csv")

    assertEquals(csv.headers, Seq("col1", "col2", "col3"))
    assertEquals(LazyList.from(csv).column["col1"].toList, List(1, 3))
  }

  test("BOM-prefixed CSV read as columns") {
    val cols = CSV.resource("bom_unquoted.csv", CsvOpts(readAs = ReadAs.Columns))
    assertEquals(cols.col1.toList, List(1, 3))
  }

  test("BOM-prefixed CSV with Auto headers: BOM does not leak into the first value") {
    val csv: CsvIterator[("col_0", "col_1", "col_2"), (String, String, String)] =
      CSV.resource("bom_unquoted.csv", CsvOpts(headerOptions = HeaderOptions.Auto, typeInferrer = TypeInferrer.StringType))

    assertEquals(csv.next().col_0, "col1")
  }

  test("BOM-prefixed CSV via runtime fromTyped") {
    val reader = CSV.fromTyped[("col1", "col2", "col3"), (Int, Int, Int)]
    val path = os.Path(this.getClass.getClassLoader.getResource("bom_unquoted.csv").getPath)

    val rows = reader(path).toList
    assertEquals(rows.map(_.col1), List(1, 3))
  }

  test("CSV without a BOM is unchanged") {
    val csv: CsvIterator[("col1", "col2", "col3"), (Int, Int, Int)] = CSV.resource("simple.csv")
    assertEquals(csv.headers, Seq("col1", "col2", "col3"))
    assertEquals(csv.toList.map(_.col1), List(1, 3, 5))
  }

  test("headers strips a leading BOM for every HeaderOptions mode") {
    def lines = Iterator(s"$bom\"a\";\"b\"", "1;2")

    assertEquals(lines.headers(HeaderOptions.Default, ';')._1, Seq("a", "b"))
    assertEquals(lines.headers(HeaderOptions.FromRows(merge = 2, dropFirst = 0), ';')._1, Seq("a 1", "b 2"))

    val (_, autoRows) = lines.headers(HeaderOptions.Auto, ';')
    assertEquals(autoRows.next(), "\"a\";\"b\"")

    val (_, manualRows) = lines.headers(HeaderOptions.Manual("x", "y"), ';')
    assertEquals(manualRows.next(), "\"a\";\"b\"")
  }

  test("headers only strips a BOM at the very start of the file") {
    val (hdrs, rows) = Iterator("a,b", s"${bom}1,2").headers(HeaderOptions.Default)
    assertEquals(hdrs, Seq("a", "b"))
    assertEquals(rows.next(), s"${bom}1,2")
  }

end BomSuite
