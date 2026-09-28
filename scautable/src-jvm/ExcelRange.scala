package io.github.quafadas.scautable

import scala.jdk.CollectionConverters.*

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.util.CellReference
import org.apache.poi.xssf.usermodel.XSSFSheet

import io.github.quafadas.scautable.BadTableException

/** Resolution of a table's extent on a sheet, from a top left anchor.
  *
  * A range may be given in full (`"B5:Q55"`), or as an anchor whose open ends are discovered by walking the sheet the way `ctrl-right` and `ctrl-down` walk it in Excel:
  *
  *   - `"B5"` - anchor only. Both the right and bottom edges are discovered.
  *   - `"B5:Q"` - the columns are pinned to `B..Q`, the bottom edge is discovered.
  *   - `"B5:55"` - the rows are pinned to `5..55`, the right edge is discovered.
  *
  * Discovery happens at compile time, so the iterator that is generated carries a fully resolved range and does no scanning at runtime.
  */
object ExcelRange:

  private val CellRef = "^\\$?([A-Za-z]+)\\$?([0-9]+)$".r
  private val ColRef = "^\\$?([A-Za-z]+)$".r
  private val RowRef = "^\\$?([0-9]+)$".r

  /** The anchor of a range, plus whichever of its far edges were pinned by the user. A `None` edge is one we have to discover. */
  private case class RangeSpec(firstRow: Int, firstCol: Int, lastRow: Option[Int], lastCol: Option[Int]):
    def isComplete: Boolean = lastRow.isDefined && lastCol.isDefined
  end RangeSpec

  /** A cell counts as empty if Excel would show nothing in it. Formatting, and formulas evaluating to the empty string, do not make a cell part of a table. */
  private def isEmpty(cell: Cell | Null): Boolean =
    cell == null || (cell.getCellType match
      case CellType.BLANK   => true
      case CellType.STRING  => cell.getStringCellValue.trim.isEmpty
      case CellType.FORMULA =>
        cell.getCachedFormulaResultType == CellType.STRING && cell.getStringCellValue.trim.isEmpty
      case _ => false)
  end isEmpty

  private def rowIsEmpty(row: Row | Null, firstCol: Int, lastCol: Int): Boolean =
    row == null || (firstCol to lastCol).forall(c => isEmpty(row.getCell(c)))

  private def parse(spec: String): RangeSpec =
    def cellRef(part: String, whole: String): (Int, Int) = part match
      case CellRef(_, _) =>
        val ref = new CellReference(part)
        (ref.getRow, ref.getCol.toInt)
      case _ =>
        throw new BadTableException(
          s"""Could not read "$whole" as a cell range. It must start with a cell reference, e.g. "B5", "B5:Q55", "B5:Q" or "B5:55"."""
        )

    spec.split(":", -1) match
      case Array(anchor) =>
        val (row, col) = cellRef(anchor, spec)
        RangeSpec(row, col, None, None)
      case Array(anchor, "") =>
        val (row, col) = cellRef(anchor, spec)
        RangeSpec(row, col, None, None)
      case Array(anchor, far) =>
        val (row, col) = cellRef(anchor, spec)
        far match
          case CellRef(_, _) =>
            val ref = new CellReference(far)
            RangeSpec(row, col, Some(ref.getRow), Some(ref.getCol.toInt))
          case ColRef(_)     => RangeSpec(row, col, None, Some(CellReference.convertColStringToIndex(far.stripPrefix("$"))))
          case RowRef(rowNo) => RangeSpec(row, col, Some(rowNo.toInt - 1), None)
          case _             =>
            throw new BadTableException(
              s"""Could not read the end of the range "$spec". It must be a cell ("Q55"), a column ("Q"), a row ("55"), or left open ("B5" / "B5:")."""
            )
        end match
      case _ =>
        throw new BadTableException(s"""Could not read "$spec" as a cell range - it has more than one ":".""")
    end match
  end parse

  /** An Excel Table (a "ListObject") anchored at exactly this cell already declares its own extent, which beats anything we could infer by walking cells. Only honoured when the
    * table starts at the anchor, so that discovery never silently moves the origin the caller asked for.
    */
  private def declaredTableAt(sheet: Sheet, firstRow: Int, firstCol: Int): Option[(Int, Int)] =
    sheet match
      case xssf: XSSFSheet =>
        xssf
          .getTables()
          .asScala
          .find(t => t.getStartRowIndex == firstRow && t.getStartColIndex == firstCol)
          .map(t => (t.getEndRowIndex, t.getEndColIndex))
      case _ => None

  /** `ctrl-right` along the header row. Headers must be contiguous - a blank header cell ends the table, because a column without a name cannot be named in the result type.
    */
  private def discoverLastCol(sheet: Sheet, firstRow: Int, firstCol: Int, spec: String): Int =
    val headerRow = sheet.getRow(firstRow)
    if rowIsEmpty(headerRow, firstCol, firstCol) then
      throw new BadTableException(
        s"""No data at ${new CellReference(firstRow, firstCol).formatAsString()} on sheet "${sheet.getSheetName}", so the extent of "$spec" could not be inferred."""
      )
    end if
    val maxCol = headerRow.getLastCellNum.toInt - 1
    var lastCol = firstCol
    while lastCol < maxCol && !isEmpty(headerRow.getCell(lastCol + 1)) do lastCol += 1
    end while
    lastCol
  end discoverLastCol

  /** `ctrl-down` from the header row. The table ends at the last row with anything in it across the full width of the table, so a gap in one column does not truncate it.
    */
  private def discoverLastRow(sheet: Sheet, firstRow: Int, firstCol: Int, lastCol: Int): Int =
    val maxRow = sheet.getLastRowNum
    var lastRow = firstRow
    while lastRow < maxRow && !rowIsEmpty(sheet.getRow(lastRow + 1), firstCol, lastCol) do lastRow += 1
    end while
    lastRow
  end discoverLastRow

  /** Resolve a range specification against a sheet, returning a fully specified range such as `"B5:Q55"`.
    *
    * @param sheet
    *   The sheet the range refers to
    * @param spec
    *   A full range, or an anchor with open ends - see the class documentation
    * @return
    *   A range string with both corners pinned
    */
  def resolve(sheet: Sheet, spec: String): String =
    val trimmed = spec.trim
    val parsed = parse(trimmed)
    if parsed.isComplete then trimmed
    else
      import parsed.{firstRow, firstCol}
      val declared = declaredTableAt(sheet, firstRow, firstCol)
      val lastCol = parsed.lastCol.orElse(declared.map(_._2)).getOrElse(discoverLastCol(sheet, firstRow, firstCol, trimmed))
      val lastRow = parsed.lastRow.orElse(declared.map(_._1)).getOrElse(discoverLastRow(sheet, firstRow, firstCol, lastCol))
      if lastCol < firstCol || lastRow < firstRow then
        throw new BadTableException(
          s"""Inferred an empty range from "$trimmed" on sheet "${sheet.getSheetName}"."""
        )
      end if
      s"${new CellReference(firstRow, firstCol).formatAsString()}:${new CellReference(lastRow, lastCol).formatAsString()}"
    end if
  end resolve

end ExcelRange
