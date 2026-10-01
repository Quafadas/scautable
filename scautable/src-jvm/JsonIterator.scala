package io.github.quafadas.scautable.json

import scala.NamedTuple.*
import scala.annotation.publicInBinary
import scala.compiletime.*

import io.github.quafadas.scautable.RowDecoder
import io.github.quafadas.scautable.json.StreamingJsonParser.*

/** A NamedTuple representation of a JSON array.
  *
  * It is an iterator that reads a JSON array of flat objects and converts each object into a NamedTuple.
  *
  * Common usage:
  *
  * ```scala sc:nocompile
  * val jsonIterator = JSON.fromString("""[{"a":1,"b":2},{"a":5,"b":3}]""")
  * val jsonData = jsonIterator.toSeq
  * ```
  *
  * Note that at this point, you are plugged right into the scala collections API.
  *
  * ```scala sc:nocompile
  * jsonData.filter(_.column("a") == 1).map(_.column("b"))
  * ```
  * etc
  */
class JsonIterator[K <: Tuple, V <: Tuple] @publicInBinary private[json] (
    private val objects: Iterator[JsonObject],
    val headers: Seq[String],
    private val source: Option[AutoCloseable] = None
)(using decoder: RowDecoder[V])
    extends Iterator[NamedTuple[K, V]]
    with AutoCloseable:

  type COLUMNS = K

  type Col[N <: Int] = Tuple.Elem[K, N]

  private var closed = false

  /** Release the stream behind this iterator.
    *
    * Idempotent, and safe to call at any point - a closed iterator simply reports no more rows. Draining the iterator calls this for you, so `.toSeq` and friends need nothing.
    * Reach for it when you stop reading early, or let `scala.util.Using` do it.
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

  /** Closes the underlying stream as soon as the objects run out, so that reading a whole file needs no cleanup. */
  override def hasNext: Boolean =
    if closed then false
    else
      val more = objects.hasNext
      if !more then close()
      end if
      more
    end if
  end hasNext

  override def next(): NamedTuple[K, V] =
    // Past a close the underlying stream is gone, so reading on would hand back whatever happened to be buffered, or fail deep inside the parser.
    if closed then throw new NoSuchElementException("This JsonIterator has been closed, so there are no more rows to read.")
    end if
    val obj = objects.next()
    // Extract values in header order, converting JsonValue to String
    val values = headers.map { header =>
      obj.fields.get(header) match
        case Some(JsonNull) => ""
        case Some(value)    => valueToString(value)
        case None           => "" // Missing field
    }.toList

    val tuple = decoder
      .decodeRow(values)
      .getOrElse(
        throw new Exception(s"Failed to decode JSON object: $values")
      )
    tuple.asInstanceOf[NamedTuple[K, V]]
  end next

  private def valueToString(value: JsonValue): String = value match
    case JsonNull      => ""
    case JsonBool(b)   => b.toString
    case JsonNumber(n) =>
      // If it's a whole number, format without decimals to avoid scientific notation
      if n.isWhole then n.toLong.toString else n.toString
    case JsonString(s)      => s
    case JsonObject(fields) => fields.toString
  end valueToString

  def headerIndex(s: String) =
    headers.zipWithIndex.find(_._1 == s).get._2

  inline def headerIndex[S <: String & Singleton] =
    headers.indexOf(constValue[S].toString)
  end headerIndex

end JsonIterator
