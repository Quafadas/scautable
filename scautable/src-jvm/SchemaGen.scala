package io.github.quafadas.scautable

import scala.io.Source

/**
  * For now, assume the simple case.
  *
  * @param absolutePath
  * @param delimiter  
  */
@main def GenSchema(absolutePath: String, delimiter: String = ","): Unit =
  val source = Source.fromFile(absolutePath)
  val headerLine = source.getLines().next()
  val headers = CSVParser.parseLine(headerLine, delimiter.head)    
  val headerTypes = headers.map(header => s"type ${header} = \"$header\"").mkString("\n  ")
    s"""object CsvSchema:
  $headerTypes
"""
  os.write.over(os.pwd / "CsvSchema.scala", headerTypes)