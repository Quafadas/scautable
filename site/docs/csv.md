# CSV


# Getting started

Our first move, is to tell the _compiler_, where your CSV file may be found. `CSV.resource` is a macro which reads the column headers and injects them into the compilers type system. Here; we inline a string for the compiler to analyze.


```scala mdoc
import io.github.quafadas.table.*

val csv : CsvIterator[("col1", "col2", "col3"), (Int, Int, Int)] = CSV.fromString("col1,col2,col3\n1,2,7\n3,4,8\n5,6,9")

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
/**
 * Note: this reads from the _compilers_ current working directory. If you are compiling via bloop through scala-cli, for example, then this will * read the temporary directory _bloop_ is running in, _not_ your project directory.
 */
val opts = CsvOpts(typeInferrer = TypeInferrer.FirstN(1000), delimiter = ';')
val csv_pwd = CSV.pwd("file.csv", opts)

```

For customisation options look at `CsvOpts`, and supply that as a second argument to any of the above methods.

## Strongly Typed CSVs

Scautable analyzes the CSV file and provides types and names for the columns. That means should get IDE support, auto complete, error messages for non sensical code, etc.


```scala mdoc
import io.github.quafadas.table.*

val experiment = asList
  .mapColumn["col1", Double](_.toDouble)
  .mapColumn["col2", Boolean](_.toInt > 3)

println(experiment.consoleFormatNt(fansi = false))

```
e.g. one cannot make column name typos because they are embedded in the type system.

```scala mdoc:fail sc:nocompile
 val nope = experiment.mapColumn["not_col1", Double](_.toDouble)

```


### Column Operations

Let's have a look at the some column manipulation helpers;

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
