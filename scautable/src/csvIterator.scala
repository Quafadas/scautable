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
import NamedTuple.*

/** A NamedTuple representation of a CSV file.
  *
  * It is a (lazy) iterator that reads a CSV file line by line and converts each line into a NamedTuple.
  *
  * Attempting to use the iterator a second time will throw a `StreamClosedException`. Common usage
  *
  * ```scala sc:nocompile
  * def csvIterator = CSV.resource("simple.csv")
  * val csvData = csvIterator.toSeq
  * ```
  *
  * Note that at this point, you are plugged right into the scala collections API.
  *
  * ```scala sc:nocompile
  * csvData.filter(_.column("colA") == "foo").drop(10).take(5).map(_.column("colB"))
  * ```
  * etc
  */
class CsvIterator[K <: Tuple](filePath: String, opts: CsvReadOptions) extends Iterator[NamedTuple[K, StringyTuple[K & Tuple]]]:
  type COLUMNS = K

  type Col[N <: Int] = Tuple.Elem[K, N]

  def getFilePath: String = filePath
  def getOpts: CsvReadOptions = opts
  lazy private val source = Source.fromFile(filePath)
  lazy private val lineIterator = source.getLines()
  lazy val headers = CSVParser.parseLine((Source.fromFile(filePath).getLines().next()))
  lazy val headersTuple =
    listToTuple(headers)

  def schemaGen: String =
    val headerTypes = headers.map(header => s"type ${header} = \"$header\"").mkString("\n  ")
    s"""object CsvSchema:
  $headerTypes

import CsvSchema.*
"""
  end schemaGen

  inline def headerIndex(s: String) =
    headers.zipWithIndex.find(_._1 == s).get._2

  inline def headerIndex[S <: String & Singleton] =
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

  inline def restart = new CsvIterator[K](filePath, opts)
  inline def copy = restart

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
