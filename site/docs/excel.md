# Excel

Reading tables from excel should work a very similar way to CSV. The default behaviour is a bit of a hail mary. It assumes the excel workbook is rather well behaved, and that a blindly configured apache POI `RowIterator` will capture appropriate data. You should _not_ expect this method to be robust to blank rows / columns/ data elsewhere in the sheet.

```scala mdoc
import io.github.quafadas.table.{*, given}

val csv: ExcelIterator[("Column 1", "Column 2", "Column 3"), (String, String, String)] = Excel.resource("SimpleTable.xlsx", "Sheet1")
println(csv.toSeq.consoleFormatNt(fansi = false))

val csv2 = Excel.resource("Numbers.xlsx", "Sheet1", TypeInferrer.FromAllRows)
println(csv2.toSeq.consoleFormatNt(fansi = false))


val range = Excel.resource("Numbers.xlsx", "Sheet1", "A1:C3", TypeInferrer.FromAllRows)

println(range.toSeq.consoleFormatNt(fansi = false))

```

One can also read from an absolute path

```scala
import io.github.quafadas.table.*
val csv = Excel.absolutePath("path/to/SimpleTable.xlsx", "Sheet1")
```

## Problems and Hints

It is strongly recommended to specify a complete range when working with Excel. e..g.

`val range = Excel.resource("Numbers.xlsx", "Sheet1", "A1:C3", TypeInferrer.FromAllRows)`

Although this _may_ work, 

`val norange = Excel.resource("Numbers.xlsx", "Sheet1", TypeInferrer.FromAllRows)`
`val norange = Excel.resource("Numbers.xlsx", "", TypeInferrer.FromAllRows)`

Excel is somewhat pathological with regard to sheet boundaries. It is likely this will include blank cells, which will muck up type inference and reading ranges.

In general, trhe implementation here is not robust to Excel's flexibility (reading formula's is unimplemented) and assumes that we are working with a simple, well formed table. 