package io.github.quafadas.scautable

import org.apache.poi.ss.usermodel.{Row, WorkbookFactory}
import org.apache.poi.ss.util.CellRangeAddress
import scala.collection.JavaConverters.*
import java.io.File
import io.github.quafadas.scautable.BadTableException

/** Common utilities and exceptions for Excel processing
  */
object ExcelUtils:

  /** Extracts headers from an Excel sheet, either from a specific range or the first row
    *
    * @param filePath
    *   Path to the Excel file
    * @param sheetName
    *   Name of the sheet
    * @param colRange
    *   Optional range specification (e.g., "A1:C10")
    * @return
    *   List of header strings
    */
  inline def extractHeaders(filePath: String, sheetName: String, colRange: Option[String]): List[String] =
    val workbook = WorkbookFactory.create(new File(filePath))
    try
      val sheet = workbook.getSheet(sheetName)

      colRange match
        case Some(range) if range.nonEmpty =>
          val cellRange = CellRangeAddress.valueOf(range)
          val firstRow = sheet.getRow(cellRange.getFirstRow)
          val cells =
            for (i <- cellRange.getFirstColumn to cellRange.getLastColumn)
              yield firstRow.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).toString
          cells.toList
        case _ =>
          if sheet.iterator().hasNext then sheet.iterator().next().cellIterator().asScala.toList.map(_.toString)
          else throw new BadTableException("No headers found in the first row of the sheet, and no range specified.")
      end match
    finally workbook.close()
    end try
  end extractHeaders

  /** Validates that headers are unique (no duplicates)
    *
    * @param headers
    *   List of header strings to validate
    * @throws BadTableException
    *   if duplicate headers are found
    */
  inline def validateUniqueHeaders(headers: List[String]): Unit =
    val headerSet = scala.collection.mutable.Set[String]()
    headers.foreach { header =>
      if headerSet.contains(header) then throw new BadTableException(s"Duplicate header found: $header, which will not work.")
      else headerSet.add(header)
    }
  end validateUniqueHeaders

  /** Parses a cell range string into its components
    *
    * @param range
    *   Excel range string (e.g., "A1:C10")
    * @return
    *   Tuple of (firstRow, lastRow, firstColumn, lastColumn)
    */
  inline def parseRange(range: String): (Int, Int, Int, Int) =
    val cellRange = CellRangeAddress.valueOf(range)
    (cellRange.getFirstRow, cellRange.getLastRow, cellRange.getFirstColumn, cellRange.getLastColumn)
  end parseRange

end ExcelUtils
