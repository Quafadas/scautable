package io.github.quafadas.scautable

/** Thrown when a non-blank CSV data line has a different number of cells than the header.
  *
  * Every read mode (rows, columns, dense arrays) throws this rather than padding or truncating the row, so that a value can never end up paired with the wrong row or column.
  *
  * @param dataLine
  *   1-based index of the offending line, counted from the first line after the header (blank lines included, so it can be found in the file)
  * @param expected
  *   the number of columns in the header
  * @param actual
  *   the number of cells found on the line
  */
final class CsvRowWidthException(val dataLine: Int, val expected: Int, val actual: Int)
    extends IllegalStateException(
      s"CSV data line $dataLine (1-based, counted after the header) has the wrong number of cells: expected $expected (the header width) but found $actual."
    )
