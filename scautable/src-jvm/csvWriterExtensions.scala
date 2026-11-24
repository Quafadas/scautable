package io.github.quafadas.scautable

import scala.NamedTuple.*
import scala.compiletime.*

/** Extension methods for writing NamedTuple collections to CSV format.
  */
object CSVWriterExtensions:

  /** Writes all lines (each terminated by a newline) to `target` in a single streaming `os.write.over`, always replacing any existing file content - even when there are no lines.
    */
  private[scautable] def writeLines(target: os.Path, lines: Iterator[String]): Unit =
    writeLines(target, geny.Generator.from(lines))

  /** As above, for a push-based [[geny.Generator]] (e.g. scalasql's `db.stream`), which cannot become an `Iterator` without buffering.
    */
  private[scautable] def writeLines(target: os.Path, lines: geny.Generator[String]): Unit =
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

  /** The CSV header line for the column names `K`. */
  private[scautable] inline def headerLine[K <: Tuple](delimiter: Char, quote: Char): String =
    CSVWriter.formatLine(constValueTuple[K].toList.map(_.toString()), delimiter, quote)

  extension [K <: Tuple, V <: Tuple](gen: geny.Generator[NamedTuple[K, V]])

    /** Converts the generator to CSV format as a generator of lines. Nothing runs until the result is consumed.
      */
    inline def toCsv(
        includeHeaders: Boolean,
        delimiter: Char,
        quote: Char
    ): geny.Generator[String] =
      val strGen = gen.map { namedTuple =>
        CSVWriter.formatRow(namedTuple.toList, delimiter, quote)
      }
      if includeHeaders then geny.Generator(headerLine[K](delimiter, quote)) ++ strGen
      else strGen
      end if
    end toCsv

    /** Writes the generator to a CSV file row by row, never holding all rows in memory. Any existing file at `path` is replaced.
      *
      * Intended for scalasql's `db.stream(query)`, whose [[geny.Generator]] keeps its JDBC result set open only while it is consumed.
      */
    inline def writeCsvGenerator(
        path: Option[os.Path] = None,
        includeHeaders: Boolean = true,
        delimiter: Char = ',',
        quote: Char = '"'
    ): os.Path =
      val target = path.getOrElse(os.temp(contents = "", suffix = ".csv", prefix = "scautable-"))
      CSVWriterExtensions.writeLines(target, toCsv(includeHeaders, delimiter, quote))
      target
    end writeCsvGenerator

  end extension

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
      val strIterator = itr.map { namedTuple =>
        CSVWriter.formatRow(namedTuple.toList, delimiter, quote)
      }
      if includeHeaders then Iterator(headerLine[K](delimiter, quote)) ++ strIterator
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
      data.iterator.toCsv(includeHeaders, delimiter, quote)
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
      val header =
        if includeHeaders then Seq(headerLine[K](delimiter, quote))
        else Seq.empty

      val dataLines = data.view.map { namedTuple =>
        CSVWriter.formatRow(namedTuple.toList, delimiter, quote)
      }.toSeq

      (header ++ dataLines).mkString("\n")
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
