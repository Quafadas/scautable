# CSV


# Getting started

Our first move, is to tell the compiler, where the file may be found. `CSV.resource` is a macro which reads the column headers and injects them into the compilers type system. Here; we inline a string for the compiler to analyze.


```scala mdoc
import io.github.quafadas.table.*

val csv : CsvIterator[("col1", "col2", "col3"), (String, String, String)] = CSV.fromString("col1,col2,col3\n1,2,7\n3,4,8\n5,6,9")

val asList = LazyList.from(csv)

asList.take(2).consoleFormatNt(fansi = false)

```

**The key point of the whole library** - Note the `take(2)` method. This is a method from _scala's stdlib_. In case it's not clear - you get all the other stuff too - `.filter`, `groupMapReduce`, which are powerful. Their use is strongly typed, because `CSVIterator` is merely an `Iterator` of `NamedTuples` - you access the columns via their column name.

## Reading CSV files

Reading CSV's as strings would be relatively uncommon - normally `.csv` is a file.

The `CSV` object has a few methods of reading CSV files. It is fundamentally `scala.Source` based inside the macro.

```scala
import io.github.quafadas.table.*

val csv_resource = CSV.resource("simple.csv")
val csv_abs = CSV.absolutePath("/users/simon/absolute/path/simple.csv")
val csv_url = CSV.url("https://example.com/simple.csv")

```

## Strongly Typed CSVs

We expose a small number of "column" methods, which allow coumn manipulation. They deal with the typelevel bookingkeeping surrounding named tuples.


```scala mdoc
import io.github.quafadas.table.*

val experiment = asList
  .mapColumn["col1", Double](_.toDouble)
  .mapColumn["col2", Boolean](_.toInt > 3)

println(experiment.consoleFormatNt(fansi = false))

```
Note, that one cannot make column name typos - the compiler will catch them. If you try to map a column which doesn't exist, the compiler will complain.

```scala mdoc:fail sc:nocompile
 val nope = experiment.mapColumn["not_col1", Double](_.toDouble)

```


### Column Operations

Let's have a look at the remainder of column manipulation;

- `dropColumn`
- `addColumn`
- `renameColumn`
- `mapColumn`

```scala mdoc
val colmanipuluation = experiment
  .dropColumn["col2"]
  .addColumn["col4", Double](x => x.col1 * 2 + x.col3.toDouble)
  .renameColumn["col4", "col4_renamed"]
  .mapColumn["col4_renamed", Double](_ * 2)

colmanipuluation.consoleFormatNt(fansi = false)

println(colmanipuluation.column["col4_renamed"].foldLeft(0.0)(_ + _))

// and select a subset of columns
colmanipuluation.columns[("col4_renamed", "col1")].consoleFormatNt(fansi = false)

```

### Accumulating, slicing etc

We can delegate all such concerns, to the standard library in the usual way - as we have everything in side the type system!

```scala mdoc sc:nocompile
colmanipuluation.filter(_.col4_renamed > 20).groupMapReduce(_.col1)(_.col4_renamed)(_ + _)

```
Otherwise, we can use fold and friends to achieve similar over the `Iterator` (i haven't written our the grouping below)

```scala mdoc sc:nocompile
colmanipuluation.filter(_.col4_renamed > 20).foldLeft(0.0)(_ + _.col4_renamed)
```

### Why are the iterators `def`?

Because if you make them `val` and try to read them a second time, you'll get a `StreamClosedException`.

Iterators are cheap to create, but I usually read all data into a `val` via a call to `toSeq` to avoid traversing the file multiple times.

### Header deduplication

If you are in the situation where you have a large number of duplicate headers, consider de-duplication.

```scala sc:nocompile
val csvDup: CsvIterator[("colA", "colA", "colA", "colB", "colC", "colA"), (String, String, String, String, String, String)] = CSV.resource("dups.csv")

val dedupCsv: CsvIterator[("colA", "colA_1", "colA_2", "colB", "colC", "colA_5"), (String, String, String, String, String, String)] = CSV.deduplicateHeader(csvDup)
```

### Example

Here's a [scastie](https://scastie.scala-lang.org/Quafadas/2JoRN3v8SHK63uTYGtKdlw/26) to a scastie which does some manipulation on the Titanic dataset.
