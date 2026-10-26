package io.github.quafadas.scautable
import scala.NamedTuple.*
import scala.annotation.publicInBinary
import scala.compiletime.*

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
  * It owns the file handle it reads from, and releases it when the rows run out - so the shape above needs no cleanup. If you stop reading early, close it yourself, or wrap it in
  * `scala.util.Using`; see [[close]].
  *
  * Note that at this point, you are plugged right into the scala collections API.
  *
  * ```scala sc:nocompile
  * csvData.filter(_.column("colA") == "foo").drop(10).take(5).map(_.column("colB"))
  * ```
  * etc
  */

class CsvIterator[K <: Tuple, V <: Tuple] @publicInBinary private[scautable] (
    private val rows: Iterator[String],
    val headers: Seq[String],
    delimiter: Char = ',',
    private val source: Option[AutoCloseable] = None
)(using
    decoder: RowDecoder[V]
) extends Iterator[NamedTuple[K, V]]
    with AutoCloseable:

  type COLUMNS = K

  type Col[N <: Int] = Tuple.Elem[K, N]

  private var closed = false

  /** Release the file handle behind this iterator.
    *
    * Idempotent, and safe to call at any point - a closed iterator simply reports no more rows. Draining the iterator calls this for you, so the common shapes (`.toSeq`,
    * `.toList`, `foreach`, a fully forced `LazyList`) need nothing. Reach for it explicitly when you stop early, or let `scala.util.Using` do it:
    *
    * ```scala sc:nocompile
    * Using(CSV.absolutePath("big.csv"))(_.take(10).toList)
    * ```
    *
    * Without either, the handle lives until the JVM notices the iterator is unreachable and reclaims it, which is tied to garbage collection rather than to your reading - so
    * enough unclosed reads in a row can still exhaust the process's file descriptors.
    */
  override def close(): Unit =
    if !closed then
      closed = true
      source.foreach { s =>
        try s.close()
        catch case _: Exception => () // a handle we cannot release is not worth failing a read over
      }
    end if
  end close

  /** Closes the underlying source as soon as the rows run out, so that the ordinary "read the whole file" shapes do not need a `close` at all. */
  override def hasNext: Boolean =
    if closed then false
    else
      val more = advance()
      if !more then close()
      end if
      more
    end if
  end hasNext

  /** The next non-blank line, read ahead so that `hasNext` can see past blank lines; `null` when none is buffered. */
  private var pending: String = null

  /** 1-based index of the last line taken from `rows`, blank lines included, for error messages. */
  private var dataLine = 0

  private val numCols = headers.length

  /** Buffers the next non-blank line, if there is one. Blank lines are skipped, as in the column and dense-array read modes. */
  private def advance(): Boolean =
    while pending == null && rows.hasNext do
      val line = rows.next()
      dataLine += 1
      if !CsvRows.isBlank(line, delimiter) then pending = line
      end if
    end while
    pending != null
  end advance

  // inline override def next() =
  //   val str = rows.next()
  //   val splitted = CSVParser.parseLine(str, delimiter)
  //   val tuple = listToTuple(splitted).asInstanceOf[StringyTuple[K]]
  //   NamedTuple.build[K & Tuple]()(tuple)
  // end next

  override def next(): NamedTuple[K, V] =
    // Not just tidiness: past a close the underlying source is gone, so reading on would hand back whatever happened to be buffered, or fail deep inside the reader.
    if closed then throw new NoSuchElementException("This CsvIterator has been closed, so there are no more rows to read.")
    end if
    if !advance() then throw new NoSuchElementException("No more rows in this CsvIterator.")
    end if
    val str = pending
    pending = null
    val splitted = CSVParser.parseLine(str, delimiter)
    val width = splitted.length
    if width != numCols then throw CsvRowWidthException(dataLine, numCols, width)
    end if
    val tuple = decoder
      .decodeRow(splitted)
      .getOrElse(
        throw new Exception("Failed to decode row: " + splitted)
      )
    tuple.asInstanceOf[NamedTuple[K, V]]
  end next

  def schemaGen: String =
    val headerTypes = headers.map(header => s"type ${header} = \"$header\"").mkString("\n  ")
    s"""object CsvSchema:
  $headerTypes

import CsvSchema.*
"""
  end schemaGen

  def headerIndex(s: String) =
    headers.zipWithIndex.find(_._1 == s).get._2

  inline def headerIndex[S <: String & Singleton] =
    headers.indexOf(constValue[S].toString)
  end headerIndex

end CsvIterator
