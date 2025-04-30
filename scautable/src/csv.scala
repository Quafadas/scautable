package io.github.quafadas.scautable

import scala.quoted.*
import scala.io.Source
import NamedTuple.withNames
import scala.NamedTuple.*
import scala.collection.immutable.Stream.Empty
import scala.deriving.Mirror
import scala.io.BufferedSource
import scala.util.Using.Manager.Resource
import scala.compiletime.*
import scala.compiletime.ops.int.*
import fansi.Str
import scala.collection.View.FlatMap
import io.github.quafadas.scautable.ConsoleFormat.*
import ColumnTyped.*
import scala.math.Fractional.Implicits.*
import scala.collection.View.Single

/**
 * Main entry point for CSV operations in Scautable.
 * 
 * This object provides functions for reading CSV files with compile-time typing,
 * including special handling for duplicate headers in CSV files.
 * 
 * == Basic Usage ==
 * 
 * Reading a CSV file with known headers:
 * {{{
 * val csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath("/path/to/file.csv")
 * 
 * // Access typed columns
 * csv.column["col1"]
 * }}}
 * 
 * == Handling Duplicate Headers ==
 * 
 * When working with CSV files that have duplicate headers, you have several options:
 * 
 * 1. '''Automatic detection''' (recommended):
 * {{{
 * // No type annotation needed - headers are automatically detected and normalized
 * val csv = CSV.detectHeaders("/path/to/file_with_duplicates.csv")
 * 
 * // If original headers were "col1,col2,col1,col3", you can access:
 * csv.col1    // First "col1" column
 * csv.col1_1  // Second "col1" column (normalized)
 * }}}
 * 
 * 2. '''Using explicit method with known normalized headers''':
 * {{{
 * // Need to know normalized header names in advance
 * val csv: CsvIterator[("col1", "col2", "col1_1", "col3")] = 
 *   CSV.absolutePathWithDuplicates("/path/to/file_with_duplicates.csv")
 * }}}
 * 
 * 3. '''Using utility methods to determine normalized headers''':
 * {{{
 * // Get normalized headers first
 * val normalizedHeadersStr = CSV.getNormalizedHeadersString("/path/to/file.csv")
 * println(normalizedHeadersStr) // e.g., ("col1", "col2", "col1_1", "col3")
 * 
 * // Then use in type annotation
 * val csv: CsvIterator[("col1", "col2", "col1_1", "col3")] = 
 *   CSV.absolutePathWithDuplicates("/path/to/file.csv")
 * }}}
 * 
 * The CsvIterator provides additional utilities for working with normalized headers:
 * {{{
 * // Check if headers were normalized
 * if (csv.hasNormalizedHeaders) {
 *   // Get a report of the normalization
 *   println(csv.headerNormalizationReport.get)
 * }
 * 
 * // Get original headers
 * csv.originalHeaders  // e.g., List("col1", "col2", "col1", "col3")
 * 
 * // Get normalized headers
 * csv.headers          // e.g., List("col1", "col2", "col1_1", "col3")
 * }}}
 */
object CSV:

  transparent inline def url[T](inline path: String) = ${ readCsvFromUrl('path) }

  transparent inline def pwd[T](inline path: String) = ${ readCsvFromCurrentDir('path) }

  transparent inline def resource[T](inline path: String) = ${ readCsvResource('path) }

  transparent inline def absolutePath[T](path: String) = ${ readCsvAbolsutePath('path) }
  
  /**
   * Reads a CSV file from an absolute path, handling duplicate headers by normalizing them.
   * This is similar to absolutePath but it explicitly handles duplicate headers.
   */
  transparent inline def absolutePathWithDuplicates[T](path: String) = 
    ${ readCsvWithDuplicateHeaders('path) }
    
  /**
   * Automatically detects headers from a CSV file and returns a CsvIterator with the correct type.
   * This is the most user-friendly way to read CSV files with potential duplicate headers.
   * 
   * Example usage:
   * ```
   * // No need to explicitly specify header types - they're automatically detected
   * val csv = CSV.detectHeaders("/path/to/file.csv") 
   * 
   * // If the file has headers like "col1,col2,col1,col3", you can use:
   * csv.col1    // First "col1" 
   * csv.col1_1  // Second "col1" that was normalized to "col1_1"
   * ```
   * 
   * @param path Path to the CSV file
   * @return CsvIterator with the correctly inferred header types
   */
  transparent inline def detectHeaders(path: String) = 
    ${ detectCsvHeaders('path) }
    
  /**
   * Utility method to help users determine the normalized headers for a file with duplicate headers.
   * This can be used to create the correct type signature for compile-time typing.
   * 
   * Example usage:
   * ```
   * // Get normalized headers
   * val normalizedHeaders = CSV.getNormalizedHeadersString("/path/to/file.csv")
   * println(normalizedHeaders) // prints: ("col1", "col2", "col1_1", "col3")
   * 
   * // Then use this string in type signature
   * val csv: CsvIterator[(normalizedHeadersType)] = CSV.absolutePathWithDuplicates(...)
   * ```
   * 
   * @param path Path to the CSV file
   * @return A formatted string containing the normalized headers, suitable for type annotations
   */
  def getNormalizedHeadersString(path: String): String = {
    val source = Source.fromFile(path)
    try {
      val headerLine = source.getLines().next()
      val headers = headerLine.split(",").toList
      val normalizedHeaders = HeaderUtils.normalizeHeaders(headers)
      
      // Format as type signature tuple
      val headerStrings = normalizedHeaders.map(h => s""""$h"""")
      s"(${headerStrings.mkString(", ")})"
    } finally {
      source.close()
    }
  }
  
  /**
   * Generates a code snippet that demonstrates how to use absolutePathWithDuplicates 
   * with the correct type signature for a file that might have duplicate headers.
   * 
   * @param path Path to the CSV file
   * @return Code snippet as string
   */
  def generateDuplicateHeaderUsageExample(path: String): String = {
    val normalizedHeadersStr = getNormalizedHeadersString(path)
    val fileName = path.split("/").last
    
    s"""// Example code for reading "$fileName" with potential duplicate headers
       |val csv: CsvIterator[$normalizedHeadersStr] = CSV.absolutePathWithDuplicates("$path")
       |
       |// The headers have been normalized as follows:
       |// Original headers: ${getOriginalHeadersString(path)}
       |// Normalized headers: $normalizedHeadersStr
       |""".stripMargin
  }
  
  /**
   * Utility method to get the original headers from a CSV file as a formatted string.
   * 
   * @param path Path to the CSV file
   * @return A formatted string containing the original headers
   */
  def getOriginalHeadersString(path: String): String = {
    val source = Source.fromFile(path)
    try {
      val headerLine = source.getLines().next()
      val headers = headerLine.split(",").toList
      
      // Format as list
      val headerStrings = headers.map(h => s""""$h"""")
      s"(${headerStrings.mkString(", ")})"
    } finally {
      source.close()
    }
  }

  given IteratorToExpr2[K <: Tuple](using ToExpr[String], Type[K]): ToExpr[CsvIterator[K]] with
    def apply(opt: CsvIterator[K])(using Quotes): Expr[CsvIterator[K]] =
      val str = Expr(opt.getFilePath)
      '{
        new CsvIterator[K]($str)
      }
    end apply
  end IteratorToExpr2
  
  /**
   * Implementation of detectHeaders macro.
   * This reads the CSV file at compile-time, normalizes any duplicate headers,
   * and automatically creates the appropriate type signature.
   */
  private def detectCsvHeaders(pathExpr: Expr[String])(using Quotes): Expr[Any] = {
    import quotes.reflect.*
    
    val path = pathExpr.valueOrAbort
    val source = Source.fromFile(path)
    
    try {
      val headers = source.getLines().next().split(",").toList
      
      // Always normalize headers
      val normalizedHeaders = HeaderUtils.normalizeHeaders(headers)
      val tupHeaders = Expr.ofTupleFromSeq(normalizedHeaders.map(Expr(_)))
      
      // Check if normalization changed the headers (e.g., due to duplicates)
      val hasDuplicates = headers.distinct.length != headers.length
      if (hasDuplicates) {
        report.info(s"Duplicate headers detected in CSV file at $path")
        report.info(s"Original headers: ${headers.mkString(", ")}")
        report.info(s"Normalized headers: ${normalizedHeaders.mkString(", ")}")
      }
      
      tupHeaders match
        case '{ $tup: t } =>
          val itr = new CsvIterator[t & Tuple](path.toString)
          Expr(itr)
        case _ => report.throwError(s"Could not summon Type for type: ${tupHeaders.show}")
      end match
    } finally {
      source.close()
    }
  }

  private transparent inline def readHeaderlineAsCsv(bs: BufferedSource, path: String)(using q: Quotes) =
    import q.reflect.*
    try
      val headers = bs.getLines().next().split(",").toList
      
      // Detect and handle duplicate headers
      val originalHeaders = headers
      val hasDuplicates = headers.distinct.length != headers.length
      
      // Use normalized headers if duplicates are found
      val processedHeaders = 
        if (hasDuplicates) {
          report.warning(s"Duplicate headers detected in CSV file. Using normalized headers.")
          HeaderUtils.normalizeHeaders(headers)
        } else {
          headers
        }
      
      val tupHeaders = Expr.ofTupleFromSeq(processedHeaders.map(Expr(_)))
      tupHeaders match
        case '{ $tup: t } =>
          val itr = new CsvIterator[t & Tuple](path.toString)
          Expr(itr)
        case _ => report.throwError(s"Could not summon Type for type: ${tupHeaders.show}")
      end match

    finally bs.close()
    end try
  end readHeaderlineAsCsv
  
  private def readCsvWithDuplicateHeaders(pathExpr: Expr[String])(using Quotes) =
    import quotes.reflect.*

    val path = pathExpr.valueOrAbort
    val source = Source.fromFile(path)
    
    try
      val headers = source.getLines().next().split(",").toList
      source.close()
      
      // Always normalize headers, even if no duplicates found
      val normalizedHeaders = HeaderUtils.normalizeHeaders(headers)
      
      // Create a new source to start over
      val newSource = Source.fromFile(path)
      val tupHeaders = Expr.ofTupleFromSeq(normalizedHeaders.map(Expr(_)))
      
      tupHeaders match
        case '{ $tup: t } =>
          val itr = new CsvIterator[t & Tuple](path.toString)
          Expr(itr)
        case _ => report.throwError(s"Could not summon Type for type: ${tupHeaders.show}")
      end match
    finally
      source.close()
    end try
  end readCsvWithDuplicateHeaders

  private def readCsvFromUrl(pathExpr: Expr[String])(using Quotes) =
    import quotes.reflect.*

    report.warning(
      "This method saves the CSV to a local temp file and opens it. There may be performance implications - it is recommended to use one of the other methods where possible."
    )
    val source = Source.fromURL(pathExpr.valueOrAbort)
    val tmpPath = os.temp(dir = os.pwd, prefix = "temp_csv_", suffix = ".csv")
    os.write.over(tmpPath, source.toArray.mkString)
    readHeaderlineAsCsv(source, tmpPath.toString)

  end readCsvFromUrl

  private def readCsvFromCurrentDir(pathExpr: Expr[String])(using Quotes) =
    import quotes.reflect.*
    val path = os.pwd / pathExpr.valueOrAbort
    val source = Source.fromFile(path.toString)
    readHeaderlineAsCsv(source, path.toString)

  end readCsvFromCurrentDir

  def readCsvAbolsutePath(pathExpr: Expr[String])(using Quotes) =
    import quotes.reflect.*

    val path = pathExpr.valueOrAbort
    val source = Source.fromFile(path)
    readHeaderlineAsCsv(source, path)
  end readCsvAbolsutePath

  private def readCsvResource(pathExpr: Expr[String])(using Quotes) =
    import quotes.reflect.*

    val path = pathExpr.valueOrAbort
    val resourcePath = this.getClass.getClassLoader.getResource(path)
    if resourcePath == null then report.throwError(s"Resource not found: $path")
    end if
    val source = Source.fromResource(path)

    readHeaderlineAsCsv(source, resourcePath.getPath)
  end readCsvResource

end CSV
