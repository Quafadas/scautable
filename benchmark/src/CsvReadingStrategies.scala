package scautable.benchmark

import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag

/** Utilities for CSV reading with different strategies */
object CsvReadingStrategies:

  /** Simple CSV parser for benchmarking purposes */
  private def parseLine(line: String, delimiter: Char = ','): List[String] =
    var inQuotes = false
    val cellBuffer = new StringBuilder
    val result = scala.collection.mutable.ListBuffer.empty[String]
    var i = 0

    while i < line.length do
      val char = line.charAt(i)

      char match
        case '"' if !inQuotes =>
          inQuotes = true
        case '"' if inQuotes =>
          if i + 1 < line.length && line.charAt(i + 1) == '"' then
            cellBuffer.append('"')
            i += 1
          else
            inQuotes = false
        case c if c == delimiter && !inQuotes =>
          result.append(cellBuffer.toString)
          cellBuffer.clear()
        case _ =>
          cellBuffer.append(char)
      end match

      i += 1
    end while

    result.append(cellBuffer.toString)
    result.toList
  end parseLine

  /** Current ArrayBuffer-based approach (as in csv.scala lines 683-703) */
  def readWithArrayBuffer(
    content: String,
    delimiter: Char = ','
  ): (Seq[String], Array[ArrayBuffer[String]]) =
    val lines = content.linesIterator
    
    // Get headers
    val headerLine = if lines.hasNext then lines.next() else ""
    val headers = parseLine(headerLine, delimiter)
    
    val numCols = headers.length
    val buffers = Array.fill(numCols)(ArrayBuffer[String]())
    
    // Read all rows into ArrayBuffers
    lines.foreach { line =>
      val parsed = parseLine(line, delimiter)
      var i = 0
      while i < parsed.length && i < numCols do
        buffers(i) += parsed(i)
        i += 1
      end while
    }
    
    (headers, buffers)
  end readWithArrayBuffer

  /** Two-pass approach with pre-allocated arrays */
  def readWithTwoPass(
    content: String,
    delimiter: Char = ','
  ): (Seq[String], Array[Array[String]]) =
    val lines = content.linesIterator
    
    // Get headers
    val headerLine = if lines.hasNext then lines.next() else ""
    val headers = parseLine(headerLine, delimiter)
    
    val numCols = headers.length
    
    // First pass: Count rows
    val remainingLines = lines.toList
    val numRows = remainingLines.length
    
    // Pre-allocate arrays of exact size
    val columns = Array.fill(numCols)(new Array[String](numRows))
    
    // Second pass: Fill arrays
    var rowIdx = 0
    remainingLines.foreach { line =>
      val parsed = parseLine(line, delimiter)
      var colIdx = 0
      while colIdx < parsed.length && colIdx < numCols do
        columns(colIdx)(rowIdx) = parsed(colIdx)
        colIdx += 1
      end while
      rowIdx += 1
    }
    
    (headers, columns)
  end readWithTwoPass

  /** Two-pass approach reading from file twice (Java-based line counting) */
  def readWithTwoPassFromFile(
    filepath: String,
    delimiter: Char = ','
  ): (Seq[String], Array[Array[String]]) =
    import scala.io.Source
    
    // First pass: Read headers and count rows
    val (headers, numRows) = 
      val source1 = Source.fromFile(filepath)
      try
        val lines1 = source1.getLines()
        val headerLine = if lines1.hasNext then lines1.next() else ""
        val hdrs = parseLine(headerLine, delimiter)
        
        var rowCount = 0
        lines1.foreach { _ => rowCount += 1 }
        
        (hdrs, rowCount)
      finally
        source1.close()
      end try
    
    val numCols = headers.length
    
    // Pre-allocate arrays of exact size
    val columns = Array.fill(numCols)(new Array[String](numRows))
    
    // Second pass: Fill arrays
    val source2 = Source.fromFile(filepath)
    try
      val lines2 = source2.getLines()
      if lines2.hasNext then lines2.next() // Skip header
      
      var rowIdx = 0
      lines2.foreach { line =>
        val parsed = parseLine(line, delimiter)
        var colIdx = 0
        while colIdx < parsed.length && colIdx < numCols do
          columns(colIdx)(rowIdx) = parsed(colIdx)
          colIdx += 1
        end while
        rowIdx += 1
      }
      
      (headers, columns)
    finally
      source2.close()
    end try
  end readWithTwoPassFromFile

  /** Count lines using OS-level wc command (faster for large files) */
  private def countLinesOsLevel(path: String): Int =
    import os.*
    val result = os.proc("wc", "-l", path).call()
    result.out.text().trim.split("\\s+").head.toInt
  end countLinesOsLevel

  /** Two-pass approach using OS-level line counting (potentially faster) */
  def readWithTwoPassFromFileOsCount(
    filepath: String,
    delimiter: Char = ','
  ): (Seq[String], Array[Array[String]]) =
    import scala.io.Source
    
    // First pass: Count total lines using OS command (includes header)
    val totalLines = countLinesOsLevel(filepath)
    val numRows = totalLines - 1 // Subtract header line
    
    // Read header
    val headers = 
      val source = Source.fromFile(filepath)
      try
        val headerLine = source.getLines().next()
        parseLine(headerLine, delimiter)
      finally
        source.close()
      end try
    
    val numCols = headers.length
    
    // Pre-allocate arrays of exact size
    val columns = Array.fill(numCols)(new Array[String](numRows))
    
    // Second pass: Fill arrays
    val source2 = Source.fromFile(filepath)
    try
      val lines2 = source2.getLines()
      if lines2.hasNext then lines2.next() // Skip header
      
      var rowIdx = 0
      lines2.foreach { line =>
        val parsed = parseLine(line, delimiter)
        var colIdx = 0
        while colIdx < parsed.length && colIdx < numCols do
          columns(colIdx)(rowIdx) = parsed(colIdx)
          colIdx += 1
        end while
        rowIdx += 1
      }
      
      (headers, columns)
    finally
      source2.close()
    end try
  end readWithTwoPassFromFileOsCount

  /** Convert column buffers to typed arrays (simulating the decoding step) */
  def decodeColumns(buffers: Array[ArrayBuffer[String]]): Array[AnyRef] =
    buffers.map { buffer =>
      // Try to decode as Int, then Double, otherwise keep as String
      try
        buffer.map(_.toInt).toArray.asInstanceOf[AnyRef]
      catch
        case _: NumberFormatException =>
          try
            buffer.map(_.toDouble).toArray.asInstanceOf[AnyRef]
          catch
            case _: NumberFormatException =>
              buffer.toArray.asInstanceOf[AnyRef]
    }
  end decodeColumns

  /** Convert pre-allocated string arrays to typed arrays */
  def decodeColumnsFromArrays(columns: Array[Array[String]]): Array[AnyRef] =
    columns.map { column =>
      // Try to decode as Int, then Double, otherwise keep as String
      try
        column.map(_.toInt).asInstanceOf[AnyRef]
      catch
        case _: NumberFormatException =>
          try
            column.map(_.toDouble).asInstanceOf[AnyRef]
          catch
            case _: NumberFormatException =>
              column.asInstanceOf[AnyRef]
    }
  end decodeColumnsFromArrays

end CsvReadingStrategies
