# Column Orient

"Vector" style computation is beyond the scope of scautable itself. However, it's clear that a row oriented representation of the data, is not always the right construct - particulaly for analysis type tasks. 

To note again: **statistics is beyond the scope of scautable**. 

It is encouraged to wheel in some other alternative mathematics / stats libary (entirely at your own discretion / risk).

What scautable will do, is re-arrange the data so that it's in a columnar format. Performance is untested - it should work on a single pass of the underlying data.

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