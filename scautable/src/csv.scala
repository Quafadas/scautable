package io.github.quafadas.scautable

import scala.io.Source
import scala.quoted.*

import io.github.quafadas.scautable.ColumnTyped.*
import io.github.quafadas.scautable.HeaderOptions.headers
import io.github.quafadas.table.TypeInferrer

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

  // Helper to extract HeaderOptions from CsvOpts expression
  private def extractHeaderOptions(optsExpr: Expr[CsvOpts])(using Quotes): Expr[HeaderOptions] =
    import quotes.reflect.*
    // Unwrap Inlined nodes to get to the actual term
    def unwrapInlined(term: Term): Term = term match
      case Inlined(_, _, body) => unwrapInlined(body)
      case other               => other

    // Helper to unwrap NamedArg nodes
    def unwrapNamedArg(term: Term): Term = term match
      case NamedArg(_, value) => value
      case other              => other

    // Helper to resolve a term that might be an Ident or a default value call
    def resolveTerm(term: Term): Option[Expr[HeaderOptions]] =
      term match
        case Ident(name) if name.contains("headerOptions") =>
          // This is a reference to a variable, it's likely using a default
          None
        case Select(_, name) if name.contains("$default$") =>
          // This is a default parameter call - use the actual default
          // For CsvOpts, the first parameter (headerOptions) default is HeaderOptions.Default
          Some('{ HeaderOptions.Default })
        case t if t.tpe <:< TypeRepr.of[HeaderOptions] => Some(t.asExprOf[HeaderOptions])
        case _                                         => None

    val term = unwrapInlined(optsExpr.asTerm)

    term match
      // Handle Block with variable bindings from default parameters
      case Block(statements, Apply(_, args)) =>
        // Look for headerOptions named argument
        val fromNamedArg = args.collectFirst { case NamedArg("headerOptions", value) =>
          resolveTerm(unwrapNamedArg(value))
        }.flatten

        // If not found as named arg, try positional args
        val fromPositionalArg =
          if fromNamedArg.isEmpty then
            args
              .map(unwrapNamedArg)
              .collectFirst {
                case i: Ident if i.symbol.name.contains("headerOptions") => resolveTerm(i)
                case term if term.tpe <:< TypeRepr.of[HeaderOptions]     => resolveTerm(term)
              }
              .flatten
          else None

        // If not found in args, check statements for variable binding
        val fromStatements =
          if fromNamedArg.isEmpty && fromPositionalArg.isEmpty then
            statements.collectFirst {
              case ValDef(name, tpt, Some(rhs)) if name.contains("headerOptions") && tpt.tpe <:< TypeRepr.of[HeaderOptions] =>
                resolveTerm(rhs).getOrElse('{ HeaderOptions.Default })
            }
          else None

        fromNamedArg.orElse(fromPositionalArg).orElse(fromStatements).getOrElse('{ HeaderOptions.Default })

      // CsvOpts(headerOptions, typeInferrer, delimiter) or CsvOpts(headerOptions, typeInferrer) or CsvOpts(headerOptions)
      // First arg is always HeaderOptions if present, otherwise check if it's TypeInferrer (then use default)
      case Apply(_, args) if args.nonEmpty =>
        val headerOptionsType = TypeRepr.of[HeaderOptions]
        val typeInferrerType = TypeRepr.of[TypeInferrer]

        // Look for headerOptions by name or by position (first arg)
        args
          .collectFirst { case NamedArg("headerOptions", value) =>
            val unwrapped = unwrapNamedArg(value)
            resolveTerm(unwrapped)
          }
          .flatten
          .orElse {
            // Check if first arg (unwrapped) is HeaderOptions
            val firstArg = unwrapNamedArg(args.head)
            resolveTerm(firstArg)
          }
          .getOrElse('{ HeaderOptions.Default })

      case Apply(_, Nil) =>
        // No arguments - shouldn't happen but use default
        '{ HeaderOptions.Default }

      case _ =>
        // Check if it's CsvOpts.default
        if optsExpr.matches('{ CsvOpts.default }) then '{ HeaderOptions.Default }
        else
          report.info(s"Could not extract HeaderOptions from CsvOpts (using default): ${optsExpr.show}")
          '{ HeaderOptions.Default }
    end match
  end extractHeaderOptions

  // Helper to extract TypeInferrer expression from CsvOpts
  private def extractTypeInferrer(optsExpr: Expr[CsvOpts])(using Quotes): Expr[TypeInferrer] =
    import quotes.reflect.*
    // Unwrap Inlined nodes
    def unwrapInlined(term: Term): Term = term match
      case Inlined(_, _, body) => unwrapInlined(body)
      case other               => other

    // Helper to unwrap NamedArg nodes
    def unwrapNamedArg(term: Term): Term = term match
      case NamedArg(_, value) => value
      case other              => other

    val term = unwrapInlined(optsExpr.asTerm)

    term match
      // Handle Block with variable bindings from default parameters
      case Block(statements, Apply(_, args)) =>
        val typeInferrerType = TypeRepr.of[TypeInferrer]

        // Helper to resolve a term that might be an Ident or a default value call
        def resolveTerm(term: Term): Option[Expr[TypeInferrer]] =
          term match
            case Select(_, name) if name.contains("$default$") =>
              // This is a default parameter call - use the actual default
              // For CsvOpts, the second parameter (typeInferrer) default is FromAllRows
              Some('{ TypeInferrer.FromAllRows })
            case Ident(name) if name.contains("typeInferrer") =>
              // This is a reference to a variable, find it in statements
              statements.collectFirst {
                case ValDef(varName, _, Some(rhs)) if varName == name =>
                  resolveTerm(rhs).getOrElse(rhs.asExprOf[TypeInferrer])
              }
            case t if t.tpe <:< typeInferrerType => Some(t.asExprOf[TypeInferrer])
            case _                               => None

        // Look for typeInferrer named argument first
        val fromNamedArg = args.collectFirst { case NamedArg("typeInferrer", value) =>
          resolveTerm(unwrapNamedArg(value))
        }.flatten

        // If not found as named arg, try positional args
        val fromPositionalArg =
          if fromNamedArg.isEmpty then
            args
              .map(unwrapNamedArg)
              .collectFirst {
                case i: Ident                              => resolveTerm(i)
                case term if term.tpe <:< typeInferrerType => resolveTerm(term)
              }
              .flatten
          else None

        // If not found in args at all, check if there's a variable in statements (default value)
        val fromStatements =
          if fromNamedArg.isEmpty && fromPositionalArg.isEmpty then
            statements.collectFirst {
              case ValDef(name, tpt, Some(rhs)) if name.contains("typeInferrer") && tpt.tpe <:< typeInferrerType =>
                resolveTerm(rhs).getOrElse(rhs.asExprOf[TypeInferrer])
            }
          else None

        fromNamedArg.orElse(fromPositionalArg).orElse(fromStatements).getOrElse('{ TypeInferrer.StringType })

      // CsvOpts can have TypeInferrer as first arg (when HeaderOptions is default) or second arg
      case Apply(_, args) =>
        val typeInferrerType = TypeRepr.of[TypeInferrer]

        // Look for typeInferrer by name first, then by type in any position
        args
          .collectFirst { case NamedArg("typeInferrer", value) =>
            val unwrapped = unwrapNamedArg(value)
            unwrapped match
              case Select(_, name) if name.contains("$default$") => Some('{ TypeInferrer.FromAllRows })
              case t if t.tpe <:< typeInferrerType               => Some(t.asExprOf[TypeInferrer])
              case _                                             => None
            end match
          }
          .flatten
          .orElse {
            // Find TypeInferrer in the arguments (unwrap NamedArgs)
            args.map(unwrapNamedArg).collectFirst {
              case Select(_, name) if name.contains("$default$") => '{ TypeInferrer.FromAllRows }
              case term if term.tpe <:< typeInferrerType         => term.asExprOf[TypeInferrer]
            }
          }
          .getOrElse('{ TypeInferrer.StringType })

      case _ =>
        // Check if it's CsvOpts.default
        if optsExpr.matches('{ CsvOpts.default }) then '{ TypeInferrer.FromAllRows }
        else
          report.info(s"Could not extract TypeInferrer from CsvOpts (using StringType): ${optsExpr.show}")
          '{ TypeInferrer.StringType }
    end match
  end extractTypeInferrer

  // Helper to extract delimiter expression from CsvOpts
  private def extractDelimiter(optsExpr: Expr[CsvOpts])(using Quotes): Expr[Char] =
    import quotes.reflect.*
    // Unwrap Inlined nodes
    def unwrapInlined(term: Term): Term = term match
      case Inlined(_, _, body) => unwrapInlined(body)
      case other               => other

    // Helper to unwrap NamedArg nodes
    def unwrapNamedArg(term: Term): Term = term match
      case NamedArg(_, value) => value
      case other              => other

    val term = unwrapInlined(optsExpr.asTerm)

    term match
      // Handle Block with variable bindings from default parameters
      case Block(statements, Apply(_, args)) =>
        // Look for delimiter by name or as a literal Char
        args
          .collectFirst {
            case NamedArg("delimiter", value) if unwrapNamedArg(value).tpe <:< TypeRepr.of[Char] =>
              unwrapNamedArg(value).asExprOf[Char]
          }
          .orElse {
            // Look for a Char argument (should be delimiter, unwrap NamedArgs)
            args.map(unwrapNamedArg).find(_.tpe <:< TypeRepr.of[Char]).map(_.asExprOf[Char])
          }
          .getOrElse('{ ',' })

      // Try to extract arguments from Apply node
      case Apply(_, args) =>
        // Look for delimiter by name first, then by Char type in any position
        args
          .collectFirst {
            case NamedArg("delimiter", value) if unwrapNamedArg(value).tpe <:< TypeRepr.of[Char] =>
              unwrapNamedArg(value).asExprOf[Char]
          }
          .orElse {
            // Look for a Char argument (should be delimiter, unwrap NamedArgs)
            args.map(unwrapNamedArg).find(_.tpe <:< TypeRepr.of[Char]).map(_.asExprOf[Char])
          }
          .getOrElse('{ ',' })

      case _ =>
        // Check if it's CsvOpts.default
        if optsExpr.matches('{ CsvOpts.default }) then '{ ',' }
        else
          report.info(s"Could not extract delimiter from CsvOpts (using comma): ${optsExpr.show}")
          '{ ',' }
    end match
  end extractDelimiter

  private transparent inline def readHeaderlineAsCsv(path: String, optsExpr: Expr[CsvOpts])(using q: Quotes) =
    import q.reflect.*
    import io.github.quafadas.scautable.HeaderOptions.*

    val csvHeadersExpr = extractHeaderOptions(optsExpr)
    val typeInferrerExpr = extractTypeInferrer(optsExpr)
    val delimiterExpr = extractDelimiter(optsExpr)

    // Extract value for compile-time processing
    val csvHeaders: HeaderOptions = csvHeadersExpr.valueOrAbort
    val delimiter: Char = delimiterExpr.valueOrAbort

    val source = Source.fromFile(path)
    val lineIterator: Iterator[String] = source.getLines()
    val (headers, iter) = lineIterator.headers(csvHeaders, delimiter)

    if headers.length != headers.distinct.length then report.info("Possible duplicated headers detected.")
    end if

    val headerTupleExpr = Expr.ofTupleFromSeq(headers.map(Expr(_)))

    def constructWithTypes[Hdrs <: Tuple: Type, Data <: Tuple: Type]: Expr[CsvIterator[Hdrs, Data]] =
      val filePathExpr = Expr(path)
      '{
        val lines = scala.io.Source.fromFile($filePathExpr).getLines()
        val (headers, iterator) = lines.headers(${ csvHeadersExpr }, ${ delimiterExpr })
        new CsvIterator[Hdrs, Data](iterator, headers, ${ delimiterExpr })
      }
    end constructWithTypes

    headerTupleExpr match
      case '{ $tup: hdrs } =>
        typeInferrerExpr match

          case '{ TypeInferrer.FromTuple[t]() } =>
            constructWithTypes[hdrs & Tuple, t & Tuple]

          case '{ TypeInferrer.StringType } =>
            constructWithTypes[hdrs & Tuple, StringyTuple[hdrs & Tuple] & Tuple]

          case '{ TypeInferrer.FirstRow } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, true, delimiter = delimiter)
            inferredTypeRepr.asType match
              case '[v] =>
                constructWithTypes[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FromAllRows } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, false, Int.MaxValue, delimiter)
            inferredTypeRepr.asType match
              case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FirstN(${ Expr(n) }) } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, true, n, delimiter)
            inferredTypeRepr.asType match
              case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FirstN(${ Expr(n) }, ${ Expr(preferIntToBoolean) }) } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, preferIntToBoolean, n, delimiter)
            inferredTypeRepr.asType match
              case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
            end match

      case _ =>
        report.throwError("Could not infer literal header tuple.")
    end match

  end readHeaderlineAsCsv

  private def readCsvFromUrl(pathExpr: Expr[String], optsExpr: Expr[CsvOpts])(using Quotes) =
    import quotes.reflect.*

    report.warning(
      "This method saves the CSV to a local temp file and opens it. There may be performance implications - it is recommended to use one of the other methods where possible."
    )
    val source = Source.fromURL(pathExpr.valueOrAbort)
    val tmpPath = os.temp(dir = os.pwd, prefix = "temp_csv_", suffix = ".csv")
    os.write.over(tmpPath, source.toArray.mkString)
    readHeaderlineAsCsv(tmpPath.toString, optsExpr)

  end readCsvFromUrl

  private def readCsvFromCurrentDir(pathExpr: Expr[String], optsExpr: Expr[CsvOpts])(using Quotes) =
    val path = os.pwd / pathExpr.valueOrAbort
    readHeaderlineAsCsv(path.toString, optsExpr)
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

    // Extract value for compile-time processing
    val csvHeaders: HeaderOptions = csvHeadersExpr.valueOrAbort
    val delimiter: Char = delimiterExpr.valueOrAbort

    val content = csvContentExpr.valueOrAbort

    if content.trim.isEmpty then report.throwError("Empty CSV content provided.")
    end if

    val lines = content.linesIterator
    val (headers, iter) = lines.headers(csvHeaders, delimiter)

    if headers.length != headers.distinct.length then report.info("Possible duplicated headers detected.")

    end if

    val headerTupleExpr = Expr.ofTupleFromSeq(headers.map(Expr(_)))

    def constructWithTypes[Hdrs <: Tuple: Type, Data <: Tuple: Type]: Expr[CsvIterator[Hdrs, Data]] =
      '{
        val content = $csvContentExpr
        val lines = content.linesIterator
        val (headers, iterator) = lines.headers($csvHeadersExpr, $delimiterExpr)
        new CsvIterator[Hdrs, Data](iterator, headers, $delimiterExpr)
      }

    headerTupleExpr match
      case '{ $tup: hdrs } =>
        typeInferrerExpr match

          case '{ TypeInferrer.FromTuple[t]() } =>
            constructWithTypes[hdrs & Tuple, t & Tuple]

          case '{ TypeInferrer.StringType } =>
            constructWithTypes[hdrs & Tuple, StringyTuple[hdrs & Tuple] & Tuple]

          case '{ TypeInferrer.FirstRow } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, true, delimiter = delimiter)
            inferredTypeRepr.asType match
              case '[v] =>
                constructWithTypes[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FromAllRows } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, false, Int.MaxValue, delimiter)
            inferredTypeRepr.asType match
              case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FirstN(${ Expr(n) }) } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, true, n, delimiter)
            inferredTypeRepr.asType match
              case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FirstN(${ Expr(n) }, ${ Expr(preferIntToBoolean) }) } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, preferIntToBoolean, n, delimiter)
            inferredTypeRepr.asType match
              case '[v] => constructWithTypes[hdrs & Tuple, v & Tuple]
            end match

      case _ =>
        report.throwError("Could not infer literal header tuple.")
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
  inline def fromTyped[K <: Tuple, V <: Tuple]: os.Path => CsvIterator[K, V] = fromTyped[K, V](HeaderOptions.Default)

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
  private inline def fromTyped[K <: Tuple, V <: Tuple](inline headers: HeaderOptions): os.Path => CsvIterator[K, V] =
    (path: os.Path) =>
      val lines = scala.io.Source.fromFile(path.toIO).getLines()
      val (hdrs, iterator) = lines.headers(headers)
      val expectedHeaders = scala.compiletime.constValueTuple[K].toArray.toSeq.asInstanceOf[Seq[String]]
      hdrs.zip(expectedHeaders).zipWithIndex.foreach { case ((a, b), idx) =>
        if a != b then
          throw new IllegalStateException(
            s"CSV headers do not match expected headers. Expected: $expectedHeaders, Got: $hdrs. Header mismatch at index $idx: expected '$b', got '$a'"
          )
      }

      if hdrs.length != expectedHeaders.length then
        throw new IllegalStateException(s"You provided: ${expectedHeaders.size} but ${hdrs.size} headers were found in the file at ${path.toString}.")
      end if

      val sizeOfV = scala.compiletime.constValue[Tuple.Size[V]]
      if hdrs.length != sizeOfV then
        throw new IllegalStateException(s"Number of headers in CSV (${hdrs.length}) does not match number (${sizeOfV}) of types provided for decoding.")
      end if

      new CsvIterator[K, V](iterator, hdrs)

end CSV
