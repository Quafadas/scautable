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

One can also read from an absolute path, or from a path anchored to the calling source file or the project root

```scala
import io.github.quafadas.table.*
val csv = Excel.absolutePath("path/to/SimpleTable.xlsx", "Sheet1")

// Relative to the source file this call sits in
val rel = Excel.relativeToSource("data/SimpleTable.xlsx", "Sheet1")
// Relative to the first ancestor holding a build marker (build.mill, build.sbt, .git, ...)
val root = Excel.projectRoot("data/SimpleTable.xlsx", "Sheet1")
```

## Inferring the range from a top left corner

Typing out the far corner of a table is tedious, and goes stale the moment someone adds a row. Instead, name the top left cell of the table and let the compiler find the rest. It walks the sheet the way `ctrl-right` and `ctrl-down` walk it in Excel - right along the header row until it hits a blank header, then down until it hits a row which is blank all the way across the table.

```scala mdoc:reset
import io.github.quafadas.table.{*, given}

// Equivalent to "A1:D3", discovered at compile time
val anchored = Excel.resource("Numbers.xlsx", "Sheet1", "A1", TypeInferrer.FromAllRows)

println(anchored.getColRange)
println(anchored.toSeq.consoleFormatNt(fansi = false))
```

Because the walk happens at compile time, the types are exactly as precise as they would be with a hand written range, and the iterator that ends up in your bytecode carries the fully resolved range - there is no scanning at runtime.

Either edge can be pinned if you would rather not have it inferred:

| Range        | Meaning                                                        |
| ------------ | -------------------------------------------------------------- |
| `"B5:Q55"`   | Exactly these cells, nothing is inferred                        |
| `"B5"`       | Anchor at B5, infer the right and bottom edges                  |
| `"B5:Q"`     | Columns pinned to `B..Q`, infer the bottom edge                 |
| `"B5:55"`    | Rows pinned to `5..55`, infer the right edge                    |

Pinning the columns is the usual escape hatch when a table has a decorative column hanging off the right of it, and pinning the rows is how you exclude a totals row that Excel would happily consider part of the table.

Two details worth knowing:

- If the anchor sits at the top left of a real Excel Table (a "ListObject" - the thing you get from _Insert -> Table_), the extent declared by that table is used verbatim, in preference to walking cells. Nothing is guessed when the workbook already says where the table ends.
- A blank header cell ends the table, because a column with no name cannot be named in the result type. Merged header cells therefore end it too - anchor at the row of real headers beneath them.

## Problems and Hints

It is strongly recommended to specify a range when working with Excel - either complete, or anchored as above. e..g.

`val range = Excel.resource("Numbers.xlsx", "Sheet1", "A1:C3", TypeInferrer.FromAllRows)`

Although this _may_ work, 

`val norange = Excel.resource("Numbers.xlsx", "Sheet1", TypeInferrer.FromAllRows)`
`val norange = Excel.resource("Numbers.xlsx", "", TypeInferrer.FromAllRows)`

Excel is somewhat pathological with regard to sheet boundaries. It is likely this will include blank cells, which will muck up type inference and reading ranges.

In general, trhe implementation here is not robust to Excel's flexibility (reading formula's is unimplemented) and assumes that we are working with a simple, well formed table. 