package io.github.quafadas.scautable.db

import java.sql.ResultSet

import scala.compiletime.erasedValue
import scala.compiletime.summonInline

/** Typeclass for decoding an entire row from a [[java.sql.ResultSet]] into a typed [[Tuple]].
  *
  * Instances are derived automatically via `summonInline` for any tuple whose element types all
  * have [[JdbcDecoder]] instances.
  */
trait JdbcRowDecoder[T <: Tuple]:
  def decodeRow(rs: ResultSet): T
end JdbcRowDecoder

object JdbcRowDecoder:

  inline def summonAll[T <: Tuple]: List[JdbcDecoder[?]] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (h *: t)   => summonInline[JdbcDecoder[h]] :: summonAll[t]

  inline given derived[T <: Tuple]: JdbcRowDecoder[T] = new JdbcRowDecoder[T]:
    private val decoders: List[JdbcDecoder[?]] = summonAll[T]

    def decodeRow(rs: ResultSet): T =
      decodeElems(rs, decoders, 1).asInstanceOf[T]

    private def decodeElems(rs: ResultSet, decs: List[JdbcDecoder[?]], colIdx: Int): Tuple =
      decs match
        case Nil      => EmptyTuple
        case d :: rest =>
          val value = d.asInstanceOf[JdbcDecoder[Any]].decode(rs, colIdx)
          value *: decodeElems(rs, rest, colIdx + 1)
  end derived

end JdbcRowDecoder
