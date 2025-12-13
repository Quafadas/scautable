package io.github.quafadas.scautable

import io.github.quafadas.table.TypeInferrer
import scala.quoted.*

/** Specifies how to read CSV data.
  *
  *   - `Rows`: Returns `CsvIterator[K, V]` - lazy row-by-row iteration
  *   - `Columns`: Returns `NamedTuple[K, (Array[T1], Array[T2], ...)]` - eager column arrays
  */
enum ReadAs:
  case Rows
  case Columns
end ReadAs

object ReadAs:
  given FromExpr[ReadAs] with
    def unapply(x: Expr[ReadAs])(using Quotes): Option[ReadAs] =
      import quotes.reflect.*

      // Try direct pattern matching first
      x match
        case '{ ReadAs.Rows }                                 => Some(ReadAs.Rows)
        case '{ ReadAs.Columns }                              => Some(ReadAs.Columns)
        case '{ io.github.quafadas.scautable.ReadAs.Rows }    => Some(ReadAs.Rows)
        case '{ io.github.quafadas.scautable.ReadAs.Columns } => Some(ReadAs.Columns)
        case _                                                =>
          // Fallback: check the term structure for Select pattern
          def unwrapInlined(term: Term): Term = term match
            case Inlined(_, _, body) => unwrapInlined(body)
            case other               => other

          unwrapInlined(x.asTerm) match
            case Select(_, "Rows")    => Some(ReadAs.Rows)
            case Select(_, "Columns") => Some(ReadAs.Columns)
            case _                    => None
          end match
      end match
    end unapply
  end given
end ReadAs

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
    * Uses default type inference (StringType for safety), comma delimiter, and row-oriented reading.
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
