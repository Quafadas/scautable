---
title: CSV
---

# Getting started

Our first move, is to tell the compiler, where the file may be found. `CSV.resource` is a macro which reads the column headers and injects them into the compilers type system.

`CsvIterator` extends iterator.

```scala sc:nocompile mdoc
import io.github.quafadas.table.*

def csv : CsvIterator[("col1", "col2", "col3")] = CSV.resource("simple.csv")
def firstRow: Iterator[(col1: String, col2: String, col3: String)] = csv.take(1)

println(firstRow.consoleShow(fancy = false))

```