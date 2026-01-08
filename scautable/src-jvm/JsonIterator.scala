package io.github.quafadas.scautable.json

import scala.NamedTuple.*
import scala.annotation.publicInBinary
import scala.compiletime.*
import io.github.quafadas.scautable.RowDecoder
import StreamingJsonParser.*

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
    val headers: Seq[String]
)(using decoder: RowDecoder[V])
    extends Iterator[NamedTuple[K, V]]:

  type COLUMNS = K

  type Col[N <: Int] = Tuple.Elem[K, N]

  inline override def hasNext: Boolean = objects.hasNext

  inline override def next() =
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
    NamedTuple.build[K & Tuple]()(tuple)
  end next

  private inline def valueToString(value: JsonValue): String = value match
    case JsonNull      => ""
    case JsonBool(b)   => b.toString
    case JsonNumber(n) =>
      // If it's a whole number, format without decimals to avoid scientific notation
      if n.isWhole then n.toLong.toString else n.toString
    case JsonString(s)      => s
    case JsonObject(fields) => fields.toString
  end valueToString

  inline def headerIndex(s: String) =
    headers.zipWithIndex.find(_._1 == s).get._2

  inline def headerIndex[S <: String & Singleton] =
    headers.indexOf(constValue[S].toString)
  end headerIndex

end JsonIterator
