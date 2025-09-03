# Bad Data

A sketch on how one might find malformed data. Note that col3 is inferred as `String`.

```scala mdoc:silent
import io.github.quafadas.table.*
val datarator : Iterator[(
    origIdx: Int,
    col1 : Int,
    col2 : Int,
    col3: String
  )
] =
  CSV.resource("simple_bad.csv", TypeInferrer.FromAllRows)
  .zipWithIndex.map{case (r, idx) => (origIdx = idx ) ++ r}

val data = LazyList.from(datarator)

```
by:

- adding a row index to the output.
- Parsing a new column and checking for the `None` values.

```scala mdoc

data.consoleFormatNt(fansi = false)

data
  .addColumn["col3_parsed", Option[Int]]{_.col3.toIntOption}
  .filter(_.col3_parsed.isEmpty)
  .consoleFormatNt(fansi = false)

```
