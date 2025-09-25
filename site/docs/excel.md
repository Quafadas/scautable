# Excel


Reading tables from excel should work a very similar way to CSV. The default behaviour is a bit of a hail mary. It assumes the excel workbook is rather well behaved, and that a blindly configured apache POI `RowIterator` will capture appropriate data. You should _not_ expect this method to be robust to blank rows / columns/ data elsewhere in the sheet.

```scala mdoc
import io.github.quafadas.table.*

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

## Limitations

Excel is rather hard to make work reliably. 

Formulae should be evaluated by default - this might make your workbook very slow, if you attempt to evaluate lots of formulae. 