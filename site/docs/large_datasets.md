# Large Csv Files

## Rows

### Type Inference

Feeding the compiler 10m rows, will probably not go well.

The type inference strategies which read all rows (`FromAllRows`) are not recommended for large datasets. Instead, prefer `FirstN` or `FirstRow` to get a reasonable approximation of the data types in your CSV file.

Or, if you have prior knowledge; use it.

```scala
import io.github.quafadas.table.*

val largeCsv: CsvIterator[("col1", "col2", "col3"), (Double, String, Boolean)] =
  CSV.resource("large_dataset.csv", TypeInferrer.FromTuple[(Double, String, Boolean)]())
```

### Query Performance

Please bear in mind that scautable is targeted for the "small", and the design is lazy in nature so a large number of rows _may_ not be problematic if you don't materialise it in memory.

Please bear in mind that we are targeting "small" and is untested beyond 10 million rows 🤷. There comes a point where some with a good (um, any query optimiser, like Polars, Spark, etc) is a better fit for your data analysis needs than scala's stblib, and you should prefer those libraries to Scautable.

## Performance

In a random fork of [a benchmark suite](https://github.com/duckdblabs/db-benchmark) on `groupBy` operations scautable was about 20x slower than Polars, and about 3x slower than R's `data.table`. This is not unexpected - scautable is not a query optimiser. At the 10m row scale, it means your query may take 0.5 seconds in stead of 0.025 seconds, which is still well within "human exploring dataset" territory.

The following flags _may_ boost your performance by allocating a bunch of memory up front for the JVM to work with:

```scala
//> using javaOpt -Xss16m -Xms8g -Xmx24g --add-modules=jdk.incubator.vector -XX:+UnlockExperimentalVMOptions -XX:+UseCompactObjectHeaders -XX:+UseStringDeduplication -XX:+UseG1GC -XX:StringDeduplicationAgeThreshold=1 -XX:SurvivorRatio=6 -XX:MaxTenuringThreshold=10
```


## Columns

For files with many columns, the strategy of injecting the column names into the compiler is somewhat risky, and as you get larger number of columns (approximately 1000), you may begin to experience compiler crashes.

The project has successfully processed a 5000 column CSV with the following JVM settings:

```sh
-Xss100m
-Xmx10G
```

and args to the scala compiler:

```scala sc:nocompile
def scalacOptions: T[Seq[String]] = Seq("-Xmax-inlines:10000")
```

passed the below test:

```scala sc:nocompile
test("5000 cols") {
  def csv = CSV.resource("testFile/5000Cols.csv")
  assert(csv.headers.length == 5000)
}
```
