# Subset

What if we wanted to extract a subset of the data?

```scala mdoc:silent
import io.github.quafadas.table.*
import scala.compiletime.constValueTuple
val datarator  =
  CSV.resource("titanic_short.csv", TypeInferrer.FromAllRows)
  .zipWithIndex.map{case (r, idx) => (origIdx = idx ) ++ r}

val data = LazyList.from(datarator)

type myCols = ("Name", "Pclass", "Ticket")

val subset = data
  .filter(_.Sex == "female")
  .columns[myCols]

```

```scala mdoc
val headers = constValueTuple[myCols].toList.mkString(",")

subset.consoleFormatNt(fansi = false)

val csv = Seq(headers) ++ subset.map(_.toList.mkString(","))

// Now pick a file writing library you like. os-lib is not in scope, but if it was...
// os.write.over(os.pwd / "subset.csv", csv.mkString("\n"))

```
This materialises the entire CSV in memory. It would also be possible to write a simple streaming transformation using similar constructs.

Note that this approach is rather primitive, and doesn't consider things like escaping quotes or commas within fields, as it isn't something I've needed yet.