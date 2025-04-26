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
import io.github.quafadas.scautable.Bah.DeduplicateTuple

object CSV:

  transparent inline def url[T](inline path: String, inline dedupHeaders: Boolean = false) = ${ readCsvFromUrl('path, 'dedupHeaders) }

  transparent inline def pwd[T](inline path: String, inline dedupHeaders: Boolean = false) = ${ readCsvFromCurrentDir('path, 'dedupHeaders) }

  transparent inline def resource[T](inline path: String, inline dedupHeaders: Boolean = false) = ${ readCsvResource('path, 'dedupHeaders) }

  transparent inline def absolutePath[T](inline path: String, inline dedupHeaders: Boolean = false) =
    ${
      readCsvAbolsutePath('path, 'dedupHeaders)
    }
  end absolutePath

  given IteratorFromExpr[K <: Tuple](using Type[K]): FromExpr[CsvIterator[K]] with
    def unapply(x: Expr[CsvIterator[K]])(using Quotes): Option[CsvIterator[K]] =
      import quotes.reflect.*
      x.asTerm.underlying.asExprOf[CsvIterator[K]] match
        case '{ new CsvIterator[K](${ Expr(filePath) }) } => Some(new CsvIterator[K](filePath))
        case _                                            => None
      end match
    end unapply
  end IteratorFromExpr

  given IteratorToExpr2[K <: Tuple](using ToExpr[String], Type[K]): ToExpr[CsvIterator[K]] with
    def apply(opt: CsvIterator[K])(using Quotes): Expr[CsvIterator[K]] =
      val str = Expr(opt.getFilePath)
      '{
        new CsvIterator[K]($str)
      }
    end apply
  end IteratorToExpr2
  given IteratorToExpr3[K <: Tuple, D <: DeduplicateTuple[K, EmptyTuple, 0]](using ToExpr[String], Type[D]): ToExpr[CsvIterator[D]] with
    def apply(opt: CsvIterator[D])(using Quotes): Expr[CsvIterator[D]] =
      val str = Expr(opt.getFilePath)
      '{
        new CsvIterator[D]($str)
      }
    end apply
  end IteratorToExpr3

  private transparent inline def readHeaderlineAsCsv(bs: BufferedSource, path: String, dedupHeaders: Boolean)(using q: Quotes) =
    import q.reflect.*
    try
      val headers = bs.getLines().next().split(",").toList
      val tupHeaders = Expr.ofTupleFromSeq(headers.map(Expr(_)))
      (tupHeaders, dedupHeaders) match
        case ('{ $tup: t }, false) =>
          val itr = new CsvIterator[t & Tuple](path.toString)
          Expr(itr)
        // case ('{ $tup: t }, true) =>
        //   val itr = new CsvIterator[t & Tuple](path.toString).deduplicateHeaders
        //   Expr(itr)
        case _ => report.throwError(s"Could not summon Type for type: ${tupHeaders.show}")
      end match

    finally bs.close()
    end try
  end readHeaderlineAsCsv

  private def defaultDefaultDedeupHeaderArg(dedup: Expr[Boolean])(using Quotes) =
    import quotes.reflect.*
    dedup.value.getOrElse(false)
  end defaultDefaultDedeupHeaderArg

  private def readCsvFromUrl(pathExpr: Expr[String], dedupHeadersE: Expr[Boolean])(using Quotes) =
    import quotes.reflect.*

    report.warning(
      "This method saves the CSV to a local temp file and opens it. There may be performance implications - it is recommended to use one of the other methods where possible."
    )
    val source = Source.fromURL(pathExpr.valueOrAbort)
    val tmpPath = os.temp(dir = os.pwd, prefix = "temp_csv_", suffix = ".csv")
    val dedupHeaders = defaultDefaultDedeupHeaderArg(dedupHeadersE)
    os.write.over(tmpPath, source.toArray.mkString)
    readHeaderlineAsCsv(source, tmpPath.toString, dedupHeaders)

  end readCsvFromUrl

  private def readCsvFromCurrentDir(pathExpr: Expr[String], dedupHeadersE: Expr[Boolean])(using Quotes) =
    import quotes.reflect.*
    val path = os.pwd / pathExpr.valueOrAbort
    val source = Source.fromFile(path.toString)
    val dedupHeaders = defaultDefaultDedeupHeaderArg(dedupHeadersE)
    readHeaderlineAsCsv(source, path.toString, dedupHeaders)

  end readCsvFromCurrentDir

  def readCsvAbolsutePath(pathExpr: Expr[String], dedupHeadersE: Expr[Boolean])(using Quotes) =
    import quotes.reflect.*

    val path = pathExpr.valueOrAbort
    val dedupHeaders = defaultDefaultDedeupHeaderArg(dedupHeadersE)
    val source = Source.fromFile(path)
    readHeaderlineAsCsv(source, path, dedupHeaders)

  end readCsvAbolsutePath

  private def readCsvResource(pathExpr: Expr[String], dedupHeadersE: Expr[Boolean])(using Quotes) =
    import quotes.reflect.*

    val path = pathExpr.valueOrAbort
    val dedupHeaders = defaultDefaultDedeupHeaderArg(dedupHeadersE)
    val resourcePath = this.getClass.getClassLoader.getResource(path)
    if resourcePath == null then report.throwError(s"Resource not found: $path")
    end if
    val source = Source.fromResource(path)

    readHeaderlineAsCsv(source, resourcePath.getPath, dedupHeaders)

  end readCsvResource

end CSV
