package io.github.quafadas.scautable

import io.github.quafadas.table.HeaderOptions
import io.github.quafadas.table.HeaderOptions.*

/** Internal extension methods for processing raw CSV rows according to HeaderOptions. */
private[scautable] object HeaderOptionsProcessing:
  extension (rows: Iterator[String])
    def headers(headers: HeaderOptions, delimiter: Char = ','): (Seq[String], Iterator[String]) =
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
  end extension
end HeaderOptionsProcessing
