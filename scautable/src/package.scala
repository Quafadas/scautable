package io.github.quafadas

import scala.quoted.*

object table:
  export io.github.quafadas.scautable.CSV
  export io.github.quafadas.scautable.CSV.*
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
  export io.github.quafadas.scautable.HeaderOptions.*
  export io.github.quafadas.scautable.Stats.*

  // Excel requires it's own decoders
  export io.github.quafadas.scautable.ExcelDecoders.given

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
