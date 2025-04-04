package io.github.quafadas.scautable

case class CsvConfig(
    delimiter: Char = ','
)

inline def defaultCsvConfig: CsvConfig = CsvConfig(',')
