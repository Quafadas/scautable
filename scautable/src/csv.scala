package io.github.quafadas.scautable

import scala.quoted.*
import scala.io.Source
import scala.annotation.experimental
import NamedTuple.withNames
import scala.NamedTuple.NamedTuple
import scala.collection.immutable.Stream.Empty
import scala.deriving.Mirror

@experimental
object CSV:

  extension (s: Seq[Product]) def pt = scautable.consoleFormat(s)

  // given IteratorToExpr[K, V](using ToExpr[String], Type[K], Type[V]): ToExpr[CsvLineIterator[K, V & Tuple]] with
  //   def apply(opt: CsvLineIterator[K, V & Tuple])(using Quotes): Expr[CsvLineIterator[K, V & Tuple]] =
  //     val str = Expr(opt.getFilePath)
  //     '{
  //       new CsvLineIterator[K, V & Tuple]($str)
  //     }
  given IteratorToExpr2[K](using ToExpr[String], Type[K]): ToExpr[CsvIterator[K]] with
    def apply(opt: CsvIterator[K])(using Quotes): Expr[CsvIterator[K]] =
      val str = Expr(opt.getFilePath)
      '{
        new CsvIterator[K]($str)
      }
    end apply

    // given IteratorStrings[Iterator[String]](using ToExpr[String]): ToExpr[Iterator[String]] with
    //   def apply(opt: scala.collection.Iterator[String])(using Quotes): Expr[Iterator[String]] =
    //     Expr(opt.toList)
  end IteratorToExpr2

  // class CsvLineIterator[K, V <: Tuple](filePath: String) extends Iterator[NamedTuple[K & Tuple, V]] {

  //   type CSV = K

  //   def getFilePath: String  = filePath
  //   private val source       = Source.fromFile(filePath)
  //   private val lineIterator = source.getLines()
  //   private var lineNumber   = 0
  //   private var isFirstLine  = true

  //   override def hasNext: Boolean = {
  //     val hasMore = lineIterator.hasNext
  //     if (!hasMore) source.close() // Close the file when done
  //     hasMore
  //   }

  //   def listToTuple(list: List[String]): Tuple = list match {
  //     case Nil    => EmptyTuple
  //     case h :: t => h *: listToTuple(t)
  //   }

  //   override def next(): NamedTuple[K & Tuple, V] = {
  //     if (!hasNext) throw new NoSuchElementException("No more lines")
  //     val str = lineIterator.next()
  //     isFirstLine = false
  //     val splitted = str.split(",").toList
  //     val tuple    = listToTuple(splitted).asInstanceOf[V]
  //     NamedTuple.build[K & Tuple]()(tuple)
  //     // (lineNumber, lineIterator.next()) // Return line number and line content
  //   }
  // }

  extension [K <: Tuple](csvItr: CsvIterator[K])
    def column[A](fct: (tup: NamedTuple.NamedTuple[K, K]) => A) =
      val itr: Iterator[NamedTuple[K & Tuple, K & Tuple]] =
        csvItr.copy()
      itr.drop(1).map(fct)

  end extension

  case class CsvIterator[K](filePath: String) extends Iterator[NamedTuple[K & Tuple, K & Tuple]]:

    type COLUMNS = K

    def getFilePath: String = filePath
    private val source = Source.fromFile(filePath)
    private val lineIterator = source.getLines()
    private var lineNumber = 0
    private var isFirstLine = true
    val headers = Source.fromFile(filePath).getLines().next().split(",").toList // done on instatiation

    def headerIndex(s: String) = headers.zipWithIndex.find(_._1 == s).get._2

    // def column[S <: Const] =
    //   val s = scala.compiletime.constValue[S]

    override def hasNext: Boolean =
      val hasMore = lineIterator.hasNext
      if !hasMore then source.close() // Close the file when done
      hasMore
    end hasNext

    def listToTuple(list: List[String]): Tuple = list match
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
      // (lineNumber, lineIterator.next()) // Return line number and line content
    end next
  end CsvIterator

  // type cols = Type.of[CSV.type]

  transparent inline def readCsvAsNamedTupleType[T](inline path: String) = ${ readCsvAsNamedTupleTypeImpl2('path) }

  def readCsvAsNamedTupleTypeImpl2(pathExpr: Expr[String])(using Quotes) =
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
  end readCsvAsNamedTupleTypeImpl2
end CSV
