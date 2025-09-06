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
val csv = subset.toCsv(includeHeaders = true, delimiter = ',', quote = '"')

```
```scala mdoc:compile-only

os.write.over(os.pwd / "subset.csv", csv)

```
This materialises the entire CSV in memory. It would also be possible to write a simple streaming transformation using similar constructs.

## Streaming

One may stream a transformation to another file with relative ease.

```scala mdoc:compile-only

val csvStrings: Iterator[String] = datarator
  .filter(_.Sex == "female")
  .columns[myCols]
  .toCsv(includeHeaders = true, delimiter = ',', quote = '"')

val fileStream = os.write.outputStream(os.pwd / "test.csv")

csvStrings.foreach{s =>
  fileStream.write(s.getBytes)
  fileStream.write('\n')
}



```