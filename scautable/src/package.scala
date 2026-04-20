package io.github.quafadas

import scala.quoted.*

object table:
  export io.github.quafadas.scautable.CSV

  export io.github.quafadas.scautable.HtmlRenderer
  export io.github.quafadas.scautable.CsvIterator
  export io.github.quafadas.scautable.Excel
  export io.github.quafadas.scautable.ExcelIterator
  export io.github.quafadas.scautable.Excel.*
  export io.github.quafadas.scautable.BadTableException
  export io.github.quafadas.scautable.CsvOpts

  export io.github.quafadas.scautable.ConsoleFormat.*
  export io.github.quafadas.scautable.NamedTupleIteratorExtensions.*
  export io.github.quafadas.scautable.CSVWriterExtensions.*
  export io.github.quafadas.scautable.Stats.*
  export io.github.quafadas.scautable.json.JsonTable

  // Excel requires it's own decoders
  export io.github.quafadas.scautable.ExcelDecoders.given

  /** Specifies how to read CSV data.
    *
    *   - `Rows`: Returns `CsvIterator[K, V]` - lazy row-by-row iteration
    *   - `Columns`: Returns `NamedTuple[K, (Array[T1], Array[T2], ...)]` - eager column arrays
    *   - `ArrayDenseColMajor[T]`: Returns a single dense array in column-major order
    *   - `ArrayDenseRowMajor[T]`: Returns a single dense array in row-major order
    */
  enum ReadAs:
    case Rows
    case Columns
    case ArrayDenseColMajor[T]()
    case ArrayDenseRowMajor[T]()
  end ReadAs

  object ReadAs:
    given FromExpr[ReadAs] with
      def unapply(x: Expr[ReadAs])(using Quotes): Option[ReadAs] =
        import quotes.reflect.*

        x match
          case '{ ReadAs.Rows }    => Some(ReadAs.Rows)
          case '{ ReadAs.Columns } => Some(ReadAs.Columns)
          case _                   =>
            def unwrapInlined(term: Term): Term = term match
              case Inlined(_, _, body) => unwrapInlined(body)
              case other               => other

            unwrapInlined(x.asTerm) match
              case Select(_, "Rows")    => Some(ReadAs.Rows)
              case Select(_, "Columns") => Some(ReadAs.Columns)
              case _                    => None
            end match
        end match
      end unapply
    end given
  end ReadAs

  /** Options for handling headers in tabular data.
    *
    * This enum provides different strategies for obtaining column headers when reading data from sources like CSV files or Excel spreadsheets.
    */
  enum HeaderOptions:
    /** Automatically generate column headers in the format "col_0", "col_1", etc. */
    case Auto

    /** Use manually specified headers.
      * @param headers
      *   The column names to use as headers
      */
    case Manual(headers: String*)

    /** Extract headers from the data rows.
      * @param merge
      *   Number of rows to merge to form the headers
      * @param dropFirst
      *   Number of rows to skip before reading header rows
      */
    case FromRows(merge: Int, dropFirst: Int = 0)
  end HeaderOptions

  object HeaderOptions:
    /** Default header option that reads headers from the first row.
      *
      * Equivalent to `HeaderOptions.FromRows(merge = 1, dropFirst = 0)`.
      */
    inline def Default: HeaderOptions = HeaderOptions.FromRows(merge = 1, dropFirst = 0)

    given FromExpr[HeaderOptions] with
      def unapply(x: Expr[HeaderOptions])(using Quotes): Option[HeaderOptions] =
        import quotes.reflect.*

        x match
          case '{ HeaderOptions.Auto } =>
            Some(HeaderOptions.Auto)

          case '{ HeaderOptions.Manual(${ Varargs(headers) }*) } =>
            val strings = headers.map(_.valueOrAbort)
            Some(HeaderOptions.Manual(strings*))

          case '{ HeaderOptions.FromRows(${ Expr(merge) }, ${ Expr(dropFirst) }) } =>
            Some(HeaderOptions.FromRows(merge, dropFirst))

          case '{ HeaderOptions.FromRows(${ Expr(merge) }) } =>
            Some(HeaderOptions.FromRows(merge))

          case '{ HeaderOptions.Default } => Some(HeaderOptions.Default)

          case x =>
            report.info(s"${x.show} from report.info")
            None
        end match
      end unapply
    end given

    given ToExpr[HeaderOptions] with
      def apply(opts: HeaderOptions)(using Quotes): Expr[HeaderOptions] =
        opts match
          case HeaderOptions.Auto =>
            '{ HeaderOptions.Auto }
          case HeaderOptions.Manual(headers*) =>
            val headersExprs = headers.map(h => Expr(h))
            '{ HeaderOptions.Manual(${ Varargs(headersExprs) }*) }
          case HeaderOptions.FromRows(merge, dropFirst) =>
            '{ HeaderOptions.FromRows(${ Expr(merge) }, ${ Expr(dropFirst) }) }
    end given
  end HeaderOptions

  /** Enumeration of strategies for inferring column types when reading CSV files.
    *
    *   - `FirstRow`: Infers types by inspecting only the first row of data.
    *   - `FirstN`: Infers types by inspecting the first `n` rows. Optionally, prefers `Int` over `Boolean` if both are possible.
    *   - `FromAllRows`: Infers types by inspecting all rows in the CSV.
    *   - `StringType`: Disables type inference; all columns are read as `String`.
    *   - `FromTuple[T]`: Uses a user-supplied tuple type `T` for decoding, allowing custom or manual type specification.
    */
  enum TypeInferrer:

    /** Infers column types by inspecting only the first row of data. Fast, but may be inaccurate if the first row is not representative.
      */
    case FirstRow

    /** Infers column types by inspecting the first `n` rows.
      * @param n
      *   Number of rows to inspect for type inference.
      * @param preferIntToBoolean
      *   If true, prefers `Int` over `Boolean` when both are possible.
      */
    case FirstN(n: Int, preferIntToBoolean: Boolean = true)

    /** Infers column types by inspecting all rows in the CSV. Most accurate, but may be slower for large files.
      */
    case FromAllRows

    /** Disables type inference; all columns are read as `String`. Use for maximum safety or when schema is unknown.
      */
    case StringType

    /** Uses a user-supplied tuple type `T` for decoding. Allows for custom or manual type specification, including custom decoders.
      * @tparam T
      *   The tuple type to use for decoding each row.
      */
    case FromTuple[T]()
  end TypeInferrer

  object TypeInferrer:
    given FromExpr[TypeInferrer] with
      def unapply(x: Expr[TypeInferrer])(using Quotes): Option[TypeInferrer] =
        import quotes.reflect.*

        x match
          case '{ TypeInferrer.FirstRow } =>
            Some(TypeInferrer.FirstRow)

          case '{ TypeInferrer.StringType } =>
            Some(TypeInferrer.StringType)

          case '{ TypeInferrer.FromAllRows } =>
            Some(TypeInferrer.FromAllRows)

          case '{ TypeInferrer.FirstN(${ Expr(n) }, ${ Expr(preferIntToBoolean) }) } =>
            Some(TypeInferrer.FirstN(n, preferIntToBoolean))

          case '{ TypeInferrer.FirstN(${ Expr(n) }) } =>
            Some(TypeInferrer.FirstN(n))

          case '{ TypeInferrer.FromTuple[t]() } =>
            Some(TypeInferrer.FromTuple())

          case _ =>
            report.info(s"Could not extract TypeInferrer from: ${x.show}")
            None
        end match
      end unapply
    end given
  end TypeInferrer

end table
