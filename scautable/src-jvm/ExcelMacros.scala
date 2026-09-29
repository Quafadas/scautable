package io.github.quafadas.scautable

import scala.collection.JavaConverters.*
import scala.quoted.*

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.util.CellRangeAddress

import io.github.quafadas.scautable.BadTableException
import io.github.quafadas.scautable.ColumnTyped.*
import io.github.quafadas.scautable.ExcelWorkbookCache
import io.github.quafadas.table.TypeInferrer

/** Compile-time macro functions for reading val initial = ColumnTypeInfo() val finalInfo = cells.foldLeft(initial)(updateTypeInfo) files These macros perform Excel file inspection
  * at compile time to determine structure
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

    val fPath = pathExpr.valueOrAbort
    val colRange = colRangeExpr.value
    val sheetNameValue = sheetName.valueOrAbort

    processExcelFile(fPath, sheetNameValue, colRange, typeInferrerExpr, fPath)
  end readExcelAbsolutePath

  /** Macro implementation for reading Excel files at a path relative to the calling source file
    */
  def readExcelRelativeToSource(pathExpr: Expr[String], sheetName: Expr[String], colRangeExpr: Expr[String], typeInferrerExpr: Expr[TypeInferrer])(using Quotes) =
    val fPath = SourceAnchor.relativeToSource(pathExpr.valueOrAbort).absolutePath.toString
    processExcelFile(fPath, sheetName.valueOrAbort, colRangeExpr.value, typeInferrerExpr, fPath)
  end readExcelRelativeToSource

  /** Macro implementation for reading Excel files at a path relative to the discovered project root
    */
  def readExcelProjectRoot(pathExpr: Expr[String], sheetName: Expr[String], colRangeExpr: Expr[String], typeInferrerExpr: Expr[TypeInferrer])(using Quotes) =
    val fPath = SourceAnchor.projectRoot(pathExpr.valueOrAbort).absolutePath.toString
    processExcelFile(fPath, sheetName.valueOrAbort, colRangeExpr.value, typeInferrerExpr, fPath)
  end readExcelProjectRoot

  /** Pulls the three fields out of an `ExcelOpts` expression at compile time.
    *
    * Handles the shapes the compiler can produce for it:
    *   1. The full 3-argument case class constructor, matched by a quoted pattern
    *   2. `ExcelOpts.default` and the single argument companion `apply`
    *   3. Named and/or defaulted arguments, e.g. `ExcelOpts(range = "A5", skipFooter = 1)`, which the compiler wraps in a `Block` of default-value `ValDef`s followed by a 3-arg
    *      `Apply`. That needs a term level walk, identifying each argument by its name and falling back to its type.
    *
    * Mirrors `CSV.extractCsvOptsField`, but returns all three fields at once rather than re-walking the term per field.
    */
  private def extractExcelOpts(optsExpr: Expr[ExcelOpts])(using Quotes): (Expr[String], Expr[TypeInferrer], Expr[Int]) =
    import quotes.reflect.*

    val defaultRange: Expr[String] = '{ "" }
    val defaultInferrer: Expr[TypeInferrer] = '{ TypeInferrer.FromAllRows }
    val defaultSkip: Expr[Int] = '{ 0 }

    optsExpr match
      case '{ ExcelOpts($r, $t, $s) }             => (r, t, s)
      case '{ new ExcelOpts($r, $t, $s) }         => (r, t, s)
      case '{ ExcelOpts.default }                 => (defaultRange, defaultInferrer, defaultSkip)
      case '{ ExcelOpts.apply($t: TypeInferrer) } => (defaultRange, t, defaultSkip)
      case _                                      =>
        def unwrapInlined(term: Term): Term = term match
          case Inlined(_, _, body) => unwrapInlined(body)
          case other               => other

        val rawTerm = unwrapInlined(optsExpr.asTerm)

        // Defaulted arguments arrive as Idents pointing at ValDefs the compiler lifted out ahead of the call.
        val (bindings, innerTerm) = rawTerm match
          case Block(stats, expr) =>
            (stats.collect { case vd: ValDef => (vd.name, vd.rhs) }.toMap, unwrapInlined(expr))
          case other => (Map.empty[String, Option[Term]], other)

        def resolveDefault(arg: Term): Term =
          val unwrapped = arg match
            case NamedArg(_, value) => value
            case other              => other
          unwrapInlined(unwrapped) match
            case Ident(name) if bindings.contains(name) =>
              bindings(name).map(rhs => resolveDefault(unwrapInlined(rhs))).getOrElse(unwrapped)
            case other => other
          end match
        end resolveDefault

        def isDefaultRef(t: Term): Boolean = unwrapInlined(t) match
          case Select(_, name) if name.contains("$default$") => true
          case _                                             => false

        innerTerm match
          case Apply(_, args) =>
            var rExpr = defaultRange
            var tExpr = defaultInferrer
            var sExpr = defaultSkip

            for arg <- args do
              val (name, rawValue) = arg match
                case NamedArg(n, v) => (Some(n), v)
                case v              => (None, v)

              val resolved = resolveDefault(rawValue)

              if isDefaultRef(resolved) then () // leave the default in place
              else
                name match
                  case Some("range")        => rExpr = resolved.asExprOf[String]
                  case Some("typeInferrer") => tExpr = resolved.asExprOf[TypeInferrer]
                  case Some("skipFooter")   => sExpr = resolved.asExprOf[Int]
                  case _                    =>
                    if resolved.tpe <:< TypeRepr.of[TypeInferrer] then tExpr = resolved.asExprOf[TypeInferrer]
                    else if resolved.tpe <:< TypeRepr.of[String] then rExpr = resolved.asExprOf[String]
                    else if resolved.tpe <:< TypeRepr.of[Int] then sExpr = resolved.asExprOf[Int]
              end if
            end for

            (rExpr, tExpr, sExpr)
          case _ =>
            report.throwError(s"Could not read the ExcelOpts given here: ${optsExpr.show}")
        end match
    end match
  end extractExcelOpts

  /** `skipFooter` is only meaningful as a compile time constant, because it changes the range the types are inferred from. */
  private def skipFooterValue(skipExpr: Expr[Int])(using Quotes): Int =
    import quotes.reflect.*
    skipExpr.value.getOrElse(
      report.throwError(s"skipFooter must be a compile time constant, but was ${skipExpr.show}.")
    )
  end skipFooterValue

  /** Macro implementation for reading Excel files from the classpath, configured with an `ExcelOpts` */
  def readExcelResourceOpts(pathExpr: Expr[String], sheetName: Expr[String], optsExpr: Expr[ExcelOpts])(using Quotes) =
    import quotes.reflect.*
    val (rangeExpr, inferrerExpr, skipExpr) = extractExcelOpts(optsExpr)
    val path = pathExpr.valueOrAbort
    val resourcePath = this.getClass.getClassLoader.getResource(path)
    if resourcePath == null then report.throwError(s"Resource not found: $path")
    end if
    val validatedPath = resourcePath.toURI.getPath
    processExcelFile(validatedPath, sheetName.valueOrAbort, rangeExpr.value, inferrerExpr, validatedPath, skipFooterValue(skipExpr))
  end readExcelResourceOpts

  /** Macro implementation for reading Excel files from an absolute path, configured with an `ExcelOpts` */
  def readExcelAbsolutePathOpts(pathExpr: Expr[String], sheetName: Expr[String], optsExpr: Expr[ExcelOpts])(using Quotes) =
    val (rangeExpr, inferrerExpr, skipExpr) = extractExcelOpts(optsExpr)
    val fPath = pathExpr.valueOrAbort
    processExcelFile(fPath, sheetName.valueOrAbort, rangeExpr.value, inferrerExpr, fPath, skipFooterValue(skipExpr))
  end readExcelAbsolutePathOpts

  /** Macro implementation for reading Excel files relative to the calling source file, configured with an `ExcelOpts` */
  def readExcelRelativeToSourceOpts(pathExpr: Expr[String], sheetName: Expr[String], optsExpr: Expr[ExcelOpts])(using Quotes) =
    val (rangeExpr, inferrerExpr, skipExpr) = extractExcelOpts(optsExpr)
    val fPath = SourceAnchor.relativeToSource(pathExpr.valueOrAbort).absolutePath.toString
    processExcelFile(fPath, sheetName.valueOrAbort, rangeExpr.value, inferrerExpr, fPath, skipFooterValue(skipExpr))
  end readExcelRelativeToSourceOpts

  /** Macro implementation for reading Excel files relative to the project root, configured with an `ExcelOpts` */
  def readExcelProjectRootOpts(pathExpr: Expr[String], sheetName: Expr[String], optsExpr: Expr[ExcelOpts])(using Quotes) =
    val (rangeExpr, inferrerExpr, skipExpr) = extractExcelOpts(optsExpr)
    val fPath = SourceAnchor.projectRoot(pathExpr.valueOrAbort).absolutePath.toString
    processExcelFile(fPath, sheetName.valueOrAbort, rangeExpr.value, inferrerExpr, fPath, skipFooterValue(skipExpr))
  end readExcelProjectRootOpts

  /** Common processing logic for both resource and absolute path Excel reading
    */
  private def processExcelFile(filePath: String, sheetName: String, rawColRange: Option[String], typeInferrerExpr: Expr[TypeInferrer], outputPath: String, skipFooter: Int = 0)(
      using Quotes
  ) =
    import quotes.reflect.*

    try
      // Resolve any open ended range (e.g. "B5") to a fully specified one (e.g. "B5:Q55") here, at compile time,
      // so that the generated iterator never has to discover the table's extent itself.
      val colRange = resolveRange(filePath, sheetName, rawColRange, skipFooter)

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

  /** Resolves a range specification against the sheet, filling in any edge the caller left open.
    *
    * A fully specified range (e.g. "B5:Q55") is returned untouched. An anchor with open ends (e.g. "B5", "B5:Q" or "B5:55") has its missing edges discovered by walking the sheet,
    * the way `ctrl-right` and `ctrl-down` walk it in Excel.
    */
  private def resolveRange(filePath: String, sheetName: String, colRange: Option[String], skipFooter: Int)(using Quotes): Option[String] =
    import quotes.reflect.*
    val spec0 = colRange.map(_.trim).filter(_.nonEmpty)
    if skipFooter != 0 && spec0.isEmpty then
      report.throwError(
        "skipFooter needs a range to trim - give one, or an anchor such as \"A1\" for the compiler to resolve. Without a range the sheet has no bottom edge to count back from."
      )
    end if
    spec0.map { spec =>
      val workbook = ExcelWorkbookCache
        .getOrCreate(filePath)
        .getOrElse(
          throw new BadTableException(s"Failed to open Excel file: $filePath")
        )
      val sheet = Option(workbook.getSheet(sheetName)).getOrElse(
        throw new BadTableException(s"Sheet not found: $sheetName in $filePath")
      )
      val resolved = ExcelRange.resolveDetailed(sheet, spec).dropFooterRows(skipFooter)
      if (resolved.wasInferred || resolved.footerRowsSkipped > 0) && rangeDiagnosticEnabled then report.info(s"scautable: ${resolved.describe}")
      end if
      resolved.range
    }
  end resolveRange

  /** An open ended range is resolved silently, at compile time, so a table whose extent was inferred wrongly is invisible at the call site - the only symptom is a column type that
    * is more pessimistic than the data deserves. Set `SCAUTABLE_RANGE=1` in the environment, or the `scautable.range` system property on the compiler, to have every anchored range
    * report what it actually resolved to, and how.
    *
    * The environment variable is the one to reach for from a shell, e.g. `SCAUTABLE_RANGE=1 ./mill scautable.jvm.compile`; the system property suits a build that wants it on
    * permanently. `-Xmacro-settings` would be the idiomatic channel, but reading it requires `@experimental`, which would spread to everything that calls these macros.
    *
    * Reported with `report.info` rather than `report.warning`, because an anchored range is a supported way to ask, not a smell, and a diagnostic should never be able to fail a
    * build under `-Xfatal-warnings`.
    */
  private def rangeDiagnosticEnabled: Boolean =
    def isOn(v: String) = v.nonEmpty && !v.equalsIgnoreCase("false") && v != "0"
    sys.env.get("SCAUTABLE_RANGE").exists(isOn) || sys.props.get("scautable.range").exists(isOn)
  end rangeDiagnosticEnabled

  /** Extracts headers from an Excel sheet, either from a specific range or the first row
    */
  private def extractHeaders(filePath: String, sheetName: String, colRange: Option[String]): List[String] =
    val workbook = ExcelWorkbookCache
      .getOrCreate(filePath)
      .getOrElse(
        throw new BadTableException(s"Failed to open Excel file: $filePath")
      )
    val sheet = workbook.getSheet(sheetName)

    colRange match
      case Some(range) if range.nonEmpty =>
        val cellRange = CellRangeAddress.valueOf(range)
        val firstRow = sheet.getRow(cellRange.getFirstRow)
        val cells =
          for i <- cellRange.getFirstColumn to cellRange.getLastColumn
          yield firstRow.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).toString
        cells.toList
      case _ =>
        if sheet.iterator().hasNext then sheet.iterator().next().cellIterator().asScala.toList.map(_.toString)
        else throw new BadTableException("No headers found in the first row of the sheet, and no range specified.")
    end match
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

    val workbook = ExcelWorkbookCache
      .getOrCreate(filePath)
      .getOrElse(
        throw new BadTableException(s"Failed to open Excel file: $filePath")
      )
    val sheet = workbook.getSheet(sheetName)

    // Extract data based on column range or use all columns
    val columnData: List[List[Cell]] = colRange match
      case Some(range) if range.nonEmpty =>
        val cellRange = CellRangeAddress.valueOf(range)
        val firstRow = cellRange.getFirstRow
        val lastRow = cellRange.getLastRow
        val firstCol = cellRange.getFirstColumn
        val lastCol = cellRange.getLastColumn

        // Read the header row plus at most `numRows` data rows from the range. Lazy, so that a
        // small `FirstN` on a large table does not walk the whole thing, and `+ 1` for the header
        // row that is dropped further down. Capped rather than added to, because `FromAllRows`
        // arrives here as `Int.MaxValue` and would otherwise overflow.
        val rowLimit = math.min(numRows.toLong + 1L, Int.MaxValue.toLong).toInt
        val targetRows = (firstRow to lastRow).iterator.map(sheet.getRow).filter(_ != null).take(rowLimit).toList

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
