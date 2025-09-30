package io.github.quafadas.scautable

import scala.quoted.*
import scala.io.Source
import ColumnTyped.*

import io.github.quafadas.table.TypeInferrer

object CSV:

  /** Saves a URL to a local CSV returns a [[io.github.quafadas.scautable.CsvIterator]].
    *
    * Example:
    * {{{
    *   val csv: CsvIterator[("colA", "colB", "colC")] = CSV.url("https://somewhere.com/file.csv")
    * }}}
    */
  transparent inline def url[T](inline csvContent: String): Any = url[T](csvContent, HeaderOptions.Default, TypeInferrer.StringType)

  transparent inline def url[T](inline csvContent: String, inline headers: HeaderOptions): Any = url[T](csvContent, headers, TypeInferrer.StringType)

  transparent inline def url[T](inline csvContent: String, inline dataType: TypeInferrer): Any = url[T](csvContent, HeaderOptions.Default, dataType)

  transparent inline def url[T](inline path: String, inline headers: HeaderOptions, inline dataType: TypeInferrer) = ${ readCsvFromUrl('path, 'headers, 'dataType) }

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
  transparent inline def pwd[T](inline csvContent: String): Any = pwd[T](csvContent, HeaderOptions.Default, TypeInferrer.StringType)

  transparent inline def pwd[T](inline csvContent: String, inline headers: HeaderOptions): Any = pwd[T](csvContent, headers, TypeInferrer.StringType)

  transparent inline def pwd[T](inline csvContent: String, inline dataType: TypeInferrer): Any = pwd[T](csvContent, HeaderOptions.Default, dataType)

  transparent inline def pwd[T](inline path: String, inline headers: HeaderOptions, inline dataType: TypeInferrer) = ${ readCsvFromCurrentDir('path, 'headers, 'dataType) }

  /** Reads a CSV present in java resources and returns a [[io.github.quafadas.scautable.CsvIterator]].
    *
    * Example:
    * {{{
    *   val csv: CsvIterator[("colA", "colB", "colC")] = CSV.resource("file.csv")
    * }}}
    */
  transparent inline def resource[T](inline csvContent: String): Any = resource[T](csvContent, HeaderOptions.Default, TypeInferrer.StringType)

  transparent inline def resource[T](inline csvContent: String, inline headers: HeaderOptions): Any = resource[T](csvContent, headers, TypeInferrer.StringType)

  transparent inline def resource[T](inline csvContent: String, inline dataType: TypeInferrer): Any = resource[T](csvContent, HeaderOptions.Default, dataType)

  transparent inline def resource[T](inline path: String, inline headers: HeaderOptions, inline dataType: TypeInferrer) = ${ readCsvResource('path, 'headers, 'dataType) }

  /** Reads a CSV file from an absolute path and returns a [[io.github.quafadas.scautable.CsvIterator]].
    *
    * Example:
    * {{{
    *   val csv: CsvIterator[("colA", "colB", "colC")] = CSV.absolutePath("/absolute/path/to/file.csv")
    * }}}
    */
  transparent inline def absolutePath[T](inline csvContent: String): Any = absolutePath[T](csvContent, HeaderOptions.Default, TypeInferrer.StringType)

  transparent inline def absolutePath[T](inline csvContent: String, inline headers: HeaderOptions): Any = absolutePath[T](csvContent, headers, TypeInferrer.StringType)

  transparent inline def absolutePath[T](inline csvContent: String, inline dataType: TypeInferrer): Any = absolutePath[T](csvContent, HeaderOptions.Default, dataType)

  transparent inline def absolutePath[T](inline path: String, inline headers: HeaderOptions, inline dataType: TypeInferrer) = ${ readCsvAbsolutePath('path, 'headers, 'dataType) }

  /** Reads a CSV from a String and returns a [[io.github.quafadas.scautable.CsvIterator]].
    *
    * Example:
    * {{{
    * val csvContent = "colA,colB\n1,a\n2,b"
    * val csv: CsvIterator[("colA", "colB")] = CSV.fromString(csvContent)
    * }}}
    */

  // transparent inline def fromString[T](inline csvContent: String): Any = fromString[T](csvContent, HeaderOptions.Default, TypeInferrer.StringType)
  transparent inline def fromString[T](inline csvContent: String): Any = fromString[T](csvContent, HeaderOptions.Default, TypeInferrer.StringType)

  transparent inline def fromString[T](inline csvContent: String, inline headers: HeaderOptions): Any = fromString[T](csvContent, headers, TypeInferrer.StringType)

  transparent inline def fromString[T](inline csvContent: String, inline dataType: TypeInferrer): Any = fromString[T](csvContent, HeaderOptions.Default, dataType)

  transparent inline def fromString[T](inline csvContent: String, inline headers: HeaderOptions, inline dataType: TypeInferrer) = ${
    readCsvFromString('csvContent, 'headers, 'dataType)
  }

  /** Reads a CSV from the system clipboard and returns a [[io.github.quafadas.scautable.CsvIterator]].
    *
    * Default behavior: single header row, infer types from all rows.
    *
    * Example:
    * {{{
    * // Copy CSV data to clipboard first
    * val csv: CsvIterator[("colA", "colB")] = CSV.clipboard()
    * }}}
    */
  transparent inline def clipboard[T](): Any = clipboard[T](HeaderOptions.Default, TypeInferrer.FromAllRows)

  transparent inline def clipboard[T](inline headers: HeaderOptions): Any = clipboard[T](headers, TypeInferrer.FromAllRows)

  transparent inline def clipboard[T](inline dataType: TypeInferrer): Any = clipboard[T](HeaderOptions.Default, dataType)

  transparent inline def clipboard[T](inline headers: HeaderOptions, inline dataType: TypeInferrer) = ${
    readCsvFromClipboard('headers, 'dataType)
  }

  private transparent inline def readHeaderlineAsCsv(path: String, csvHeaders: Expr[HeaderOptions], dataType: Expr[TypeInferrer])(using q: Quotes) =
    import q.reflect.*
    import io.github.quafadas.scautable.HeaderOptions.*

    val source = Source.fromFile(path)
    val lineIterator: Iterator[String] = source.getLines()
    val (headers, iter) = lineIterator.headers(csvHeaders.valueOrAbort)

    if headers.length != headers.distinct.length then report.info("Possible duplicated headers detected.")
    end if

    val headerTupleExpr = Expr.ofTupleFromSeq(headers.map(Expr(_)))

    def constructWithTypes[Hdrs <: Tuple: Type, Data <: Tuple: Type]: Expr[CsvIterator[Hdrs, Data]] =
      val filePathExpr = Expr(path)
      '{
        val lines = scala.io.Source.fromFile($filePathExpr).getLines()
        val (headers, iterator) = lines.headers(${ csvHeaders })
        new CsvIterator[Hdrs, Data](iterator, headers)
      }
    end constructWithTypes

    headerTupleExpr match
      case '{ $tup: hdrs } =>
        dataType match

          case '{ TypeInferrer.FromTuple[t]() } =>
            constructWithTypes[hdrs & Tuple, t & Tuple]

          case '{ TypeInferrer.StringType } =>
            constructWithTypes[hdrs & Tuple, StringyTuple[hdrs & Tuple] & Tuple]

          case '{ TypeInferrer.FirstRow } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, true)
            inferredTypeRepr.asType match
              case '[v] =>
                constructWithTypes[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FromAllRows } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, false, Int.MaxValue)
            inferredTypeRepr.asType match
              case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FirstN(${ Expr(n) }) } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, true, n)
            inferredTypeRepr.asType match
              case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FirstN(${ Expr(n) }, ${ Expr(preferIntToBoolean) }) } =>
            println(preferIntToBoolean)
            val inferredTypeRepr = InferrerOps.inferrer(iter, preferIntToBoolean, n)
            inferredTypeRepr.asType match
              case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
            end match

      case _ =>
        report.throwError("Could not infer literal header tuple.")
    end match

  end readHeaderlineAsCsv

  private def readCsvFromUrl(pathExpr: Expr[String], csvHeaders: Expr[HeaderOptions], dataType: Expr[TypeInferrer])(using Quotes) =
    import quotes.reflect.*

    report.warning(
      "This method saves the CSV to a local temp file and opens it. There may be performance implications - it is recommended to use one of the other methods where possible."
    )
    val source = Source.fromURL(pathExpr.valueOrAbort)
    val tmpPath = os.temp(dir = os.pwd, prefix = "temp_csv_", suffix = ".csv")
    os.write.over(tmpPath, source.toArray.mkString)
    readHeaderlineAsCsv(tmpPath.toString, csvHeaders, dataType)

  end readCsvFromUrl

  private def readCsvFromCurrentDir(pathExpr: Expr[String], csvHeaders: Expr[HeaderOptions], dataType: Expr[TypeInferrer])(using Quotes) =
    val path = os.pwd / pathExpr.valueOrAbort
    readHeaderlineAsCsv(path.toString, csvHeaders, dataType)
  end readCsvFromCurrentDir

  def readCsvAbsolutePath(pathExpr: Expr[String], csvHeaders: Expr[HeaderOptions], dataType: Expr[TypeInferrer])(using Quotes) =
    val path = pathExpr.valueOrAbort
    readHeaderlineAsCsv(path, csvHeaders, dataType)
  end readCsvAbsolutePath

  private def readCsvResource(pathExpr: Expr[String], csvHeaders: Expr[HeaderOptions], dataType: Expr[TypeInferrer])(using Quotes) =
    import quotes.reflect.*

    val path = pathExpr.valueOrAbort
    val resourcePath = this.getClass.getClassLoader.getResource(path)
    if resourcePath == null then report.throwError(s"Resource not found: $path")
    end if

    readHeaderlineAsCsv(resourcePath.getPath, csvHeaders, dataType)
  end readCsvResource

  private def readCsvFromString(csvContentExpr: Expr[String], csvHeaders: Expr[HeaderOptions], dataType: Expr[TypeInferrer])(using Quotes) =
    import quotes.reflect.*
    import io.github.quafadas.scautable.HeaderOptions.*

    val content = csvContentExpr.valueOrAbort

    if content.trim.isEmpty then report.throwError("Empty CSV content provided.")
    end if

    val lines = content.linesIterator
    val (headers, iter) = lines.headers(csvHeaders.valueOrAbort)

    if headers.length != headers.distinct.length then report.info("Possible duplicated headers detected.")

    end if

    val headerTupleExpr = Expr.ofTupleFromSeq(headers.map(Expr(_)))

    def constructWithTypes[Hdrs <: Tuple: Type, Data <: Tuple: Type]: Expr[CsvIterator[Hdrs, Data]] =
      '{
        val content = $csvContentExpr
        val lines = content.linesIterator
        val (headers, iterator) = lines.headers($csvHeaders)
        new CsvIterator[Hdrs, Data](iterator, headers)
      }

    headerTupleExpr match
      case '{ $tup: hdrs } =>
        dataType match

          case '{ TypeInferrer.FromTuple[t]() } =>
            constructWithTypes[hdrs & Tuple, t & Tuple]

          case '{ TypeInferrer.StringType } =>
            constructWithTypes[hdrs & Tuple, StringyTuple[hdrs & Tuple] & Tuple]

          case '{ TypeInferrer.FirstRow } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, true)
            inferredTypeRepr.asType match
              case '[v] =>
                constructWithTypes[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FromAllRows } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, false, Int.MaxValue)
            inferredTypeRepr.asType match
              case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FirstN(${ Expr(n) }) } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, true, n)
            inferredTypeRepr.asType match
              case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FirstN(${ Expr(n) }, ${ Expr(preferIntToBoolean) }) } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, preferIntToBoolean, n)
            inferredTypeRepr.asType match
              case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
            end match

      case _ =>
        report.throwError("Could not infer literal header tuple.")
    end match
  end readCsvFromString

  private def readCsvFromClipboard(csvHeaders: Expr[HeaderOptions], dataType: Expr[TypeInferrer])(using Quotes) =
    import quotes.reflect.*
    import io.github.quafadas.scautable.HeaderOptions.*

    // Read clipboard content at compile time
    val platformSpecific = new io.github.quafadas.scautable.PlatformSpecific {}
    val content = platformSpecific.readClipboard()

    if content.trim.isEmpty then report.throwError("Empty clipboard content. Please copy CSV data to clipboard.")
    end if

    val lines = content.linesIterator
    val (headers, iter) = lines.headers(csvHeaders.valueOrAbort)

    if headers.length != headers.distinct.length then report.info("Possible duplicated headers detected.")

    end if

    val headerTupleExpr = Expr.ofTupleFromSeq(headers.map(Expr(_)))

    def constructWithTypes[Hdrs <: Tuple: Type, Data <: Tuple: Type]: Expr[CsvIterator[Hdrs, Data]] =
      '{
        val platformSpecific = new io.github.quafadas.scautable.PlatformSpecific {}
        val content = platformSpecific.readClipboard()
        val lines = content.linesIterator
        val (headers, iterator) = lines.headers($csvHeaders)
        new CsvIterator[Hdrs, Data](iterator, headers)
      }

    headerTupleExpr match
      case '{ $tup: hdrs } =>
        dataType match

          case '{ TypeInferrer.FromTuple[t]() } =>
            constructWithTypes[hdrs & Tuple, t & Tuple]

          case '{ TypeInferrer.StringType } =>
            constructWithTypes[hdrs & Tuple, StringyTuple[hdrs & Tuple] & Tuple]

          case '{ TypeInferrer.FirstRow } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, true)
            inferredTypeRepr.asType match
              case '[v] =>
                constructWithTypes[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FromAllRows } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, false, Int.MaxValue)
            inferredTypeRepr.asType match
              case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FirstN(${ Expr(n) }) } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, true, n)
            inferredTypeRepr.asType match
              case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FirstN(${ Expr(n) }, ${ Expr(preferIntToBoolean) }) } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, preferIntToBoolean, n)
            inferredTypeRepr.asType match
              case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
            end match

      case _ =>
        report.throwError("Could not infer literal header tuple.")
    end match
  end readCsvFromClipboard

end CSV
