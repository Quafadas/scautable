package io.github.quafadas.scautable

import pprint.TPrint
import pprint.TPrintColors

object CsvIteratorTPrint: 
  given csvIterTPrint[K <: Tuple]: TPrint[CsvIterator[K]] = new TPrint[CsvIterator[K]]:
    def render(implicit tpc: TPrintColors): fansi.Str =
      fansi.Str("CsvIterator[K <: Tuple]")
      
  extension [K <: Tuple](csvIterator: CsvIterator[K])
    def prettyPrint(implicit tpc: TPrintColors): fansi.Str =
      val columnNames = csvIterator.headers
      val colTypes = List.fill(columnNames.size)("String")
      val coltypes = columnNames.zip(colTypes).map { case (name, colType) =>
        fansi.Str(s"$name: $colType,")
      }.mkString("[\n\t", "\n\t", "\n]")
      fansi.Str("CsvIterator") ++ fansi.Str(coltypes)