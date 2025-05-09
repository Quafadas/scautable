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

    for char <- line do
      char match
        case `quote` if !inQuotes =>
          // Start of quoted section
          inQuotes = true

        case `quote` if inQuotes =>
          // End of quoted section (peek ahead to handle escaped quotes)
          if cellBuffer.nonEmpty && cellBuffer.last == quote then
            cellBuffer.deleteCharAt(cellBuffer.length - 1) // Handle escaped quote
            cellBuffer.append(char)
          else inQuotes = false

        case `delimiter` if !inQuotes =>
          // Delimiter outside quotes ends the current cell
          result.append(cellBuffer.toString)
          cellBuffer.clear()

        case _ =>
          // Add character to the current cell
          cellBuffer.append(char)
    end for

    // Append the last cell, if any
    result.append(cellBuffer.toString)

    result.toList
  end parseLine
end CSVParser
