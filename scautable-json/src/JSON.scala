package io.github.quafadas.scautable.json

import scala.io.Source
import scala.NamedTuple.*
import scala.quoted.*
import ujson.*

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
object JSON:

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

    val parsed =
      try ujson.read(content)
      catch
        case e: Exception =>
          report.throwError(s"Failed to parse JSON: ${e.getMessage}")

    parsed match
      case arr: Arr =>
        processJsonArray(arr, jsonContentExpr, typeInferrerExpr)
      case _ =>
        report.throwError("JSON must be an array of objects")
    end match
  end readJsonFromString

  private def readJsonResource(pathExpr: Expr[String], typeInferrerExpr: Expr[TypeInferrer])(using Quotes) =
    import quotes.reflect.*

    val path = pathExpr.valueOrAbort
    val resourcePath = this.getClass.getClassLoader.getResource(path)
    if resourcePath == null then report.throwError(s"Resource not found: $path")
    end if

    val content = Source.fromURL(resourcePath).mkString
    val contentExpr = Expr(content)
    readJsonFromString(contentExpr, typeInferrerExpr)
  end readJsonResource

  private def readJsonAbsolutePath(pathExpr: Expr[String], typeInferrerExpr: Expr[TypeInferrer])(using Quotes) =
    import quotes.reflect.*

    val path = pathExpr.valueOrAbort
    val content =
      try Source.fromFile(path).mkString
      catch
        case e: Exception =>
          report.throwError(s"Failed to read file at path $path: ${e.getMessage}")

    val contentExpr = Expr(content)
    readJsonFromString(contentExpr, typeInferrerExpr)
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

  private def processJsonArray(arr: Arr, jsonContentExpr: Expr[String], typeInferrerExpr: Expr[TypeInferrer])(using Quotes) =
    import quotes.reflect.*

    // Extract headers
    val headers = JsonInferrerOps.extractHeaders(arr)
    val headerTupleExpr = Expr.ofTupleFromSeq(headers.map(Expr(_)))

    def constructIterator[Hdrs <: Tuple: Type, Data <: Tuple: Type]: Expr[JsonIterator[Hdrs, Data]] =
      '{
        val content = $jsonContentExpr
        val parsed = ujson.read(content)
        val arr = parsed.arr
        val objects = arr.iterator.collect { case obj: Obj => obj }
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
            val inferredTypeRepr = JsonInferrerOps.inferrer(arr, preferIntToBoolean = true, numRows = 1)
            inferredTypeRepr.asType match
              case '[v] => constructIterator[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FromAllRows } =>
            val inferredTypeRepr = JsonInferrerOps.inferrer(arr, preferIntToBoolean = false, numRows = Int.MaxValue)
            inferredTypeRepr.asType match
              case '[v] => constructIterator[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FirstN(${ Expr(n) }) } =>
            val inferredTypeRepr = JsonInferrerOps.inferrer(arr, preferIntToBoolean = true, numRows = n)
            inferredTypeRepr.asType match
              case '[v] => constructIterator[hdrs & Tuple, v & Tuple]
            end match

          case '{ TypeInferrer.FirstN(${ Expr(n) }, ${ Expr(preferIntToBoolean) }) } =>
            val inferredTypeRepr = JsonInferrerOps.inferrer(arr, preferIntToBoolean, numRows = n)
            inferredTypeRepr.asType match
              case '[v] => constructIterator[hdrs & Tuple, v & Tuple]
            end match

      case _ =>
        report.throwError("Could not infer literal header tuple.")
    end match
  end processJsonArray

end JSON
