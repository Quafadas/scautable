package io.github.quafadas.scautable

import scala.io.Source
import scala.util.Try
import org.apache.poi.ss.usermodel.{DataFormatter, WorkbookFactory, Row}
import scala.util.chaining.*
import scala.util.matching.Regex
import scala.NamedTuple.*
import scala.compiletime.*
import CSV.*
import ConsoleFormat.*
import ColumnTyped.*

import java.io.File
import scala.collection.JavaConverters.*
import scala.quoted.*

object Excel:

  class BadTableException(message: String) extends Exception(message)

  given IteratorToExpr2[K](using ToExpr[String], Type[K]): ToExpr[ExcelIterator[K]] with
    def apply(opt: ExcelIterator[K])(using Quotes): Expr[ExcelIterator[K]] =
      val str = Expr(opt.getFilePath)
      val sheet = Expr(opt.getSheet)
      '{
        new ExcelIterator[K]($str, $sheet)
      }
    end apply
  end IteratorToExpr2

  transparent inline def absolutePath[K](filePath: String, sheetName: String) = ${ readExcelAbolsutePath('filePath, 'sheetName) }
  
  // New method that allows runtime path evaluation
  def openExcel(filePath: String, sheetName: String): ExcelIterator[Tuple] =
    val headers = getExcelHeaders(filePath, sheetName)
    new ExcelIterator[Tuple](filePath, sheetName)
  
  // Helper method to get headers from an Excel file
  private def getExcelHeaders(filePath: String, sheetName: String): List[String] =
    val workbook = WorkbookFactory.create(new File(filePath))
    val sheet = workbook.getSheet(sheetName)
    val headerRow = sheet.iterator().asScala.next()
    headerRow.cellIterator().asScala.toList.map(_.toString)

  def readExcelAbolsutePath(pathExpr: Expr[String], sheetName: Expr[String])(using Quotes) =
    import quotes.reflect.*

    val fPath = pathExpr.valueOrAbort
    val headers = ExcelIterator(pathExpr.valueOrAbort, sheetName.valueOrAbort).headers
    val tupleExpr2 = Expr.ofTupleFromSeq(headers.map(Expr(_)))
    tupleExpr2 match
      case '{ $tup: t } =>

        val itr = new ExcelIterator[t](fPath, sheetName.valueOrAbort)
        // println("tup")
        // println(tup)
        // '{ NamedTuple.build[t & Tuple]()($tup) }
        Expr(itr)
      case _ => report.throwError(s"Could not summon Type for type: ${tupleExpr2.show}")
    end match
  end readExcelAbolsutePath
end Excel

class ExcelIterator[K](filePath: String, sheetName: String) extends Iterator[NamedTuple[K & Tuple, StringyTuple[K & Tuple]]]:
  type COLUMNS = K

  def getFilePath: String = filePath
  def getSheet: String = sheetName

  lazy val sheetIterator =
    val workbook = WorkbookFactory.create(new File(filePath))
    val sheet = workbook.getSheet(sheetName)
    sheet.iterator().asScala
  end sheetIterator

  val headers = if sheetIterator.hasNext then sheetIterator.next().cellIterator().asScala.toList.map(_.toString) else List.empty

  val headerSet = scala.collection.mutable.Set[String]()
  headers.foreach { header =>
    if headerSet.contains(header) then throw new Excel.BadTableException(s"Duplicate header found: $header, which will not work. ")
    else headerSet.add(header)
  }

  lazy val headersTuple =
    listToTuple(headers)

  var debugi: Int = 0
  // var nextRow = sheetIterator.next().cellIterator().asScala.toList.map(_.toString)
  override def next(): NamedTuple[K & Tuple, StringyTuple[K & Tuple]] =
    if !hasNext then throw new NoSuchElementException("No more rows")
    end if
    val row = sheetIterator.next()
    val cells = row.cellIterator().asScala.toList.map(_.toString)
    if cells.size != headers.size then
      throw new Excel.BadTableException(s"Row $debugi has ${cells.size} cells, but the table has ${headers.size} cells. Reading data was terminated")
    end if
    val tuple = listToTuple(cells)
    debugi += 1
    NamedTuple.build[K & Tuple]()(tuple).asInstanceOf[StringyTuple[K & Tuple]]
  end next

  override def hasNext: Boolean = sheetIterator.hasNext
  
  // Add a method to get a specific column by name
  def getColumn(columnName: String): List[String] =
    val columnIndex = headers.indexOf(columnName)
    if columnIndex < 0 then 
      throw new Excel.BadTableException(s"Column not found: $columnName")
    else
      val result = scala.collection.mutable.ListBuffer[String]()
      val iterator = sheetIterator.drop(0) // We've already consumed the header row
      while (iterator.hasNext) {
        val row = iterator.next()
        val cells = row.cellIterator().asScala.toList.map(_.toString)
        if (cells.size == headers.size && columnIndex < cells.size) {
          result += cells(columnIndex)
        }
      }
      result.toList
  
  // Add a method to convert to a list of maps for easier data manipulation
  def toMaps: List[Map[String, String]] =
    val result = scala.collection.mutable.ListBuffer[Map[String, String]]()
    while (hasNext) {
      val row = next()
      val rowMap = headers.zip(row.productIterator.toList.map(_.toString)).toMap
      result += rowMap
    }
    result.toList

end ExcelIterator
