# Getting Started

Scautable: One line CSV import and dataframe utilities based on scala's `NamedTuple`.

## Scala CLI

`//> using dep io.github.quafadas::scautable::@VERSION@`

### Quick REPL Start

To start a REPL session with scautable ready to use:

```bash
scala-cli --dep io.github.quafadas::scautable::@VERSION@ \
  --scalac-option -Xmax-inlines --scalac-option 2048 \
  --java-opt -Xss4m \
  --repl-init-script 'import io.github.quafadas.table.{*, given}'
```

This command includes the necessary compiler options for scautable's metaprogramming:
- `--scalac-option -Xmax-inlines --scalac-option 2048`: Increases inline limit to 2048 for compile-time type inference
- `--java-opt -Xss4m`: Increases JVM stack size to 4MB for heavy metaprogramming
- `--repl-init-script`: Auto-imports the scautable package when the REPL starts

### Example Usage

Here's a screencap of a tiny, self contained example.

![Example](../assets/getting_started.png)

Quickstart...

Source: [Kaggle](https://www.kaggle.com/datasets/crawford/80-cereals)

[cereals](../assets/cereals.csv)

```scala
//> using scala 3.7.2
//> using dep io.github.quafadas::scautable::@VERSION@
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
  data.collect{
    case row if row.`type` == "H" =>
      (name = row.name, made_by = row.manufacturer, sugar = row.sugars, salt = row.sodium)
  }.ptbln


```

## Mill
`mvn"io.github.quafadas::scautable::@VERSION@"`

Then run the same code as above in `src/Example.scala`.

## Goals


- Strongly typed compile-time CSV import
- pretty printing to console for `Product` types
- Auto-magically generate html tables from case classes
- Searchable, sortable browser GUI for your tables

### 5 second CSV quickstart

```scala mdoc:silent
import io.github.quafadas.table.*
val data = CSV.resource("titanic.csv", TypeInferrer.FromAllRows)

// This doesn't display well on a website because of the ANSI...
data.toSeq.describe
// But these lines should be all you need to get an overview of the data.



// In order to make it look nice on a website
val (numerics, categoricals) = LazyList.from(
  CSV.resource("titanic.csv", TypeInferrer.FromAllRows)
).summary
```
```scala mdoc:invisible:reset
import io.github.quafadas.table.*
def data2 = CSV.resource("titanic.csv", TypeInferrer.FromAllRows)
val (numerics, categoricals) = LazyList.from(data2).summary
```
In order to make it look nice on a website
```scala mdoc

println(
    numerics
      .mapColumn["mean", String](s => "%.2f".format(s))
      .mapColumn["0.25", String](s => "%.2f".format(s))
      .mapColumn["0.75", String](s => "%.2f".format(s))
      .consoleFormatNt(fansi = false)
)

println(
  categoricals
  .mapColumn["sample", String](_.take(20))
  .consoleFormatNt(fansi = false)
)
```