package io.github.quafadas.scautable

import pprint.TPrint
import pprint.TPrintColors

object CsvIteratorTPrint: 
  given csvIterTPrint[K <: Tuple, V <: Tuple]: TPrint[CsvIterator[K, V]] = new TPrint[CsvIterator[K, V]]:
    def render(implicit tpc: TPrintColors): fansi.Str =
      fansi.Str("CsvIterator[K <: Tuple, V <: Tuple]")

  import scala.compiletime.{erasedValue, summonInline}
  import scala.deriving.Mirror
  import scala.annotation.tailrec


  inline def getTypeNames[T <: Tuple](implicit tpc: TPrintColors): List[String] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (h *: t) => summonInline[TPrint[h]].render(tpc).toString :: getTypeNames[t]

  extension [K <: Tuple, V <: Tuple](csvIterator: CsvIterator[K, V])
    inline def prettyPrint(implicit tpc: TPrintColors): fansi.Str =
      val columnNames = csvIterator.headers

      val colTypes = getTypeNames[V]
      val coltypes = columnNames.zipAll(colTypes, "<missing>", "<unknown>")
        .map { case (name, colType) => fansi.Str(s"$name: $colType") }
        .mkString("[\n\t", ",\n\t", "\n]")
      fansi.Str("CsvIterator") ++ fansi.Str(coltypes)