# Summaries

You can investigate the describe method, to see how it breaks the data into numeric and non numeric columns, and folds down these sets of columns.

```scala mdoc:silent
import io.github.quafadas.table.*
val data = CSV.resource("titanic.csv", TypeInferrer.FromAllRows)

data.toSeq.describe
```