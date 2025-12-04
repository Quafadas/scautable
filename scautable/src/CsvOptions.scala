
package io.github.quafadas.scautable

import io.github.quafadas.table.TypeInferrer

case class CsvReadOptions(
    delimiter: Char,
    typeInferrer: TypeInferrer,
    headers: List[String] = List.empty
)