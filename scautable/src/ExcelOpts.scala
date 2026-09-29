package io.github.quafadas.scautable

import io.github.quafadas.table.TypeInferrer

/** Configuration options for reading Excel sheets.
  *
  * The plain entry points take a range and a type inferrer positionally, which covers most reading. Reach for this when you need an option they do not expose - currently
  * `skipFooter`.
  *
  * @param range
  *   Cell range, empty string reads the whole sheet. Either fully specified ("A1:C10"), or a top left anchor whose open edges are discovered at compile time - "A1" infers both
  *   edges, "A1:C" pins the columns, "A1:10" pins the rows
  * @param typeInferrer
  *   How to infer types for columns
  * @param skipFooter
  *   Rows to drop from the bottom of the range before anything else happens, including type inference. The escape hatch for a totals row or a source note that sits directly under
  *   the data with no blank row to separate it from the table
  */
case class ExcelOpts(
    range: String = "",
    typeInferrer: TypeInferrer = TypeInferrer.FromAllRows,
    skipFooter: Int = 0
)

object ExcelOpts:
  /** Default Excel options: read the whole sheet, infer types from all rows, skip nothing. */
  transparent inline def default: ExcelOpts = ExcelOpts("", TypeInferrer.FromAllRows, 0)

  /** Excel options with custom type inference over the whole sheet.
    *
    * `typeInferrer` is the second field, so this overload exists to let it be given on its own without a named argument.
    */
  inline def apply(typeInferrer: TypeInferrer): ExcelOpts = ExcelOpts("", typeInferrer, 0)
end ExcelOpts
