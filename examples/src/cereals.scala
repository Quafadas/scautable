import viz.Macros.Implicits.given_Writer_T

import io.github.quafadas.table.*
import viz.*

@main def cereals =
  import viz.PlotTargets.desktopBrowser
  val data = CSV.resource("cereals.csv", TypeInferrer.FirstN(1000))

  val cereals = LazyList.from(data)

  println(s"Number of cereals: ${cereals.length}")

  println("numeric")
  cereals.numericCols.take(10).ptbln

  cereals.take(10).ptbln

  println("plot regression")
  cereals.plotScatter["calories", "protein"]
  cereals.plotHistogram["protein"]
