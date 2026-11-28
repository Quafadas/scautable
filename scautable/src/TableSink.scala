package io.github.quafadas.scautable

/** Where `ptbln` sends a table.
  *
  * The default prints to the console. Another sink can be brought into scope as a `given` and will be preferred - the `scautable-almond` module uses this to publish to a notebook
  * instead, without core depending on almond.
  */
trait TableSink:
  /** @param headers
    *   column names, taken from the named tuple type
    * @param formatters
    *   one [[CellFormat]] per column, positionally aligned with each row
    * @param rows
    *   the rows, as plain tuples
    */
  def show(headers: List[String], formatters: Array[Any => String], rows: Iterator[Product]): Unit

  /** As [[show]], with a [[CellStyle]] per cell from a [[Styler]]. A sink that cannot style simply ignores them, which is the default.
    *
    * @param styles
    *   the style of each cell of a row, positionally aligned with it
    */
  def showStyled(headers: List[String], formatters: Array[Any => String], styles: Product => IndexedSeq[CellStyle], rows: Iterator[Product]): Unit =
    show(headers, formatters, rows)
end TableSink

object TableSink:

  given console: TableSink with
    def show(headers: List[String], formatters: Array[Any => String], rows: Iterator[Product]): Unit =
      println(TerminalTable.render(headers, rows.map(row => TableRow(CellFormat.formatRow(row, formatters))).toSeq))

    override def showStyled(headers: List[String], formatters: Array[Any => String], styles: Product => IndexedSeq[CellStyle], rows: Iterator[Product]): Unit =
      val materialised = rows.toSeq
      println(TerminalTable.render(headers, materialised.map(row => TableRow(CellFormat.formatRow(row, formatters))), styles = materialised.map(styles)))
    end showStyled
  end console

end TableSink
