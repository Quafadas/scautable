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
  import scala.quoted.Quotes

  enum CsvErrorStrategy:
    /** Fails the compilation if an error is encountered while reading the CSV. */
    case Fail

    /** Returns an empty iterator if an error is encountered while reading the CSV. */
    case Empty
  end CsvErrorStrategy

  enum TypeStrategy:
    case FirstRow, AutoType, AllStrings
  end TypeStrategy

  given FromExpr[CsvErrorStrategy] with
    def unapply(expr: Expr[CsvErrorStrategy])(using Quotes): Option[CsvErrorStrategy] =
      expr match
        case '{ CsvErrorStrategy.Fail }  => Some(CsvErrorStrategy.Fail)
        case '{ CsvErrorStrategy.Empty } => Some(CsvErrorStrategy.Empty)
        case _                           => None
  end given

  given FromExpr[TypeStrategy] with
    def unapply(expr: Expr[TypeStrategy])(using Quotes): Option[TypeStrategy] =
      expr match
        case '{ TypeStrategy.FirstRow }   => Some(TypeStrategy.FirstRow)
        case '{ TypeStrategy.AutoType }   => Some(TypeStrategy.AutoType)
        case '{ TypeStrategy.AllStrings } => Some(TypeStrategy.AllStrings)
        case _                            => None
  end given

  given FromExpr[CsvReadOptions] with
    def unapply(expr: Expr[CsvReadOptions])(using Quotes): Option[CsvReadOptions] =
      expr match
        case '{ CsvReadOptions($delimiter, $errorStrategy, $typeStrategy) } =>
          for
            d <- delimiter.value
            e <- errorStrategy.value
            t <- typeStrategy.value
          yield CsvReadOptions(d, e, t)
        case _ => None
  end given

  // ...existing code...
  given FromExpr[CsvReadOptions2] with
    def unapply(expr: Expr[CsvReadOptions2])(using Quotes): Option[CsvReadOptions2] =
      import quotes.reflect.*
      println(expr.asTerm.asExprOf[CsvReadOptions2])
      val argd = expr.asTerm
      val delimiter = Select(argd, Symbol.)


    end unapply
  end given
  // ...existing code...
  given ToExpr[CsvReadOptions2] with
    def apply(opt: CsvReadOptions2)(using Quotes): Expr[CsvReadOptions2] =
      val delimiterExpr = Expr(opt.delimiter)
      '{ CsvReadOptions2($delimiterExpr) }
    end apply
  end given

  case class CsvReadOptions(
      delimiter: Char,
      errorStrategy: CsvErrorStrategy = CsvErrorStrategy.Fail,
      typeStrategy: TypeStrategy = TypeStrategy.FirstRow
  )
  case class CsvReadOptions2(
      delimiter: Char
  )

  given ToExpr[CsvErrorStrategy] with
    def apply(e: CsvErrorStrategy)(using Quotes): Expr[CsvErrorStrategy] = e match
      case CsvErrorStrategy.Fail  => '{ CsvErrorStrategy.Fail }
      case CsvErrorStrategy.Empty => '{ CsvErrorStrategy.Empty }
  end given

  given ToExpr[CsvReadOptions] with
    def apply(opt: CsvReadOptions)(using Quotes): Expr[CsvReadOptions] =
      val delimiterExpr = Expr(opt.delimiter)
      val errorStrategyExpr = Expr(opt.errorStrategy)
      val typeStrategyExpr = Expr(opt.typeStrategy)
      '{ CsvReadOptions($delimiterExpr, $errorStrategyExpr, $typeStrategyExpr) }
    end apply
  end given

  given ToExpr[TypeStrategy] with
    def apply(ts: TypeStrategy)(using Quotes): Expr[TypeStrategy] = ts match
      case TypeStrategy.FirstRow   => '{ TypeStrategy.FirstRow }
      case TypeStrategy.AutoType   => '{ TypeStrategy.AutoType }
      case TypeStrategy.AllStrings => '{ TypeStrategy.AllStrings }
  end given

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

  transparent inline def resource[T](inline path: String, inline opts: CsvReadOptions2) =
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

  given IteratorToExpr2[K <: Tuple](using ToExpr[String], Type[K]): ToExpr[CsvIterator[K]] with
    def apply(opt: CsvIterator[K])(using Quotes): Expr[CsvIterator[K]] =
      val str = Expr(opt.getFilePath)
      '{
        new CsvIterator[K]($str)
      }
    end apply
  end IteratorToExpr2

  given IteratorFromExpr[K <: Tuple](using Type[K]): FromExpr[CsvIterator[K]] with
    def unapply(x: Expr[CsvIterator[K]])(using Quotes): Option[CsvIterator[K]] =
      import quotes.reflect.*
      x.asTerm.underlying.asExprOf[CsvIterator[K]] match
        case '{ new CsvIterator[K](${ Expr(filePath) }) } => Some(new CsvIterator[K](filePath))
        case _                                            => None
      end match
    end unapply
  end IteratorFromExpr

  private transparent inline def readHeaderlineAsCsv(path: String)(using q: Quotes) =
    import q.reflect.*

    val itr = new CsvIterator(path.toString)
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

  private def readCsvResoourceNoOpts(pathExpr: Expr[String])(using Quotes) =
    readCsvResource(
      pathExpr,
      Expr(
        CsvReadOptions2(
          delimiter = ','
        )
      )
    )
  end readCsvResoourceNoOpts

  private def readCsvResource(pathExpr: Expr[String], opts: Expr[CsvReadOptions2])(using Quotes) =
    import quotes.reflect.*
    val arg = opts.valueOrAbort
    println(arg)
    val path = pathExpr.valueOrAbort
    val resourcePath = this.getClass.getClassLoader.getResource(path)
    if resourcePath == null then report.throwError(s"Resource not found: $path")
    end if

    readHeaderlineAsCsv(resourcePath.getPath)
  end readCsvResource

  private def deduplicateHeadersCode[K <: Tuple](objExpr: Expr[CsvIterator[K]])(using Quotes, Type[K]) =
    import quotes.reflect.*

    val obj = objExpr.valueOrAbort

    val headers = obj.headers
    val uniqueHeaders = for ((h, i) <- headers.zipWithIndex) yield if headers.indexOf(h) != i then s"${h}_${i}" else h

    Expr.ofTupleFromSeq(uniqueHeaders.map(Expr(_))) match
      case '{ $tup: t } =>
        val itr = new CsvIterator[t & Tuple](obj.getFilePath)
        Expr(itr)
      case _ => report.throwError(s"Could not infer a literal type for ${uniqueHeaders}")
    end match
  end deduplicateHeadersCode

end CSV
