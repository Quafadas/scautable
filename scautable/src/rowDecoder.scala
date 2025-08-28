package io.github.quafadas.scautable

import scala.compiletime.{erasedValue, summonInline}
import io.github.quafadas.scautable.Decoder


private[scautable] trait RowDecoder[T]:
  def decodeRow(row: List[String]): Option[T]

private[scautable] object RowDecoder:
  inline def summonAllDecoders[T <: Tuple]: List[Decoder[?]] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (h *: t) => summonInline[Decoder[h]] :: summonAllDecoders[t]

  inline given derived[T <: Tuple]: RowDecoder[T] = new RowDecoder[T]:
    private val decoders: List[Decoder[?]] = summonAllDecoders[T]

    def decodeRow(row: List[String]): Option[T] =
      decodeElems(row, decoders).asInstanceOf[Option[T]]

    private def decodeElems(row: List[String], decs: List[Decoder[?]]): Option[Tuple] =
      (row, decs) match
        case (Nil, Nil) => Some(EmptyTuple)
        case (h :: t, d :: dt) =>
          val decoder = d.asInstanceOf[Decoder[Any]]
          decoder.decode(h).flatMap { hv =>
            decodeElems(t, dt).map(rest => hv *: rest)
          }
        case _ => None

