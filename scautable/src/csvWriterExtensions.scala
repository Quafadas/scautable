package io.github.quafadas.scautable

import scala.NamedTuple.*
import scala.compiletime.*

/** Extension methods for writing NamedTuple collections to CSV format.
  */
object CSVWriterExtensions:

  extension [K <: Tuple, V <: Tuple](itr: Iterator[NamedTuple[K, V]])

    /** Converts the iterator to CSV format as a string.
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
    inline def toCsv(
        includeHeaders: Boolean = true,
        delimiter: Char = ',',
        quote: Char = '"'
    ): Iterator[String] =
      val headers = constValueTuple[K].toList.map(_.toString())
      val headerLine = CSVWriter.formatLine(headers, delimiter, quote)

      val striterator = itr.map { namedTuple =>
        val values = namedTuple.toList.map(_.toString)
        CSVWriter.formatLine(values, delimiter, quote)
      }
      if includeHeaders then Iterator(headerLine) ++ striterator
      else striterator
      end if
    end toCsv

  end extension

  extension [CC[X] <: Iterable[X], K <: Tuple, V <: Tuple](data: CC[NamedTuple[K, V]])

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
    inline def toCsv(
        includeHeaders: Boolean,
        delimiter: Char,
        quote: Char
    ): String =
      val headers = constValueTuple[K].toList.map(_.toString())
      val headerLine =
        if includeHeaders then Seq(CSVWriter.formatLine(headers, delimiter, quote))
        else Seq.empty

      val dataLines = data.view.map { namedTuple =>
        val values = namedTuple.toList.map(_.toString)
        CSVWriter.formatLine(values, delimiter, quote)
      }.toSeq

      (headerLine ++ dataLines).mkString("\n")
    end toCsv

  end extension

end CSVWriterExtensions
