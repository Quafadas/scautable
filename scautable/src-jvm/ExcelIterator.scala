package io.github.quafadas.scautable

import java.io.File
import scala.NamedTuple.*
import scala.collection.JavaConverters.*
import org.apache.poi.ss.usermodel.{Row, WorkbookFactory}
import org.apache.poi.ss.util.CellRangeAddress
import io.github.quafadas.scautable.BadTableException

/** Iterator for reading Excel files with compile-time type safety
  *
  * @param filePath
  *   Path to the Excel file
  * @param sheetName
  *   Name of the Excel sheet to read
  * @param colRange
  *   Optional cell range specification (e.g., "A1:C10")
  * @param decoder
  *   Row decoder for converting string data to typed tuples
  * @tparam K
  *   Tuple type representing column names
  * @tparam V
  *   Tuple type representing column value types
  */
class ExcelIterator[K <: Tuple, V <: Tuple](filePath: String, sheetName: String, colRange: Option[String])(using decoder: RowDecoder[V]) extends Iterator[NamedTuple[K, V]]:

  type COLUMNS = K

  // Public accessors for compile-time code generation
  def getFilePath: String = filePath
  def getSheet: String = sheetName
  def getColRange: Option[String] = colRange

  /** Parses a cell range string into its components
    */
  private def parseRange(range: String): (Int, Int, Int, Int) =
    val cellRange = CellRangeAddress.valueOf(range)
    (cellRange.getFirstRow, cellRange.getLastRow, cellRange.getFirstColumn, cellRange.getLastColumn)
  end parseRange

  /** Validates that headers are unique (no duplicates)
    */
  private def validateUniqueHeaders(headers: List[String]): Unit =
    val headerSet = scala.collection.mutable.Set[String]()
    headers.foreach { header =>
      if headerSet.contains(header) then throw new BadTableException(s"Duplicate header found: $header, which will not work.")
      else headerSet.add(header)
    }
  end validateUniqueHeaders

  // Lazy-initialized sheet iterator to avoid opening file until needed
  private lazy val sheetIterator =
    val workbook = WorkbookFactory.create(new File(filePath))
    val sheet = workbook.getSheet(sheetName)
    sheet.iterator().asScala
  end sheetIterator

  // Track current row number for error reporting - starts where data begins
  private var currentRowIndex: Int = colRange match
    case None                          => 0
    case Some(range) if range.nonEmpty =>
      val (firstRow, _, _, _) = parseRange(range)
      firstRow
    case _ => 0

  // Extract headers from the first row or specified range
  private val headers: List[String] =
    colRange match
      case Some(range) if range.nonEmpty =>
        extractHeadersFromRange(range)
      case _ =>
        extractHeadersFromFirstRow()

  private lazy val numCellsPerRow = headers.size

  // Validate headers are unique at initialization
  validateUniqueHeaders(headers)

  /** Extract headers from a specified cell range This consumes the header row from the sheet iterator
    */
  private inline def extractHeadersFromRange(range: String): List[String] =
    val (firstRow, _, firstCol, lastCol) = parseRange(range)
    val headerRow = sheetIterator.drop(firstRow).next()
    val cells =
      for (i <- firstCol.to(lastCol))
        yield headerRow.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).toString
    cells.toList
  end extractHeadersFromRange

  /** Extract headers from the first row of the sheet This consumes the header row from the sheet iterator
    */
  private inline def extractHeadersFromFirstRow(): List[String] =
    if sheetIterator.hasNext then sheetIterator.next().cellIterator().asScala.toList.map(_.toString)
    else throw new BadTableException("No headers found in the first row of the sheet, and no range specified.")
  end extractHeadersFromFirstRow

  /** Extract cell values from a row based on the column range
    */
  private inline def extractCellValues(row: org.apache.poi.ss.usermodel.Row): List[String] =
    colRange match
      case Some(range) if range.nonEmpty =>
        val (_, _, firstCol, lastCol) = parseRange(range)
        val cells =
          for (i <- firstCol.to(lastCol))
            yield row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).toString
        cells.toList
      case _ =>
        row.cellIterator().asScala.toList.map(_.toString)
  end extractCellValues

  override def next(): NamedTuple[K, V] =
    if !hasNext then throw new NoSuchElementException("No more rows")
    end if

    val row = sheetIterator.next()
    val cellValues = extractCellValues(row)

    // Validate row has expected number of cells
    if cellValues.size != headers.size then
      throw new BadTableException(
        s"Row $currentRowIndex has ${cellValues.size} cells, but expected ${headers.size} cells. Reading terminated."
      )
    end if

    // Decode the row using the provided decoder
    val decodedTuple = decoder
      .decodeRow(cellValues)
      .getOrElse(
        throw new Exception(s"Failed to decode row $currentRowIndex: $cellValues")
      )

    currentRowIndex += 1
    NamedTuple.build[K]()(decodedTuple)
  end next

  override def hasNext: Boolean =
    colRange match
      case Some(range) if range.nonEmpty =>
        val (_, lastRow, _, _) = parseRange(range)
        currentRowIndex < lastRow
      case _ =>
        sheetIterator.hasNext
  end hasNext

end ExcelIterator
