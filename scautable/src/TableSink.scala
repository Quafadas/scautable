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
end TableSink

object TableSink:

  given console: TableSink = (headers, formatters, rows) => println(TerminalTable.render(headers, rows.map(row => TableRow(CellFormat.formatRow(row, formatters))).toSeq))

end TableSink
