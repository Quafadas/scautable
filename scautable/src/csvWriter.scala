package io.github.quafadas.scautable

/** RFC 4180 compliant CSV writer that mirrors the CSVParser functionality.
  */
private[scautable] object CSVWriter:

  /** Formats a field for CSV output according to RFC 4180.
    *
    * @param value
    *   The field value to format
    * @param delimiter
    *   The delimiter character (usually comma)
    * @param quote
    *   The quote character (usually double quote)
    * @return
    *   The formatted field, quoted if necessary
    */
  def formatField(value: String, delimiter: Char = ',', quote: Char = '"'): String =
    if needsQuoting(value, delimiter, quote) then s"$quote${escapeQuotes(value, quote)}$quote"
    else value
  end formatField

  /** Formats a complete line for CSV output.
    *
    * @param fields
    *   The sequence of field values
    * @param delimiter
    *   The delimiter character (usually comma)
    * @param quote
    *   The quote character (usually double quote)
    * @return
    *   The formatted CSV line
    */
  def formatLine(fields: Seq[String], delimiter: Char = ',', quote: Char = '"'): String =
    fields.map(formatField(_, delimiter, quote)).mkString(delimiter.toString)
  end formatLine

  /** Converts a single cell value to its CSV string representation.
    *
    * Options are unwrapped (recursively, so nested options also work): `Some(x)` is written as the string form of `x`, and `None` (or `null`) as an empty field. This keeps CSV
    * output round-trippable, since an empty cell is read back as `None` for an `Option[T]` column.
    */
  def cellToString(value: Any): String =
    value match
      case null        => ""
      case None        => ""
      case Some(inner) => cellToString(inner)
      case other       => other.toString
  end cellToString

  /** Formats a row of arbitrary cell values as a CSV line, using [[cellToString]] for each cell. */
  def formatRow(values: Seq[Any], delimiter: Char = ',', quote: Char = '"'): String =
    formatLine(values.map(cellToString), delimiter, quote)
  end formatRow

  /** Determines if a field needs quoting according to RFC 4180.
    *
    * Fields need quoting if they contain:
    *   - The delimiter character
    *   - Quote characters
    *   - Newline characters (CR or LF)
    *   - Leading or trailing whitespace
    */
  private def needsQuoting(value: String, delimiter: Char, quote: Char): Boolean =
    value.contains(delimiter) ||
      value.contains(quote) ||
      value.contains('\n') ||
      value.contains('\r') ||
      value.startsWith(" ") ||
      value.endsWith(" ")
  end needsQuoting

  /** Escapes quote characters within a field value according to RFC 4180.
    *
    * In RFC 4180, quote characters are escaped by doubling them.
    */
  private def escapeQuotes(value: String, quote: Char): String =
    value.replace(quote.toString, quote.toString + quote.toString)
  end escapeQuotes

end CSVWriter
