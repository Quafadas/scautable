package io.github.quafadas.scautable

/** Parses a single RFC 4180 CSV line.
  *
  * The only escape mechanism is the doubled quote (`""` inside a quoted field becomes `"`). Backslashes have no special meaning and are kept literally, so values such as Windows
  * paths (`"C:\new\path"`) round-trip unchanged.
  */
private[scautable] object CSVParser:
  def parseLine(line: String, delimiter: Char = ',', quote: Char = '"'): List[String] =
    var inQuotes = false
    val cellBuffer = new StringBuilder
    val result = scala.collection.mutable.ListBuffer.empty[String]
    var i = 0

    while i < line.length do
      val char = line.charAt(i)

      char match
        case `quote` if !inQuotes =>
          // Start of quoted section
          inQuotes = true

        case `quote` if inQuotes =>
          // Check for RFC 4180 double-quote escaping
          if i + 1 < line.length && line.charAt(i + 1) == quote then
            // RFC 4180: doubled quote within quotes becomes a single quote
            cellBuffer.append(quote)
            i += 1 // Skip the next quote
          else
            // End of quoted section
            inQuotes = false

        case `delimiter` if !inQuotes =>
          // Delimiter outside quotes ends the current cell
          result.append(cellBuffer.toString)
          cellBuffer.clear()

        case _ =>
          // Add character to the current cell
          cellBuffer.append(char)
      end match

      i += 1
    end while

    // Append the last cell, if any
    result.append(cellBuffer.toString)

    result.toList
  end parseLine

  /** Groups physical lines into CSV records.
    *
    * RFC 4180 lets a quoted field contain line breaks, so one record can span several physical lines. Every reader produces lines with `getLines()` / `linesIterator`, which split
    * on every line break, quoted or not. This re-joins lines (with `\n`) for as long as a quoted field is still open, so that `parseLine` always sees a whole record.
    *
    * Line breaks inside a quoted field come back as `\n` whatever the file used (`\r\n` included), since the line iterators have already dropped the original terminator.
    *
    * The quote tracking mirrors `parseLine` exactly - a doubled quote (`""`) stays inside the field and backslashes are ordinary characters - so that the records found here are
    * the ones `parseLine` will agree on. Keep the two in step.
    *
    * An unterminated quote swallows the rest of the input into a single record, which is what `parseLine` would do with it anyway.
    */
  def records(lines: Iterator[String], quote: Char = '"'): Iterator[String] =
    new Iterator[String]:
      def hasNext: Boolean = lines.hasNext

      def next(): String =
        val first = lines.next()
        var open = endsInsideQuotes(first, inQuotes = false, quote)
        if !open then first
        else
          val sb = new StringBuilder(first)
          while open && lines.hasNext do
            val line = lines.next()
            sb.append('\n').append(line)
            open = endsInsideQuotes(line, inQuotes = true, quote)
          end while
          sb.toString
        end if
      end next

  /** Whether a quoted section is still open at the end of `line`, given whether one was open at its start. Same state machine as `parseLine`. */
  private def endsInsideQuotes(line: String, inQuotes: Boolean, quote: Char): Boolean =
    var open = inQuotes
    var i = 0
    while i < line.length do
      val c = line.charAt(i)
      if c == quote then
        if !open then open = true
        else if i + 1 < line.length && line.charAt(i + 1) == quote then i += 1 // RFC 4180 doubled quote
        else open = false
      end if
      i += 1
    end while
    open
  end endsInsideQuotes
end CSVParser
