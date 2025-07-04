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
import scala.annotation.publicInBinary

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
class CsvIterator[K <: Tuple] @publicInBinary private[scautable] (private val rows: Iterator[String], val headers: Seq[String]) extends Iterator[NamedTuple[K, StringyTuple[K & Tuple]]]:
  type COLUMNS = K

  type Col[N <: Int] = Tuple.Elem[K, N]
  
  inline override def hasNext: Boolean = rows.hasNext

  inline override def next() =
    val str = rows.next()
    val splitted = CSVParser.parseLine(str)
    val tuple = listToTuple(splitted).asInstanceOf[StringyTuple[K & Tuple]]
    NamedTuple.build[K & Tuple]()(tuple)
  end next

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

end CsvIterator
