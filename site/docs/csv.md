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

// Anchored to the source file this call sits in, and to the project root above it,
// so neither depends on where the build was invoked from.
val opts = CsvOpts(typeInferrer = TypeInferrer.FirstN(1000), delimiter = ';')
val csv_rel = CSV.relativeToSource("file.csv", opts)
val csv_root = CSV.projectRoot("data/file.csv", opts)

```

For customisation options look at `CsvOpts`, and supply that as a second argument to any of the above methods.

## Columnar Reading

By default, CSV data is read as an iterator of rows (`CsvIterator`). For analytical workloads, you can read CSV data directly into a columnar format using `ReadAs.Columns`:

```scala mdoc
import io.github.quafadas.table.*

// Read as columns - returns NamedTuple of Arrays
val columnar = CSV.fromString("name,age,score\nAlice,30,95.5\nBob,25,87.3", CsvOpts(readAs = ReadAs.Columns))

// Access columns directly as typed arrays
val names: Array[String] = columnar.name
val ages: Array[Int] = columnar.age
val scores: Array[Double] = columnar.score

println(s"Average age: ${ages.sum.toDouble / ages.length}")
println(s"Max score: ${scores.max}")
```

Columnar reading:
- Loads all data into memory at once
- Provides direct array access to columns
- More efficient for column-oriented analytics
- Works with all CSV reading methods (`resource`, `absolutePath`, `fromString`, etc.)
- Supports all type inference options

For advanced use cases requiring a single dense array with stride information (e.g., for BLAS/LAPACK interop), see `ReadAs.ArrayDenseColMajor[T]()` and `ReadAs.ArrayDenseRowMajor[T]()` in the [Column Orient cookbook](cookbook/ColumnOrient.md#reading-csv-as-dense-arrays).

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

### Several columns at once

`mapColumn` takes one column at a time, which gets repetitive on a wide table - a
spreadsheet where a dozen columns all arrived as `String` needs a dozen calls. `mapColumns`
takes a *spec* instead: a named tuple whose names are columns and whose values are the
functions to apply.

```scala mdoc
val parsed = asList.mapColumns((
  col1 = (i: Int) => i.toDouble,
  col2 = (i: Int) => i > 3
))

parsed.consoleFormatNt(fansi = false)
```

Each column's new type is its function's result type. Columns the spec doesn't mention are
untouched, and the order of the spec is irrelevant. Note that the lambdas need their
parameter types written out - the spec's type is being inferred *from* the lambdas, so there
is no expected type to infer them from.

The errors are the same shape as `mapColumn`'s, and name the offending column:

```scala mdoc:fail sc:nocompile
asList.mapColumns((not_col1 = (i: Int) => i.toDouble))
```

```scala mdoc:fail sc:nocompile
asList.mapColumns((col1 = (s: String) => s.toDouble))
```

`retype` is the same idea for `forceColumnType` - it forces several column types in one
call. Like `forceColumnType` it is an unchecked cast, so see
[Displaying Tables](displayingTables.md#tag-late), where stripping display tags back off is
the use that is always sound.

### Accumulating, slicing etc

We can delegate all such concerns, to the standard library in the usual way - as we have everything in side the type system!

```scala mdoc sc:nocompile
colmanipuluation.filter(_.col4_renamed > 20).groupMapReduce(_.col1)(_.col4_renamed)(_ + _)

```

### Joining

Joining is the one relational operation the standard library cannot do for us. It can group and
fold rows perfectly well, but it has no way to work out the *type* of two named tuples stitched
together on a key - so `join` is a first class operation here.

```scala mdoc
val orders = Seq(
  (custId = 1, qty = 5),
  (custId = 2, qty = 7),
  (custId = 1, qty = 9)
)

val customers = Seq(
  (custId = 1, name = "ada"),
  (custId = 2, name = "bob")
)

orders.join(customers)["custId"].consoleFormatNt(fansi = false)
```

The key is written *after* the right hand table, because the compiler works that table's shape out
from the argument and only the key needs spelling out. The result is every column of the left
table, then every column of the right one except the key.

Where the key is named differently on each side, use `joinOn`. The output keeps the *left* name.

```scala mdoc
val people = Seq((id = 1, name = "ada"), (id = 2, name = "bob"))

orders.joinOn(people)["custId", "id"].consoleFormatNt(fansi = false)
```

`leftJoin` and `leftJoinOn` keep every left row, and the right hand columns become `Option`:

```scala mdoc
val sparse = Seq((custId = 1, name = "ada"))

orders.leftJoin(sparse)["custId"].consoleFormatNt(fansi = false)
```

A right hand column that is *already* an `Option` is left as it is rather than nesting into
`Option[Option[_]]` - which does mean an unmatched row and a matched row holding `None` look
alike.

A column name that appears on both sides is a compile error naming the offender, rather than a
silently duplicated or overwritten column. Rename or drop it first.

```scala mdoc:fail sc:nocompile
Seq((custId = 1, name = "x")).join(customers)["custId"]
```

Unknown keys, and keys whose types disagree, are caught in the same way:

```scala mdoc:fail sc:nocompile
orders.joinOn(people)["custId", "nope"]
```

Worth knowing:

- The left side streams; the right is read into a hash index the first time the result is pulled.
  The right table is therefore the one that has to fit in memory - put the smaller table there.
- A `None` key never matches anything - as in SQL, where `NULL = NULL` is never true, and pandas,
  which drops missing keys from a merge. `leftJoin` still keeps such a row, with its right hand
  columns all `None`. Were `None` to match `None` instead, missing data would square itself: three
  missing keys on each side would be nine output rows carrying no information. To match missing to
  missing deliberately, map the key to a sentinel first -
  `mapColumn["custId", Int](_.getOrElse(-1))`.
- Left order is preserved, and a left row matching several right rows emits them in the right
  table's own order.
- Key types must agree exactly. A column carrying a display tag from `formatColumn` will not match
  an untagged one - join first, format afterwards.
- Only inner and left joins are built in. A right join is a left join with the tables swapped.
