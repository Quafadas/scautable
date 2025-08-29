import viz.Macros.Implicits.given_Writer_T

import io.github.quafadas.table.*

// import viz.PlotTargets.desktopBrowser
import viz.*
import viz.PlotTargets.websocket
import viz.vegaFlavour


@main def cereals =
  val data = CSV.resource("cereals.csv", TypeInferrer.FirstN(1000))

  val cereals = LazyList.from(data)

  println(s"Number of cereals: ${cereals.length}")

  println("numeric")
  cereals.numericCols.take(10).ptbln

  cereals.take(10).ptbln

  println("plot regression")
  cereals.plotRegression["calories", "protein"]
  cereals.plotRegression["fiber", "protein"]
  cereals.plotScatter["fiber", "protein"]