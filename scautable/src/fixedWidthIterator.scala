package io.github.quafadas.scautable
import scala.NamedTuple.*
import scala.annotation.publicInBinary
import scala.compiletime.*

/** A NamedTuple representation of a fixed-width file.
  *
  * It is a (lazy) iterator that reads a fixed-width file line by line and converts each line into a NamedTuple.
  *
  * Attempting to use the iterator a second time will throw a `StreamClosedException`. Common usage
  *
  * ```scala sc:nocompile
  * def fwIterator = FixedWidth.resource("data.txt", Seq(10, 5, 20))
  * val fwData = fwIterator.toSeq
  * ```
  *
  * Note that at this point, you are plugged right into the scala collections API.
  *
  * ```scala sc:nocompile
  * fwData.filter(_.column("colA") == "foo").drop(10).take(5).map(_.column("colB"))
  * ```
  * etc
  */

class FixedWidthIterator[K <: Tuple, V <: Tuple] @publicInBinary private[scautable] (
    private val rows: Iterator[String],
    val headers: Seq[String],
    columnWidths: Seq[Int],
    paddingChar: Char = ' ',
    trimFields: Boolean = true
)(using decoder: RowDecoder[V]) extends Iterator[NamedTuple[K, V]]:

  type COLUMNS = K

  type Col[N <: Int] = Tuple.Elem[K, N]

  inline override def hasNext: Boolean = rows.hasNext

  inline override def next() =
    val str = rows.next()
    val splitted = FixedWidthParser.parseLineWithWidths(str, columnWidths, trimFields)
    val tuple = decoder
      .decodeRow(splitted)
      .getOrElse(
        throw new Exception("Failed to decode row: " + splitted)
      )
    NamedTuple.build[K & Tuple]()(tuple)
  end next

  def schemaGen: String =
    val headerTypes = headers.map(header => s"type ${header} = \"$header\"").mkString("\n  ")
    s"""object FixedWidthSchema:
  $headerTypes

import FixedWidthSchema.*
"""
  end schemaGen

  inline def headerIndex(s: String) =
    headers.zipWithIndex.find(_._1 == s).get._2

  inline def headerIndex[S <: String & Singleton] =
    headers.indexOf(constValue[S].toString)
  end headerIndex

end FixedWidthIterator
