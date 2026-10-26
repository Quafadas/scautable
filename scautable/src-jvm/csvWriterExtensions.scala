package io.github.quafadas.scautable

import scala.NamedTuple.*
import scala.compiletime.*

/** Extension methods for writing NamedTuple collections to CSV format.
  */
object CSVWriterExtensions:

  /** Writes all lines (each terminated by a newline) to `target` in a single streaming `os.write.over`, always replacing any existing file content - even when there are no lines.
    */
  private[scautable] def writeLines(target: os.Path, lines: Iterator[String]): Unit =
    val writable = new geny.Writable:
      override def writeBytesTo(out: java.io.OutputStream): Unit =
        val writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(out, java.nio.charset.StandardCharsets.UTF_8))
        lines.foreach { line =>
          writer.write(line)
          writer.write('\n')
        }
        writer.flush()
      end writeBytesTo
    os.write.over(target, writable)
  end writeLines

  extension [K <: Tuple, V <: Tuple](itr: Iterator[NamedTuple[K, V]])

    /** Converts the iterator to CSV format as an iterator of lines.
      *
      * @param includeHeaders
      *   Whether to include column headers as the first line
      * @param delimiter
      *   The delimiter character (default: comma)
      * @param quote
      *   The quote character (default: double quote)
      * @return
      *   Iterator of CSV formatted lines
      */
    inline def toCsv(
        includeHeaders: Boolean,
        delimiter: Char,
        quote: Char
    ): Iterator[String] =
      val headers = constValueTuple[K].toList.map(_.toString())
      val headerLine = CSVWriter.formatLine(headers, delimiter, quote)

      val strIterator = itr.map { namedTuple =>
        CSVWriter.formatRow(namedTuple.toList, delimiter, quote)
      }
      if includeHeaders then Iterator(headerLine) ++ strIterator
      else strIterator
      end if
    end toCsv

    /** Writes the iterator to a CSV file, streaming line by line. Any existing file at `path` is replaced.
      *
      * @param path
      *   The file path to write to
      * @param includeHeaders
      *   Whether to include column headers as the first line
      * @param delimiter
      *   The delimiter character (default: comma)
      * @param quote
      *   The quote character (default: double quote)
      */
    inline def writeCsvIterator(
        path: Option[os.Path] = None,
        includeHeaders: Boolean = true,
        delimiter: Char = ',',
        quote: Char = '"'
    ): os.Path =
      val target = path.getOrElse(os.temp(contents = "", suffix = ".csv", prefix = "scautable-"))
      CSVWriterExtensions.writeLines(target, toCsv(includeHeaders, delimiter, quote))
      target
    end writeCsvIterator

  end extension

  extension [CC[X] <: Iterable[X], K <: Tuple, V <: Tuple](data: CC[NamedTuple[K, V]])

    /** Converts the iterable to CSV format as an iterator of lines.
      *
      * @param includeHeaders
      *   Whether to include column headers as the first line
      * @param delimiter
      *   The delimiter character (default: comma)
      * @param quote
      *   The quote character (default: double quote)
      * @return
      *   Iterator of CSV formatted lines
      */
    inline def toCsv(
        includeHeaders: Boolean,
        delimiter: Char,
        quote: Char
    ): Iterator[String] =
      val headers = constValueTuple[K].toList.map(_.toString())
      val headerLine = CSVWriter.formatLine(headers, delimiter, quote)

      val strIterator = data.iterator.map { namedTuple =>
        CSVWriter.formatRow(namedTuple.toList, delimiter, quote)
      }
      if includeHeaders then Iterator(headerLine) ++ strIterator
      else strIterator
      end if
    end toCsv

    /** Converts the iterable to CSV format as a string.
      *
      * @param includeHeaders
      *   Whether to include column headers as the first line
      * @param delimiter
      *   The delimiter character (default: comma)
      * @param quote
      *   The quote character (default: double quote)
      * @return
      *   CSV formatted string
      */
    inline def toCsvString(
        includeHeaders: Boolean,
        delimiter: Char,
        quote: Char
    ): String =
      val headers = constValueTuple[K].toList.map(_.toString())
      val headerLine =
        if includeHeaders then Seq(CSVWriter.formatLine(headers, delimiter, quote))
        else Seq.empty

      val dataLines = data.view.map { namedTuple =>
        CSVWriter.formatRow(namedTuple.toList, delimiter, quote)
      }.toSeq

      (headerLine ++ dataLines).mkString("\n")
    end toCsvString

    /** Writes the iterable to a CSV file, streaming line by line. Any existing file at `path` is replaced.
      *
      * @param path
      *   The file path to write to
      * @param includeHeaders
      *   Whether to include column headers as the first line
      * @param delimiter
      *   The delimiter character (default: comma)
      * @param quote
      *   The quote character (default: double quote)
      */
    inline def writeCsv(
        path: Option[os.Path] = None,
        includeHeaders: Boolean = true,
        delimiter: Char = ',',
        quote: Char = '"'
    ): os.Path =
      val target = path.getOrElse(os.temp(contents = "", suffix = ".csv", prefix = "scautable-"))
      CSVWriterExtensions.writeLines(target, toCsv(includeHeaders, delimiter, quote))
      target
    end writeCsv

  end extension

end CSVWriterExtensions
