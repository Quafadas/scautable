package io.github.quafadas.scautable

import scala.io.Source
import scala.util.Try
import scala.util.chaining.*
import scala.util.matching.Regex

/** According to chatGPT will parse RFC 4180 compliant CSV line.
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

        case '\\' if inQuotes && i + 1 < line.length =>
          // Handle backslash-escaped characters
          val nextChar = line.charAt(i + 1)
          nextChar match
            case 'n' =>
              // Escaped linefeed
              cellBuffer.append('\n')
              i += 1
            case 'r' =>
              // Escaped carriage return
              cellBuffer.append('\r')
              i += 1
            case '\\' =>
              // Escaped backslash
              cellBuffer.append('\\')
              i += 1
            case `delimiter` =>
              // Escaped delimiter
              cellBuffer.append(delimiter)
              i += 1
            case `quote` =>
              // Escaped quote character
              cellBuffer.append(quote)
              i += 1
            case _ =>
              // Unknown escape sequence - treat backslash literally
              cellBuffer.append('\\')
              // Don't increment i, let the next character be processed normally

        case `delimiter` if !inQuotes =>
          // Delimiter outside quotes ends the current cell
          result.append(cellBuffer.toString)
          cellBuffer.clear()

        case _ =>
          // Add character to the current cell
          cellBuffer.append(char)

      i += 1
    end while

    // Append the last cell, if any
    result.append(cellBuffer.toString)

    result.toList
  end parseLine
end CSVParser
