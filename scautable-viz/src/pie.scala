package io.github.quafadas.scautable

import scala.annotation.implicitNotFound
import scala.Tuple.Elem
import io.github.quafadas.scautable.ColumnTyped.IsColumn
import io.github.quafadas.scautable.ColumnTyped.IdxAtName
import io.github.quafadas.scautable.ColumnTyped.AllAreColumns
import scala.NamedTuple.NamedTuple
import io.github.quafadas.table.column
import viz.Plottable.plot


object Pie:

  extension [CC[X] <: Iterable[X], K <: Tuple, V <: Tuple](data: Iterable[NamedTuple[K, V]])

    /** Creates and displays a pie chart for a categorical column.
      *
      * This method generates a pie chart visualization by grouping values in the specified column
      * and counting their occurrences. Each unique value becomes a slice of the pie, with the
      * slice size proportional to the count of that value.
      *
      * @tparam S the name of the column to visualize (must be a string literal type)
      *
      * @param ev compile-time evidence that column S exists in the dataframe structure
      * @param s value-level access to the column name
      * @param ctx the plotting target context (browser, console, etc.)
      *
      * Assumptions and behavior:
      * - The column values will be converted to strings for grouping (via `.toString()`)
      * - All values (including nulls) are grouped and counted - no filtering is applied
      * - The chart displays category labels at radius `width / 2.5` from center
      * - The arc outer radius is `width / 3`
      * - Font size scales with chart width (`width / 40` for labels, `width / 20` for title)
      * - The chart uses Vega-Lite's default color scheme for categories
      * - Tooltips are enabled showing category and value on hover
      *
      * Example:
      * {{{
      * val passengers = CSV.read["titanic.csv"]("Sex", "Pclass")
      * passengers.plotPieChart["Sex"]  // Visualizes gender distribution
      * }}}
      */
    inline def plotPieChart[S <: String](using
        @implicitNotFound("Column ${S} not found")
        ev: IsColumn[S, K] =:= true,
        s: ValueOf[S]
    )(using ctx: viz.LowPriorityPlotTarget): Unit =
      import viz.vegaFlavour
      import viz.NamedTupleReadWriter.given_ReadWriter_T
      val oneCol = data.column[S]
      val spec = os.resource / "pieChart.vg.json"
      val dataGrouped = oneCol.groupMapReduce(identity)(_ => 1)(_ + _).toSeq
      val colName: String = s.value
      spec.plot(
        List(
          spec =>
            spec("data") = upickle.default.writeJs(
              (values = dataGrouped.map: d =>
                (
                  category = d._1.toString(),
                  value = d._2
                ))
            ),
          spec => spec("description") = colName,
          spec =>
            spec("title") = upickle.default.writeJs(
              text = colName,
              fontSize = (
                expr = "width / 20"
              )
            )
        )
      )

    end plotPieChart

    /** Creates and displays a pie chart for a numeric column by summing values per category.
      *
      * This overload handles numeric columns by grouping on the values themselves (converted to strings)
      * and summing the numeric values for each group. This is useful when you want to visualize
      * the total or sum of numeric values across different categories, rather than just counts.
      *
      * @tparam S the name of the numeric column to visualize (must be a string literal type)
      *
      * @param columnName the name of the column (provided explicitly as a parameter)
      * @param ev compile-time evidence that column S exists in the dataframe structure
      * @param s value-level access to the column name
      * @param numeric1 compile-time evidence that the column contains numeric values
      * @param n numeric operations for the column type (used to convert to Double and sum)
      * @param ctx the plotting target context (browser, console, etc.)
      *
      * Assumptions and behavior:
      * - Column must contain numeric values (Int, Double, Long, etc.)
      * - Values are grouped by their string representation (via `.toString()`)
      * - Numeric values in each group are summed (not counted) using `n.toDouble(_)`
      * - The pie slice size represents the sum of numeric values, not the count
      * - Chart layout and styling identical to the categorical version (see other overload)
      * - All numeric values (including zeros) are included - no filtering applied
      *
      * Example:
      * {{{
      * val sales = CSV.pwd("sales.csv").toSeq
      * sales.plotPieChart["Revenue"]
      * // Each slice size = sum of all Revenue values for that product
      * }}}
      *
      * Note: This is typically used when the numeric column represents a value to aggregate
      * (like amounts, revenues, quantities) rather than a categorical identifier.
      */
    inline def plotPieChart[S <: String](columnName: S)(using
        @implicitNotFound("Column ${S} not found")
        ev: IsColumn[S, K] =:= true,
        s: ValueOf[S],
        @implicitNotFound("Column ${S} is not numeric")
        numeric1: Numeric[Elem[V, IdxAtName[S, K]]],
        n: Numeric[Elem[V, IdxAtName[S, K]]]
    )(using ctx: viz.LowPriorityPlotTarget): Unit =

      import viz.vegaFlavour
      import viz.NamedTupleReadWriter.given_ReadWriter_T
      val oneCol = data.column[S]
      val spec = os.resource / "pieChart.vg.json"
      val dataGrouped = oneCol.groupMapReduce(identity)(n.toDouble(_))(_ + _).toSeq
      val colName: String = s.value
      spec.plot(
        List(
          spec =>
            spec("data") = upickle.default.writeJs(
              (values = dataGrouped.map: d =>
                (
                  category = d._1.toString(),
                  value = d._2
                ))
            ),
          spec => spec("description") = colName,
          spec =>
            spec("title") = upickle.default.writeJs(
              text = colName,
              fontSize = (
                expr = "width / 20"
              )
            )
        )
      )

    end plotPieChart

  end extension