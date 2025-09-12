import io.github.quafadas.table.*


import viz.Plottable.*

import upickle.default.ReadWriter.join
import viz.Macros.Implicits.given_Writer_T
// import viz.NamedTupleReadWriter.given_ReadWriter_T
import viz.*
import NamedTuple.NamedTuple

import NamedTuple.withNames
import io.github.quafadas.scautable.ColumnTyped.StringyTuple
import scala.annotation.implicitNotFound
import io.github.quafadas.scautable.ColumnTyped.IsColumn
import io.github.quafadas.scautable.ColumnTyped.IsNumeric
import io.github.quafadas.scautable.ColumnTyped.GetTypeAtName
import io.github.quafadas.scautable.ColumnTyped.AllAreColumns
import scala.concurrent.Future
import io.github.quafadas.scautable.ColumnTyped
import scala.Tuple.Elem
import io.github.quafadas.scautable.ColumnTyped.IdxAtName

enum Gender:
  case Male, Female, Unknown
end Gender

/** Before running this, you shoudl have the visualisation websocket server running. `./mill examples.vizserver.runBackground 8085`
  *
  * It will start a websocket server on port 8085, then open these urls in your browser:
  *
  * http://127.0.0.1:8085/view/Survived
  *
  * http://127.0.0.1:8085/view/Sex
  *
  * http://127.0.0.1:8085/view/Fare
  *
  * http://127.0.0.1:8085/view/AgeIsDefined
  *
  * http://127.0.0.1:8085/view/Age
  *
  * http://127.0.0.1:8085/view/Age-VS-Fare
  *
  * http://127.0.0.1:8085/view/Regression-Age-VS-Fare
  *
  * Then run the example: `./mill examples.run --main-class titanic`
  *
  * The "view" urls will be updated with the data from the example. The last part of the URL, is the "description" field of the chart.
  */
@main def titanic =
  import viz.PlotTargets.websocket
  val titanic = CSV.resource("titanic.csv", TypeInferrer.FromAllRows)

  val data = LazyList.from(
    titanic
      .dropColumn["PassengerId"]
      .addColumn["AgeIsDefined", Boolean](_.Age.isDefined)
  )


  val surived: (survivied: Int, total: Int, pct: Double) = data
    .column["Survived"]
    .foldLeft((survivied = 0, total = 0, pct = 0.0)) { case (acc, survived) =>
      val survivedI = if survived then 1 else 0
      (acc.survivied + survivedI, acc.total + 1, 100 * acc.survivied.toDouble / acc.total.toDouble)
    }

  val dataArr = data.toArray
  data.toArray.take(20).ptbln
  // scautable.desktopShowNt(dataArr) // Will pop up a browser window with the data

  val sex = dataArr.map(_.Sex).groupMapReduce(identity)(_ => 1)(_ + _).toSeq

  val age = dataArr.map(_.Age).groupMapReduce(identity)(_ => 1)(_ + _).toList

  val group =
    dataArr
      .map(x => (x.Survived, x.Sex).withNames[("Survived", "Sex")])
      .groupMapReduce(_.Sex)(x => (if x.Survived then 1 else 0, 1, 0.0)) { case ((surviveAcc, oneAcc, percAcc), (c, d, e)) =>
        (
          surviveAcc + c,
          oneAcc + d,
          100 * (surviveAcc + c).toDouble / (oneAcc + d).toDouble
        )
      }
      .toList
      .map { case (x, (a, b, c)) =>
        (sex = x, Survived = a, CohortCount = b, % = c)
      }

  println()
  println("Surived: ")
  Seq(surived).ptbln

  println()
  println("Gender Info")

  sex.ptbl

  println()
  println("Survived By Gender")

  group.ptbln

  println("plots")
  data.plotPieChart["Sex"]
  data.plotPieChart["Survived"]
  data.plotHistogram["Fare"]
  data.plotPieChart["AgeIsDefined"]
  data.filter(_.Age.isDefined).mapColumn["Age", Double](_.get).plotHistogram["Age"]
  data.filter(_.Age.isDefined).mapColumn["Age", Double](_.get).plotMarginalHistogram["Age", "Fare"]
  data.filter(_.Age.isDefined).mapColumn["Age", Double](_.get).plotScatter["Age", "Fare"]
  data.filter(_.Age.isDefined).mapColumn["Age", Double](_.get).plotRegression["Age", "Fare"]

end titanic

extension [K <: Tuple, V <: Tuple](data: Seq[NamedTuple[K, V]])
  inline def plotPieChart[S <: String](using
      @implicitNotFound("Column ${S} not found")
      ev: IsColumn[S, K] =:= true,
      s: ValueOf[S]
  )(using ctx: viz.LowPriorityPlotTarget): Unit =
    import viz.vegaFlavour
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

  inline def plotHistogram[S <: String](using
      @implicitNotFound("Column ${S} not found")
      ev: IsColumn[S, K] =:= true,
      s: ValueOf[S],
      @implicitNotFound("Column ${S} is not numeric")
      numeric: Numeric[Elem[V, IdxAtName[S, K]]],
  )(using ctx: viz.LowPriorityPlotTarget): Unit =
    import viz.vegaFlavour
    val oneCol = data.column[S]
    val spec = os.resource / "histogram.vg.json"
    val colName: String = s.value
    spec.plot(
      List(
        spec =>
          spec("data") = upickle.default.writeJs(
            (values = oneCol.map: d =>
              ujson.Obj(
                colName -> numeric.toDouble(d)
              ))
          ),
        spec => spec("encoding")("x")("field") = colName,
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
  end plotHistogram

  inline def plotMarginalHistogram[S1 <: String, S2 <: String](using
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
    val column1 = data.column[S1]
    val column2 = data.column[S2]
    val zipped = column1.zip(column2)
    val spec = os.resource / "marginalHistogram.vg.json"
    val col1Name: String = s1.value
    val col2Name: String = s2.value
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
        spec => spec("description") = s"${col1Name}-VS-${col2Name}",
        spec => spec("vconcat")(0)("encoding")("x")("field") = col1Name,
        spec => spec("vconcat")(1)("hconcat")(0)("encoding")("x")("field") = col1Name,
        spec => spec("vconcat")(1)("hconcat")(0)("encoding")("y")("field") = col2Name,
        spec => spec("vconcat")(1)("hconcat")(1)("encoding")("y")("field") = col2Name,
        spec =>
          spec("title") = upickle.default.writeJs(
            text = s"$col1Name vs $col2Name",
            fontSize = (
              expr = "width / 20"
            )
          )
      )
    )
  end plotMarginalHistogram

  inline def plotScatter[S1 <: String, S2 <: String](using
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
    val column1 = data.column[S1]
    val column2 = data.column[S2]
    val zipped = column1.zip(column2)
    val spec = os.resource / "scatter.vg.json"
    val col1Name: String = s1.value
    val col2Name: String = s2.value
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
        spec => spec("description") = s"${col1Name}-VS-${col2Name}",
        spec => spec("encoding")("x")("field") = col1Name,
        spec => spec("encoding")("y")("field") = col2Name,
        spec =>
          spec("title") = upickle.default.writeJs(
            text = s"$col1Name vs $col2Name",
            fontSize = (
              expr = "width / 20"
            )
          )
      )
    )
  end plotScatter

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

end extension
