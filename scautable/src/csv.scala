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
  transparent inline def resource[T](inline path: String) =
    ${ readCsvResoourceNoOpts('path) }
  end resource

  transparent inline def resource[T](inline path: String, inline opts: CsvReadOptions) =
    ${ readCsvResource('path, 'opts) }
  end resource

  /** Reads a CSV file from an absolute path and returns a [[io.github.quafadas.scautable.CsvIterator]].
    *
    * Example:
    * {{{
    *   val csv: CsvIterator[("colA", "colB", "colC")] = CSV.absolutePath("/absolute/path/to/file.csv")
    * }}}
    */
  transparent inline def absolutePath[T](path: String) = ${ readCsvAbolsutePath('path) }

  /** Ensures unique column names for the iterator.
    *
    * For each repeated column name, appends the column’s 0-based index to the name.
    *
    * Example:
    * {{{
    *    val csv: CsvIterator[("colA", "colB" "colA")] = CSV.absolutePath("...")
    *    val uniqCsv: CsvIterator[("colA", "colB", "colA_2")] = CSV.deduplicateHeaders(csv)
    * }}}
    */
  transparent inline def deduplicateHeaders[K <: Tuple](obj: CsvIterator[K]) = ${ deduplicateHeadersCode('obj) }

  private transparent inline def readHeaderlineAsCsv(path: String, opts: CsvReadOptions)(using q: Quotes) =
    import q.reflect.*

    val itr = new CsvIterator(path.toString, opts)
    val headers = itr.headers

    if headers.length != headers.distinct.length then report.info("Possible duplicated headers detected. Consider using `CSV.deduplicateHeaders`.")
    end if

    val tupHeaders = Expr.ofTupleFromSeq(headers.map(Expr(_)))
    tupHeaders match
      case '{ $tup: t } =>
        // val itr = new CsvIterator[t & Tuple](path.toString)
        Expr(itr.asInstanceOf[CsvIterator[t & Tuple]])
      case _ => report.throwError(s"Could not summon Type for type: ${tupHeaders.show}")
    end match

  end readHeaderlineAsCsv

  private def readCsvFromUrl(pathExpr: Expr[String])(using Quotes) =
    import quotes.reflect.*
    val opts = CsvReadOptions(
      delimiter = ',',
      typeInferenceStrategy = TypeInferenceStrategy.StringsOnly
    )
    report.warning(
      "This method saves the CSV to a local temp file and opens it. There may be performance implications - it is recommended to use one of the other methods where possible."
    )
    val source = Source.fromURL(pathExpr.valueOrAbort)
    val tmpPath = os.temp(dir = os.pwd, prefix = "temp_csv_", suffix = ".csv")
    os.write.over(tmpPath, source.toArray.mkString)
    readHeaderlineAsCsv(tmpPath.toString, opts)

  end readCsvFromUrl

  private def readCsvFromCurrentDir(pathExpr: Expr[String])(using Quotes) =
    import quotes.reflect.*
    val path = os.pwd / pathExpr.valueOrAbort
    val opts = CsvReadOptions(
      delimiter = ',',
      typeInferenceStrategy = TypeInferenceStrategy.StringsOnly
    )
    readHeaderlineAsCsv(path.toString, opts)

  end readCsvFromCurrentDir

  def readCsvAbolsutePath(pathExpr: Expr[String])(using Quotes) =
    import quotes.reflect.*

    val path = pathExpr.valueOrAbort
    readHeaderlineAsCsv(
      path,
      opts = CsvReadOptions(
        delimiter = ',',
        typeInferenceStrategy = TypeInferenceStrategy.StringsOnly
      )
    )
  end readCsvAbolsutePath

  private def readCsvResoourceNoOpts(pathExpr: Expr[String])(using Quotes) =
    readCsvResource(
      pathExpr,
      Expr(
        CsvReadOptions(
          delimiter = ';',
          typeInferenceStrategy = TypeInferenceStrategy.StringsOnly
        )
      )
    )
  end readCsvResoourceNoOpts

  private def readCsvResource(pathExpr: Expr[String], optsExpr: Expr[CsvReadOptions])(using Quotes) =
    import quotes.reflect.*
    val opts = optsExpr.valueOrAbort
    val path = pathExpr.valueOrAbort
    val resourcePath = this.getClass.getClassLoader.getResource(path)
    if resourcePath == null then report.throwError(s"Resource not found: $path")
    end if

    readHeaderlineAsCsv(resourcePath.getPath, opts)
  end readCsvResource

  private def deduplicateHeadersCode[K <: Tuple](objExpr: Expr[CsvIterator[K]])(using Quotes, Type[K]) =
    import quotes.reflect.*

    val obj = objExpr.valueOrAbort

    val headers = obj.headers
    val uniqueHeaders = for ((h, i) <- headers.zipWithIndex) yield if headers.indexOf(h) != i then s"${h}_${i}" else h

    Expr.ofTupleFromSeq(uniqueHeaders.map(Expr(_))) match
      case '{ $tup: t } =>
        val itr = new CsvIterator[t & Tuple](obj.getFilePath, obj.getOpts)
        Expr(itr)
      case _ => report.throwError(s"Could not infer a literal type for ${uniqueHeaders}")
    end match
  end deduplicateHeadersCode

  given IteratorToExpr2[K <: Tuple](using ToExpr[String], Type[K]): ToExpr[CsvIterator[K]] with
    def apply(opt: CsvIterator[K])(using Quotes): Expr[CsvIterator[K]] =
      val str = Expr(opt.getFilePath)
      val opts = Expr(opt.getOpts)
      '{
        new CsvIterator[K]($str, $opts)
      }
    end apply
  end IteratorToExpr2

  given IteratorFromExpr[K <: Tuple](using Type[K]): FromExpr[CsvIterator[K]] with
    def unapply(x: Expr[CsvIterator[K]])(using Quotes): Option[CsvIterator[K]] =
      import quotes.reflect.*
      x.asTerm.underlying.asExprOf[CsvIterator[K]] match
        case '{ new CsvIterator[K](${ Expr(filePath) }, ${ Expr(opts) }) } => Some(new CsvIterator[K](filePath, opts))
        case _                                                             => None
      end match
    end unapply
  end IteratorFromExpr

end CSV
