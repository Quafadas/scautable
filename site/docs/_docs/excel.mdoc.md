---
title: Excel
---

# Getting started

In the same way as CSV, it is possible import a table from Excel. All columns are assumed to be strings. 

```scala mdoc sc:nocompile
import io.github.quafadas.table.*

def table: CsvIterator[("Column 1","Column 2","Column 3")] = Excel.resource("SimpleTable.xlsx", "Sheet1")
def firstRows = table.take(2)

println(firstRows.toArray.consoleFormatNt(fansi = false))

```
