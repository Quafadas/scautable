package io.github.quafadas.scautable

import scala.io.Source
import scala.util.Try
import scala.util.chaining.*
import scala.util.matching.Regex
import scala.NamedTuple.*
import scala.compiletime.*
import CSV.*
import ConsoleFormat.*
import ColumnTyped.*

class CsvIterator[K](filePath: String) extends Iterator[NamedTuple[K & Tuple, StringyTuple[K & Tuple]]]:
  type COLUMNS = K

  def getFilePath: String = filePath
  lazy private val source = Source.fromFile(filePath)
  lazy private val lineIterator = source.getLines()
  lazy val headers = CSVParser.parseLine((Source.fromFile(filePath).getLines().next()))
  lazy val headersTuple =
    listToTuple(headers)

  inline def headerIndex(s: String) =
    headers.zipWithIndex.find(_._1 == s).get._2

  /** Here be dragons, in Tuple Land, Tuple XXL is reversed, creating a discontinuity. Small tuples start at 1, big tuples start the other end.
    *
    * Apparently fixed in 3.6.3
    *
    * @return
    */
  inline def headerIndex[S <: String & Singleton] =
    val headers2 = if headers.size > 22 then headers.reverse else headers
    headers.indexOf(constValue[S].toString)
  end headerIndex

  inline override def hasNext: Boolean =
    val hasMore = lineIterator.hasNext
    if !hasMore then source.close()
    end if
    hasMore
  end hasNext

  // def numericTypeTest(sample: Option[Int] = None) =
  //   val sampled = sample match
  //     case Some(n) =>
  //       this.take(n)
  //     case None =>
  //       this
  //   val asList = headers.map(_ => ConversionAcc(0, 0, 0))

  //   sampled.foldLeft((asList, 0L))((acc: (List[ConversionAcc], Long), elem: NamedTuple[K & Tuple, StringyTuple[K & Tuple]]) =>

  //     val list = elem.toList.asInstanceOf[List[String]].zip(acc._1).map { case (str, acc) =>

  //       (
  //         ConversionAcc(
  //           acc.validInts + str.toIntOption.fold(0)(_ => 1),
  //           acc.validDoubles + str.toDoubleOption.fold(0)(_ => 1),
  //           acc.validLongs + str.toLongOption.fold(0)(_ => 1)
  //         )
  //       )
  //     }
  //     (list, acc._2 + 1)
  //   )
  // end numericTypeTest

  inline override def next() =
    if !hasNext then throw new NoSuchElementException("No more lines")
    end if
    val str = lineIterator.next()
    val splitted = CSVParser.parseLine(str)
    val tuple = listToTuple(splitted).asInstanceOf[StringyTuple[K & Tuple]]
    NamedTuple.build[K & Tuple]()(tuple)
  end next

  next() // drop the headers
end CsvIterator
