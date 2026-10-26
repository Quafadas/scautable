package io.github.quafadas.scautable

import scala.util.Try

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.CellValue
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.FormulaError
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.usermodel.Workbook

/** How a cell reads, shared by compile time type inference and the runtime iterator so the two cannot disagree.
  *
  * `Cell.toString` is right for every cell type but one: on a formula cell it returns the formula itself, so `=A2*2` would reach the decoders as the text "A2*2" while inference,
  * which evaluates, had typed the column `Int`.
  */
private[scautable] object ExcelCells:

  /** Reads cells with one `FormulaEvaluator` per workbook, so POI's evaluation cache is reused across a sheet rather than rebuilt for every formula cell. Not thread safe, like the
    * workbook it reads.
    */
  final class Reader:
    private var current: Option[(Workbook, FormulaEvaluator)] = None

    def evaluator(cell: Cell): FormulaEvaluator =
      val workbook = cell.getSheet.getWorkbook
      current match
        case Some((wb, e)) if wb eq workbook => e
        case _                               =>
          val e = workbook.getCreationHelper.createFormulaEvaluator()
          current = Some((workbook, e))
          e
      end match
    end evaluator

    def text(cell: Cell): String = ExcelCells.text(cell, evaluator(cell))
  end Reader

  /** The result of a formula cell: evaluated if POI can, otherwise whatever Excel cached when it last saved the file. */
  def formulaResult(cell: Cell, evaluator: FormulaEvaluator): Option[CellValue] =
    Try(Option(evaluator.evaluate(cell))).toOption.flatten.orElse(cachedResult(cell))

  private def cachedResult(cell: Cell): Option[CellValue] =
    Try(cell.getCachedFormulaResultType match
      case CellType.NUMERIC => Some(new CellValue(cell.getNumericCellValue))
      case CellType.STRING  => Some(new CellValue(cell.getStringCellValue))
      case CellType.BOOLEAN => Some(CellValue.valueOf(cell.getBooleanCellValue))
      case CellType.ERROR   => Some(CellValue.getError(cell.getErrorCellValue))
      case _                => None).toOption.flatten

  /** Whether a numeric value in this cell displays as a date. `DateUtil.isCellDateFormatted` cannot be used on formula cells, as it reads the cached value. */
  def isDate(cell: Cell, value: Double): Boolean =
    val style = cell.getCellStyle
    DateUtil.isADateFormat(style.getDataFormat, style.getDataFormatString) && DateUtil.isValidExcelDate(value)
  end isDate

  /** The text of a cell as the decoders see it - `Cell.toString`, except that a formula cell renders its result the way `toString` renders a plain cell holding that value. */
  def text(cell: Cell, evaluator: => FormulaEvaluator): String =
    if cell.getCellType != CellType.FORMULA then cell.toString
    else
      formulaResult(cell, evaluator).fold("") { v =>
        v.getCellType match
          case CellType.NUMERIC if isDate(cell, v.getNumberValue) =>
            val style = cell.getCellStyle
            DataFormatter().formatRawCellContents(v.getNumberValue, style.getDataFormat, style.getDataFormatString)
          case CellType.NUMERIC => java.lang.Double.toString(v.getNumberValue)
          case CellType.STRING  => v.getStringValue
          case CellType.BOOLEAN => if v.getBooleanValue then "TRUE" else "FALSE"
          case CellType.ERROR   => FormulaError.forInt(v.getErrorValue).getString
          case _                => ""
      }
  end text

end ExcelCells
