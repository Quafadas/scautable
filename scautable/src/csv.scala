package io.github.quafadas.scautable

import scala.quoted.*
import scala.io.Source
import scala.annotation.experimental
import NamedTuple.withNames
import scala.NamedTuple.NamedTuple
import scala.collection.immutable.Stream.Empty

@experimental
object CSV:

  extension (s: Seq[Product]) def pt = scautable.consoleFormat(s)

  given IteratorToExpr[K, V](using ToExpr[String], Type[K], Type[V]): ToExpr[FileLineIterator[K, V & Tuple]] with
    def apply(opt: FileLineIterator[K, V & Tuple])(using Quotes): Expr[FileLineIterator[K, V & Tuple]] =
      val str = Expr(opt.getFilePath)
      '{
        new FileLineIterator[K, V & Tuple]($str)
      }
  given IteratorToExpr2[K](using ToExpr[String], Type[K]): ToExpr[FileLineIterator2[K]] with
    def apply(opt: FileLineIterator2[K])(using Quotes): Expr[FileLineIterator2[K]] =
      val str = Expr(opt.getFilePath)
      '{
        new FileLineIterator2[K]($str)
      }

    // given IteratorStrings[Iterator[String]](using ToExpr[String]): ToExpr[Iterator[String]] with
    //   def apply(opt: scala.collection.Iterator[String])(using Quotes): Expr[Iterator[String]] =
    //     Expr(opt.toList)

  class FileLineIterator[K, V <: Tuple](filePath: String) extends Iterator[NamedTuple[K & Tuple, V]] {
    def getFilePath: String  = filePath
    private val source       = Source.fromFile(filePath)
    private val lineIterator = source.getLines()
    private var lineNumber   = 0
    private var isFirstLine  = true

    override def hasNext: Boolean = {
      val hasMore = lineIterator.hasNext
      if (!hasMore) source.close() // Close the file when done
      hasMore
    }

    def listToTuple(list: List[String]): Tuple = list match {
      case Nil    => EmptyTuple
      case h :: t => h *: listToTuple(t)
    }

    override def next(): NamedTuple[K & Tuple, V] = {
      if (!hasNext) throw new NoSuchElementException("No more lines")
      val str = lineIterator.next()
      isFirstLine = false
      val splitted = str.split(",").toList
      val tuple    = listToTuple(splitted).asInstanceOf[V]
      NamedTuple.build[K & Tuple]()(tuple)
      // (lineNumber, lineIterator.next()) // Return line number and line content
    }
  }

  class FileLineIterator2[K](filePath: String) extends Iterator[NamedTuple[K & Tuple, K & Tuple]] {
    def getFilePath: String  = filePath
    private val source       = Source.fromFile(filePath)
    private val lineIterator = source.getLines()
    private var lineNumber   = 0
    private var isFirstLine  = true

    override def hasNext: Boolean = {
      val hasMore = lineIterator.hasNext
      if (!hasMore) source.close() // Close the file when done
      hasMore
    }

    def listToTuple(list: List[String]): Tuple = list match {
      case Nil    => EmptyTuple
      case h :: t => h *: listToTuple(t)
    }

    override def next(): NamedTuple[K & Tuple, K & Tuple] = {
      if (!hasNext) throw new NoSuchElementException("No more lines")
      val str = lineIterator.next()
      isFirstLine = false
      val splitted = str.split(",").toList
      val tuple    = listToTuple(splitted).asInstanceOf[K & Tuple]
      NamedTuple.build[K & Tuple]()(tuple)
      // (lineNumber, lineIterator.next()) // Return line number and line content
    }
  }

  transparent inline def readCsvAsNamedTupleType[T](inline path: String) = ${ readCsvAsNamedTupleTypeImpl2('path) }

  def readCsvAsNamedTupleTypeImpl2(pathExpr: Expr[String])(using Quotes) = {
    import quotes.reflect._

    import quotes.reflect.*

    val path = pathExpr.valueOrAbort

    val source = Source.fromFile(path)
    val headerLine =
      try source.getLines().next()
      finally source.close()

    val headers    = headerLine.split(",").toList
    val tupleExpr2 = Expr.ofTupleFromSeq(headers.map(Expr(_)))
    tupleExpr2 match
      case '{ $tup: t } =>
        val itr: FileLineIterator[t, Tuple] = new FileLineIterator[t, Tuple](path)
        '{ NamedTuple.build[t & Tuple]()($tup) }
      // '{ itr }
      // Expr(itr)
      case _ => report.throwError(s"Could not summon Type for type: ${tupleExpr2.show}")

    tupleExpr2 match
      case '{ $tup: t } =>
        val itr = new FileLineIterator2[t](path)
        // '{ NamedTuple.build[t & Tuple]()($tup) }
        Expr(itr)
      // Expr(itr)
      case _ => report.throwError(s"Could not summon Type for type: ${tupleExpr2.show}")

  }
