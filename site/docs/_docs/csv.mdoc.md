---
title: CSV
---

# Getting started

Our first move, is to tell the compiler, where the file may be found. `CSV.resource` is a macro which reads the column headers and injects them into the compilers type system. Here; for the file `simple.csv` in the project resource directory.


```scala mdoc sc:nocompile
import io.github.quafadas.table.*

def csv : CsvIterator[("col1", "col2", "col3")] = CSV.resource("simple.csv")
def firstRows: Iterator[(col1: String, col2: String, col3: String)] = csv.take(2)

println(firstRows.toArray.consoleFormatNt(fansi = false))

```

Note the `take(2)` method. This is a _standard scala method_, and the key point behind the whole idea - that we can trivially plug scala's std lib into a CSV file.

## Reading CSV files

`CSV` has a few methods of reading CSV files. It is fundamentally `scala.Source` based inside the macro.

```scala sc:nocompile
import io.github.quafadas.table.*

def csv_resource = CSV.resource("simple.csv")
def csv_abs = CSV.absolutePath("/users/simon/absolute/path/simple.csv")
def csv_url = CSV.url("https://example.com/simple.csv")

```

## Strongly Typed CSVs

We expose a small number of "column" methods, which allow coumn manipulation. They deal with the typelevel bookingkeeping surrounding named tuples. Note that these operate on iterators.


```scala mdoc sc:nocompile
import io.github.quafadas.table.*

def typeSperiment: Iterator[(col1 : Double, col2: Boolean, col3: String)] = csv
  .mapColumn["col1", Double](_.toDouble)
  .mapColumn["col2", Boolean](_.toInt > 3)

println(typeSperiment.toArray.consoleFormatNt(fansi = false))

```
Note, that one cannot make column name typos - the compiler will catch them. If you try to map a column which doesn't exist, the compiler will complain.

We'll leave out explicit type ascriptions for the rest of the examples.

```scala mdoc:fail sc:nocompile
 def nope = typeSperiment.mapColumn["not_col1", Double](_.toDouble)

```

### Column Operations



```scala mdoc sc:nocompile
def colmanipuluation = typeSperiment
  .dropColumn["col2"]
  .addColumn["col4", Double](x => x.col1 * 2 + x.col3.toDouble)
  .renameColumn["col4", "col4_renamed"]
  .mapColumn["col4_renamed", Double](_ * 2)

println(colmanipuluation.toArray.consoleFormatNt(fansi = false))

println(colmanipuluation.column["col4_renamed"].foldLeft(0.0)(_ + _))

```

### Accumulating, slicing etc

We can now delegate all this, to the standard library now. In my mind, there are more or less two ways of going about this. I'm usually working in the small , so I materialise the iterator early and treat it as a list.

```scala mdoc sc:nocompile
val asList = colmanipuluation.toList

println(asList.filter(_.col4_renamed > 20).groupMapReduce(_.col1)(_.col4_renamed)(_ + _))

```
Otherwise, we can use fold and friends to achieve similar over the `Iterator` (i haven't written our the grouping below)

```scala mdoc sc:nocompile
println(colmanipuluation.filter(_.col4_renamed > 20).foldLeft(0.0)(_ + _.col4_renamed))
```





