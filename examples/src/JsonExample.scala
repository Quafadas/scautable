package examples

import io.github.quafadas.plots.SetupVega.{*, given}
import io.github.quafadas.scautable.json.*
import io.circe.syntax.*
import viz.PlotTargets.websocket

@main def jsonExample(): Unit =
  // Example 1: Simple JSON parsing
  inline val simpleJson = """[
    {"name": "Alice", "age": 30, "score": 95.5},
    {"name": "Bob", "age": 25, "score": 87.0},
    {"name": "Charlie", "age": 35, "score": 92.3}
  ]"""

  val result1 = JSON.fromString(simpleJson).toVector

  val scatter = VegaPlot.fromResource("scatter.vg.json")

  scatter.plot(
    _.data.values := result1.asJson,
    _.encoding.x.field := "age",
    _.encoding.y.field := "score"
  )

  Thread.sleep(1000)