import io.github.quafadas.table.*

import viz.PlotTargets.desktopBrowser
import viz.Plottable.*
import viz.vegaFlavour
import upickle.default.ReadWriter.join
import viz.Macros.Implicits.given_Writer_T
// import viz.NamedTupleReadWriter.given_ReadWriter_T
import viz.*
import NamedTuple.NamedTuple

import NamedTuple.withNames
import io.github.quafadas.scautable.ColumnTyped.StringyTuple
import scala.annotation.implicitNotFound

enum Gender:
  case Male, Female, Unknown
end Gender

@main def titanic =

  val titanic = CSV.resource("titanic.csv").toSeq

  def data = titanic
    .mapColumn["Sex", Gender]((x: String) => Gender.valueOf(x.capitalize))
    .dropColumn["PassengerId"]
    .mapColumn["Age", Option[Double]](_.toDoubleOption)
    .mapColumn["Survived", Boolean](_ == "1")

  def surived: (survivied: Int, total: Int, pct: Double) = data
    .column["Survived"]
    .foldLeft((survivied = 0, total = 0, pct = 0.0)) { case (acc, survived) =>
      val survivedI = if survived then 1 else 0
      (acc.survivied + survivedI, acc.total + 1, 100 * acc.survivied.toDouble / acc.total.toDouble)
    }

  val dataArr = data.toArray
  println(data.toArray.take(20).consoleFormatNt(fansi = false))
  // scautable.desktopShowNt(dataArr) // Will pop up a browser window with the data

  val sex: Seq[(Gender, Int)] = dataArr.map(_.Sex).groupMapReduce(identity)(_ => 1)(_ + _).toSeq

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
  titanic.columns["Sex" *: EmptyTuple].plotPieChart
  titanic.columns["Embarked" *: EmptyTuple].plotPieChart
  sex.ptbl

  println()
  println("Survived By Gender")

  group.ptbln

end titanic

extension [K <: Tuple, V <: Tuple](data: Seq[NamedTuple[K, V]])(using
    @implicitNotFound("Only valid for one column")
    evK: Tuple.Size[K] =:= 1,
    @implicitNotFound("Only valid for one column")
    evV: Tuple.Size[V] =:= 1
)
  inline def plotPieChart: Unit =
    val name = scala.compiletime.constValueTuple[K].head.asInstanceOf[String]
    val spec = os.resource / "pieChart.vg.json"
    val dataGrouped = data.map(_.head).groupMapReduce(identity)(_ => 1)(_ + _).toSeq
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
        spec =>
          spec("title") = upickle.default.writeJs(
            text = name,
            fontSize = (
              expr = "width / 20"
            )
          )
      )
    )

end extension
