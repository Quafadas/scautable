# Excel


Reading tables from excel should work a very similar way to CSV. The default behaviour is a bit of a hail mary. It assumes the excel workbook is rather well behaved, and that a blindly configured apache POI `RowIterator` will capture appropriate data. You should _not_ expect this method to be robust to blank rows / columns/ data elsewhere in the sheet.

```scala sc:nocompile
import io.github.quafadas.table.*

def csv: ExcelIterator[("Column 1", "Column 2", "Column 3")] = Excel.absolutePath("path/to/SimpleTable.xlsx", "Sheet1")
def csv: ExcelIterator[("Column 1", "Column 2", "Column 3")] = Excel.resource("SimpleTable.xlsx", "Sheet1")
```

One can also read a (presumably well behaved) range.

```scala sc:nocompile
import io.github.quafadas.table.*

def csv: ExcelIterator[("Column 1", "Column 2", "Column 3")] = Excel.resource("path/to/SimpleTable.xlsx", "Sheet1", "A1:C3")
```
