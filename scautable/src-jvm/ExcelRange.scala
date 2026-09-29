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

  /** How one edge of a resolved range came to be. Carried so that an inferred extent can be explained back to the caller, who never sees the walk happen. */
  enum EdgeOrigin:
    /** The caller pinned this edge themselves, so nothing was inferred. */
    case Pinned

    /** An Excel Table (a "ListObject") anchored at the same cell declared it. */
    case DeclaredTable

    /** Discovered by walking the sheet's cells. */
    case Walked
  end EdgeOrigin

  /** A resolved range, plus enough provenance to explain how it was arrived at.
    *
    * @param range
    *   The fully specified range, e.g. "B5:Q55"
    * @param colEdge
    *   How the right edge was decided
    * @param rowEdge
    *   How the bottom edge was decided
    * @param spec
    *   What the caller originally wrote
    */
  case class Resolved(
      range: String,
      firstRow: Int,
      firstCol: Int,
      lastRow: Int,
      lastCol: Int,
      colEdge: EdgeOrigin,
      rowEdge: EdgeOrigin,
      spec: String,
      sheetName: String,
      footerRowsSkipped: Int = 0
  ):
    /** True when at least one edge was decided for the caller rather than by them. */
    def wasInferred: Boolean = colEdge != EdgeOrigin.Pinned || rowEdge != EdgeOrigin.Pinned

    def columns: Int = lastCol - firstCol + 1

    /** Rows of data, i.e. excluding the header row at `firstRow`. */
    def dataRows: Int = lastRow - firstRow

    /** A human readable account of what was inferred and how, for the compile time diagnostic. */
    def describe: String =
      val firstColRef = CellReference.convertNumToColString(firstCol)
      val lastColRef = CellReference.convertNumToColString(lastCol)
      val edges = List(
        colEdge match
          case EdgeOrigin.Pinned        => None
          case EdgeOrigin.DeclaredTable => Some("last column declared by an Excel Table anchored at the same cell")
          case EdgeOrigin.Walked        => Some(s"last column found by walking right along header row ${firstRow + 1} to the first empty header")
        ,
        rowEdge match
          case EdgeOrigin.Pinned        => None
          case EdgeOrigin.DeclaredTable => Some("last row declared by an Excel Table anchored at the same cell")
          case EdgeOrigin.Walked        => Some(s"last row found by walking down to the last row with content across $firstColRef..$lastColRef")
      ).flatten
      val footer =
        if footerRowsSkipped == 0 then Nil
        else
          val plural = if footerRowsSkipped == 1 then "row" else "rows"
          List(s"$footerRowsSkipped footer $plural skipped, so the table ends at row ${lastRow + 1} rather than ${lastRow + 1 + footerRowsSkipped}")
      s"""range "$spec" on sheet "$sheetName" resolved to "$range" - $columns columns x $dataRows data rows""" +
        (edges ++ footer).mkString("\n    ", "\n    ", "")
    end describe

    /** Trim `n` rows off the bottom of the table.
      *
      * This is the escape hatch for a totals row or a "Source: ..." note sitting directly under the data. Nothing separates such a row from the table, so the downward walk has no
      * way to know it is not data - but the reader does, and usually knows exactly how many rows it is.
      *
      * Applied to a pinned range as readily as to an inferred one, so that it means the same thing wherever the extent came from.
      */
    def dropFooterRows(n: Int): Resolved =
      if n == 0 then this
      else if n < 0 then throw new BadTableException(s"skipFooter must not be negative, but was $n.")
      else if n >= dataRows then
        throw new BadTableException(
          s"""skipFooter = $n would leave no data rows in "$range" on sheet "$sheetName", which has $dataRows."""
        )
      else
        val trimmedLastRow = lastRow - n
        copy(
          range = s"${new CellReference(firstRow, firstCol).formatAsString()}:${new CellReference(trimmedLastRow, lastCol).formatAsString()}",
          lastRow = trimmedLastRow,
          footerRowsSkipped = footerRowsSkipped + n
        )
      end if
    end dropFooterRows
  end Resolved

  private val CellRef = "^\\$?([A-Za-z]+)\\$?([0-9]+)$".r
  private val ColRef = "^\\$?([A-Za-z]+)$".r
  private val RowRef = "^\\$?([0-9]+)$".r

  /** The anchor of a range, plus whichever of its far edges were pinned by the user. A `None` edge is one we have to discover. */
  private case class RangeSpec(firstRow: Int, firstCol: Int, lastRow: Option[Int], lastCol: Option[Int])

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
  def resolve(sheet: Sheet, spec: String): String = resolveDetailed(sheet, spec).range

  /** As `resolve`, but also reports how each edge was decided, so that an inferred extent can be explained back to the caller.
    *
    * @param sheet
    *   The sheet the range refers to
    * @param spec
    *   A full range, or an anchor with open ends - see the class documentation
    * @return
    *   The resolved range and its provenance
    */
  def resolveDetailed(sheet: Sheet, spec: String): Resolved =
    val trimmed = spec.trim
    val parsed = parse(trimmed)
    import parsed.{firstRow, firstCol}

    (parsed.lastRow, parsed.lastCol) match
      case (Some(lastRow), Some(lastCol)) =>
        // Nothing to discover, and nothing to second guess - pass the caller's range through untouched.
        Resolved(trimmed, firstRow, firstCol, lastRow, lastCol, EdgeOrigin.Pinned, EdgeOrigin.Pinned, trimmed, sheet.getSheetName)
      case _ =>
        val declared = declaredTableAt(sheet, firstRow, firstCol)
        val (lastCol, colEdge) = parsed.lastCol.map((_, EdgeOrigin.Pinned)) `orElse`
          declared.map((d: (Int, Int)) => (d._2, EdgeOrigin.DeclaredTable)) getOrElse
          (discoverLastCol(sheet, firstRow, firstCol, trimmed), EdgeOrigin.Walked)
        val (lastRow, rowEdge) = parsed.lastRow.map((_, EdgeOrigin.Pinned)) `orElse`
          declared.map((d: (Int, Int)) => (d._1, EdgeOrigin.DeclaredTable)) getOrElse
          (discoverLastRow(sheet, firstRow, firstCol, lastCol), EdgeOrigin.Walked)

        if lastCol < firstCol || lastRow < firstRow then
          throw new BadTableException(
            s"""Inferred an empty range from "$trimmed" on sheet "${sheet.getSheetName}"."""
          )
        end if
        val range = s"${new CellReference(firstRow, firstCol).formatAsString()}:${new CellReference(lastRow, lastCol).formatAsString()}"
        Resolved(range, firstRow, firstCol, lastRow, lastCol, colEdge, rowEdge, trimmed, sheet.getSheetName)
    end match
  end resolveDetailed

end ExcelRange
