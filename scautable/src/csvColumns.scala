package io.github.quafadas.scautable

import scala.NamedTuple.*
import scala.compiletime.*
import scala.quoted.*
import scala.reflect.ClassTag

import io.github.quafadas.scautable.ColumnTyped.*
import io.github.quafadas.scautable.HeaderOptions.headers
import io.github.quafadas.table.TypeInferrer

/** Decoder for converting a column of strings to a typed array. */
private[scautable] trait ColumnDecoder[T]:
  def decodeColumn(values: scala.collection.mutable.ArrayBuffer[String]): Array[T]
end ColumnDecoder

private[scautable] object ColumnDecoder:
  import scala.collection.mutable.ArrayBuffer

  inline given intDecoder: ColumnDecoder[Int] with
    def decodeColumn(values: ArrayBuffer[String]): Array[Int] =
      val arr = new Array[Int](values.length)
      var i = 0
      while i < values.length do
        arr(i) = values(i).toInt
        i += 1
      end while
      arr
    end decodeColumn
  end intDecoder

  inline given longDecoder: ColumnDecoder[Long] with
    def decodeColumn(values: ArrayBuffer[String]): Array[Long] =
      val arr = new Array[Long](values.length)
      var i = 0
      while i < values.length do
        arr(i) = values(i).toLong
        i += 1
      end while
      arr
    end decodeColumn
  end longDecoder

  inline given doubleDecoder: ColumnDecoder[Double] with
    def decodeColumn(values: ArrayBuffer[String]): Array[Double] =
      val arr = new Array[Double](values.length)
      var i = 0
      while i < values.length do
        arr(i) = values(i).toDouble
        i += 1
      end while
      arr
    end decodeColumn
  end doubleDecoder

  inline given booleanDecoder: ColumnDecoder[Boolean] with
    def decodeColumn(values: ArrayBuffer[String]): Array[Boolean] =
      val arr = new Array[Boolean](values.length)
      var i = 0
      while i < values.length do
        val s = values(i).toLowerCase
        arr(i) = s == "true" || s == "1"
        i += 1
      end while
      arr
    end decodeColumn
  end booleanDecoder

  inline given stringDecoder: ColumnDecoder[String] with
    def decodeColumn(values: ArrayBuffer[String]): Array[String] =
      values.toArray
  end stringDecoder

  inline given optionDecoder[T](using d: ColumnDecoder[T], ct: ClassTag[Option[T]]): ColumnDecoder[Option[T]] with
    def decodeColumn(values: ArrayBuffer[String]): Array[Option[T]] =
      // For Option types, we need to handle empty strings specially
      val nonEmpty = ArrayBuffer[String]()
      val emptyIndices = ArrayBuffer[Int]()
      var i = 0
      while i < values.length do
        if values(i).isEmpty then
          emptyIndices += i
          nonEmpty += "0" // placeholder for decoding (will be replaced with None)
        else nonEmpty += values(i)
        end if
        i += 1
      end while

      // Decode non-empty values
      val decoded = d.decodeColumn(nonEmpty)
      val result = new Array[Option[T]](values.length)

      i = 0
      var emptyIdx = 0
      while i < values.length do
        if emptyIdx < emptyIndices.length && emptyIndices(emptyIdx) == i then
          result(i) = None
          emptyIdx += 1
        else result(i) = Some(decoded(i))
        end if
        i += 1
      end while
      result
    end decodeColumn
  end optionDecoder
end ColumnDecoder

/** Helper object for decoding columns at runtime using compile-time derived decoders */
private[scautable] object ColumnsDecoder:
  import scala.collection.mutable.ArrayBuffer

  /** Decode columns recursively. V is the tuple of Array types (e.g., (Array[Int], Array[String])). */
  inline def decodeAllColumns[V <: Tuple](buffers: Array[ArrayBuffer[String]], idx: Int = 0): V =
    inline erasedValue[V] match
      case _: EmptyTuple =>
        EmptyTuple.asInstanceOf[V]
      case _: (Array[h] *: t) =>
        val decoder = summonInline[ColumnDecoder[h]]
        val head: Array[h] = decoder.decodeColumn(buffers(idx))
        val tail = decodeAllColumns[t](buffers, idx + 1)
        (head *: tail).asInstanceOf[V]

end ColumnsDecoder

/** Object for reading CSV files into column-oriented NamedTuples.
  *
  * Unlike `CSV` which returns an iterator of rows, this reads all data into memory as arrays, one per column. This is more efficient for columnar analytics.
  *
  * Example:
  * {{{
  * val cols = CsvColumnsReader.resource("employees.csv")
  * val names: Array[String] = cols.name
  * val ages: Array[Int] = cols.age
  * }}}
  */
object CsvColumnsReader:

  /** Reads a CSV from java resources and returns column-oriented data as a NamedTuple of Arrays. */
  transparent inline def resource[T](inline csvPath: String): Any = resource[T](csvPath, CsvOpts.default)

  transparent inline def resource[T](inline csvPath: String, inline headers: HeaderOptions): Any = resource[T](csvPath, CsvOpts(headers))

  transparent inline def resource[T](inline csvPath: String, inline dataType: TypeInferrer): Any = resource[T](csvPath, CsvOpts.apply(dataType))

  transparent inline def resource[T](inline csvPath: String, inline headers: HeaderOptions, inline dataType: TypeInferrer): Any =
    resource[T](csvPath, CsvOpts(headers, dataType))

  transparent inline def resource[T](inline path: String, inline opts: CsvOpts) = ${ readCsvResourceAsColumns('path, 'opts) }

  /** Reads a CSV from an absolute path and returns column-oriented data as a NamedTuple of Arrays. */
  transparent inline def absolutePath[T](inline csvPath: String): Any = absolutePath[T](csvPath, CsvOpts.default)

  transparent inline def absolutePath[T](inline csvPath: String, inline headers: HeaderOptions): Any = absolutePath[T](csvPath, CsvOpts(headers))

  transparent inline def absolutePath[T](inline csvPath: String, inline dataType: TypeInferrer): Any = absolutePath[T](csvPath, CsvOpts.apply(dataType))

  transparent inline def absolutePath[T](inline csvPath: String, inline headers: HeaderOptions, inline dataType: TypeInferrer): Any =
    absolutePath[T](csvPath, CsvOpts(headers, dataType))

  transparent inline def absolutePath[T](inline path: String, inline opts: CsvOpts) = ${ readCsvAbsolutePathAsColumns('path, 'opts) }

  /** Reads a CSV from a string and returns column-oriented data as a NamedTuple of Arrays. */
  transparent inline def fromString[T](inline csvContent: String): Any = fromString[T](csvContent, CsvOpts.default)

  transparent inline def fromString[T](inline csvContent: String, inline headers: HeaderOptions): Any = fromString[T](csvContent, CsvOpts(headers))

  transparent inline def fromString[T](inline csvContent: String, inline dataType: TypeInferrer): Any = fromString[T](csvContent, CsvOpts.apply(dataType))

  transparent inline def fromString[T](inline csvContent: String, inline headers: HeaderOptions, inline dataType: TypeInferrer): Any =
    fromString[T](csvContent, CsvOpts(headers, dataType))

  transparent inline def fromString[T](inline csvContent: String, inline opts: CsvOpts) = ${ readCsvFromStringAsColumns('csvContent, 'opts) }

  // Macro implementations

  private def readCsvResourceAsColumns(pathExpr: Expr[String], optsExpr: Expr[CsvOpts])(using Quotes) =
    import quotes.reflect.*

    val path = pathExpr.valueOrAbort
    val resourcePath = this.getClass.getClassLoader.getResource(path)
    if resourcePath == null then report.throwError(s"Resource not found: $path")
    end if

    readCsvAsColumnsImpl(Expr(resourcePath.getPath), optsExpr)
  end readCsvResourceAsColumns

  private def readCsvAbsolutePathAsColumns(pathExpr: Expr[String], optsExpr: Expr[CsvOpts])(using Quotes) =
    readCsvAsColumnsImpl(pathExpr, optsExpr)

  private def readCsvFromStringAsColumns(csvContentExpr: Expr[String], optsExpr: Expr[CsvOpts])(using Quotes) =
    import quotes.reflect.*

    val csvHeadersExpr = CSV.extractHeaderOptions(optsExpr)
    val typeInferrerExpr = CSV.extractTypeInferrer(optsExpr)
    val delimiterExpr = CSV.extractDelimiter(optsExpr)

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

    // Convert a value type tuple (Int, String, Double) to array type tuple (Array[Int], Array[String], Array[Double])
    def toArrayTupleType(valueTypeRepr: TypeRepr): TypeRepr =
      valueTypeRepr.asType match
        case '[EmptyTuple] => TypeRepr.of[EmptyTuple]
        case '[h *: t]     =>
          val headArrayType = TypeRepr.of[Array].appliedTo(TypeRepr.of[h])
          val tailArrayType = toArrayTupleType(TypeRepr.of[t])
          TypeRepr.of[*:].appliedTo(List(headArrayType, tailArrayType))

    def constructWithTypes[Hdrs <: Tuple: Type, ArrayData <: Tuple: Type]: Expr[NamedTuple[Hdrs, ArrayData]] =
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

    headerTupleExpr match
      case '{ $tup: hdrs } =>
        typeInferrerExpr match

          case '{ TypeInferrer.FromTuple[t]() } =>
            val arrayTypeRepr = toArrayTupleType(TypeRepr.of[t])
            arrayTypeRepr.asType match
              case '[arrTup] => constructWithTypes[hdrs & Tuple, arrTup & Tuple]
            end match

          case '{ TypeInferrer.StringType } =>
            val stringyType = TypeRepr.of[StringyTuple[hdrs & Tuple]]
            val arrayTypeRepr = toArrayTupleType(stringyType)
            arrayTypeRepr.asType match
              case '[arrTup] => constructWithTypes[hdrs & Tuple, arrTup & Tuple]
            end match

          case '{ TypeInferrer.FirstRow } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, true, delimiter = delimiter)
            val arrayTypeRepr = toArrayTupleType(inferredTypeRepr)
            arrayTypeRepr.asType match
              case '[arrTup] => constructWithTypes[hdrs & Tuple, arrTup & Tuple]
            end match

          case '{ TypeInferrer.FromAllRows } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, false, Int.MaxValue, delimiter)
            val arrayTypeRepr = toArrayTupleType(inferredTypeRepr)
            arrayTypeRepr.asType match
              case '[arrTup] => constructWithTypes[hdrs & Tuple, arrTup & Tuple]
            end match

          case '{ TypeInferrer.FirstN(${ Expr(n) }) } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, true, n, delimiter)
            val arrayTypeRepr = toArrayTupleType(inferredTypeRepr)
            arrayTypeRepr.asType match
              case '[arrTup] => constructWithTypes[hdrs & Tuple, arrTup & Tuple]
            end match

          case '{ TypeInferrer.FirstN(${ Expr(n) }, ${ Expr(preferIntToBoolean) }) } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, preferIntToBoolean, n, delimiter)
            val arrayTypeRepr = toArrayTupleType(inferredTypeRepr)
            arrayTypeRepr.asType match
              case '[arrTup] => constructWithTypes[hdrs & Tuple, arrTup & Tuple]
            end match

      case _ =>
        report.throwError("Could not infer literal header tuple.")
    end match
  end readCsvFromStringAsColumns

  private def readCsvAsColumnsImpl(pathExpr: Expr[String], optsExpr: Expr[CsvOpts])(using Quotes) =
    import quotes.reflect.*

    val csvHeadersExpr = CSV.extractHeaderOptions(optsExpr)
    val typeInferrerExpr = CSV.extractTypeInferrer(optsExpr)
    val delimiterExpr = CSV.extractDelimiter(optsExpr)

    val csvHeaders: HeaderOptions = csvHeadersExpr.valueOrAbort
    val delimiter: Char = delimiterExpr.valueOrAbort

    val path = pathExpr.valueOrAbort
    val source = scala.io.Source.fromFile(path)
    val lines = source.getLines()
    val (headers, iter) = lines.headers(csvHeaders, delimiter)

    if headers.length != headers.distinct.length then report.info("Possible duplicated headers detected.")
    end if

    val headerTupleExpr = Expr.ofTupleFromSeq(headers.map(Expr(_)))

    // Convert a value type tuple (Int, String, Double) to array type tuple (Array[Int], Array[String], Array[Double])
    def toArrayTupleType(valueTypeRepr: TypeRepr): TypeRepr =
      valueTypeRepr.asType match
        case '[EmptyTuple] => TypeRepr.of[EmptyTuple]
        case '[h *: t]     =>
          val headArrayType = TypeRepr.of[Array].appliedTo(TypeRepr.of[h])
          val tailArrayType = toArrayTupleType(TypeRepr.of[t])
          TypeRepr.of[*:].appliedTo(List(headArrayType, tailArrayType))

    def constructWithTypes[Hdrs <: Tuple: Type, ArrayData <: Tuple: Type]: Expr[NamedTuple[Hdrs, ArrayData]] =
      '{
        val source = scala.io.Source.fromFile($pathExpr)
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

    headerTupleExpr match
      case '{ $tup: hdrs } =>
        typeInferrerExpr match

          case '{ TypeInferrer.FromTuple[t]() } =>
            val arrayTypeRepr = toArrayTupleType(TypeRepr.of[t])
            arrayTypeRepr.asType match
              case '[arrTup] => constructWithTypes[hdrs & Tuple, arrTup & Tuple]
            end match

          case '{ TypeInferrer.StringType } =>
            val stringyType = TypeRepr.of[StringyTuple[hdrs & Tuple]]
            val arrayTypeRepr = toArrayTupleType(stringyType)
            arrayTypeRepr.asType match
              case '[arrTup] => constructWithTypes[hdrs & Tuple, arrTup & Tuple]
            end match

          case '{ TypeInferrer.FirstRow } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, true, delimiter = delimiter)
            val arrayTypeRepr = toArrayTupleType(inferredTypeRepr)
            arrayTypeRepr.asType match
              case '[arrTup] => constructWithTypes[hdrs & Tuple, arrTup & Tuple]
            end match

          case '{ TypeInferrer.FromAllRows } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, false, Int.MaxValue, delimiter)
            val arrayTypeRepr = toArrayTupleType(inferredTypeRepr)
            arrayTypeRepr.asType match
              case '[arrTup] => constructWithTypes[hdrs & Tuple, arrTup & Tuple]
            end match

          case '{ TypeInferrer.FirstN(${ Expr(n) }) } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, true, n, delimiter)
            val arrayTypeRepr = toArrayTupleType(inferredTypeRepr)
            arrayTypeRepr.asType match
              case '[arrTup] => constructWithTypes[hdrs & Tuple, arrTup & Tuple]
            end match

          case '{ TypeInferrer.FirstN(${ Expr(n) }, ${ Expr(preferIntToBoolean) }) } =>
            val inferredTypeRepr = InferrerOps.inferrer(iter, preferIntToBoolean, n, delimiter)
            val arrayTypeRepr = toArrayTupleType(inferredTypeRepr)
            arrayTypeRepr.asType match
              case '[arrTup] => constructWithTypes[hdrs & Tuple, arrTup & Tuple]
            end match

      case _ =>
        report.throwError("Could not infer literal header tuple.")
    end match
  end readCsvAsColumnsImpl

end CsvColumnsReader
