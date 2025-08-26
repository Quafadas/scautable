# CSV


# Getting started

Our first move, is to tell the compiler, where the file may be found. `CSV.resource` is a macro which reads the column headers and injects them into the compilers type system. Here; for the file `simple.csv` in the project resource directory.


```scala mdoc
import io.github.quafadas.table.*

def csv : CsvIterator[("col1", "col2", "col3"), (String, String, String)] = CSV.fromString("col1,col2,col3\n1,2,7\n3,4,8\n5,6,9")

LazyList.from(csv).take(2).ptbln

```

Note the `take(2)` method. This is a _standard scala method_, and the key point behind the whole idea - that we can trivially plug scala's std lib into a CSV file.

## Reading CSV files

`CSV` has a few methods of reading CSV files. It is fundamentally `scala.Source` based inside the macro.

```scala
import io.github.quafadas.table.*

def csv_resource = CSV.resource("simple.csv")
def csv_abs = CSV.absolutePath("/users/simon/absolute/path/simple.csv")
def csv_url = CSV.url("https://example.com/simple.csv")

```

## Strongly Typed CSVs

We expose a small number of "column" methods, which allow coumn manipulation. They deal with the typelevel bookingkeeping surrounding named tuples. Note that these operate on iterators.


```scala mdoc
import io.github.quafadas.table.*

def experiment: Iterator[(col1 : Double, col2: Boolean, col3: String)] = csv
  .mapColumn["col1", Double](_.toDouble)
  .mapColumn["col2", Boolean](_.toInt > 3)

println(experiment.toArray.consoleFormatNt(fansi = false))

```
Note, that one cannot make column name typos - the compiler will catch them. If you try to map a column which doesn't exist, the compiler will complain.

We'll leave out explicit type ascriptions for the rest of the examples.

```scala mdoc:fail sc:nocompile
 def nope = experiment.mapColumn["not_col1", Double](_.toDouble)

```



### Column Operations

and look at the extension methods

```scala mdoc
def colmanipuluation = experiment
  .dropColumn["col2"]
  .addColumn["col4", Double](x => x.col1 * 2 + x.col3.toDouble)
  .renameColumn["col4", "col4_renamed"]
  .mapColumn["col4_renamed", Double](_ * 2)

colmanipuluation.toArray.consoleFormatNt(fansi = false)

println(colmanipuluation.column["col4_renamed"].foldLeft(0.0)(_ + _))

// and select a subset of columns
colmanipuluation.columns[("col4_renamed", "col1")].toArray.consoleFormatNt(fansi = false)

```

### Accumulating, slicing etc

We can delegate all such concerns, to the standard library in the usual way - as we have everything in side the type system!

In my mind, there are more or less two ways of going about this. I'm usually working in the small , so I materialise the iterator early and treat it as a list.

```scala mdoc sc:nocompile
val asList = colmanipuluation.toList

println(asList.filter(_.col4_renamed > 20).groupMapReduce(_.col1)(_.col4_renamed)(_ + _))

```
Otherwise, we can use fold and friends to achieve similar over the `Iterator` (i haven't written our the grouping below)

```scala mdoc sc:nocompile
println(colmanipuluation.filter(_.col4_renamed > 20).foldLeft(0.0)(_ + _.col4_renamed))
```

### Why are the iterators `def`?

Because if you make them `val` and try to read them a second time, you'll get a `StreamClosedException`.

Iterators are cheap to create, but I usually read all data into a `val` via a call to `toSeq` to avoid traversing the file multiple times.

### Header deduplication

If you are in the situation where you have a large number of duplicate headers, consider de-duplication.

```scala sc:nocompile
def csvDup: CsvIterator[("colA", "colA", "colA", "colB", "colC", "colA"), (String, String, String, String, String, String)] = CSV.resource("dups.csv")

def dedupCsv: CsvIterator[("colA", "colA_1", "colA_2", "colB", "colC", "colA_5"), (String, String, String, String, String, String)] = CSV.deduplicateHeader(csvDup)
```

### Example

Here's a [scastie](https://scastie.scala-lang.org/Quafadas/2JoRN3v8SHK63uTYGtKdlw/26) to a scastie which does some manipulation on the Titanic dataset.
