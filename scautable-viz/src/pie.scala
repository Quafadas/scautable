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