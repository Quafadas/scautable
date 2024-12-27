package io.github.quafadas.scautable

import scala.quoted.*
import scala.io.Source
import scala.annotation.experimental
import NamedTuple.withNames
import scala.NamedTuple.NamedTuple
import scala.collection.immutable.Stream.Empty

@experimental
object CSV:

  class FileLineIterator[N <: Tuple, V <: Tuple](filePath: String) extends Iterator[NamedTuple[N, V]] {
    def getFilePath: String  = filePath
    private val source       = Source.fromFile(filePath)
    private val lineIterator = source.getLines()
    private var lineNumber   = 0

    override def hasNext: Boolean = {
      val hasMore = lineIterator.hasNext
      if (!hasMore) source.close() // Close the file when done
      hasMore
    }

    override def next(): NamedTuple[N, V] = {
      if (!hasNext) throw new NoSuchElementException("No more lines")
      ???
      // (lineNumber, lineIterator.next()) // Return line number and line content
    }
  }

  transparent inline def readCsvAsNamedTupleType[T](inline path: String) = ${ readCsvAsNamedTupleTypeImpl2('path) }

  def readCsvAsNamedTupleTypeImpl2(pathExpr: Expr[String])(using Quotes) = {
    import quotes.reflect._

    def listToTuple(list: List[String]): Tuple = list match {
      case Nil    => EmptyTuple
      case h :: t => h *: listToTuple(t)
    }

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
        given IteratorToExpr[K, V: Type: ToExpr]: ToExpr[FileLineIterator[K, V]] with
          def apply(opt: FileLineIterator[K, V])(using Quotes): Expr[FileLineIterator[K, V]] =
            val str = opt.getFilePath
            '{ new FileLineIterator[K, V](${ Expr(str) }) }

        val itr: FileLineIterator[t & Tuple, Tuple] = new FileLineIterator[t & Tuple, Tuple](path)

        Expr(itr)
      case _ => report.throwError(s"Could not summon Type for type: ${tupleExpr2.show}")
  }
