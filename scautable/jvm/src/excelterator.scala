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

case class ConversionAcc(validInts: Long, validDoubles: Long, validLongs: Long) {
  def recommendType(totalRows: Long): String = {
    val intPercentage = validInts.toDouble / totalRows
    val doublePercentage = validDoubles.toDouble / totalRows
    val longPercentage = validLongs.toDouble / totalRows
    
    if (intPercentage >= 0.95) "Int"
    else if (longPercentage >= 0.95) "Long"
    else if (doublePercentage >= 0.95) "Double"
    else "String"
  }
}

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
    
  // Analyze Excel file and infer column types
  def analyzeTypes(filePath: String, sheetName: String, sampleRows: Int = 100): Map[String, String] = {
    val excel = openExcel(filePath, sheetName)
    val headers = excel.headers
    val totalRows = math.min(sampleRows, excel.countRows())
    
    if (totalRows == 0) return headers.map(_ -> "String").toMap
    
    // Initialize accumulators for each column
    val accumulators = headers.map(_ => ConversionAcc(0, 0, 0)).toArray
    
    // Sample rows for type analysis
    var rowCount = 0
    while (excel.hasNext && rowCount < sampleRows) {
      val row = excel.next()
      // Extract values from the NamedTuple using toTuple instead of productIterator
      val values = row.toTuple.toList.map(_.toString)
      
      // Update accumulators for each column
      for (i <- headers.indices) {
        if (i < values.size) {
          val value = values(i)
          accumulators(i) = ConversionAcc(
            accumulators(i).validInts + value.toIntOption.fold(0)(_ => 1),
            accumulators(i).validDoubles + value.toDoubleOption.fold(0)(_ => 1),
            accumulators(i).validLongs + value.toLongOption.fold(0)(_ => 1)
          )
        }
      }
      rowCount += 1
    }
    
    // Generate type recommendations
    headers.zip(accumulators.map(_.recommendType(rowCount))).toMap
  }
  
  // Generate case class code based on inferred types
  def generateCaseClass(filePath: String, sheetName: String, className: String, sampleRows: Int = 100): String = {
    val typeMap = analyzeTypes(filePath, sheetName, sampleRows)
    
    val fields = typeMap.map { case (header, typeName) =>
      val fieldName = header.replaceAll("\\s+", "_").replaceAll("[^a-zA-Z0-9_]", "").toLowerCase
      s"  $fieldName: $typeName"
    }.mkString(",\n")
    
    s"""case class $className(
$fields
)"""
  }

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
  
  // Convert to a list of maps for easier data manipulation
  def toMapsList: List[Map[String, String]] =
    val result = scala.collection.mutable.ListBuffer[Map[String, String]]()
    while (hasNext) {
      val row = next()
      // Extract values from the NamedTuple using pattern matching instead of productIterator
      val values = row.toTuple.toList.map(_.toString)
      val rowMap = headers.zip(values).toMap
      result += rowMap
    }
    result.toList
    
  // Count total rows in the Excel sheet
  def countRows(): Int = {
    var count = 0
    val workbook = WorkbookFactory.create(new File(filePath))
    val sheet = workbook.getSheet(sheetName)
    val rowIterator = sheet.iterator()
    while (rowIterator.hasNext) {
      rowIterator.next()
      count += 1
    }
    workbook.close()
    count - 1 // Subtract header row
  }
  
  // Analyze types in this Excel file
  def analyzeTypes(sampleRows: Int = 100): Map[String, String] = {
    Excel.analyzeTypes(filePath, sheetName, sampleRows)
  }
  
  // Generate case class from this Excel file
  def generateCaseClass(className: String, sampleRows: Int = 100): String = {
    Excel.generateCaseClass(filePath, sheetName, className, sampleRows)
  }

end ExcelIterator
