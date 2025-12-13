# Column Orient

"Vector" style computation is beyond the scope of scautable itself. However, it's clear that a row oriented representation of the data, is not always the right construct - particularly for analysis type tasks. 

To note again: **statistics is beyond the scope of scautable**. 

It is encouraged to wheel in some other alternative mathematics / stats library (entirely at your own discretion / risk).

## Reading CSV directly as columns

Scautable can read CSV data directly into a columnar format using the `ReadAs.Columns` option. This is more efficient than reading rows and then converting, as it only requires a single pass through the data:

```scala mdoc

import io.github.quafadas.table.*

// Read directly as columns - returns NamedTuple of Arrays
val cols = CSV.resource("simple.csv", CsvOpts(readAs = ReadAs.Columns))

// Access columns directly as typed arrays
val col1: Array[Int] = cols.col1
val col2: Array[Int] = cols.col2
val col3: Array[Int] = cols.col3

// Works with type inference too
val titanic = CSV.resource("titanic.csv", CsvOpts(TypeInferrer.FromAllRows, ReadAs.Columns))
val ages: Array[Option[Double]] = titanic.Age
val survived: Array[Boolean] = titanic.Survived

```

## Converting row-oriented data to columns

Alternatively, you can read data as rows (the default) and then convert to columnar format:

```scala mdoc

//> using dep io.github.quafadas::vecxt:0.0.31

import io.github.quafadas.table.*
import vecxt.all.cumsum
import vecxt.BoundsCheck.DoBoundsCheck.yes

type cols = ("Name", "Sex", "Age")

val data = CSV.resource("titanic.csv", TypeInferrer.FromAllRows)
            .take(3)
            .columns[cols]

val cols = LazyList.from(data).toColumnOrientedAs[Array]

cols.Age

cols.Age.map(_.get).cumsum

```

The direct columnar reading (first approach) is recommended when you know upfront that you need columnar access, as it's more efficient.