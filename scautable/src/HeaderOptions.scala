package io.github.quafadas.scautable

import io.github.quafadas.table.HeaderOptions
import io.github.quafadas.table.HeaderOptions.*

/** Internal extension methods for processing raw CSV rows according to HeaderOptions. */
private[scautable] object HeaderOptionsProcessing:
  private val Bom: Char = 0xfeff

  /** Drops a UTF-8 byte order mark from the first line. `scala.io.Source` keeps it, and left in place it sticks to the first header (and stops a quoted header being seen as
    * quoted). Every reader - compile time and runtime - goes through `headers`, so stripping here keeps the inferred types and the data in agreement.
    */
  private def stripBom(rows: Iterator[String]): Iterator[String] =
    val buffered = rows.buffered
    if buffered.hasNext && buffered.head.headOption.contains(Bom) then Iterator.single(buffered.next().substring(1)) ++ buffered
    else buffered
    end if
  end stripBom

  extension (rawRows: Iterator[String])
    def headers(headers: HeaderOptions, delimiter: Char = ','): (Seq[String], Iterator[String]) =
      val rows = stripBom(rawRows)
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
