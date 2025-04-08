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

class CsvIterator[K <: Tuple](filePath: String) extends Iterator[NamedTuple[K, StringyTuple[K & Tuple]]]:
  type COLUMNS = K

  def getFilePath: String = filePath
  
  // Initialize these as vals to avoid reopening the file multiple times
  private val fileContents = {
    val src = Source.fromFile(filePath)
    try {
      src.getLines().toVector
    } finally {
      src.close()
    }
  }
  
  // Iterator over the loaded content
  private val lineIterator = fileContents.iterator.drop(1) // Drop header line
  
  // Original raw headers from CSV file
  lazy val originalHeaders: List[String] = CSVParser.parseLine(fileContents.head)
  
  // Headers used for the type-level representation (may be normalized if duplicates exist)
  lazy val headers: List[String] = {
    val rawHeaders = originalHeaders
    if (rawHeaders.distinct.length != rawHeaders.length) {
      // If duplicates exist, use normalized headers
      HeaderUtils.normalizeHeaders(rawHeaders).toList
    } else {
      // Otherwise use originals
      rawHeaders
    }
  }
  
  lazy val headersTuple = listToTuple(headers)
  
  // Check if headers were normalized due to duplicates
  lazy val hasNormalizedHeaders: Boolean = originalHeaders.distinct.length != originalHeaders.length
  
  // Generate a report about header normalization if duplicates were found
  def headerNormalizationReport: Option[String] = 
    if (hasNormalizedHeaders) {
      Some(HeaderUtils.createHeaderMappingTable(originalHeaders, headers))
    } else {
      None // No report needed if no normalization happened
    }
  
  // Get the normalized version of a header (useful for accessing columns by original name)
  def getNormalizedHeader(originalHeader: String): String = {
    if (!hasNormalizedHeaders) return originalHeader
    
    val indices = originalHeaders.zipWithIndex.filter(_._1 == originalHeader).map(_._2)
    if (indices.length == 1) {
      // If there's only one instance of this header, return the corresponding normalized header
      headers(indices.head)
    } else {
      // If there are multiple instances, find the first one
      val idx = originalHeaders.indexOf(originalHeader)
      headers(idx)
    }
  }

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

  inline override def hasNext: Boolean = lineIterator.hasNext

  inline override def next() =
    if !hasNext then throw new NoSuchElementException("No more lines")
    end if
    val str = lineIterator.next()
    val splitted = CSVParser.parseLine(str)
    val tuple = listToTuple(splitted).asInstanceOf[StringyTuple[K & Tuple]]
    NamedTuple.build[K & Tuple]()(tuple)
  end next
end CsvIterator
