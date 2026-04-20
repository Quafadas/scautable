package io.github.quafadas.scautable

import io.github.quafadas.table.TypeInferrer
import io.github.quafadas.table.HeaderOptions
import io.github.quafadas.table.ReadAs

/** Configuration options for reading CSV files.
  *
  * @param headerOptions
  *   How to handle headers when reading the CSV
  * @param typeInferrer
  *   How to infer types for columns
  * @param delimiter
  *   The character used to separate fields (default: comma)
  * @param readAs
  *   Whether to read as rows (iterator) or columns (arrays)
  */
case class CsvOpts(
    headerOptions: HeaderOptions = HeaderOptions.Default,
    typeInferrer: TypeInferrer = TypeInferrer.FromAllRows,
    delimiter: Char = ',',
    readAs: ReadAs = ReadAs.Rows
)

object CsvOpts:
  /** Default CSV options: read headers from first row, infer types from all rows, use comma delimiter, read as rows */
  transparent inline def default: CsvOpts = CsvOpts(HeaderOptions.Default, TypeInferrer.FromAllRows, ',', ReadAs.Rows)

  /** CSV options with custom header handling.
    *
    * Uses default type inference (FromAllRows), comma delimiter, and row-oriented reading.
    */
  inline def apply(headers: HeaderOptions): CsvOpts = CsvOpts(headers, TypeInferrer.FromAllRows, ',', ReadAs.Rows)

  /** CSV options with custom type inference.
    *
    * Uses default header handling (read from first row), comma delimiter, and row-oriented reading.
    */
  inline def apply(typeInferrer: TypeInferrer): CsvOpts = CsvOpts(HeaderOptions.Default, typeInferrer, ',', ReadAs.Rows)

  /** CSV options with both header handling and type inference.
    *
    * Uses comma delimiter and row-oriented reading.
    */
  inline def apply(headers: HeaderOptions, typeInferrer: TypeInferrer): CsvOpts =
    CsvOpts(headers, typeInferrer, ',', ReadAs.Rows)

  /** CSV options with readAs mode.
    *
    * Use this overload when you only need to specify whether to read the CSV as rows (iterator) or columns (arrays). All other options (headers, type inference, delimiter) use
    * their defaults.
    *
    * @param readAs
    *   Whether to read as rows (iterator) or columns (arrays)
    */
  inline def apply(readAs: ReadAs): CsvOpts = CsvOpts(HeaderOptions.Default, TypeInferrer.FromAllRows, ',', readAs)

  /** CSV options with custom type inference and read mode.
    *
    * @param typeInferrer
    *   How to infer types for columns (e.g., from all rows, as strings, etc.)
    * @param readAs
    *   Whether to read as rows (iterator) or columns (arrays)
    *
    * Uses default header handling (HeaderOptions.Default) and comma delimiter.
    */
  inline def apply(typeInferrer: TypeInferrer, readAs: ReadAs): CsvOpts =
    CsvOpts(HeaderOptions.Default, typeInferrer, ',', readAs)

end CsvOpts
