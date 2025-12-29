package examples
import viz.Macros.Implicits.given_Writer_T

import io.github.quafadas.table.*
import viz.*
import io.github.quafadas.scautable.plots.*

@main def cereals =
  import viz.PlotTargets.desktopBrowser
  val data = CSV.resource("cereals.csv", TypeInferrer.FirstN(1000))

  val cereals = LazyList.from(data)
  cereals.describe

  // println(s"Number of cereals: ${cereals.length}")

  // println("numeric")

  // cereals.numericCols.take(10).ptbln

  // cereals.take(10).ptbln
  // println("plot regression")
  cereals.plotScatter["calories", "rating"]
  cereals.plotHistogram["protein"]
  cereals.plotHistogram["rating"]
  cereals.plotHistogram["type"]
  cereals.plotPieChart["type"]

  cereals.plotRegression["calories", "rating"]

end cereals
