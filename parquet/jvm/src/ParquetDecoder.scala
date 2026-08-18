package io.github.quafadas.scautable.parquet

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

import scala.compiletime.erasedValue
import scala.compiletime.summonInline

/** Raised when a decoded parquet value cannot be surfaced as the inferred Scala type. */
final class ParquetDecodeException(msg: String) extends Exception(msg)

/** Turns the raw value produced by [[ParquetColumnSource]] into the Scala type inferred by the macro.
  *
  * Raw values are already the right runtime class (both sides are driven by [[ParquetScalaType]]), so instances are mostly a cast plus a null check. `null` is only legal for
  * `Option` columns, which correspond to `optional` fields in the parquet schema.
  */
trait ParquetDecoder[T]:
  def decode(raw: Any): T
end ParquetDecoder

object ParquetDecoder:

  private def nonNull[T](raw: Any, typeName: String): T =
    if raw == null then
      throw ParquetDecodeException(
        s"Encountered a null parquet value for a column typed as $typeName. Use Option[$typeName] for columns declared `optional`."
      )
    else raw.asInstanceOf[T]

  given ParquetDecoder[Int] = raw => nonNull[Int](raw, "Int")
  given ParquetDecoder[Long] = raw => nonNull[Long](raw, "Long")
  given ParquetDecoder[Float] = raw => nonNull[Float](raw, "Float")
  given ParquetDecoder[Double] = raw => nonNull[Double](raw, "Double")
  given ParquetDecoder[Boolean] = raw => nonNull[Boolean](raw, "Boolean")
  given ParquetDecoder[String] = raw => nonNull[String](raw, "String")
  given ParquetDecoder[Array[Byte]] = raw => nonNull[Array[Byte]](raw, "Array[Byte]")
  given ParquetDecoder[LocalDate] = raw => nonNull[LocalDate](raw, "LocalDate")
  given ParquetDecoder[Instant] = raw => nonNull[Instant](raw, "Instant")
  given ParquetDecoder[BigDecimal] = raw => nonNull[BigDecimal](raw, "BigDecimal")
  given ParquetDecoder[UUID] = raw => nonNull[UUID](raw, "UUID")

  given [T](using inner: ParquetDecoder[T]): ParquetDecoder[Option[T]] =
    raw => if raw == null then None else Some(inner.decode(raw))

end ParquetDecoder

/** Decodes one row out of a column-oriented row group into a typed [[Tuple]]. */
trait ParquetRowDecoder[T <: Tuple]:
  def decodeRow(columns: Array[Array[Any]], rowIdx: Int): T
end ParquetRowDecoder

object ParquetRowDecoder:

  inline def summonAll[T <: Tuple]: List[ParquetDecoder[?]] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (h *: t)   => summonInline[ParquetDecoder[h]] :: summonAll[t]

  inline given derived[T <: Tuple]: ParquetRowDecoder[T] = new ParquetRowDecoder[T]:
    private val decoders: List[ParquetDecoder[?]] = summonAll[T]

    def decodeRow(columns: Array[Array[Any]], rowIdx: Int): T =
      decodeElems(columns, rowIdx, decoders, 0).asInstanceOf[T]

    private def decodeElems(columns: Array[Array[Any]], rowIdx: Int, decs: List[ParquetDecoder[?]], colIdx: Int): Tuple =
      decs match
        case Nil       => EmptyTuple
        case d :: rest =>
          val value = d.asInstanceOf[ParquetDecoder[Any]].decode(columns(colIdx)(rowIdx))
          value *: decodeElems(columns, rowIdx, rest, colIdx + 1)
  end derived

end ParquetRowDecoder
