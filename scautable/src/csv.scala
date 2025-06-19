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
import io.github.quafadas.scautable.CSVUtils.*


object CSV:

  /** Saves a URL to a local CSV returns a [[io.github.quafadas.scautable.CsvIterator]].
    *
    * Example:
    * {{{
    *   val csv: CsvIterator[("colA", "colB", "colC")] = CSV.url("https://somewhere.com/file.csv")
    * }}}
    */
  transparent inline def url[T](inline path: String) = ${ readCsvFromUrl('path) }

  /** Reads a CSV present in the current _compiler_ working directory resources and returns a [[io.github.quafadas.scautable.CsvIterator]].
    *
    * Note that in most cases, this is _not_ the same as the current _runtime_ working directory, and you are likely to get the bloop server directory.
    *
    * Hopefully, useful in almond notebooks.
    *
    * Example:
    * {{{
    *   val csv: CsvIterator[("colA", "colB", "colC")] = CSV.pwd("file.csv")
    * }}}
    */
  transparent inline def pwd[T](inline path: String) = ${ readCsvFromCurrentDir('path) }

  /** Reads a CSV present in java resources and returns a [[io.github.quafadas.scautable.CsvIterator]].
    *
    * Example:
    * {{{
    *   val csv: CsvIterator[("colA", "colB", "colC")] = CSV.resource("file.csv")
    * }}}
    */
  transparent inline def resource[T](inline path: String) = ${ readCsvResource('path) }

  /** Reads a CSV file from an absolute path and returns a [[io.github.quafadas.scautable.CsvIterator]].
    *
    * Example:
    * {{{
    *   val csv: CsvIterator[("colA", "colB", "colC")] = CSV.absolutePath("/absolute/path/to/file.csv")
    * }}}
    */
  transparent inline def absolutePath[T](inline path: String) = ${ readCsvAbolsutePath('path) }

    /** Reads a CSV from a String and returns a [[io.github.quafadas.scautable.CsvIterator]].
    *
    * Example:
    * {{{
    * val csvContent = "colA,colB\n1,a\n2,b"
    * val csv: CsvIterator[("colA", "colB")] = CSV.fromString(csvContent)
    * }}}
    */
  transparent inline def fromString[T](inline csvContent: String): CsvIterator[?] = fromString[T](csvContent, HeaderOptions.Default)
  
  transparent inline def fromString[T](inline csvContent: String, inline headers: HeaderOptions) = ${ readCsvFromString('csvContent, 'headers) }

  private transparent inline def readHeaderlineAsCsv(path: String)(using q: Quotes) =
    import q.reflect.*

    lazy val source = Source.fromFile(path)
    lazy val lineIterator: Iterator[String] = source.getLines()
    lazy val headers = CSVParser.parseLine(lineIterator.next())
    val itr = new CsvIterator(lineIterator, headers)

    if headers.length != headers.distinct.length then report.info("Possible duplicated headers detected.")
    end if

    val headerTupleExpr = Expr.ofTupleFromSeq(headers.map(Expr(_)))
    headerTupleExpr match
      case '{ $tup: t } =>
        val filePathExpr = Expr(path)
        '{
          val lines = scala.io.Source.fromFile($filePathExpr).getLines()
          val headers = CSVParser.parseLine(lines.next())
          new CsvIterator[t & Tuple](lines, headers)
        }
      case _ =>
        report.throwError(s"Could not infer literal tuple type from headers: ${headers}")

  end readHeaderlineAsCsv

  private def readCsvFromUrl(pathExpr: Expr[String])(using Quotes) =
    import quotes.reflect.*

    report.warning(
      "This method saves the CSV to a local temp file and opens it. There may be performance implications - it is recommended to use one of the other methods where possible."
    )
    val source = Source.fromURL(pathExpr.valueOrAbort)
    val tmpPath = os.temp(dir = os.pwd, prefix = "temp_csv_", suffix = ".csv")
    os.write.over(tmpPath, source.toArray.mkString)
    readHeaderlineAsCsv(tmpPath.toString)

  end readCsvFromUrl

  private def readCsvFromCurrentDir(pathExpr: Expr[String])(using Quotes) =
    import quotes.reflect.*
    val path = os.pwd / pathExpr.valueOrAbort
    readHeaderlineAsCsv(path.toString)

  end readCsvFromCurrentDir

  def readCsvAbolsutePath(pathExpr: Expr[String])(using Quotes) =
    import quotes.reflect.*

    val path = pathExpr.valueOrAbort
    readHeaderlineAsCsv(path)
  end readCsvAbolsutePath

  private def readCsvResource(pathExpr: Expr[String])(using Quotes) =
    import quotes.reflect.*

    val path = pathExpr.valueOrAbort
    val resourcePath = this.getClass.getClassLoader.getResource(path)
    if resourcePath == null then report.throwError(s"Resource not found: $path")
    end if

    readHeaderlineAsCsv(resourcePath.getPath)
  end readCsvResource

  private def readCsvFromString(csvContentExpr: Expr[String], csvHeaders: Expr[HeaderOptions])(using Quotes) =
    import quotes.reflect.*
    import io.github.quafadas.scautable.HeaderOptions.*

    val content = csvContentExpr.valueOrAbort
    if content.trim.isEmpty then
      report.throwError("Empty CSV content provided.")
    val lines = content.linesIterator
    val (headers, iter) = lines.headers(csvHeaders.valueOrAbort)


    if headers.length != headers.distinct.length then report.info("Possible duplicated headers detected.")
    end if

    val headerTupleExpr = Expr.ofTupleFromSeq(headers.map(Expr(_)))
    headerTupleExpr match
      case '{ $tup: t } =>
        '{
          val content = ${csvContentExpr}
          val lines = content.linesIterator
          val (headers, iterator) = lines.headers(${csvHeaders})
          new CsvIterator[t & Tuple](iterator, headers)
        }
      case _ =>
        report.throwError(s"Could not infer literal tuple type from headers: ${headers}")
  end readCsvFromString


end CSV