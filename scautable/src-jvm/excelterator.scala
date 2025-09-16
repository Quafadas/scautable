package io.github.quafadas.scautable
import java.io.File

import scala.NamedTuple.*
import scala.collection.JavaConverters.*
import scala.quoted.*

import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.util.CellRangeAddress

import io.github.quafadas.scautable.ColumnTyped.*


class ExcelIterator[K <: Tuple, V <: Tuple](filePath: String, sheetName: String, colRange: Option[String])(using decoder: RowDecoder[V]) extends Iterator[NamedTuple[K, V]]:
  type COLUMNS = K

  def getFilePath: String = filePath
  def getSheet: String = sheetName
  def getColRange: Option[String] = colRange

  def getRanges(r: String) =
    val range = CellRangeAddress.valueOf(r)
    (range.getFirstRow, range.getLastRow, range.getFirstColumn, range.getLastColumn)
  end getRanges

  lazy val sheetIterator =
    val workbook = WorkbookFactory.create(new File(filePath))
    val sheet = workbook.getSheet(sheetName)
    sheet.iterator().asScala
  end sheetIterator

  var debugi: Int = colRange match
    case None        => 0
    case Some(range) =>
      val (firstRow, lastRow, firstCol, lastCol) = getRanges(range)
      firstRow

  val headers: List[String] =
    colRange match
      case None =>
        if sheetIterator.hasNext then sheetIterator.next().cellIterator().asScala.toList.map(_.toString)
        else throw new Excel.BadTableException("No headers found in the first row of the sheet, and no range specified.")
      case Some(range) =>
        val (firstRow, lastRow, firstCol, lastCol) = getRanges(range)
        // println(s"Row firstRow: $firstRow, lastRow: $lastRow, firstCol: $firstCol, lastCol: $lastCol")
        val firstRow_ = sheetIterator.drop(firstRow).next()
        val cells =
          for (
              i <- firstCol to
                lastCol
            )
            yield firstRow_.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).toString
        cells.toList

  lazy val numCellsPerRow = headers.size

  val headerSet = scala.collection.mutable.Set[String]()
  headers.foreach { header =>
    if headerSet.contains(header) then throw new Excel.BadTableException(s"Duplicate header found: $header, which will not work. ")
    else headerSet.add(header)
  }

  lazy val headersTuple =
    listToTuple(headers)

  override def next(): NamedTuple[K, V] =
    if !hasNext then throw new NoSuchElementException("No more rows")
    end if
    val row = sheetIterator.next()
    val lastColumn = row.getLastCellNum();
    val cells = row.cellIterator().asScala
    val cellStr = colRange match
      case None =>
        cells.toList.map(_.toString)
      case Some(range) =>
        val (firstRow, lastRow, firstCol, lastCol) = getRanges(range)
        // println(s"Row $debugi: $firstRow, $lastRow, $firstCol, $lastCol")
        val cells = for (i <- firstCol to lastCol) yield row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).toString
        cells.toList

    // println(s"Row $debugi: ${cellStr.map(_.toString).mkString(", ")}")
    if cellStr.size != headers.size then
      throw new Excel.BadTableException(s"Row $debugi has ${cells.size} cells, but the table has ${headers.size} cells. Reading data was terminated")
    end if    
    val tuple = decoder.decodeRow(cellStr).getOrElse(
      throw new Exception("Failed to decode row: " + cellStr)
    )
    debugi += 1
    NamedTuple.build[K]()(tuple)
  end next

  override def hasNext: Boolean =
    colRange match
      case None        => sheetIterator.hasNext
      case Some(value) =>
        val (firstRow, lastRow, firstCol, lastCol) = getRanges(value)
        debugi < lastRow

end ExcelIterator
