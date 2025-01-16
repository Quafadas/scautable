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

  inline def headerIndex[S <: String & Singleton] =    
    headers.indexOf(constValue[S].toString)
  end headerIndex

  inline override def hasNext: Boolean =
    val hasMore = lineIterator.hasNext
    if !hasMore then source.close()
    end if
    hasMore
  end hasNext

  inline override def next() =
    if !hasNext then throw new NoSuchElementException("No more lines")
    end if
    val str = lineIterator.next()
    val splitted = CSVParser.parseLine(str)
    val tuple = listToTuple(splitted).asInstanceOf[StringyTuple[K & Tuple]]
    NamedTuple.build[K & Tuple]()(tuple)
  end next

  next()
end CsvIterator
