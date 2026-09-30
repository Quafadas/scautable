package io.github.quafadas.scautable

import scala.NamedTuple.*
import scala.collection.JavaConverters.*

import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.util.CellRangeAddress

import io.github.quafadas.scautable.BadTableException
import io.github.quafadas.scautable.ExcelWorkbookCache

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

  /** The range's corners, parsed once. `CellRangeAddress.valueOf` is string parsing, and this sits on the per-row path. */
  private lazy val parsedRange: Option[(Int, Int, Int, Int)] = colRange.filter(_.nonEmpty).map(parseRange)

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
    val workbook = ExcelWorkbookCache
      .getOrCreate(filePath)
      .getOrElse(
        throw new BadTableException(s"Failed to open Excel file: $filePath")
      )
    val sheet = workbook.getSheet(sheetName)
    // Create an iterator that gives us rows by index for the specified range
    parsedRange match
      case Some((firstRow, lastRow, _, _)) =>
        val dataStartRow = firstRow + 1
        val dataRowIndices = (dataStartRow to lastRow).toIterator
        dataRowIndices.map(rowIndex => sheet.getRow(rowIndex)).filter(_ != null)
      case None =>
        sheet.iterator().asScala // For no range, use default iterator
    end match
  end sheetIterator

  /** The spreadsheet row number (1 based, as Excel shows it) of the row last returned by `next()`, for error reporting.
    *
    * Taken from the row itself rather than counted, because the iterator skips rows POI reports as absent - a counter would drift from the sheet the moment it met one, and name
    * the wrong row in an error.
    */
  private var currentRowIndex: Int = 0

  // Extract headers from the first row or specified range
  private val headers: List[String] =
    parsedRange match
      case Some(_) => extractHeadersFromRange()
      case None    => extractHeadersFromFirstRow()

  private lazy val numCellsPerRow = headers.size

  // Validate headers are unique at initialization
  validateUniqueHeaders(headers)

  /** Extract headers from a specified cell range This accesses the header row directly by index
    */
  private def extractHeadersFromRange(): List[String] =
    val (firstRow, _, firstCol, lastCol) = parsedRange.get
    val workbook = ExcelWorkbookCache
      .getOrCreate(filePath)
      .getOrElse(
        throw new BadTableException(s"Failed to open Excel file: $filePath")
      )
    val sheet = workbook.getSheet(sheetName)
    val headerRow = sheet.getRow(firstRow)
    val cells =
      for i <- firstCol.to(lastCol)
      yield headerRow.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).toString
    cells.toList
  end extractHeadersFromRange

  /** Extract headers from the first row of the sheet This consumes the header row from the sheet iterator
    */
  private def extractHeadersFromFirstRow(): List[String] =
    if sheetIterator.hasNext then sheetIterator.next().cellIterator().asScala.toList.map(_.toString)
    else throw new BadTableException("No headers found in the first row of the sheet, and no range specified.")
  end extractHeadersFromFirstRow

  /** Extract cell values from a row based on the column range
    */
  private def extractCellValues(row: org.apache.poi.ss.usermodel.Row): List[String] =
    parsedRange match
      case Some((_, _, firstCol, lastCol)) =>
        val cells =
          for i <- firstCol.to(lastCol)
          yield row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).toString
        cells.toList
      case None =>
        row.cellIterator().asScala.toList.map(_.toString)
  end extractCellValues

  override def next(): NamedTuple[K, V] =
    if !hasNext then throw new NoSuchElementException("No more rows")
    end if

    val row = sheetIterator.next()
    currentRowIndex = row.getRowNum + 1
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
        throw new Exception(
          s"Failed to decode row $currentRowIndex: $cellValues.\n One cause could be if you do not have `given` cell decoders for your column types. Please check your given imports."
        )
      )

    NamedTuple.build[K]()(decodedTuple)
  end next

  /** Whether another row is actually available.
    *
    * Asks the row iterator, rather than comparing a counter against the range's last row. The two are not the same: `sheetIterator` drops rows POI reports as absent, which is what
    * an entirely blank row inside the range is - a visual separator between groups, say. Counting row numbers therefore promised more rows than existed, and `next()` fell off the
    * end of the underlying iterator with a bare `NoSuchElementException`.
    *
    * The range still bounds the iteration, because `sheetIterator` is built from `firstRow + 1 to lastRow`. Skipping blank rows also keeps reading consistent with compile time
    * type inference, which walks the range the same way.
    */
  override def hasNext: Boolean = sheetIterator.hasNext

end ExcelIterator
