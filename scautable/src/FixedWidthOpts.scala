package io.github.quafadas.scautable

import io.github.quafadas.table.TypeInferrer

/** Configuration options for reading fixed-width files.
  *
  * Fixed-width files have fields at specific character positions rather than being delimited by a character.
  * Column widths are automatically inferred by detecting 2+ consecutive padding characters as field separators.
  *
  * @param headerOptions
  *   How to handle headers when reading the file
  * @param typeInferrer
  *   How to infer types for columns
  * @param trimFields
  *   Whether to trim leading and trailing whitespace from each field (default: true).
  *   Fixed-width files often pad fields with spaces, so trimming is usually desired.
  * @param paddingChar
  *   The character used for padding between fields (default: space). The parser detects 2+ consecutive
  *   padding characters as field separators.
  */
case class FixedWidthOpts(
    headerOptions: HeaderOptions = HeaderOptions.Default,
    typeInferrer: TypeInferrer = TypeInferrer.FromAllRows,
    trimFields: Boolean = true,
    paddingChar: Char = ' '
)

object FixedWidthOpts:
  /** Default fixed-width options: infer widths and types from all rows, trim fields, use space padding */
  val default: FixedWidthOpts = FixedWidthOpts()
end FixedWidthOpts
