# Getting Started

Scautable: One line CSV import and dataframe utilities based on scala's `NamedTuple`.

## Scala CLI

`//> using dep io.github.quafadas::scautable::`${version.latest}

Here's a screencap of a tiny, self contained example.

![Example](../assets/getting_started.png)

Quickstart...

Source: [Kaggle](https://www.kaggle.com/datasets/crawford/80-cereals)

[cereals](../assets/cereals.csv)

```scala
//> using scala 3.7.2
//> using dep io.github.quafadas::scautable::0.0.27
//> using resourceDir resources

import io.github.quafadas.table.*

@main def run(): Unit =
  val df = CSV.resource("cereals.csv", TypeInferrer.FromAllRows)

  val data = LazyList.from(
    df
      .addColumn["double_the_sugar", Double](_.sugars * 2)
      .dropColumn["fiber"] // no one cares about the healthy bit
      .mapColumn["name", String](_.toUpperCase)
      .renameColumn["mfr", "manufacturer"]
  )

  data.take(20).ptbln

  println("Hot cereals: ")
  println(data.collect{
    case row if row.`type` == "H" =>
      (name = row.name, made_by = row.manufacturer, sugar = row.sugars, salt = row.sodium)
  }.ptbln


```

## Mill
`mvn"io.github.quafadas::scautable::`${version.latest}"

## Goals


- Strongly typed compile-time CSV import
- pretty printing to console for `Product` types
- Auto-magically generate html tables from case classes
- Searchable, sortable browser GUI for your tables

### 5 second CSV quickstart

```scala mdoc:silent
import io.github.quafadas.table.*
val data = CSV.resource("cereals.csv", TypeInferrer.FromAllRows)

data.numericCols.numericSummary.ptbln
```
```scala mdoc:invisible:reset
import io.github.quafadas.table.*
val data = CSV.resource("cereals.csv", TypeInferrer.FromAllRows)
```
In order to make it look nice on a website
```scala mdoc
println(
  LazyList.from(data)
    .numericCols
    .numericSummary
    .mapColumn["mean", String](s => "%.2f".format(s))
    .consoleFormatNt(fansi = false)
)

```