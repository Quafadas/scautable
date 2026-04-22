package io.github.quafadas.scautable

import scala.NamedTuple.*
import scala.io.Source
import scala.quoted.*

import io.github.quafadas.scautable.ColumnTyped.*
import io.github.quafadas.table.HeaderOptions
import io.github.quafadas.scautable.HeaderOptionsProcessing.headers
import io.github.quafadas.table.TypeInferrer
import io.github.quafadas.table.ReadAs

object CSV:

  /** Saves a URL to a local CSV returns a [[io.github.quafadas.scautable.CsvIterator]].
    *
    * Example:
    * {{{
    *   val csv: CsvIterator[("colA", "colB", "colC")] = CSV.url("https://somewhere.com/file.csv")
    * }}}
    */
  transparent inline def url[T](inline csvContent: String): Any = url[T](csvContent, CsvOpts.default)

  transparent inline def url[T](inline csvContent: String, inline headers: HeaderOptions): Any = url[T](csvContent, CsvOpts(headers))

  transparent inline def url[T](inline csvContent: String, inline dataType: TypeInferrer): Any = url[T](csvContent, CsvOpts.apply(dataType))

  transparent inline def url[T](inline path: String, inline headers: HeaderOptions, inline dataType: TypeInferrer): Any = url[T](path, CsvOpts(headers, dataType))

  transparent inline def url[T](inline path: String, inline opts: CsvOpts) = ${ readCsvFromUrl('path, 'opts) }

  /** Reads a CSV present in the current _compiler_ working directory resources and returns a [[io.github.quafadas.scautable.CsvIterator]].
    *
    * Note that in most cases, this is _not_ the same as the current _runtime_ working directory, and you are likely to get the bloop server directory.
    *
    * Hopefully, useful in almond notebooks.
    *
    * Example:
    * {{{
    *   val csv: CsvIterator[("colA", "colB", "colC")] = CSV.pwd("file.csv")
    * }}}
    */
  transparent inline def pwd[T](inline csvContent: String): Any = pwd[T](csvContent, CsvOpts.default)

  transparent inline def pwd[T](inline csvContent: String, inline headers: HeaderOptions): Any = pwd[T](csvContent, CsvOpts(headers))

  transparent inline def pwd[T](inline csvContent: String, inline dataType: TypeInferrer): Any = pwd[T](csvContent, CsvOpts.apply(dataType))

  transparent inline def pwd[T](inline path: String, inline headers: HeaderOptions, inline dataType: TypeInferrer): Any = pwd[T](path, CsvOpts(headers, dataType))

  transparent inline def pwd[T](inline path: String, inline opts: CsvOpts) = ${ readCsvFromCurrentDir('path, 'opts) }

  /** Reads a CSV present in java resources and returns a [[io.github.quafadas.scautable.CsvIterator]].
    *
    * Example:
    * {{{
    *   val csv: CsvIterator[("colA", "colB", "colC")] = CSV.resource("file.csv")
    * }}}
    */
  transparent inline def resource[T](inline csvContent: String): Any = resource[T](csvContent, CsvOpts.default)

  transparent inline def resource[T](inline csvContent: String, inline headers: HeaderOptions): Any = resource[T](csvContent, CsvOpts(headers))

  transparent inline def resource[T](inline csvContent: String, inline dataType: TypeInferrer): Any = resource[T](csvContent, CsvOpts.apply(dataType))

  transparent inline def resource[T](inline path: String, inline headers: HeaderOptions, inline dataType: TypeInferrer): Any = resource[T](path, CsvOpts(headers, dataType))

  transparent inline def resource[T](inline path: String, inline opts: CsvOpts) = ${ readCsvResource('path, 'opts) }

  /** Reads a CSV file from an absolute path and returns a [[io.github.quafadas.scautable.CsvIterator]].
    *
    * Example:
    * {{{
    *   val csv: CsvIterator[("colA", "colB", "colC")] = CSV.absolutePath("/absolute/path/to/file.csv")
    * }}}
    */
  transparent inline def absolutePath[T](inline csvContent: String): Any = absolutePath[T](csvContent, CsvOpts.default)

  transparent inline def absolutePath[T](inline csvContent: String, inline headers: HeaderOptions): Any = absolutePath[T](csvContent, CsvOpts(headers))

  transparent inline def absolutePath[T](inline csvContent: String, inline dataType: TypeInferrer): Any = absolutePath[T](csvContent, CsvOpts.apply(dataType))

  transparent inline def absolutePath[T](inline path: String, inline headers: HeaderOptions, inline dataType: TypeInferrer): Any = absolutePath[T](path, CsvOpts(headers, dataType))

  transparent inline def absolutePath[T](inline path: String, inline opts: CsvOpts) = ${ readCsvAbsolutePath('path, 'opts) }

  /** Reads a CSV from a String and returns a [[io.github.quafadas.scautable.CsvIterator]].
    *
    * Example:
    * {{{
    * val csvContent = "colA,colB\n1,a\n2,b"
    * val csv: CsvIterator[("colA", "colB")] = CSV.fromString(csvContent)
    * }}}
    */

  transparent inline def fromString[T](inline csvContent: String): Any = fromString[T](csvContent, CsvOpts.default)

  transparent inline def fromString[T](inline csvContent: String, inline headers: HeaderOptions): Any = fromString[T](csvContent, CsvOpts(headers))

  transparent inline def fromString[T](inline csvContent: String, inline dataType: TypeInferrer): Any = fromString[T](csvContent, CsvOpts.apply(dataType))

  transparent inline def fromString[T](inline csvContent: String, inline headers: HeaderOptions, inline dataType: TypeInferrer): Any =
    fromString[T](csvContent, CsvOpts(headers, dataType))

  transparent inline def fromString[T](inline csvContent: String, inline opts: CsvOpts) = ${
    readCsvFromString('csvContent, 'opts)
  }

  /** Extract a field expression from a CsvOpts expression using quoted pattern matching.
    *
    * Handles three families of construction:
    *   1. The full 4-argument case class constructor (matched by quoted patterns)
    *   2. Companion `apply` overloads with 1-2 args (matched by quoted patterns)
    *   3. Case class constructor with named/default args, e.g. `CsvOpts(readAs = ReadAs.Columns)`. The compiler wraps these in a `Block` of default-value `ValDef`s followed by a
    *      4-arg `Apply`. We handle this with a term-level fallback.
    */
  private[scautable] def extractCsvOptsField[A: Type](
      optsExpr: Expr[CsvOpts],
      fieldName: String,
      default: Expr[A],
      pick: (Expr[HeaderOptions], Expr[TypeInferrer], Expr[Char], Expr[ReadAs]) => Expr[A]
  )(using q: Quotes): Expr[A] =
    import q.reflect.*

    val defaultH = '{ HeaderOptions.Default }
    val defaultT = '{ TypeInferrer.FromAllRows }
    val defaultD = '{ ',' }
    val defaultR = '{ ReadAs.Rows }

    optsExpr match
      // Full 4-arg case class constructor
      case '{ CsvOpts($h, $t, $d, $r) }     => pick(h, t, d, r)
      case '{ new CsvOpts($h, $t, $d, $r) } => pick(h, t, d, r)
      // CsvOpts.default
      case '{ CsvOpts.default } => default
      // Companion apply overloads
      case '{ CsvOpts.apply($h: HeaderOptions) }                   => pick(h, defaultT, defaultD, defaultR)
      case '{ CsvOpts.apply($t: TypeInferrer) }                    => pick(defaultH, t, defaultD, defaultR)
      case '{ CsvOpts.apply($h: HeaderOptions, $t: TypeInferrer) } => pick(h, t, defaultD, defaultR)
      case '{ CsvOpts.apply($r: ReadAs) }                          => pick(defaultH, defaultT, defaultD, r)
      case '{ CsvOpts.apply($t: TypeInferrer, $r: ReadAs) }        => pick(defaultH, t, defaultD, r)
      case _                                                       =>
        // Fallback: handle any CsvOpts constructor or companion apply call at the term level.
        // Covers two cases:
        //   1. Case class constructor with named/default args, e.g. CsvOpts(readAs = ReadAs.Columns).
        //      The compiler generates Block(ValDefs for defaults, Apply(_, 4 args)).
        //   2. Companion apply calls via export aliases (io.github.quafadas.table.CsvOpts)
        //      that quoted patterns can't match.
        // We identify each argument by named-arg name or by type.
        def unwrapInlined(term: Term): Term = term match
          case Inlined(_, _, body) => unwrapInlined(body)
          case other               => other

        val rawTerm = unwrapInlined(optsExpr.asTerm)

        val (bindings, innerTerm) = rawTerm match
          case Block(stats, expr) =>
            val vds = stats.collect { case vd: ValDef => (vd.name, vd.rhs) }.toMap
            (vds, unwrapInlined(expr))
          case other => (Map.empty[String, Option[Term]], other)

        // Resolve an arg that may be an Ident referring to a default-value ValDef
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

        // Check if a resolved term represents a default parameter call
        def isDefaultRef(t: Term): Boolean = unwrapInlined(t) match
          case Select(_, name) if name.contains("$default$") => true
          case _                                             => false

        innerTerm match
          case Apply(_, args) =>
            var hExpr = defaultH
            var tExpr = defaultT
            var dExpr = defaultD
            var rExpr = defaultR

            for arg <- args do
              val (name, rawValue) = arg match
                case NamedArg(n, v) => (Some(n), v)
                case v              => (None, v)

              val resolved = resolveDefault(rawValue)

              if isDefaultRef(resolved) then () // skip — keep the default
              else
                name match
                  case Some("headerOptions") => hExpr = resolved.asExprOf[HeaderOptions]
                  case Some("typeInferrer")  => tExpr = resolved.asExprOf[TypeInferrer]
                  case Some("delimiter")     => dExpr = resolved.asExprOf[Char]
                  case Some("readAs")        => rExpr = resolved.asExprOf[ReadAs]
                  case _                     =>
                    // Identify field by type
                    if resolved.tpe <:< TypeRepr.of[HeaderOptions] then hExpr = resolved.asExprOf[HeaderOptions]
                    else if resolved.tpe <:< TypeRepr.of[ReadAs] then rExpr = resolved.asExprOf[ReadAs]
                    else if resolved.tpe <:< TypeRepr.of[TypeInferrer] then tExpr = resolved.asExprOf[TypeInferrer]
                    else if resolved.tpe <:< TypeRepr.of[Char] then dExpr = resolved.asExprOf[Char]
              end if
            end for

            pick(hExpr, tExpr, dExpr, rExpr)
          case _ =>
            report.info(s"Could not extract $fieldName from CsvOpts (using default): ${optsExpr.show}")
            default
        end match
    end match
  end extractCsvOptsField

  // Helper to extract HeaderOptions from CsvOpts expression
  private[scautable] def extractHeaderOptions(optsExpr: Expr[CsvOpts])(using Quotes): Expr[HeaderOptions] =
    extractCsvOptsField[HeaderOptions](optsExpr, "HeaderOptions", '{ HeaderOptions.Default }, (h, _, _, _) => h)
  end extractHeaderOptions

  // Helper to extract TypeInferrer expression from CsvOpts
  private[scautable] def extractTypeInferrer(optsExpr: Expr[CsvOpts])(using Quotes): Expr[TypeInferrer] =
    extractCsvOptsField[TypeInferrer](optsExpr, "TypeInferrer", '{ TypeInferrer.FromAllRows }, (_, t, _, _) => t)
  end extractTypeInferrer

  // Helper to extract delimiter expression from CsvOpts
  private[scautable] def extractDelimiter(optsExpr: Expr[CsvOpts])(using Quotes): Expr[Char] =
    extractCsvOptsField[Char](optsExpr, "delimiter", '{ ',' }, (_, _, d, _) => d)
  end extractDelimiter

  // Helper to extract ReadAs expression from CsvOpts
  private[scautable] def extractReadAs(optsExpr: Expr[CsvOpts])(using Quotes): Expr[ReadAs] =
    extractCsvOptsField[ReadAs](optsExpr, "ReadAs", '{ ReadAs.Rows }, (_, _, _, r) => r)
  end extractReadAs

  // Convert a value type tuple (Int, String, Double) to array type tuple (Array[Int], Array[String], Array[Double])
  private[scautable] def toArrayTupleType(using q: Quotes)(valueTypeRepr: q.reflect.TypeRepr): q.reflect.TypeRepr =
    import q.reflect.*
    valueTypeRepr.asType match
      case '[EmptyTuple] => TypeRepr.of[EmptyTuple]
      case '[h *: t]     =>
        val headArrayType = TypeRepr.of[Array].appliedTo(TypeRepr.of[h])
        val tailArrayType = toArrayTupleType(TypeRepr.of[t])
        TypeRepr.of[*:].appliedTo(List(headArrayType, tailArrayType))
    end match
  end toArrayTupleType

  // Helper to extract type parameter from ReadAs dense array enum case application
  private[scautable] def extractDenseArrayType(using Quotes)(term: quotes.reflect.Term, caseName: String): Option[quotes.reflect.TypeRepr] =
    import quotes.reflect.*
    term match
      // Match: ReadAs.ArrayDenseColMajor.apply[T]() or ReadAs.ArrayDenseRowMajor.apply[T]()
      case Typed(Apply(TypeApply(Select(Select(_, enumName), "apply"), targs), _), _) if enumName == caseName =>
        Some(targs.head.tpe)
      case Apply(TypeApply(Select(Select(_, enumName), "apply"), targs), _) if enumName == caseName =>
        Some(targs.head.tpe)
      case _ => None
    end match
  end extractDenseArrayType

  // Helper to build dense array expression from buffers - column-major layout
  private[scautable] def buildDenseArrayColMajor[T: Type](using
      Quotes
  )(
      buffersExpr: Expr[Array[scala.collection.mutable.ArrayBuffer[String]]],
      decoderExpr: Expr[ColumnDecoder[T]],
      ct: Expr[scala.reflect.ClassTag[T]]
  ): Expr[NamedTuple[("data", "rowStride", "colStride", "rows", "cols"), (Array[T], Int, Int, Int, Int)]] =
    '{
      val buffers = $buffersExpr
      val numCols = buffers.length
      val numRows = if buffers.nonEmpty then buffers(0).length else 0
      val totalElements = numRows * numCols
      val data = new Array[T](totalElements)(using $ct)

      // Decode each column using ColumnDecoder and fill in column-major order
      val decoder = $decoderExpr
      var colIdx = 0
      while colIdx < numCols do
        val colData = decoder.decodeColumn(buffers(colIdx))
        var rowIdx = 0
        while rowIdx < numRows do
          data(colIdx * numRows + rowIdx) = colData(rowIdx)
          rowIdx += 1
        end while
        colIdx += 1
      end while

      // In column-major: colStride = 1 (stride between rows in same column), rowStride = numRows (stride between columns at same row)
      val result = (data, numRows, 1, numRows, numCols)
      NamedTuple.build[("data", "rowStride", "colStride", "rows", "cols")]()(result)
    }
  end buildDenseArrayColMajor

  // Helper to build dense array expression from buffers - row-major layout
  private[scautable] def buildDenseArrayRowMajor[T: Type](using
      Quotes
  )(
      buffersExpr: Expr[Array[scala.collection.mutable.ArrayBuffer[String]]],
      decoderExpr: Expr[ColumnDecoder[T]],
      ct: Expr[scala.reflect.ClassTag[T]]
  ): Expr[NamedTuple[("data", "rowStride", "colStride", "rows", "cols"), (Array[T], Int, Int, Int, Int)]] =
    '{
      val buffers = $buffersExpr
      val numCols = buffers.length
      val numRows = if buffers.nonEmpty then buffers(0).length else 0
      val totalElements = numRows * numCols
      val data = new Array[T](totalElements)(using $ct)

      // Decode each column using ColumnDecoder
      val decoder = $decoderExpr
      var colIdx = 0
      while colIdx < numCols do
        val colData = decoder.decodeColumn(buffers(colIdx))
        var rowIdx = 0
        while rowIdx < numRows do
          data(rowIdx * numCols + colIdx) = colData(rowIdx)
          rowIdx += 1
        end while
        colIdx += 1
      end while

      // In row-major: rowStride = 1 (stride between columns in same row), colStride = numCols (stride between rows at same column)
      val result = (data, 1, numCols, numRows, numCols)
      NamedTuple.build[("data", "rowStride", "colStride", "rows", "cols")]()(result)
    }
  end buildDenseArrayRowMajor

  private transparent inline def readHeaderlineAsCsv(path: String, optsExpr: Expr[CsvOpts])(using q: Quotes) =
    import q.reflect.*
    import io.github.quafadas.table.HeaderOptions.*

    val csvHeadersExpr = extractHeaderOptions(optsExpr)
    val typeInferrerExpr = extractTypeInferrer(optsExpr)
    val delimiterExpr = extractDelimiter(optsExpr)
    val readAsExpr = extractReadAs(optsExpr)

    // Extract value for compile-time processing
    val csvHeaders: HeaderOptions = csvHeadersExpr.valueOrAbort
    val delimiter: Char = delimiterExpr.valueOrAbort

    // Determine readAs mode by inspecting the term
    def unwrapTerm(term: Term): Term = term match
      case Inlined(_, _, body) => unwrapTerm(body)
      case other               => other

    val readAsTerm = unwrapTerm(readAsExpr.asTerm)

    // Determine which mode we're in and extract element type if needed
    val isColumnMode = readAsTerm match
      case Select(_, "Columns") => true
      case _                    =>
        readAsExpr match
          case '{ ReadAs.Columns }                          => true
          case '{ io.github.quafadas.table.ReadAs.Columns } => true
          case _                                            => false

    val denseColMajorType: Option[TypeRepr] = CSV.extractDenseArrayType(readAsTerm, "ArrayDenseColMajor")
    val denseRowMajorType: Option[TypeRepr] = CSV.extractDenseArrayType(readAsTerm, "ArrayDenseRowMajor")

    val source = Source.fromFile(path)
    val lineIterator: Iterator[String] = source.getLines()
    val (headers, iter) = lineIterator.headers(csvHeaders, delimiter)

    if headers.length != headers.distinct.length then report.info("Possible duplicated headers detected.")
    end if

    val headerTupleExpr = Expr.ofTupleFromSeq(headers.map(Expr(_)))

    def constructRowIterator[Hdrs <: Tuple: Type, Data <: Tuple: Type]: Expr[CsvIterator[Hdrs, Data]] =
      val filePathExpr = Expr(path)
      '{
        val lines = scala.io.Source.fromFile($filePathExpr).getLines()
        val (headers, iterator) = lines.headers(${ csvHeadersExpr }, ${ delimiterExpr })
        new CsvIterator[Hdrs, Data](iterator, headers, ${ delimiterExpr })
      }
    end constructRowIterator

    def constructColumnArrays[Hdrs <: Tuple: Type, ArrayData <: Tuple: Type]: Expr[NamedTuple[Hdrs, ArrayData]] =
      val filePathExpr = Expr(path)
      '{
        val source = scala.io.Source.fromFile($filePathExpr)
        val lines = source.getLines()
        val (headers, iterator) = lines.headers($csvHeadersExpr, $delimiterExpr)

        val numCols = headers.length
        val buffers = Array.fill(numCols)(scala.collection.mutable.ArrayBuffer[String]())

        iterator.foreach { line =>
          val parsed = CSVParser.parseLine(line, $delimiterExpr)
          var i = 0
          while i < parsed.length && i < numCols do
            buffers(i) += parsed(i)
            i += 1
          end while
        }

        source.close()

        val typedColumns = ColumnsDecoder.decodeAllColumns[ArrayData](buffers)
        NamedTuple.build[Hdrs & Tuple]()(typedColumns)
      }
    end constructColumnArrays

    def constructDenseArrayColMajor[T: Type](using
        ct: Expr[scala.reflect.ClassTag[T]]
    ): Expr[NamedTuple[("data", "rowStride", "colStride", "rows", "cols"), (Array[T], Int, Int, Int, Int)]] =
      val filePathExpr = Expr(path)
      // Summon the decoder at compile-time
      val decoderExpr = Expr.summon[ColumnDecoder[T]].getOrElse {
        report.throwError(s"No ColumnDecoder available for type ${Type.show[T]}")
      }
      val buffersExpr = '{
        val source = scala.io.Source.fromFile($filePathExpr)
        val lines = source.getLines()
        val (headers, iterator) = lines.headers($csvHeadersExpr, $delimiterExpr)

        val numCols = headers.length
        val buffers = Array.fill(numCols)(scala.collection.mutable.ArrayBuffer[String]())

        iterator.foreach { line =>
          val parsed = CSVParser.parseLine(line, $delimiterExpr)
          var i = 0
          while i < parsed.length && i < numCols do
            buffers(i) += parsed(i)
            i += 1
          end while
        }

        source.close()
        buffers
      }
      CSV.buildDenseArrayColMajor[T](buffersExpr, decoderExpr, ct)
    end constructDenseArrayColMajor

    def constructDenseArrayRowMajor[T: Type](using
        ct: Expr[scala.reflect.ClassTag[T]]
    ): Expr[NamedTuple[("data", "rowStride", "colStride", "rows", "cols"), (Array[T], Int, Int, Int, Int)]] =
      val filePathExpr = Expr(path)
      // Summon the decoder at compile-time
      val decoderExpr = Expr.summon[ColumnDecoder[T]].getOrElse {
        report.throwError(s"No ColumnDecoder available for type ${Type.show[T]}")
      }
      val buffersExpr = '{
        val source = scala.io.Source.fromFile($filePathExpr)
        val lines = source.getLines()
        val (headers, iterator) = lines.headers($csvHeadersExpr, $delimiterExpr)

        val numCols = headers.length
        val buffers = Array.fill(numCols)(scala.collection.mutable.ArrayBuffer[String]())

        iterator.foreach { line =>
          val parsed = CSVParser.parseLine(line, $delimiterExpr)
          var i = 0
          while i < parsed.length && i < numCols do
            buffers(i) += parsed(i)
            i += 1
          end while
        }

        source.close()
        buffers
      }
      CSV.buildDenseArrayRowMajor[T](buffersExpr, decoderExpr, ct)
    end constructDenseArrayRowMajor

    // Handle dense array modes first
    denseColMajorType match
      case Some(elemType) =>
        elemType.asType match
          case '[t] =>
            given Expr[scala.reflect.ClassTag[t]] = Expr.summon[scala.reflect.ClassTag[t]].getOrElse {
              report.throwError(s"ClassTag not found for type ${elemType.show}")
            }
            constructDenseArrayColMajor[t]
      case None =>
        denseRowMajorType match
          case Some(elemType) =>
            elemType.asType match
              case '[t] =>
                given Expr[scala.reflect.ClassTag[t]] = Expr.summon[scala.reflect.ClassTag[t]].getOrElse {
                  report.throwError(s"ClassTag not found for type ${elemType.show}")
                }
                constructDenseArrayRowMajor[t]
          case None =>
            // Handle rows or columns mode
            if !isColumnMode then
              headerTupleExpr match
                case '{ $tup: hdrs } =>
                  typeInferrerExpr match

                    case '{ TypeInferrer.FromTuple[t]() } =>
                      constructRowIterator[hdrs & Tuple, t & Tuple]

                    case '{ TypeInferrer.StringType } =>
                      constructRowIterator[hdrs & Tuple, StringyTuple[hdrs & Tuple] & Tuple]

                    case '{ TypeInferrer.FirstRow } =>
                      val inferredTypeRepr = InferrerOps.inferrer(iter, true, delimiter = delimiter)
                      inferredTypeRepr.asType match
                        case '[v] =>
                          constructRowIterator[hdrs & Tuple, v & Tuple]
                      end match

                    case '{ TypeInferrer.FromAllRows } =>
                      val inferredTypeRepr = InferrerOps.inferrer(iter, false, Int.MaxValue, delimiter)
                      inferredTypeRepr.asType match
                        case '[v] => constructRowIterator[hdrs & Tuple, v & Tuple]
                      end match

                    case '{ TypeInferrer.FirstN(${ Expr(n) }) } =>
                      val inferredTypeRepr = InferrerOps.inferrer(iter, true, n, delimiter)
                      inferredTypeRepr.asType match
                        case '[v] => constructRowIterator[hdrs & Tuple, v & Tuple]
                      end match

                    case '{ TypeInferrer.FirstN(${ Expr(n) }, ${ Expr(preferIntToBoolean) }) } =>
                      val inferredTypeRepr = InferrerOps.inferrer(iter, preferIntToBoolean, n, delimiter)
                      inferredTypeRepr.asType match
                        case '[v] => constructRowIterator[hdrs & Tuple, v & Tuple]
                      end match

                case _ =>
                  report.throwError("Could not infer literal header tuple.")
              end match
            else // isColumnMode
              headerTupleExpr match
                case '{ $tup: hdrs } =>
                  typeInferrerExpr match

                    case '{ TypeInferrer.FromTuple[t]() } =>
                      val arrayTypeRepr = toArrayTupleType(TypeRepr.of[t])
                      arrayTypeRepr.asType match
                        case '[arrTup] => constructColumnArrays[hdrs & Tuple, arrTup & Tuple]
                      end match

                    case '{ TypeInferrer.StringType } =>
                      val stringyType = TypeRepr.of[StringyTuple[hdrs & Tuple]]
                      val arrayTypeRepr = toArrayTupleType(stringyType)
                      arrayTypeRepr.asType match
                        case '[arrTup] => constructColumnArrays[hdrs & Tuple, arrTup & Tuple]
                      end match

                    case '{ TypeInferrer.FirstRow } =>
                      val inferredTypeRepr = InferrerOps.inferrer(iter, true, delimiter = delimiter)
                      val arrayTypeRepr = toArrayTupleType(inferredTypeRepr)
                      arrayTypeRepr.asType match
                        case '[arrTup] => constructColumnArrays[hdrs & Tuple, arrTup & Tuple]
                      end match

                    case '{ TypeInferrer.FromAllRows } =>
                      val inferredTypeRepr = InferrerOps.inferrer(iter, false, Int.MaxValue, delimiter)
                      val arrayTypeRepr = toArrayTupleType(inferredTypeRepr)
                      arrayTypeRepr.asType match
                        case '[arrTup] => constructColumnArrays[hdrs & Tuple, arrTup & Tuple]
                      end match

                    case '{ TypeInferrer.FirstN(${ Expr(n) }) } =>
                      val inferredTypeRepr = InferrerOps.inferrer(iter, true, n, delimiter)
                      val arrayTypeRepr = toArrayTupleType(inferredTypeRepr)
                      arrayTypeRepr.asType match
                        case '[arrTup] => constructColumnArrays[hdrs & Tuple, arrTup & Tuple]
                      end match

                    case '{ TypeInferrer.FirstN(${ Expr(n) }, ${ Expr(preferIntToBoolean) }) } =>
                      val inferredTypeRepr = InferrerOps.inferrer(iter, preferIntToBoolean, n, delimiter)
                      val arrayTypeRepr = toArrayTupleType(inferredTypeRepr)
                      arrayTypeRepr.asType match
                        case '[arrTup] => constructColumnArrays[hdrs & Tuple, arrTup & Tuple]
                      end match

                case _ =>
                  report.throwError("Could not infer literal header tuple.")
              end match
            end if
    end match

  end readHeaderlineAsCsv

  private def readCsvFromUrl(pathExpr: Expr[String], optsExpr: Expr[CsvOpts])(using Quotes) =
    import quotes.reflect.*

    report.warning(
      "This method saves the CSV to a local temp file and opens it. There may be performance implications - it is recommended to use one of the other methods where possible."
    )

    val source = Source.fromURL(pathExpr.valueOrAbort)
    val tmpPath = java.nio.file.Files.createTempFile("temp_csv_", ".csv")
    java.nio.file.Files.writeString(tmpPath, source.mkString)
    readHeaderlineAsCsv(tmpPath.toString, optsExpr)

  end readCsvFromUrl

  private def readCsvFromCurrentDir(pathExpr: Expr[String], optsExpr: Expr[CsvOpts])(using Quotes) =
    val cwd = java.nio.file.Paths.get(".").toAbsolutePath.normalize()
    val path = cwd.resolve(pathExpr.valueOrAbort).toString
    readHeaderlineAsCsv(path, optsExpr)
  end readCsvFromCurrentDir

  def readCsvAbsolutePath(pathExpr: Expr[String], optsExpr: Expr[CsvOpts])(using Quotes) =
    val path = pathExpr.valueOrAbort
    readHeaderlineAsCsv(path, optsExpr)
  end readCsvAbsolutePath

  private def readCsvResource(pathExpr: Expr[String], optsExpr: Expr[CsvOpts])(using Quotes) =
    import quotes.reflect.*

    val path = pathExpr.valueOrAbort
    val resourcePath = this.getClass.getClassLoader.getResource(path)
    if resourcePath == null then report.throwError(s"Resource not found: $path")
    end if

    readHeaderlineAsCsv(resourcePath.getPath, optsExpr)
  end readCsvResource

  private def readCsvFromString(csvContentExpr: Expr[String], optsExpr: Expr[CsvOpts])(using Quotes) =
    import quotes.reflect.*

    val csvHeadersExpr = extractHeaderOptions(optsExpr)
    val typeInferrerExpr = extractTypeInferrer(optsExpr)
    val delimiterExpr = extractDelimiter(optsExpr)
    val readAsExpr = extractReadAs(optsExpr)

    // Extract value for compile-time processing
    val csvHeaders: HeaderOptions = csvHeadersExpr.valueOrAbort
    val delimiter: Char = delimiterExpr.valueOrAbort

    // Determine readAs mode by inspecting the term
    def unwrapTerm(term: Term): Term = term match
      case Inlined(_, _, body) => unwrapTerm(body)
      case other               => other

    val readAsTerm = unwrapTerm(readAsExpr.asTerm)

    // Determine which mode we're in and extract element type if needed
    val isColumnMode = readAsTerm match
      case Select(_, "Columns") => true
      case _                    =>
        readAsExpr match
          case '{ ReadAs.Columns }                          => true
          case '{ io.github.quafadas.table.ReadAs.Columns } => true
          case _                                            => false

    val denseColMajorType: Option[TypeRepr] = CSV.extractDenseArrayType(readAsTerm, "ArrayDenseColMajor")
    val denseRowMajorType: Option[TypeRepr] = CSV.extractDenseArrayType(readAsTerm, "ArrayDenseRowMajor")

    val content = csvContentExpr.valueOrAbort

    if content.trim.isEmpty then report.throwError("Empty CSV content provided.")
    end if

    val lines = content.linesIterator
    val (headers, iter) = lines.headers(csvHeaders, delimiter)

    if headers.length != headers.distinct.length then report.info("Possible duplicated headers detected.")

    end if

    val headerTupleExpr = Expr.ofTupleFromSeq(headers.map(Expr(_)))

    def constructRowIterator[Hdrs <: Tuple: Type, Data <: Tuple: Type]: Expr[CsvIterator[Hdrs, Data]] =
      '{
        val content = $csvContentExpr
        val lines = content.linesIterator
        val (headers, iterator) = lines.headers($csvHeadersExpr, $delimiterExpr)
        new CsvIterator[Hdrs, Data](iterator, headers, $delimiterExpr)
      }

    def constructColumnArrays[Hdrs <: Tuple: Type, ArrayData <: Tuple: Type]: Expr[NamedTuple[Hdrs, ArrayData]] =
      '{
        val content = $csvContentExpr
        val lines = content.linesIterator
        val (headers, iterator) = lines.headers($csvHeadersExpr, $delimiterExpr)

        val numCols = headers.length
        val buffers = Array.fill(numCols)(scala.collection.mutable.ArrayBuffer[String]())

        iterator.foreach { line =>
          val parsed = CSVParser.parseLine(line, $delimiterExpr)
          var i = 0
          while i < parsed.length && i < numCols do
            buffers(i) += parsed(i)
            i += 1
          end while
        }

        val typedColumns = ColumnsDecoder.decodeAllColumns[ArrayData](buffers)
        NamedTuple.build[Hdrs & Tuple]()(typedColumns)
      }

    def constructDenseArrayColMajor[T: Type](using
        ct: Expr[scala.reflect.ClassTag[T]]
    ): Expr[NamedTuple[("data", "rowStride", "colStride", "rows", "cols"), (Array[T], Int, Int, Int, Int)]] =
      // Summon the decoder at compile-time
      val decoderExpr = Expr.summon[ColumnDecoder[T]].getOrElse {
        report.throwError(s"No ColumnDecoder available for type ${Type.show[T]}")
      }
      val buffersExpr = '{
        val content = $csvContentExpr
        val lines = content.linesIterator
        val (headers, iterator) = lines.headers($csvHeadersExpr, $delimiterExpr)

        val numCols = headers.length
        val buffers = Array.fill(numCols)(scala.collection.mutable.ArrayBuffer[String]())

        iterator.foreach { line =>
          val parsed = CSVParser.parseLine(line, $delimiterExpr)
          var i = 0
          while i < parsed.length && i < numCols do
            buffers(i) += parsed(i)
            i += 1
          end while
        }

        buffers
      }
      CSV.buildDenseArrayColMajor[T](buffersExpr, decoderExpr, ct)
    end constructDenseArrayColMajor

    def constructDenseArrayRowMajor[T: Type](using
        ct: Expr[scala.reflect.ClassTag[T]]
    ): Expr[NamedTuple[("data", "rowStride", "colStride", "rows", "cols"), (Array[T], Int, Int, Int, Int)]] =
      // Summon the decoder at compile-time
      val decoderExpr = Expr.summon[ColumnDecoder[T]].getOrElse {
        report.throwError(s"No ColumnDecoder available for type ${Type.show[T]}")
      }
      val buffersExpr = '{
        val content = $csvContentExpr
        val lines = content.linesIterator
        val (headers, iterator) = lines.headers($csvHeadersExpr, $delimiterExpr)

        val numCols = headers.length
        val buffers = Array.fill(numCols)(scala.collection.mutable.ArrayBuffer[String]())

        iterator.foreach { line =>
          val parsed = CSVParser.parseLine(line, $delimiterExpr)
          var i = 0
          while i < parsed.length && i < numCols do
            buffers(i) += parsed(i)
            i += 1
          end while
        }

        buffers
      }
      CSV.buildDenseArrayRowMajor[T](buffersExpr, decoderExpr, ct)
    end constructDenseArrayRowMajor

    // Handle dense array modes first
    denseColMajorType match
      case Some(elemType) =>
        elemType.asType match
          case '[t] =>
            given Expr[scala.reflect.ClassTag[t]] = Expr.summon[scala.reflect.ClassTag[t]].getOrElse {
              report.throwError(s"ClassTag not found for type ${elemType.show}")
            }
            constructDenseArrayColMajor[t]
      case None =>
        denseRowMajorType match
          case Some(elemType) =>
            elemType.asType match
              case '[t] =>
                given Expr[scala.reflect.ClassTag[t]] = Expr.summon[scala.reflect.ClassTag[t]].getOrElse {
                  report.throwError(s"ClassTag not found for type ${elemType.show}")
                }
                constructDenseArrayRowMajor[t]
          case None =>
            // Handle rows or columns mode
            if !isColumnMode then
              headerTupleExpr match
                case '{ $tup: hdrs } =>
                  typeInferrerExpr match

                    case '{ TypeInferrer.FromTuple[t]() } =>
                      constructRowIterator[hdrs & Tuple, t & Tuple]

                    case '{ TypeInferrer.StringType } =>
                      constructRowIterator[hdrs & Tuple, StringyTuple[hdrs & Tuple] & Tuple]

                    case '{ TypeInferrer.FirstRow } =>
                      val inferredTypeRepr = InferrerOps.inferrer(iter, true, delimiter = delimiter)
                      inferredTypeRepr.asType match
                        case '[v] =>
                          constructRowIterator[hdrs & Tuple, v & Tuple]
                      end match

                    case '{ TypeInferrer.FromAllRows } =>
                      val inferredTypeRepr = InferrerOps.inferrer(iter, false, Int.MaxValue, delimiter)
                      inferredTypeRepr.asType match
                        case '[v] => constructRowIterator[hdrs & Tuple, v & Tuple]
                      end match

                    case '{ TypeInferrer.FirstN(${ Expr(n) }) } =>
                      val inferredTypeRepr = InferrerOps.inferrer(iter, true, n, delimiter)
                      inferredTypeRepr.asType match
                        case '[v] => constructRowIterator[hdrs & Tuple, v & Tuple]
                      end match

                    case '{ TypeInferrer.FirstN(${ Expr(n) }, ${ Expr(preferIntToBoolean) }) } =>
                      val inferredTypeRepr = InferrerOps.inferrer(iter, preferIntToBoolean, n, delimiter)
                      inferredTypeRepr.asType match
                        case '[v] => constructRowIterator[hdrs & Tuple, v & Tuple]
                      end match

                case _ =>
                  report.throwError("Could not infer literal header tuple.")
              end match
            else // isColumnMode
              headerTupleExpr match
                case '{ $tup: hdrs } =>
                  typeInferrerExpr match

                    case '{ TypeInferrer.FromTuple[t]() } =>
                      val arrayTypeRepr = toArrayTupleType(TypeRepr.of[t])
                      arrayTypeRepr.asType match
                        case '[arrTup] => constructColumnArrays[hdrs & Tuple, arrTup & Tuple]
                      end match

                    case '{ TypeInferrer.StringType } =>
                      val stringyType = TypeRepr.of[StringyTuple[hdrs & Tuple]]
                      val arrayTypeRepr = toArrayTupleType(stringyType)
                      arrayTypeRepr.asType match
                        case '[arrTup] => constructColumnArrays[hdrs & Tuple, arrTup & Tuple]
                      end match

                    case '{ TypeInferrer.FirstRow } =>
                      val inferredTypeRepr = InferrerOps.inferrer(iter, true, delimiter = delimiter)
                      val arrayTypeRepr = toArrayTupleType(inferredTypeRepr)
                      arrayTypeRepr.asType match
                        case '[arrTup] => constructColumnArrays[hdrs & Tuple, arrTup & Tuple]
                      end match

                    case '{ TypeInferrer.FromAllRows } =>
                      val inferredTypeRepr = InferrerOps.inferrer(iter, false, Int.MaxValue, delimiter)
                      val arrayTypeRepr = toArrayTupleType(inferredTypeRepr)
                      arrayTypeRepr.asType match
                        case '[arrTup] => constructColumnArrays[hdrs & Tuple, arrTup & Tuple]
                      end match

                    case '{ TypeInferrer.FirstN(${ Expr(n) }) } =>
                      val inferredTypeRepr = InferrerOps.inferrer(iter, true, n, delimiter)
                      val arrayTypeRepr = toArrayTupleType(inferredTypeRepr)
                      arrayTypeRepr.asType match
                        case '[arrTup] => constructColumnArrays[hdrs & Tuple, arrTup & Tuple]
                      end match

                    case '{ TypeInferrer.FirstN(${ Expr(n) }, ${ Expr(preferIntToBoolean) }) } =>
                      val inferredTypeRepr = InferrerOps.inferrer(iter, preferIntToBoolean, n, delimiter)
                      val arrayTypeRepr = toArrayTupleType(inferredTypeRepr)
                      arrayTypeRepr.asType match
                        case '[arrTup] => constructColumnArrays[hdrs & Tuple, arrTup & Tuple]
                      end match

                case _ =>
                  report.throwError("Could not infer literal header tuple.")
              end match
            end if
    end match
  end readCsvFromString

  /** Creates a function that reads a CSV file from a runtime path and returns a [[io.github.quafadas.scautable.CsvIterator]].
    *
    * Unlike other CSV methods that require the file path at compile time, this method allows you to specify the column types at compile time but provide the file path at runtime.
    * This is useful when you know the structure of a CSV file in advance but the actual file location is determined at runtime.
    *
    * Example:
    * {{{
    *   val csvReader = CSV.fromTyped[("name", "age", "salary"), (String, Int, Double)]
    *   val data = csvReader(os.pwd / "employees.csv")
    *   // data is a CsvIterator[("name", "age", "salary"), (String, Int, Double)]
    * }}}
    *
    * @tparam K
    *   A tuple of string literal types representing the column names
    * @tparam V
    *   A tuple of types representing the column value types (must have Decoders available)
    * @return
    *   A function that takes an os.Path and returns a CsvIterator with the specified types
    */
  inline def fromTyped[K <: Tuple, V <: Tuple]: PlatformPath => CsvIterator[K, V] = fromTyped[K, V](HeaderOptions.Default)

  /** Creates a function that reads a CSV file from a runtime path and returns a [[io.github.quafadas.scautable.CsvIterator]].
    *
    * This overload allows you to specify custom header options. [SP note: I'm not sure if this should be possible ]
    *
    * Example:
    * {{{
    *   // Skip 2 header rows and merge them
    *   val csvReader = CSV.fromTyped[("Name First", "Age Years"), (String, Int)](HeaderOptions.FromRows(2))
    *   val data = csvReader(os.pwd / "employees.csv")
    * }}}
    *
    * @tparam K
    *   A tuple of string literal types representing the column names
    * @tparam V
    *   A tuple of types representing the column value types (must have Decoders available)
    * @param headers
    *   The header options to use when reading the CSV
    * @return
    *   A function that takes an os.Path and returns a CsvIterator with the specified types
    */
  private inline def fromTyped[K <: Tuple, V <: Tuple](inline headers: HeaderOptions): PlatformPath => CsvIterator[K, V] =
    (path: PlatformPath) =>
      val lines = scala.io.Source.fromFile(path.platformPathString).getLines()
      val (hdrs, iterator) = lines.headers(headers)
      val expectedHeaders = scala.compiletime.constValueTuple[K].toArray.toSeq.asInstanceOf[Seq[String]]
      hdrs.zip(expectedHeaders).zipWithIndex.foreach { case ((a, b), idx) =>
        if a != b then
          throw new IllegalStateException(
            s"CSV headers do not match expected headers. Expected: $expectedHeaders, Got: $hdrs. Header mismatch at index $idx: expected '$b', got '$a'"
          )
      }

      if hdrs.length != expectedHeaders.length then
        throw new IllegalStateException(s"You provided: ${expectedHeaders.size} but ${hdrs.size} headers were found in the file at ${path.platformPathString}.")
      end if

      val sizeOfV = scala.compiletime.constValue[Tuple.Size[V]]
      if hdrs.length != sizeOfV then
        throw new IllegalStateException(s"Number of headers in CSV (${hdrs.length}) does not match number (${sizeOfV}) of types provided for decoding.")
      end if

      new CsvIterator[K, V](iterator, hdrs)

end CSV
