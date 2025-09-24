package io.github.quafadas.scautable
import java.io.File

import scala.NamedTuple.*
import scala.collection.JavaConverters.*
import scala.quoted.*

import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.util.CellRangeAddress

import io.github.quafadas.scautable.ColumnTyped.*
import io.github.quafadas.table.TypeInferrer

// Excel-specific decoders that can handle numeric strings like "1.0"
// These need to be in scope when RowDecoder.summonAllDecoders is called
inline given excelIntDecoder: Decoder[Int] with
  def decode(str: String): Option[Int] =
    str.toDoubleOption.flatMap { d =>
      if d.isWhole && d >= Int.MinValue && d <= Int.MaxValue then
        Some(d.toInt)
      else
        None
    }.orElse(str.toIntOption) // fallback to regular int parsing

inline given excelLongDecoder: Decoder[Long] with  
  def decode(str: String): Option[Long] =
    str.toDoubleOption.flatMap { d =>
      if d.isWhole && d >= Long.MinValue && d <= Long.MaxValue then
        Some(d.toLong)
      else
        None
    }.orElse(str.toLongOption) // fallback to regular long parsing

/** */
object Excel:

  class BadTableException(message: String) extends Exception(message)

  given IteratorToExpr2[K <: Tuple, V <: Tuple](using ToExpr[String], Type[K], Type[V]): ToExpr[ExcelIterator[K, V]] with
    def apply(opt: ExcelIterator[K, V])(using Quotes): Expr[ExcelIterator[K, V]] =
      val str = Expr(opt.getFilePath)
      val sheet = Expr(opt.getSheet)
      val colRange = Expr(opt.getColRange)
      '{
        new ExcelIterator[K, V]($str, $sheet, $colRange)
      }
    end apply
  end IteratorToExpr2

  transparent inline def absolutePath[K](filePath: String, sheetName: String, range: String = "", inline typeInferrer: TypeInferrer = TypeInferrer.StringType) = ${ readExcelAbolsutePath('filePath, 'sheetName, 'range, 'typeInferrer) }
  transparent inline def resource[K](filePath: String, sheetName: String, range: String = "", inline typeInferrer: TypeInferrer = TypeInferrer.StringType) = ${ readExcelResource('filePath, 'sheetName, 'range, 'typeInferrer) }

  def readExcelResource(pathExpr: Expr[String], sheetName: Expr[String], colRangeExpr: Expr[String], typeInferrerExpr: Expr[TypeInferrer])(using Quotes) =
    import quotes.reflect.*

    val path = pathExpr.valueOrAbort
    val resourcePath = this.getClass.getClassLoader.getResource(path)
    if resourcePath == null then report.throwError(s"Resource not found: $path")
    end if
    val validatedPath = resourcePath.toURI.getPath
    val colRange = colRangeExpr.value
    
    // Create a simple ExcelIterator just to get the headers at compile time
    val tempFile = new java.io.File(validatedPath)
    val workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(tempFile)
    val sheet = workbook.getSheet(sheetName.valueOrAbort)
    val headers = colRange match
      case Some(range) if range.nonEmpty =>
        val cellRange = org.apache.poi.ss.util.CellRangeAddress.valueOf(range)
        val firstRow = sheet.getRow(cellRange.getFirstRow)
        val cells = for (i <- cellRange.getFirstColumn to cellRange.getLastColumn) 
          yield firstRow.getCell(i, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).toString
        cells.toList
      case _ =>
        if sheet.iterator().hasNext then 
          sheet.iterator().next().cellIterator().asScala.toList.map(_.toString)
        else 
          throw new Excel.BadTableException("No headers found in the first row of the sheet, and no range specified.")
    workbook.close()
    
    // Check for duplicate headers at compile time
    val headerSet = scala.collection.mutable.Set[String]()
    headers.foreach { header =>
      if headerSet.contains(header) then report.throwError(s"Duplicate header found: $header, which will not work. ")
      else headerSet.add(header)
    }
    
    val tupleExpr2 = Expr.ofTupleFromSeq(headers.map(Expr(_)))
    
    def constructWithStringTypes[Hdrs <: Tuple : Type]: Expr[ExcelIterator[Hdrs, StringyTuple[Hdrs]]] =
      '{
        new ExcelIterator[Hdrs, StringyTuple[Hdrs]](${ Expr(validatedPath) }, $sheetName, ${ Expr(colRange) })
      }
    
    def constructWithTypes[Hdrs <: Tuple : Type, V <: Tuple : Type]: Expr[ExcelIterator[Hdrs, V]] =
      '{
        new ExcelIterator[Hdrs, V](${ Expr(validatedPath) }, $sheetName, ${ Expr(colRange) })
      }
    
    tupleExpr2 match
      case '{ $tup: hdrs } =>
        typeInferrerExpr match
          case '{ TypeInferrer.FromTuple[t]() } =>
            constructWithTypes[hdrs & Tuple, t & Tuple]
          case '{ TypeInferrer.StringType } =>
            constructWithStringTypes[hdrs & Tuple]
          case '{ TypeInferrer.FirstRow } => 
            report.throwError("TypeInferrer.FirstRow is not yet supported for Excel. Only StringType and FromTuple are currently supported.")
          case '{ TypeInferrer.FromAllRows } => 
            report.throwError("TypeInferrer.FromAllRows is not yet supported for Excel. Only StringType and FromTuple are currently supported.")
          case '{ TypeInferrer.FirstN(${ Expr(n) }) } => 
            report.throwError(s"TypeInferrer.FirstN($n) is not yet supported for Excel. Only StringType and FromTuple are currently supported.")
          case _ => 
            report.throwError("Only TypeInferrer.StringType and TypeInferrer.FromTuple are currently supported for Excel")
      case _ => report.throwError(s"Could not summon Type for type: ${tupleExpr2.show}")
    end match
  end readExcelResource

  def readExcelAbolsutePath(pathExpr: Expr[String], sheetName: Expr[String], colRangeExpr: Expr[String], typeInferrerExpr: Expr[TypeInferrer])(using Quotes) =
    import quotes.reflect.*

    val fPath = pathExpr.valueOrAbort
    val colRange = colRangeExpr.value
    
    // Create a simple ExcelIterator just to get the headers at compile time
    val tempFile = new java.io.File(fPath)
    val workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(tempFile)
    val sheet = workbook.getSheet(sheetName.valueOrAbort)
    val headers = colRange match
      case Some(range) if range.nonEmpty =>
        val cellRange = org.apache.poi.ss.util.CellRangeAddress.valueOf(range)
        val firstRow = sheet.getRow(cellRange.getFirstRow)
        val cells = for (i <- cellRange.getFirstColumn to cellRange.getLastColumn) 
          yield firstRow.getCell(i, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).toString
        cells.toList
      case _ =>
        if sheet.iterator().hasNext then 
          sheet.iterator().next().cellIterator().asScala.toList.map(_.toString)
        else 
          throw new Excel.BadTableException("No headers found in the first row of the sheet, and no range specified.")
    workbook.close()
    
    // Check for duplicate headers at compile time
    val headerSet = scala.collection.mutable.Set[String]()
    headers.foreach { header =>
      if headerSet.contains(header) then report.throwError(s"Duplicate header found: $header, which will not work. ")
      else headerSet.add(header)
    }
    
    val tupleExpr2 = Expr.ofTupleFromSeq(headers.map(Expr(_)))

    def constructWithStringTypes[Hdrs <: Tuple : Type]: Expr[ExcelIterator[Hdrs, StringyTuple[Hdrs]]] =
      '{
        new ExcelIterator[Hdrs, StringyTuple[Hdrs]](${ Expr(fPath) }, $sheetName, ${ Expr(colRange) })
      }

    def constructWithTypes[Hdrs <: Tuple : Type, V <: Tuple : Type]: Expr[ExcelIterator[Hdrs, V]] =
      '{
        new ExcelIterator[Hdrs, V](${ Expr(fPath) }, $sheetName, ${ Expr(colRange) })
      }

    tupleExpr2 match
      case '{ $tup: hdrs } =>
        typeInferrerExpr match
          case '{ TypeInferrer.FromTuple[t]() } =>
            constructWithTypes[hdrs & Tuple, t & Tuple]
          case '{ TypeInferrer.StringType } =>
            constructWithStringTypes[hdrs & Tuple]
          case '{ TypeInferrer.FirstRow } => 
            report.throwError("TypeInferrer.FirstRow is not yet supported for Excel. Only StringType and FromTuple are currently supported.")
          case '{ TypeInferrer.FromAllRows } => 
            report.throwError("TypeInferrer.FromAllRows is not yet supported for Excel. Only StringType and FromTuple are currently supported.")
          case '{ TypeInferrer.FirstN(${ Expr(n) }) } => 
            report.throwError(s"TypeInferrer.FirstN($n) is not yet supported for Excel. Only StringType and FromTuple are currently supported.")
          case _ => 
            report.throwError("Only TypeInferrer.StringType and TypeInferrer.FromTuple are currently supported for Excel")
      case _ => report.throwError(s"Could not summon Type for type: ${tupleExpr2.show}")
    end match
  end readExcelAbolsutePath
end Excel

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
    case Some(range) if range.nonEmpty =>
      val (firstRow, lastRow, firstCol, lastCol) = getRanges(range)
      firstRow
    case _ => 0

  val headers: List[String] =
    colRange match
      case Some(range) if range.nonEmpty =>
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
      case _ =>
        if sheetIterator.hasNext then sheetIterator.next().cellIterator().asScala.toList.map(_.toString)
        else throw new Excel.BadTableException("No headers found in the first row of the sheet, and no range specified.")

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
      case Some(range) if range.nonEmpty =>
        val (firstRow, lastRow, firstCol, lastCol) = getRanges(range)
        // println(s"Row $debugi: $firstRow, $lastRow, $firstCol, $lastCol")
        val cells = for (i <- firstCol to lastCol) yield row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).toString
        cells.toList
      case _ =>
        cells.toList.map(_.toString)

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
      case Some(value) if value.nonEmpty =>
        val (firstRow, lastRow, firstCol, lastCol) = getRanges(value)
        debugi < lastRow
      case _ => sheetIterator.hasNext

end ExcelIterator
