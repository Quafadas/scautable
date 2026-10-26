package io.github.quafadas.scautable

import scala.compiletime.*
import scala.reflect.ClassTag

/** Decoder for converting a column of strings to a typed array. */
private[scautable] trait ColumnDecoder[T]:
  def decodeColumn(values: scala.collection.mutable.ArrayBuffer[String]): Array[T]
end ColumnDecoder

private[scautable] object ColumnDecoder:
  import scala.collection.mutable.ArrayBuffer

  given intDecoder: ColumnDecoder[Int] with
    def decodeColumn(values: ArrayBuffer[String]): Array[Int] =
      val arr = new Array[Int](values.length)
      var i = 0
      while i < values.length do
        arr(i) = values(i).toInt
        i += 1
      end while
      arr
    end decodeColumn
  end intDecoder

  given longDecoder: ColumnDecoder[Long] with
    def decodeColumn(values: ArrayBuffer[String]): Array[Long] =
      val arr = new Array[Long](values.length)
      var i = 0
      while i < values.length do
        arr(i) = values(i).toLong
        i += 1
      end while
      arr
    end decodeColumn
  end longDecoder

  given doubleDecoder: ColumnDecoder[Double] with
    def decodeColumn(values: ArrayBuffer[String]): Array[Double] =
      val arr = new Array[Double](values.length)
      var i = 0
      while i < values.length do
        arr(i) = values(i).toDouble
        i += 1
      end while
      arr
    end decodeColumn
  end doubleDecoder

  given booleanDecoder: ColumnDecoder[Boolean] with
    def decodeColumn(values: ArrayBuffer[String]): Array[Boolean] =
      val arr = new Array[Boolean](values.length)
      var i = 0
      while i < values.length do
        val s = values(i).toLowerCase
        arr(i) = s == "true" || s == "1"
        i += 1
      end while
      arr
    end decodeColumn
  end booleanDecoder

  given stringDecoder: ColumnDecoder[String] with
    def decodeColumn(values: ArrayBuffer[String]): Array[String] =
      values.toArray
  end stringDecoder

  given optionDecoder[T](using d: ColumnDecoder[T], ct: ClassTag[Option[T]]): ColumnDecoder[Option[T]] with
    def decodeColumn(values: ArrayBuffer[String]): Array[Option[T]] =
      // For Option types, we need to handle empty strings specially
      val nonEmpty = ArrayBuffer[String]()
      val emptyIndices = ArrayBuffer[Int]()
      var i = 0
      while i < values.length do
        if values(i).isEmpty then
          emptyIndices += i
          nonEmpty += "0" // placeholder for decoding (will be replaced with None)
        else nonEmpty += values(i)
        end if
        i += 1
      end while

      // Decode non-empty values
      val decoded = d.decodeColumn(nonEmpty)
      val result = new Array[Option[T]](values.length)

      i = 0
      var emptyIdx = 0
      while i < values.length do
        if emptyIdx < emptyIndices.length && emptyIndices(emptyIdx) == i then
          result(i) = None
          emptyIdx += 1
        else result(i) = Some(decoded(i))
        end if
        i += 1
      end while
      result
    end decodeColumn
  end optionDecoder
end ColumnDecoder

/** Runtime helpers shared by every CSV read mode, so that rows, columns and dense arrays agree on what counts as a row. */
private[scautable] object CsvRows:
  import scala.collection.mutable.ArrayBuffer

  /** A line holding nothing but whitespace is not a row, and is skipped by every read mode.
    *
    * The delimiter never counts as whitespace, so a tab-delimited `"\t\t"` is still a row of empty cells.
    */
  def isBlank(line: String, delimiter: Char): Boolean =
    val n = line.length
    var i = 0
    while i < n && line.charAt(i) != delimiter && Character.isWhitespace(line.charAt(i)) do i += 1
    end while
    i == n
  end isBlank

  /** Parses every data line into one buffer per column, skipping blank lines and rejecting any row whose width differs from `numCols` with a [[CsvRowWidthException]].
    *
    * All buffers therefore always have the same length.
    */
  def fillColumnBuffers(lines: Iterator[String], numCols: Int, delimiter: Char): Array[ArrayBuffer[String]] =
    val bufs = Array.fill(numCols)(ArrayBuffer[String]())
    var dataLine = 0
    while lines.hasNext do
      val line = lines.next()
      dataLine += 1
      if !isBlank(line, delimiter) then
        // Walk the List once: `parsed(i)` / `parsed.length` would make each row quadratic in its width.
        var cells = CSVParser.parseLine(line, delimiter)
        var i = 0
        while i < numCols && cells.nonEmpty do
          bufs(i) += cells.head
          cells = cells.tail
          i += 1
        end while
        // Partially filled buffers are discarded along with the exception.
        if i != numCols || cells.nonEmpty then throw CsvRowWidthException(dataLine, numCols, i + cells.length)
        end if
      end if
    end while
    bufs
  end fillColumnBuffers
end CsvRows

/** Helper object for decoding columns at runtime using compile-time derived decoders */
private[scautable] object ColumnsDecoder:
  import scala.collection.mutable.ArrayBuffer

  /** Decode columns recursively. V is the tuple of Array types (e.g., (Array[Int], Array[String])). */
  inline def decodeAllColumns[V <: Tuple](buffers: Array[ArrayBuffer[String]], idx: Int = 0): V =
    inline erasedValue[V] match
      case _: EmptyTuple =>
        EmptyTuple.asInstanceOf[V]
      case _: (Array[h] *: t) =>
        val decoder = summonInline[ColumnDecoder[h]]
        val head: Array[h] = decoder.decodeColumn(buffers(idx))
        val tail = decodeAllColumns[t](buffers, idx + 1)
        (head *: tail).asInstanceOf[V]

end ColumnsDecoder
