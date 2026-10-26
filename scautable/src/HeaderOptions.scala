package io.github.quafadas.scautable

import io.github.quafadas.table.HeaderOptions
import io.github.quafadas.table.HeaderOptions.*

/** Internal extension methods for processing raw CSV rows according to HeaderOptions. */
private[scautable] object HeaderOptionsProcessing:
  private val Bom: String = 0xfeff.toChar.toString

  /** Drops a UTF-8 byte order mark from the first line. `scala.io.Source` keeps it, and left in place it sticks to the first header (and stops a quoted header being seen as
    * quoted). Every reader - compile time and runtime - goes through `headers`, so stripping here keeps the inferred types and the data in agreement.
    */
  private def stripBom(rows: Iterator[String]): Iterator[String] =
    val buffered = rows.buffered
    if buffered.hasNext && buffered.head.startsWith(Bom) then Iterator.single(buffered.next().substring(1)) ++ buffered
    else buffered
    end if
  end stripBom

  /** Describes how `headers` and a data row disagree on width, or `None` when they agree.
    *
    * The names become the NamedTuple's `K` and the row's values its `V`, so a mismatch would build a NamedTuple whose names and values have different lengths. This is the one
    * place that wording lives: the compile-time readers abort with it and the runtime readers throw it.
    */
  private[scautable] def headerWidthMismatch(headers: Seq[String], dataRow: Seq[String]): Option[String] =
    val dataWidth = dataRow.length
    if headers.length == dataWidth then None
    else
      val names = headers.map(h => if h.isEmpty then "<empty>" else h).mkString(", ")
      val base = s"Header has ${headers.length} columns ($names) but data rows have $dataWidth columns."
      val hint =
        if headers.length == dataWidth + 1 && headers.lastOption.contains("") then
          " The header line appears to end with a trailing delimiter, which produces an extra column with an empty name. Remove the trailing delimiter from the header line, or name the columns with HeaderOptions.Manual."
        else if dataWidth == headers.length + 1 && dataRow.lastOption.contains("") then
          " The data rows appear to end with a trailing delimiter, which produces an extra empty value. Remove the trailing delimiter from the data rows."
        else if headers.exists(_.isEmpty) then " The header contains an empty column name."
        else ""
      Some(base + hint)
    end if
  end headerWidthMismatch

  /** Checks the first data row against `headers`. Returns the message from [[headerWidthMismatch]] on disagreement, otherwise the rows, unconsumed. With no data rows there is
    * nothing to disagree with.
    */
  private[scautable] def validateDataWidth(headers: Seq[String], rows: Iterator[String], delimiter: Char): Either[String, Iterator[String]] =
    val buffered = rows.buffered
    if !buffered.hasNext then Right(buffered)
    else headerWidthMismatch(headers, CSVParser.parseLine(buffered.head, delimiter)).toLeft(buffered)
    end if
  end validateDataWidth

  extension (rawRows: Iterator[String])
    def headers(headers: HeaderOptions, delimiter: Char = ','): (Seq[String], Iterator[String]) =
      // Records, not physical lines: a quoted field may contain line breaks. Done here because every reader - compile time and runtime - comes through `headers`, so type
      // inference and decoding see the same rows.
      val rows = CSVParser.records(stripBom(rawRows))
      headers match
        case Manual(seq*) =>
          (seq, rows)

        case FromRows(merge, dropFirst) =>

          val rowsAfterDrop = rows.drop(dropFirst)

          val headerLinesRaw: Seq[String] = rowsAfterDrop.take(merge).toSeq

          val parsedHeaderSegments: Seq[Seq[String]] = headerLinesRaw.map(line => CSVParser.parseLine(line, delimiter))

          val combinedHeaders: Seq[String] =
            val maxCols = parsedHeaderSegments.map(_.size).maxOption.getOrElse(0)

            val paddedSegments = parsedHeaderSegments.map { segment =>
              segment ++ Seq.fill(maxCols - segment.size)("")
            }

            paddedSegments.transpose.map { columnParts =>
              columnParts.filter(_.nonEmpty).mkString(" ").trim
            }.toSeq
          end combinedHeaders
          (combinedHeaders, rows)

        case Auto =>
          val buffered = rows.buffered
          val firstLine = CSVParser.parseLine(buffered.head, delimiter)
          val headers = firstLine.indices.map(i => s"col_$i")
          (headers, buffered)
      end match
  end extension
end HeaderOptionsProcessing
