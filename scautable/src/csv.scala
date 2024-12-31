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

@experimental
object CSV:

  given IteratorToExpr2[K](using ToExpr[String], Type[K]): ToExpr[CsvIterator[K]] with
    def apply(opt: CsvIterator[K])(using Quotes): Expr[CsvIterator[K]] =
      val str = Expr(opt.getFilePath)
      '{
        new CsvIterator[K]($str)
      }
    end apply
  end IteratorToExpr2

  extension [K <: Tuple](csvItr: CsvIterator[K])
    def mapRows[A](fct: (tup: NamedTuple.NamedTuple[K, K]) => A) =
      val itr: Iterator[NamedTuple[K & Tuple, K & Tuple]] =
        csvItr.copy()
      itr.drop(1).map(fct)

  end extension

  extension [K <: Tuple, V <: Tuple](nt: Seq[NamedTuple[K, V]])
    inline def consolePrint(headers: List[String], fansi: Boolean = true) =
      // val headers = nt.names
      val values = nt.map(_.toTuple)
      scautable.consoleFormat(values, fansi, headers)

    end consolePrint
  end extension

  case class CsvIterator[K](filePath: String) extends Iterator[NamedTuple[K & Tuple, K & Tuple]]:

    type COLUMNS = K

    type IsColumn[StrConst <: String, T] = T match
      case EmptyTuple => false
      case (head *: tail) => IsMatch[StrConst, head] match
        case true => true
        case false => IsColumn[StrConst, tail]
      case _ => false

    type IsMatch[A <: String, B <: String] = B match
      case A => true
      case _ => false

    def column[S <: String, A](fct: String => A = identity)(using ev: IsColumn[S, K] =:= true, s: ValueOf[S]): Iterator[A] = {
      column[S](using ev, s).map(fct)
    }

    def column[S <: String](using ev: IsColumn[S, K] =:= true, s: ValueOf[S]): Iterator[String]= {
      val idx = headerIndex(s.value)
      val itr: Iterator[NamedTuple[K & Tuple, K & Tuple]] =
        this.copy()
      itr.drop(1).map(x => x.toTuple.productElement(idx).asInstanceOf[String])
    }

    def getFilePath: String = filePath
    private val source = Source.fromFile(filePath)
    private val lineIterator = source.getLines()
    private var lineNumber = 0
    private var isFirstLine = true
    val headers = Source.fromFile(filePath).getLines().next().split(",").toList // done on instatiation
    val headersTuple =
      listToTuple(headers)

    def headerIndex(s: String) = headers.zipWithIndex.find(_._1 == s).get._2
    override def hasNext: Boolean =
      val hasMore = lineIterator.hasNext
      if !hasMore then source.close() // Close the file when done
      hasMore
    end hasNext

    private def listToTuple(list: List[String]): Tuple = list match
      case Nil    => EmptyTuple
      case h :: t => h *: listToTuple(t)

    override def next(): NamedTuple[K & Tuple, K & Tuple] =
      if !hasNext then throw new NoSuchElementException("No more lines")
      end if
      val str = lineIterator.next()
      isFirstLine = false
      val splitted = str.split(",").toList
      val tuple = listToTuple(splitted).asInstanceOf[K & Tuple]
      NamedTuple.build[K & Tuple]()(tuple)
    end next
  end CsvIterator

  transparent inline def url[T](inline path: String) = ${ readCsvFromUrl('path) }

  transparent inline def pwd[T](inline path: String) = ${ readCsvFromCurrentDir('path) }

  transparent inline def absolutePath[T](inline path: String) = ${ readCsvAbolsutePath('path) }

  // TODO : I can't figure out how to refactor the common code inside the contraints of metaprogamming... 4 laterz.

  def readCsvFromUrl(pathExpr: Expr[String])(using Quotes) =
    import quotes.reflect.*

    report.warning("This method saves the CSV to a local file and opens it. This is a security risk, a performance risk and a lots of things risk. Use at your own risk and no where near something you care about.")
    val source = Source.fromURL(pathExpr.valueOrAbort)
    val tmpPath = os.temp(dir = os.pwd, prefix = "temp_csv_", suffix = ".csv")
    os.write.over(tmpPath, source.toArray.mkString)

    println(tmpPath.toString())

    val headerLine =
      try Source.fromFile(tmpPath.toString()).getLines().next()
      finally source.close()

    val headers = headerLine.split(",").toList
    val tupleExpr2 = Expr.ofTupleFromSeq(headers.map(Expr(_)))

    tupleExpr2 match
      case '{ $tup: t } =>
        val itr = new CsvIterator[t](tmpPath.toString())
        // '{ NamedTuple.build[t & Tuple]()($tup) }
        Expr(itr)
      case _ => report.throwError(s"Could not summon Type for type: ${tupleExpr2.show}")
    end match

  end readCsvFromUrl

  def readCsvFromCurrentDir(pathExpr: Expr[String])(using Quotes) =
    import quotes.reflect.*

    val path = os.pwd / pathExpr.valueOrAbort

    val source = Source.fromFile(path.toString)
    val headerLine =
      try source.getLines().next()
      finally source.close()

    val headers = headerLine.split(",").toList
    val tupleExpr2 = Expr.ofTupleFromSeq(headers.map(Expr(_)))

    tupleExpr2 match
      case '{ $tup: t } =>
        val itr = new CsvIterator[t](path.toString)
        // '{ NamedTuple.build[t & Tuple]()($tup) }
        Expr(itr)
      case _ => report.throwError(s"Could not summon Type for type: ${tupleExpr2.show}")
    end match

  end readCsvFromCurrentDir

  def readCsvAbolsutePath(pathExpr: Expr[String])(using Quotes) =
    import quotes.reflect.*

    val path = pathExpr.valueOrAbort

    val source = Source.fromFile(path)
    val headerLine =
      try source.getLines().next()
      finally source.close()

    val headers = headerLine.split(",").toList
    val tupleExpr2 = Expr.ofTupleFromSeq(headers.map(Expr(_)))

    tupleExpr2 match
      case '{ $tup: t } =>
        val itr = new CsvIterator[t](path)
        // '{ NamedTuple.build[t & Tuple]()($tup) }
        Expr(itr)
      case _ => report.throwError(s"Could not summon Type for type: ${tupleExpr2.show}")
    end match
  end readCsvAbolsutePath
end CSV
