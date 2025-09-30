package io.github.quafadas.scautable

import scala.quoted.*
import io.github.quafadas.scautable.ColumnTyped.*
import io.github.quafadas.table.TypeInferrer
import org.apache.poi.ss.usermodel.{Row, WorkbookFactory, Cell, CellType, DateUtil}
import org.apache.poi.ss.util.CellRangeAddress
import scala.collection.JavaConverters.*
import java.io.File
import io.github.quafadas.scautable.BadTableException
import io.github.quafadas.scautable.InferrerOps

/** Compile-time macro functions for reading     val initial = ColumnTypeInfo()
    val finalInfo = cells.foldLeft(initial)(updateTypeInfo) files These macros perform Excel file inspection at compile time to determine structure
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
              val inferredTypeRepr = inferTypesFromExcelDataDirect(filePath, sheetName, colRange, headers, 1, true)
              inferredTypeRepr.asType match
                case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
              end match
            case '{ TypeInferrer.FromAllRows } =>
              // FromAllRows is equivalent to FirstN(Int.MaxValue)
              val inferredTypeRepr = inferTypesFromExcelDataDirect(filePath, sheetName, colRange, headers, Int.MaxValue, true)
              inferredTypeRepr.asType match
                case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
              end match
            case '{ TypeInferrer.FirstN(${ Expr(n) }) } =>
              // FirstN with default preferIntToBoolean = true
              val inferredTypeRepr = inferTypesFromExcelDataDirect(filePath, sheetName, colRange, headers, n, true)
              inferredTypeRepr.asType match
                case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
              end match
            case '{ TypeInferrer.FirstN(${ Expr(n) }, ${ Expr(preferIntToBoolean) }) } =>
              // FirstN with custom preferIntToBoolean setting
              val inferredTypeRepr = inferTypesFromExcelDataDirect(filePath, sheetName, colRange, headers, n, preferIntToBoolean)
              inferredTypeRepr.asType match
                case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
              end match
            case other =>
              report.throwError(s"TypeInferrer not found: ${other}")
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

  /** Extract sample rows from Excel using Apache POI CellType to directly determine Scala types and return TypeRepr.
    *
    * This method improves upon the original CSV-based approach by:
    *   1. Using Apache POI's CellType enum to directly determine the native Excel data types
    *   2. Handling dates, formulas, and other Excel-specific cell types correctly
    *   3. Avoiding the overhead of converting to CSV format and re-parsing
    *   4. Providing more accurate type inference based on actual cell content
    *
    * Cell type mapping follows Apache POI documentation:
    *   - CellType.STRING: String values, with additional parsing for Int/Long/Double/Boolean
    *   - CellType.NUMERIC: Double values, or dates (converted to String for consistency)
    *   - CellType.BOOLEAN: Boolean values
    *   - CellType.FORMULA: Evaluated to determine the result type
    *   - CellType.BLANK: Empty cells (contribute to Option wrapping)
    *
    * @param filePath
    *   Excel file path
    * @param sheetName
    *   Excel sheet name
    * @param colRange
    *   Optional cell range (e.g. "A1:C10")
    * @param headers
    *   List of column headers
    * @param numRows
    *   Number of rows to sample for type inference
    * @param preferIntToBoolean
    *   When true, prefer Int over Boolean for 0/1 values
    * @return
    *   TypeRepr representing the inferred tuple type for the Excel data
    */
  private def inferTypesFromExcelDataDirect(using
      Quotes
  )(
      filePath: String,
      sheetName: String,
      colRange: Option[String],
      headers: List[String],
      numRows: Int,
      preferIntToBoolean: Boolean
  ): quotes.reflect.TypeRepr =
    import quotes.reflect.*

    val workbook = WorkbookFactory.create(new File(filePath))
    try
      val sheet = workbook.getSheet(sheetName)

      // Extract data based on column range or use all columns
      val columnData: List[List[Cell]] = colRange match
        case Some(range) if range.nonEmpty =>
          val cellRange = CellRangeAddress.valueOf(range)
          val firstRow = cellRange.getFirstRow
          val lastRow = cellRange.getLastRow
          val firstCol = cellRange.getFirstColumn
          val lastCol = cellRange.getLastColumn
          
          // Read only the specific rows from the range (including headers)
          val targetRows = (firstRow to lastRow).map(sheet.getRow).filter(_ != null).toList
          
          targetRows.map { row =>
            (firstCol to lastCol).map { i =>
              row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
            }.toList
          }
        case _ =>
          val sheetIterator = sheet.iterator().asScala
          // Skip header row
          if sheetIterator.hasNext then sheetIterator.next()
          end if
          val sampleRows = sheetIterator.take(numRows).toList
          sampleRows.map { row =>
            // Ensure we extract exactly headers.length columns to match headers
            (0 until headers.length).map { i =>
              row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
            }.toList
          }

      // Transpose to get columns instead of rows
      val columns = columnData.transpose

      // Infer type for each column using Apache POI cell types
      // SKIP THE FIRST ROW (header row) for type inference
      val columnTypes: List[TypeRepr] = columns.map { columnCells =>
        // TODO Interaction with HeaderOptions
        val dataRows = columnCells.drop(1) // Skip header row
        inferColumnTypeFromCells(dataRows, preferIntToBoolean)
      }

      // Build tuple type from column types
      val tupleType: TypeRepr = columnTypes.foldRight(TypeRepr.of[EmptyTuple]) { (tpe, acc) =>
        TypeRepr.of[*:].appliedTo(List(tpe, acc))
      }

      tupleType
    finally 
      try 
        workbook.close()
      catch
        case _: Exception => 
          // Workbook close can fail for Excel files with corrupted drawings - this is expected
          println(s"Warning: Could not close workbook for file: $filePath")
      end try
  end inferTypesFromExcelDataDirect

  /** Infer the most appropriate Scala type for a column based on Apache POI cell types
    */
  private def inferColumnTypeFromCells(using Quotes)(cells: List[Cell], preferIntToBoolean: Boolean): quotes.reflect.TypeRepr =
    import quotes.reflect.*

    case class ColumnTypeInfo(
        couldBeInt: Boolean = true,
        couldBeLong: Boolean = true,
        couldBeDouble: Boolean = true,
        couldBeBoolean: Boolean = true,
        seenEmpty: Boolean = false
    )

    def updateTypeInfo(info: ColumnTypeInfo, cell: Cell): ColumnTypeInfo =
      cell.getCellType match
        case CellType.BLANK =>
          info.copy(seenEmpty = true)
        case CellType.STRING =>
          val str = cell.getStringCellValue
          if str.isEmpty then info.copy(seenEmpty = true)
          else
            info.copy(
              couldBeInt = info.couldBeInt && str.toIntOption.isDefined,
              couldBeLong = info.couldBeLong && str.toLongOption.isDefined,
              couldBeDouble = info.couldBeDouble && str.toDoubleOption.isDefined,
              couldBeBoolean = info.couldBeBoolean && (str.toBooleanOption.isDefined || str == "0" || str == "1")
            )
          end if
        case CellType.NUMERIC =>
          if DateUtil.isCellDateFormatted(cell) then
            // TODO: Dates are represented as strings for consistency with CSV behavior
            info.copy(
              couldBeInt = false,
              couldBeLong = false,
              couldBeDouble = false,
              couldBeBoolean = false
            )
          else
            val numericValue = cell.getNumericCellValue
            val isWholeNumber = numericValue == numericValue.toLong && !numericValue.isInfinite && !numericValue.isNaN
            info.copy(
              couldBeInt = info.couldBeInt && isWholeNumber && numericValue >= Int.MinValue && numericValue <= Int.MaxValue,
              couldBeLong = info.couldBeLong && isWholeNumber && numericValue >= Long.MinValue && numericValue <= Long.MaxValue,
              couldBeDouble = info.couldBeDouble && !numericValue.isInfinite && !numericValue.isNaN, // All finite numeric values can be Double
              // Be more conservative with Boolean inference for numeric cells - only if it's exactly 0 or 1
              couldBeBoolean = info.couldBeBoolean && isWholeNumber && (numericValue == 0.0 || numericValue == 1.0)
            )
        case CellType.BOOLEAN =>
          info.copy(
            couldBeInt = false,
            couldBeLong = false,
            couldBeDouble = false
          )
        case CellType.FORMULA =>
          // For formulas, evaluate the result and recurse
          try
            val evaluatedCell = cell.getSheet.getWorkbook.getCreationHelper.createFormulaEvaluator().evaluate(cell)
            if evaluatedCell != null then
              // Create a temporary cell with the evaluated value to determine type
              val tempCell = cell.getRow.createCell(cell.getColumnIndex + 1000, evaluatedCell.getCellType)
              evaluatedCell.getCellType match
                case CellType.NUMERIC => tempCell.setCellValue(evaluatedCell.getNumberValue)
                case CellType.STRING  => tempCell.setCellValue(evaluatedCell.getStringValue)
                case CellType.BOOLEAN => tempCell.setCellValue(evaluatedCell.getBooleanValue)
                case _                => // Keep current info for other types
              end match
              val result = updateTypeInfo(info, tempCell)
              cell.getRow.removeCell(tempCell) // Clean up
              result
            else info
            end if
          catch case _ => info // If formula evaluation fails, keep current info
        case _ =>
          // For other cell types (ERROR, etc.), treat as string
          info.copy(
            couldBeInt = false,
            couldBeLong = false,
            couldBeDouble = false,
            couldBeBoolean = false
          )
    end updateTypeInfo

    val initial = ColumnTypeInfo()
    val finalInfo = cells.foldLeft(initial)(updateTypeInfo)

    // // Debug output to understand type inference
    // println(s"DEBUG inferColumnTypeFromCells: ${cells.length} cells")
    // cells.take(3).foreach { cell =>
    //   println(s"  Cell type: ${cell.getCellType}, value: '${cell.toString}'")
    // }
    // println(s"  Final: couldBeInt=${finalInfo.couldBeInt}, couldBeDouble=${finalInfo.couldBeDouble}, couldBeBoolean=${finalInfo.couldBeBoolean}, seenEmpty=${finalInfo.seenEmpty}")

    // Determine the most appropriate type based on what the column could be
    val baseType = 
      // If we have no cells, default to String (safest option)
      if cells.isEmpty then TypeRepr.of[String]
      else if preferIntToBoolean then
        if finalInfo.couldBeInt then TypeRepr.of[Int]
        else if finalInfo.couldBeBoolean then TypeRepr.of[Boolean]
        else if finalInfo.couldBeLong then TypeRepr.of[Long]
        else if finalInfo.couldBeDouble then TypeRepr.of[Double]
        else TypeRepr.of[String]
      else
        // When preferIntToBoolean=false, be more conservative with Boolean inference
        // Only infer Boolean if we can't be Int/Long/Double, to avoid mismatched data types
        if finalInfo.couldBeBoolean && !finalInfo.couldBeInt && !finalInfo.couldBeLong && !finalInfo.couldBeDouble then TypeRepr.of[Boolean]
        else if finalInfo.couldBeInt then TypeRepr.of[Int]
        else if finalInfo.couldBeLong then TypeRepr.of[Long]
        else if finalInfo.couldBeDouble then TypeRepr.of[Double]
        else TypeRepr.of[String]

    // Wrap in Option if we've seen empty cells
    if finalInfo.seenEmpty then TypeRepr.of[Option].appliedTo(baseType) else baseType
    end if
  end inferColumnTypeFromCells
end ExcelMacros
