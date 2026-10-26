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
end CSVParser
