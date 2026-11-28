# Exploring a Column

There is no dedicated `unique` or `value_counts` method. `column["name"]` hands back a plain, correctly typed `Iterator` (or collection), and the standard library does the rest.

```scala mdoc:silent
import io.github.quafadas.table.*

val cereals = CSV.resource("cereals.csv", TypeInferrer.FromAllRows).toSeq
```

`.toSeq` matters: on an `Iterator`, `column` consumes the stream, so materialise first if you want to look at more than one column.

## Unique values

The equivalents of pandas `unique()` and `nunique()`.

```scala mdoc
val mfr = cereals.column["mfr"]

mfr.distinct

mfr.toSet.size
```

## Value counts

The equivalent of pandas `value_counts()`: count each value, then sort by descending count.

```scala mdoc
val counts = mfr
  .groupMapReduce(identity)(_ => 1)(_ + _)
  .toSeq
  .sortBy((_, n) => -n)
```

To display the result as a table (`ptbln` in the REPL), make the pairs named tuples.

```scala mdoc
println(counts.map((v, n) => (mfr = v, count = n)).consoleFormatNt(fansi = false))
```

Proportions rather than counts, like `value_counts(normalize=True)`:

```scala mdoc
println(counts.map((v, n) => (mfr = v, share = n.toDouble / mfr.size)).consoleFormatNt(fansi = false))
```

## Missing values

A column with gaps is inferred as `Option[A]`. Pandas' `value_counts` drops missing values by default. Here you choose:

```scala mdoc:silent
val titanic = CSV.resource("titanic_short.csv", TypeInferrer.FromAllRows).toSeq
val cabin = titanic.column["Cabin"]
```

```scala mdoc
// drop missing, like pandas' default
cabin.flatten.distinct.size

// keep missing as its own group, like dropna=False
cabin.groupMapReduce(identity)(_ => 1)(_ + _).get(None)
```
