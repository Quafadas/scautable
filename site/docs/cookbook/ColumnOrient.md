# Column Orient

"Vector" style computation is beyond the scope of scautable itself. However, it's clear that a row oriented representation of the data, is not always the right construct - particularly for analysis type tasks. 

To note again: **statistics is beyond the scope of scautable**. 

It is encouraged to wheel in some other alternative mathematics / stats library (entirely at your own discretion / risk).

## Reading CSV directly as columns

Scautable can read CSV data directly into a columnar format using the `ReadAs.Columns` option. This is more efficient than reading rows and then converting, as it only requires a single pass through the data.

This will fire up a repl with necssary imports;

```sh
scala-cli repl --dep io.github.quafadas::scautable::@VERSION@ --dep io.github.quafadas::vecxt:0.0.35 --java-opt "--add-modules=jdk.incubator.vector" --scalac-option -Xmax-inlines --scalac-option 2048 --java-opt -Xss4m --repl-init-script 'import io.github.quafadas.table.{*, given}; import vecxt.all.{*, given}'
```

```scala mdoc

import io.github.quafadas.table.*

// Read directly as columns - returns NamedTuple of Arrays
// lazy - useful to prevent printing repl
lazy val simpleCols = CSV.resource("simple.csv", CsvOpts(readAs = ReadAs.Columns))

// Access columns directly as typed arrays
val col1: Array[Int] = simpleCols.col1
val col2: Array[Int] = simpleCols.col2
val col3: Array[Int] = simpleCols.col3

// With vecxt, we get optimsed vector operations too.
// simpleCols.col1 + simpleCols.cols2

// Works with type inference
val titanicCols = CSV.resource("titanic.csv", CsvOpts(TypeInferrer.FromAllRows, ReadAs.Columns))
val ages: Array[Option[Double]] = titanicCols.Age
val survived: Array[Boolean] = titanicCols.Survived


```


## Converting row-oriented data to columns

Alternatively, you can read data as rows (the default) and then convert to columnar format:

```scala mdoc

//> using dep io.github.quafadas::vecxt:0.0.31

import io.github.quafadas.table.*
import vecxt.all.cumsum
import vecxt.BoundsCheck.DoBoundsCheck.yes

type ColSubset = ("Name", "Sex", "Age")

val data = CSV.resource("titanic.csv", TypeInferrer.FromAllRows)
            .take(3)
            .columns[ColSubset]

val colData = LazyList.from(data).toColumnOrientedAs[Array]

colData.Age

colData.Age.map(_.get).cumsum

```

The direct columnar reading (first approach) is recommended when you know upfront that you need columnar access, as it's more efficient.