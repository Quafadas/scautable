package io.github.quafadas.scautable

import scala.quoted.*
import scala.io.Source
import scala.annotation.experimental
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

@experimental
object CSV:

  transparent inline def url[T](inline path: String) = ${ readCsvFromUrl('path) }

  transparent inline def pwd[T](inline path: String) = ${ readCsvFromCurrentDir('path) }

  transparent inline def resource[T](inline path: String) = ${ readCsvResource('path) }

  transparent inline def absolutePath[T](path: String) = ${ readCsvAbolsutePath('path) }

  given IteratorToExpr2[K <: Tuple](using ToExpr[String], Type[K]): ToExpr[CsvIterator[K]] with
    def apply(opt: CsvIterator[K])(using Quotes): Expr[CsvIterator[K]] =
      val str = Expr(opt.getFilePath)
      '{
        new CsvIterator[K]($str)
      }
    end apply
  end IteratorToExpr2

  private transparent inline def readHeaderlineAsCsv(bs: BufferedSource, path: String)(using q: Quotes) =
    import q.reflect.*
    try
      val headers = bs.getLines().next().split(",").toList
      val tupHeaders = Expr.ofTupleFromSeq(headers.map(Expr(_)))
      tupHeaders match
        case '{ $tup: t } =>
          val itr = new CsvIterator[t & Tuple](path.toString)
          Expr(itr)
        case _ => report.throwError(s"Could not summon Type for type: ${tupHeaders.show}")
      end match

    finally bs.close()
    end try
  end readHeaderlineAsCsv

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
