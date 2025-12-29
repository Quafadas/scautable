package io.github.quafadas.scautable

import scala.annotation.implicitNotFound
import scala.Tuple.Elem
import io.github.quafadas.scautable.ColumnTyped.IsColumn
import io.github.quafadas.scautable.ColumnTyped.IdxAtName
import io.github.quafadas.scautable.ColumnTyped.AllAreColumns
import scala.NamedTuple.NamedTuple
import io.github.quafadas.table.column
import viz.Plottable.plot

object Regression:
  extension [CC[X] <: Iterable[X], K <: Tuple, V <: Tuple](data: Iterable[NamedTuple[K, V]])

    inline def plotRegression[S1 <: String, S2 <: String](using
        @implicitNotFound("Column ${S1} not found")
        ev1: IsColumn[S1, K] =:= true,
        @implicitNotFound("Column ${S2} not found")
        ev2: IsColumn[S2, K] =:= true,
        ev: AllAreColumns[(S1, S2), K] =:= true,
        s1: ValueOf[S1],
        s2: ValueOf[S2],
        @implicitNotFound("Column ${S1} is not numeric")
        numeric1: Numeric[Elem[V, IdxAtName[S1, K]]],
        @implicitNotFound("Column ${S2} is not numeric")
        numeric2: Numeric[Elem[V, IdxAtName[S2, K]]]
    )(using ctx: viz.LowPriorityPlotTarget): Unit =
      import viz.vegaFlavour
      import viz.NamedTupleReadWriter.given_ReadWriter_T
      val column1 = data.column[S1]
      val column2 = data.column[S2]
      val zipped = column1.zip(column2)
      val spec = os.resource / "regression.vg.json"
      val col1Name: String = s1.value
      val col2Name: String = s2.value
      val encoding = (
        x = (
          field = col1Name,
          `type` = "quantitative"
        ),
        y = (
          field = col2Name,
          `type` = "quantitative"
        )
      )
      println("here")
      spec.plot(
        List(
          spec =>
            spec("data") = upickle.default.writeJs(
              (values = zipped.map: d =>
                ujson.Obj(
                  col1Name -> numeric1.toDouble(d._1),
                  col2Name -> numeric2.toDouble(d._2)
                ))
            ),
          spec => spec("description") = s"Regression-${col1Name}-VS-${col2Name}",
          spec => spec("layer")(0)("encoding") = upickle.default.writeJs(encoding),
          spec => spec("layer")(1)("encoding") = upickle.default.writeJs(encoding),
          spec => spec("layer")(1)("transform")(0)("regression") = col2Name,
          spec => spec("layer")(1)("transform")(0)("on") = col1Name,
          spec => spec("layer")(2)("transform")(0)("regression") = col2Name,
          spec => spec("layer")(2)("transform")(0)("on") = col1Name,
          spec =>
            spec("title") = upickle.default.writeJs(
              text = s"$col1Name vs $col2Name",
              fontSize = (
                expr = "width / 20"
              )
            )
        )
      )
    end plotRegression