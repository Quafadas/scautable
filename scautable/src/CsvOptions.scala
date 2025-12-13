package io.github.quafadas.scautable

import io.github.quafadas.table.TypeInferrer

/** Configuration options for reading CSV files.
  *
  * @param headerOptions
  *   How to handle headers when reading the CSV
  * @param typeInferrer
  *   How to infer types for columns
  * @param delimiter
  *   The character used to separate fields (default: comma)
  */
case class CsvOpts(
    headerOptions: HeaderOptions = HeaderOptions.Default,
    typeInferrer: TypeInferrer = TypeInferrer.FromAllRows,
    delimiter: Char = ','
)

object CsvOpts:
  /** Default CSV options: read headers from first row, infer types from all rows, use comma delimiter */
  transparent inline def default: CsvOpts = CsvOpts(HeaderOptions.Default, TypeInferrer.FromAllRows, ',')

  /** CSV options with custom header handling */
  inline def apply(headers: HeaderOptions): CsvOpts = CsvOpts(headers, TypeInferrer.StringType, ',')

  /** CSV options with custom type inference */
  inline def apply(typeInferrer: TypeInferrer): CsvOpts = CsvOpts(HeaderOptions.Default, typeInferrer, ',')

  /** CSV options with both header handling and type inference */
  inline def apply(headers: HeaderOptions, typeInferrer: TypeInferrer): CsvOpts =
    CsvOpts(headers, typeInferrer, ',')

end CsvOpts
