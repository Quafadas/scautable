package io.github.quafadas.scautable

import scala.quoted.*
import io.github.quafadas.scautable.ColumnTyped.*
import io.github.quafadas.table.TypeInferrer
import org.apache.poi.ss.usermodel.{Row, WorkbookFactory}
import org.apache.poi.ss.util.CellRangeAddress
import scala.collection.JavaConverters.*
import java.io.File
import io.github.quafadas.scautable.BadTableException
import io.github.quafadas.scautable.InferrerOps

/** Compile-time macro functions for reading Excel files These macros perform Excel file inspection at compile time to determine structure
  */
object ExcelMacros:

  /** ToExpr instance for ExcelIterator to support compile-time code generation
    */
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

  /** Macro implementation for reading Excel files from the classpath
    */
  def readExcelResource(pathExpr: Expr[String], sheetName: Expr[String], colRangeExpr: Expr[String], typeInferrerExpr: Expr[TypeInferrer])(using Quotes) =
    import quotes.reflect.*

    val path = pathExpr.valueOrAbort
    val resourcePath = this.getClass.getClassLoader.getResource(path)
    if resourcePath == null then report.throwError(s"Resource not found: $path")
    end if

    val validatedPath = resourcePath.toURI.getPath
    val colRange = colRangeExpr.value
    val sheetNameValue = sheetName.valueOrAbort

    processExcelFile(validatedPath, sheetNameValue, colRange, typeInferrerExpr, validatedPath)
  end readExcelResource

  /** Macro implementation for reading Excel files from an absolute path
    */
  def readExcelAbsolutePath(pathExpr: Expr[String], sheetName: Expr[String], colRangeExpr: Expr[String], typeInferrerExpr: Expr[TypeInferrer])(using Quotes) =
    import quotes.reflect.*

    val fPath = pathExpr.valueOrAbort
    val colRange = colRangeExpr.value
    val sheetNameValue = sheetName.valueOrAbort

    processExcelFile(fPath, sheetNameValue, colRange, typeInferrerExpr, fPath)
  end readExcelAbsolutePath

  /** Common processing logic for both resource and absolute path Excel reading
    */
  private def processExcelFile(filePath: String, sheetName: String, colRange: Option[String], typeInferrerExpr: Expr[TypeInferrer], outputPath: String)(using Quotes) =
    import quotes.reflect.*

    try
      // Extract headers at compile time
      val headers = extractHeaders(filePath, sheetName, colRange)

      // Validate headers at compile time
      validateUniqueHeaders(headers)

      val tupleExpr2 = Expr.ofTupleFromSeq(headers.map(Expr(_)))

      def constructWithStringTypes[Hdrs <: Tuple: Type]: Expr[ExcelIterator[Hdrs, StringyTuple[Hdrs]]] =
        '{
          new ExcelIterator[Hdrs, StringyTuple[Hdrs]](${ Expr(outputPath) }, ${ Expr(sheetName) }, ${ Expr(colRange) })
        }

      def constructWithTypes[Hdrs <: Tuple: Type, V <: Tuple: Type]: Expr[ExcelIterator[Hdrs, V]] =
        '{
          new ExcelIterator[Hdrs, V](${ Expr(outputPath) }, ${ Expr(sheetName) }, ${ Expr(colRange) })
        }

      tupleExpr2 match
        case '{ $tup: hdrs } =>
          typeInferrerExpr match
            case '{ TypeInferrer.FromTuple[t]() } =>
              constructWithTypes[hdrs & Tuple, t & Tuple]
            case '{ TypeInferrer.StringType } =>
              constructWithStringTypes[hdrs & Tuple]
            case '{ TypeInferrer.FirstRow } =>
              // FirstRow is equivalent to FirstN(1) 
              val inferredTypeRepr = inferTypesFromExcelData(filePath, sheetName, colRange, headers, 1, true)
              inferredTypeRepr.asType match
                case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
            case '{ TypeInferrer.FromAllRows } =>
              // FromAllRows is equivalent to FirstN(Int.MaxValue)
              val inferredTypeRepr = inferTypesFromExcelData(filePath, sheetName, colRange, headers, Int.MaxValue, true)
              inferredTypeRepr.asType match
                case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
            case '{ TypeInferrer.FirstN(${ Expr(n) }) } =>
              // FirstN with default preferIntToBoolean = true
              val inferredTypeRepr = inferTypesFromExcelData(filePath, sheetName, colRange, headers, n, true)
              inferredTypeRepr.asType match
                case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
            case '{ TypeInferrer.FirstN(${ Expr(n) }, ${ Expr(preferIntToBoolean) }) } =>
              // FirstN with custom preferIntToBoolean setting
              val inferredTypeRepr = inferTypesFromExcelData(filePath, sheetName, colRange, headers, n, preferIntToBoolean)
              inferredTypeRepr.asType match
                case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
            case _ =>
              report.throwError("Only TypeInferrer.StringType and TypeInferrer.FromTuple are currently supported for Excel")
        case _ =>
          report.throwError(s"Could not summon Type for type: ${tupleExpr2.show}")
      end match
    catch
      case ex: BadTableException =>
        report.throwError(ex.getMessage)
      case ex: Exception =>
        report.throwError(s"Error processing Excel file: ${ex.getMessage}")
    end try
  end processExcelFile

  /** Extracts headers from an Excel sheet, either from a specific range or the first row
    */
  private def extractHeaders(filePath: String, sheetName: String, colRange: Option[String]): List[String] =
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
    */
  private def validateUniqueHeaders(headers: List[String]): Unit =
    val headerSet = scala.collection.mutable.Set[String]()
    headers.foreach { header =>
      if headerSet.contains(header) then throw new BadTableException(s"Duplicate header found: $header, which will not work.")
      else headerSet.add(header)
    }
  end validateUniqueHeaders

  /** Helper function to perform type inference on Excel data and return the inferred TypeRepr
    */
  private def inferTypesFromExcelData(using Quotes)(
      filePath: String, 
      sheetName: String, 
      colRange: Option[String], 
      headers: List[String],
      numRows: Int, 
      preferIntToBoolean: Boolean
  ): quotes.reflect.TypeRepr =
    
    // Extract sample rows for type inference
    val sampleRows = extractSampleRows(filePath, sheetName, colRange, numRows, headers.length)
    
    // Convert rows to CSV-like format for the InferrerOps
    val csvRows = sampleRows.map(_.mkString(","))
    val rowsIterator = csvRows.iterator
    
    // Use InferrerOps to infer types
    InferrerOps.inferrer(rowsIterator, preferIntToBoolean, numRows)
  end inferTypesFromExcelData

  /** Extracts sample data rows from an Excel sheet for type inference
    */
  private def extractSampleRows(filePath: String, sheetName: String, colRange: Option[String], numRows: Int, numColumns: Int): List[List[String]] =
    val workbook = WorkbookFactory.create(new File(filePath))
    try
      val sheet = workbook.getSheet(sheetName)
      val sheetIterator = sheet.iterator().asScala
      
      // Skip header row
      if sheetIterator.hasNext then sheetIterator.next()
      
      val sampleRows = sheetIterator.take(numRows).toList
      
      colRange match
        case Some(range) if range.nonEmpty =>
          val cellRange = CellRangeAddress.valueOf(range)
          val firstCol = cellRange.getFirstColumn
          val lastCol = cellRange.getLastColumn
          sampleRows.map { row =>
            (firstCol to lastCol).map { i =>
              row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).toString
            }.toList
          }
        case _ =>
          sampleRows.map { row =>
            // Ensure we extract exactly numColumns to match headers
            (0 until numColumns).map { i =>
              row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).toString
            }.toList
          }
      end match
    finally workbook.close()
    end try
  end extractSampleRows

end ExcelMacros
