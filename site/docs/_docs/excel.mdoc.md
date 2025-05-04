---
title: Excel
---

Reading tables from excel should work a very similar way to CSV, with support being rather experimental and assuming that the excel workbook is rather well behaved.


```scala sc:nocompile
import io.github.quafadas.table.*

def csv: ExcelIterator[("Column 1", "Column 2", "Column 3")] = Excel.absolutePath("path/to/SimpleTable.xlsx", "Sheet1")
```