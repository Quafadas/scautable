package io.github.quafadas.scautable

import scala.compiletime.*
import scala.collection.BuildFrom

/** Extension methods for writing NamedTuple collections to CSV format.
  */
object CSVWriterExtensions:

  extension [K <: Tuple, V <: Tuple](itr: Iterator[NamedTuple[K, V]])
    
    /** Converts the iterator to CSV format as a string.
      *
      * @param includeHeaders Whether to include column headers as the first line
      * @param delimiter The delimiter character (default: comma)
      * @param quote The quote character (default: double quote)
      * @return CSV formatted string
      */
    def toCsv(
        includeHeaders: Boolean = true,
        delimiter: Char = ',',
        quote: Char = '"'
    ): String =
      val headers = constValueTuple[K].toList.map(_.toString())
      val headerLine = if includeHeaders then
        Seq(CSVWriter.formatLine(headers, delimiter, quote))
      else
        Seq.empty
      
      val dataLines = itr.map { namedTuple =>
        val values = namedTuple.toList.map(_.toString)
        CSVWriter.formatLine(values, delimiter, quote)
      }.toSeq
      
      (headerLine ++ dataLines).mkString("\n")
    end toCsv
    
    /** Writes the iterator to a CSV file.
      *
      * @param path The file path to write to
      * @param includeHeaders Whether to include column headers as the first line
      * @param delimiter The delimiter character (default: comma)
      * @param quote The quote character (default: double quote)
      */
    def writeCsv(
        path: os.Path,
        includeHeaders: Boolean = true,
        delimiter: Char = ',',
        quote: Char = '"'
    ): Unit =
      os.write.over(path, toCsv(includeHeaders, delimiter, quote))
    end writeCsv

  end extension

  extension [CC[X] <: Iterable[X], K <: Tuple, V <: Tuple](data: CC[NamedTuple[K, V]])
    
    /** Converts the iterable to CSV format as a string.
      *
      * @param includeHeaders Whether to include column headers as the first line
      * @param delimiter The delimiter character (default: comma)
      * @param quote The quote character (default: double quote)
      * @return CSV formatted string
      */
    def toCsv(
        includeHeaders: Boolean = true,
        delimiter: Char = ',',
        quote: Char = '"'
    ): String =
      val headers = constValueTuple[K].toList.map(_.toString())
      val headerLine = if includeHeaders then
        Seq(CSVWriter.formatLine(headers, delimiter, quote))
      else
        Seq.empty
      
      val dataLines = data.view.map { namedTuple =>
        val values = namedTuple.toList.map(_.toString)
        CSVWriter.formatLine(values, delimiter, quote)
      }.toSeq
      
      (headerLine ++ dataLines).mkString("\n")
    end toCsv
    
    /** Writes the iterable to a CSV file.
      *
      * @param path The file path to write to
      * @param includeHeaders Whether to include column headers as the first line
      * @param delimiter The delimiter character (default: comma)
      * @param quote The quote character (default: double quote)
      */
    def writeCsv(
        path: os.Path,
        includeHeaders: Boolean = true,
        delimiter: Char = ',',
        quote: Char = '"'
    ): Unit =
      os.write.over(path, toCsv(includeHeaders, delimiter, quote))
    end writeCsv

  end extension

end CSVWriterExtensions