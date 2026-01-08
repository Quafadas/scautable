package io.github.quafadas.scautable.json

import scala.io.Source
import scala.NamedTuple.*
import scala.quoted.*
import StreamingJsonParser.*

import io.github.quafadas.table.TypeInferrer
import io.github.quafadas.scautable.RowDecoder

/** JSON parsing utilities for creating typed iterators from JSON arrays of flat objects.
  *
  * Similar to CSV, this object provides compile-time methods to parse JSON arrays where:
  *   - The JSON is a flat array of objects
  *   - Each object has the same structure (no nesting)
  *   - Type inference happens at compile time
  *
  * Example JSON:
  * ```json
  * [
  *   {"a": 1, "b": 2},
  *   {"a": 5, "b": 3}
  * ]
  * ```
  */
object JsonTable:

  /** Reads JSON from a String and returns a [[JsonIterator]].
    *
    * Example:
    * {{{
    * val jsonContent = """[{"a":1,"b":2},{"a":5,"b":3}]"""
    * val json: JsonIterator[("a", "b"), (Int, Int)] = JSON.fromString(jsonContent)
    * }}}
    */
  transparent inline def fromString[T](inline jsonContent: String): Any =
    fromString[T](jsonContent, TypeInferrer.FromAllRows)

  transparent inline def fromString[T](inline jsonContent: String, inline dataType: TypeInferrer): Any =
    ${ readJsonFromString('jsonContent, 'dataType) }

  /** Reads a JSON file present in java resources and returns a [[JsonIterator]].
    *
    * Example:
    * {{{
    * val json: JsonIterator[("a", "b"), (Int, Int)] = JSON.resource("data.json")
    * }}}
    */
  transparent inline def resource[T](inline jsonPath: String): Any =
    resource[T](jsonPath, TypeInferrer.FromAllRows)

  transparent inline def resource[T](inline jsonPath: String, inline dataType: TypeInferrer): Any =
    ${ readJsonResource('jsonPath, 'dataType) }

  /** Reads a JSON file from an absolute path and returns a [[JsonIterator]].
    *
    * Example:
    * {{{
    * val json: JsonIterator[("a", "b"), (Int, Int)] = JSON.absolutePath("/absolute/path/to/file.json")
    * }}}
    */
  transparent inline def absolutePath[T](inline jsonPath: String): Any =
    absolutePath[T](jsonPath, TypeInferrer.FromAllRows)

  transparent inline def absolutePath[T](inline jsonPath: String, inline dataType: TypeInferrer): Any =
    ${ readJsonAbsolutePath('jsonPath, 'dataType) }

  /** Reads a JSON file present in the current _compiler_ working directory and returns a [[JsonIterator]].
    *
    * Example:
    * {{{
    * val json: JsonIterator[("a", "b"), (Int, Int)] = JSON.pwd("data.json")
    * }}}
    */
  transparent inline def pwd[T](inline jsonPath: String): Any =
    pwd[T](jsonPath, TypeInferrer.FromAllRows)

  transparent inline def pwd[T](inline jsonPath: String, inline dataType: TypeInferrer): Any =
    ${ readJsonFromCurrentDir('jsonPath, 'dataType) }

  /** Saves a URL to a local JSON file and returns a [[JsonIterator]].
    *
    * Example:
    * {{{
    * val json: JsonIterator[("a", "b"), (Int, Int)] = JSON.url("https://somewhere.com/file.json")
    * }}}
    */
  transparent inline def url[T](inline jsonUrl: String): Any =
    url[T](jsonUrl, TypeInferrer.FromAllRows)

  transparent inline def url[T](inline jsonUrl: String, inline dataType: TypeInferrer): Any =
    ${ readJsonFromUrl('jsonUrl, 'dataType) }

  private def readJsonFromString(jsonContentExpr: Expr[String], typeInferrerExpr: Expr[TypeInferrer])(using Quotes) =
    import quotes.reflect.*

    val content = jsonContentExpr.valueOrAbort

    if content.trim.isEmpty then report.throwError("Empty JSON content provided.")
    end if

    // For fromString, parse using string-based parser
    val objects = StreamingJsonParser.parseArrayFromString(content)
    processJsonArrayFromStream(objects, jsonContentExpr, typeInferrerExpr, isResource = false, isString = true)
  end readJsonFromString

  private def readJsonResource(pathExpr: Expr[String], typeInferrerExpr: Expr[TypeInferrer])(using Quotes) =
    import quotes.reflect.*

    val path = pathExpr.valueOrAbort
    val resourcePath = this.getClass.getClassLoader.getResource(path)
    if resourcePath == null then report.throwError(s"Resource not found: $path")
    end if

    // Use streaming for compile-time type inference
    val source = Source.fromURL(resourcePath)
    try
      val objects = StreamingJsonParser.parseArrayFromSource(source)
      processJsonArrayFromFile(objects, pathExpr, typeInferrerExpr, isResource = true)
    finally source.close()
    end try
  end readJsonResource

  private def readJsonAbsolutePath(pathExpr: Expr[String], typeInferrerExpr: Expr[TypeInferrer])(using Quotes) =
    import quotes.reflect.*

    val path = pathExpr.valueOrAbort
    val source =
      try Source.fromFile(path)
      catch
        case e: Exception =>
          report.throwError(s"Failed to read file at path $path: ${e.getMessage}")

    try
      val objects = StreamingJsonParser.parseArrayFromSource(source)
      processJsonArrayFromFile(objects, pathExpr, typeInferrerExpr, isResource = false)
    finally source.close()
    end try
  end readJsonAbsolutePath

  private def readJsonFromCurrentDir(pathExpr: Expr[String], typeInferrerExpr: Expr[TypeInferrer])(using Quotes) =
    val path = os.pwd / pathExpr.valueOrAbort
    val pathStringExpr = Expr(path.toString)
    readJsonAbsolutePath(pathStringExpr, typeInferrerExpr)
  end readJsonFromCurrentDir

  private def readJsonFromUrl(urlExpr: Expr[String], typeInferrerExpr: Expr[TypeInferrer])(using Quotes) =
    import quotes.reflect.*

    report.warning(
      "This method saves the JSON to a local temp file and opens it. There may be performance implications - it is recommended to use one of the other methods where possible."
    )

    val source = Source.fromURL(urlExpr.valueOrAbort)
    val tmpPath = os.temp(dir = os.pwd, prefix = "temp_json_", suffix = ".json")
    os.write.over(tmpPath, source.mkString)

    val pathExpr = Expr(tmpPath.toString)
    readJsonAbsolutePath(pathExpr, typeInferrerExpr)
  end readJsonFromUrl

  private def processJsonArrayFromStream(
      objectsIter: Iterator[JsonObject],
      jsonContentExpr: Expr[String],
      typeInferrerExpr: Expr[TypeInferrer],
      isResource: Boolean,
      isString: Boolean
  )(using Quotes) =
    import quotes.reflect.*

    // For compile-time type inference, we need to consume the iterator
    // We'll buffer it so we can use it twice (once for headers, once for inference)
    val objects = objectsIter.toList

    // Extract headers
    val headers = JsonInferrerOps.extractHeaders(objects.iterator)
    val headerTupleExpr = Expr.ofTupleFromSeq(headers.map(Expr(_)))

    def constructIterator[Hdrs <: Tuple: Type, Data <: Tuple: Type]: Expr[JsonIterator[Hdrs, Data]] =
      '{
        val content = $jsonContentExpr
        val objects = StreamingJsonParser.parseArrayFromString(content)
        new JsonIterator[Hdrs, Data](objects, ${ Expr.ofSeq(headers.map(Expr(_))) }.toSeq)
      }
    end constructIterator

    headerTupleExpr match
      case '{ $tup: hdrs } =>
        typeInferrerExpr match
          case '{ TypeInferrer.FromTuple[t]() } =>
            constructIterator[hdrs & Tuple, t & Tuple]

          case '{ TypeInferrer.StringType } =>
            // Build a tuple of all String types
            val stringTypes = headers.map(_ => TypeRepr.of[String])
            val stringTupleType = stringTypes.foldRight(TypeRepr.of[EmptyTuple]) { (tpe, acc) =>
              TypeRepr.of[*:].appliedTo(List(tpe, acc))
            }
            stringTupleType.asType match
              case '[v] => constructIterator[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FirstRow } =>
            val inferredTypeRepr = JsonInferrerOps.inferrer(objects.iterator, preferIntToBoolean = true, numRows = 1)
            inferredTypeRepr.asType match
              case '[v] => constructIterator[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FromAllRows } =>
            val inferredTypeRepr = JsonInferrerOps.inferrer(objects.iterator, preferIntToBoolean = false, numRows = Int.MaxValue)
            inferredTypeRepr.asType match
              case '[v] => constructIterator[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FirstN(${ Expr(n) }) } =>
            val inferredTypeRepr = JsonInferrerOps.inferrer(objects.iterator, preferIntToBoolean = true, numRows = n)
            inferredTypeRepr.asType match
              case '[v] => constructIterator[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FirstN(${ Expr(n) }, ${ Expr(preferIntToBoolean) }) } =>
            val inferredTypeRepr = JsonInferrerOps.inferrer(objects.iterator, preferIntToBoolean, numRows = n)
            inferredTypeRepr.asType match
              case '[v] => constructIterator[hdrs & Tuple, v & Tuple]
            end match

      case _ =>
        report.throwError("Could not infer literal header tuple.")
    end match
  end processJsonArrayFromStream

  /** Process JSON array from a file path - generates code that reads file at runtime using streaming This avoids loading large JSON files entirely into memory at compile time
    */
  private def processJsonArrayFromFile(
      objectsIter: Iterator[JsonObject],
      pathExpr: Expr[String],
      typeInferrerExpr: Expr[TypeInferrer],
      isResource: Boolean
  )(using Quotes) =
    import quotes.reflect.*

    // For compile-time type inference, determine how many rows to read based on TypeInferrer
    val numRowsForInference = typeInferrerExpr match
      case '{ TypeInferrer.FirstRow }                => 1
      case '{ TypeInferrer.FirstN(${ Expr(n) }) }    => n
      case '{ TypeInferrer.FirstN(${ Expr(n) }, _) } => n
      case _                                         => 1000 // Default limit for FromAllRows at compile time

    // Read limited number of objects for type inference at compile time
    val objects = objectsIter.take(numRowsForInference).toList

    // Extract headers at compile time
    val headers = JsonInferrerOps.extractHeaders(objects.iterator)
    val headerTupleExpr = Expr.ofTupleFromSeq(headers.map(Expr(_)))

    def constructIterator[Hdrs <: Tuple: Type, Data <: Tuple: Type]: Expr[JsonIterator[Hdrs, Data]] =
      if isResource then
        '{
          val path = $pathExpr
          val resourceUrl = this.getClass.getClassLoader.getResource(path)
          if resourceUrl == null then throw new RuntimeException(s"Resource not found: $path")
          val inputStream = resourceUrl.openStream()
          val objects = StreamingJsonParser.parseArrayStream(inputStream)
          new JsonIterator[Hdrs, Data](objects, ${ Expr.ofSeq(headers.map(Expr(_))) }.toSeq)
        }
      else
        '{
          val path = $pathExpr
          val inputStream = new java.io.FileInputStream(path)
          val objects = StreamingJsonParser.parseArrayStream(inputStream)
          new JsonIterator[Hdrs, Data](objects, ${ Expr.ofSeq(headers.map(Expr(_))) }.toSeq)
        }
    end constructIterator

    headerTupleExpr match
      case '{ $tup: hdrs } =>
        typeInferrerExpr match
          case '{ TypeInferrer.FromTuple[t]() } =>
            constructIterator[hdrs & Tuple, t & Tuple]

          case '{ TypeInferrer.StringType } =>
            // Build a tuple of all String types
            val stringTypes = headers.map(_ => TypeRepr.of[String])
            val stringTupleType = stringTypes.foldRight(TypeRepr.of[EmptyTuple]) { (tpe, acc) =>
              TypeRepr.of[*:].appliedTo(List(tpe, acc))
            }
            stringTupleType.asType match
              case '[v] => constructIterator[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FirstRow } =>
            val inferredTypeRepr = JsonInferrerOps.inferrer(objects.iterator, preferIntToBoolean = true, numRows = 1)
            inferredTypeRepr.asType match
              case '[v] => constructIterator[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FromAllRows } =>
            // Use the sampled objects for type inference
            val inferredTypeRepr = JsonInferrerOps.inferrer(objects.iterator, preferIntToBoolean = false, numRows = Int.MaxValue)
            inferredTypeRepr.asType match
              case '[v] => constructIterator[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FirstN(${ Expr(n) }) } =>
            val inferredTypeRepr = JsonInferrerOps.inferrer(objects.iterator, preferIntToBoolean = true, numRows = n)
            inferredTypeRepr.asType match
              case '[v] => constructIterator[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FirstN(${ Expr(n) }, ${ Expr(preferIntToBoolean) }) } =>
            val inferredTypeRepr = JsonInferrerOps.inferrer(objects.iterator, preferIntToBoolean, numRows = n)
            inferredTypeRepr.asType match
              case '[v] => constructIterator[hdrs & Tuple, v & Tuple]
            end match

      case _ =>
        report.throwError("Could not infer literal header tuple.")
    end match
  end processJsonArrayFromFile

  /** Creates a function that reads a JSON file from a runtime path and returns a [[JsonIterator]].
    *
    * Unlike other JSON methods that require the file path at compile time, this method allows you to specify the column types at compile time but provide the file path at runtime. This is useful when
    * you know the structure of a JSON file in advance but the actual file location is determined at runtime.
    *
    * Example:
    * {{{
    *   val jsonReader = JsonTable.fromTyped[("name", "age", "salary"), (String, Int, Double)]
    *   val data = jsonReader(os.pwd / "employees.json")
    *   // data is a JsonIterator[("name", "age", "salary"), (String, Int, Double)]
    * }}}
    *
    * @tparam K
    *   A tuple of string literal types representing the column names (JSON field keys)
    * @tparam V
    *   A tuple of types representing the column value types (must have RowDecoders available)
    * @return
    *   A function that takes an os.Path and returns a JsonIterator with the specified types
    */
  inline def fromTyped[K <: Tuple, V <: Tuple](using decoder: RowDecoder[V]): os.Path => JsonIterator[K, V] =
    (path: os.Path) =>
      val inputStream = new java.io.FileInputStream(path.toIO)
      val objects = StreamingJsonParser.parseArrayStream(inputStream)

      // Peek at first object to validate headers match expected
      val bufferedObjects = objects.buffered
      if !bufferedObjects.hasNext then throw new IllegalStateException(s"JSON file at ${path.toString} contains no objects")

      val firstObj = bufferedObjects.head
      val actualHeaders = firstObj.fields.keys.toSeq
      val expectedHeaders = scala.compiletime.constValueTuple[K].toArray.toSeq.asInstanceOf[Seq[String]]

      // Validate headers match (order doesn't need to match for JSON, but all expected fields must be present)
      val missingHeaders = expectedHeaders.filterNot(actualHeaders.contains)
      if missingHeaders.nonEmpty then
        throw new IllegalStateException(
          s"JSON object missing expected fields. Expected: $expectedHeaders, Got: $actualHeaders. Missing: $missingHeaders"
        )
      end if

      val sizeOfV = scala.compiletime.constValue[Tuple.Size[V]]
      if expectedHeaders.length != sizeOfV then
        throw new IllegalStateException(s"Number of expected headers (${expectedHeaders.length}) does not match number (${sizeOfV}) of types provided for decoding.")
      end if

      new JsonIterator[K, V](bufferedObjects, expectedHeaders)

end JsonTable
