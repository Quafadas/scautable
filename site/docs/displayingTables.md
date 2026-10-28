
# Displaying Tables

## In console

In scripts or conosle, fansi formatting is strongly preferred. 

```scala mdoc sc:nocompile
import io.github.quafadas.table.*

def csv = CSV.fromString("col1,col2,col3\n1,2,7\n3,4,8\n5,6,9")
csv.toSeq.consoleFormatNt(fansi = false)

// This  much better in a console / script, but ugly in browser
csv.toSeq.consoleFormatNt(fansi = true)

// but this is easier to type and is fansi;
csv.toSeq.ptbln

```

## Formatting individual columns

By default every cell is rendered with `toString`, which is rarely what you want for
numbers - a price, a rate and a ratio are all `Double`, but none of them should look
the same.

`formatColumn` attaches a *display tag* to one column. The tag lives only in the type;
the value underneath is untouched, and attaching one costs nothing at runtime.

```scala
import io.github.quafadas.table.*

def csv = CSV.fromString("product,price,margin\nwidget,1234.5678,0.0425\ngizmo,99.5,0.113")

csv.toSeq
  .formatColumn["price", Currency["$", 2]]
  .formatColumn["margin", Percent[2]]
  .ptbln
```

```
+---------+-----------+--------+
| product | price     | margin |
+---------+-----------+--------+
| widget  | $1,234.57 | 4.25%  |
| gizmo   | $99.50    | 11.30% |
+---------+-----------+--------+
```

The same tags drive `html` and `desktopShowNt`, so the browser and the terminal agree.

### Styling many columns at once

`formatColumn` takes one column at a time. `style` takes a *spec* - a named tuple type whose
names are columns and whose values are tags - and applies the lot in one call:

```scala
import io.github.quafadas.table.*

type Report = (price: Currency["$", 2], margin: Percent[2])

def csv = CSV.fromString("product,price,margin\nwidget,1234.5678,0.0425\ngizmo,99.5,0.113")

csv.toSeq.style[Report].ptbln
```

```
+---------+-----------+--------+
| product | price     | margin |
+---------+-----------+--------+
| widget  | $1,234.57 | 4.25%  |
| gizmo   | $99.50    | 11.30% |
+---------+-----------+--------+
```

Identical to the `formatColumn` chain above - including at runtime, where both are casts.
What the spec buys you is that it is a *type*: it can be named, reused across every table
that has those columns, and kept next to the code that displays the data rather than the
code that builds it.

Columns the spec doesn't mention are left alone, so one spec can serve a family of tables,
and the order of the spec is irrelevant.

Spreadsheet columns often have spaces in their names, which backticks handle:

```scala
type PriceSheet = (
    `Bid Spread`: Decimals[2],
    `Offer Spread`: Decimals[2],
    `Change`: Decimals[4],
    EL: Percent[1]
)

brokerInfo.style[PriceSheet].ptbln
```

A spec that names a column which isn't there, or hands a column a tag it can't carry, is a
compile error that names the column:

```
style: no column named prise
style: the tag given for column product is not compatible with its type
```

### Built-in tags

`N` is always the number of decimal places to round to.

| Tag | Applies to | `1234.5678` renders as |
|-|-|-|
| `Decimals[N]` | `Double` | `Decimals[2]` → `1234.57` |
| `SigFigs[N]` | `Double` | `SigFigs[4]` → `1235` |
| `Thousands` | `Double` | `1,234.57` |
| `Currency[Sym, N]` | `Double` | `Currency["$", 2]` → `$1,234.57` |
| `IntThousands` | `Int` | `9876543` → `9,876,543` |
| `LongThousands` | `Long` | `9876543210` → `9,876,543,210` |

Rates, where the value is a fraction and you want it read at a different scale:

| Tag | Applies to | Example |
|-|-|-|
| `Percent[N]` | `Double` | `Percent[2]` of `0.0425` → `4.25%` |
| `BasisPoints[N]` | `Double` | `BasisPoints[0]` of `0.0425` → `425 bps` |

Large magnitudes, where you want a compact number rather than a long one. Note these
*rescale* the value, unlike `Thousands`, which keeps it at full scale and only adds
grouping separators:

| Tag | Applies to | Example |
|-|-|-|
| `InThousands[N]` | `Double` | `InThousands[3]` of `1234.0` → `1.234 k` |
| `InMillions[N]` | `Double` | `InMillions[4]` of `123400.0` → `0.1234 M` |
| `InBillions[N]` | `Double` | `InBillions[2]` of `2500000000.0` → `2.50 B` |

Rescaling and percentage arithmetic are done in `BigDecimal`, not on the `Double`, so
`InMillions[4]` of `123400.0` is exactly `0.1234 M` rather than `0.1233 M`.

Columns read from a CSV with blank cells arrive as `Option[Double]`; tag them exactly the
same way, and `None` still renders as `None`.

Formatting is locale-independent - a `.` is always the decimal separator, on the JVM and
in the browser alike.

### Defining your own

A tag is a *bounded opaque type* plus a `given CellFormat` for it:

```scala
import io.github.quafadas.table.*

object MyTags:
  opaque type Bytes <: Long = Long
  given CellFormat[Bytes] = (b: Bytes) =>
    if b < 1024 then s"$b B" else f"${b / 1024.0}%.1f KiB"

import MyTags.{Bytes, given}

data.formatColumn["size", Bytes].ptbln
```

The bound (`<: Long`) is what keeps the column usable: `numericCols`, `summary` and
arithmetic all still see a `Long`. A plain marker trait would *not* work here, because
`Long` is final - the compiler proves `Long & Bytes` uninhabited and the column machinery
stops reducing.

### Tag late

A tag is a presentation concern, so apply it at the end of a pipeline, just before
displaying. Because the column's declared type becomes `Decimals[2]` rather than `Double`,
anything that needs an *invariant* typeclass on that exact type - `Numeric`, `ClassTag`, a
CSV `Decoder` - will no longer resolve for it. Match-type machinery (`numericCols`,
`columns`, `dropColumn`, `summary`) is unaffected.

`forceColumnType` is the escape hatch if you need to strip a tag back off, and `retype`
does it for several columns at once:

```scala
tagged.forceColumnType["price", Double]

tagged.retype[(price: Double, margin: Double)]
```

Both are unchecked casts, but stripping a display tag is the case where that is always
sound - a tag erases to the type underneath it, so nothing about the data changes.


## In a notebook (almond)

The `scautable-almond` module renders tables in an [almond](https://almond.sh) Jupyter notebook. It is
a separate artifact so that core scautable doesn't depend on almond, which the kernel already provides.

```scala
//> using dep io.github.quafadas::scautable-almond:@VERSION@
// or: import $ivy.`io.github.quafadas::scautable-almond:@VERSION@`

import io.github.quafadas.table.*
import io.github.quafadas.scautable.notebook.{*, given}

val data = CSV.resource("cereals.csv").toVector

data.display()                            // head and tail, if over 60 rows
data.display(maxRows = 200, edgeRows = 10)
data.displayAll                           // every row
data.ptbln                                // in a notebook, the same as display()
```

Column headers come from the named tuple's type, so an empty table still shows its headers.
Format tags (`formatColumn`, `style`) apply exactly as they do in the console.

Each call publishes a MIME bundle:

- `text/html` - a scrollable table with a sticky header, styled to fit in JupyterLab, the classic notebook and VS Code
- `text/plain` - an uncoloured text table, for `nbconvert`, exports and consoles
- `application/vnd.dataresource+json` - only with `display(dataResource = true)`. A [Table Schema](https://specs.frictionlessdata.io/table-schema/)
  payload of the first `maxRows` rows, for frontends with a data-explorer extension.

Like pandas, tables over `maxRows` (default 60) show `edgeRows` (default 5) from each end, with a
"… n more rows" marker between them. Only the rows shown are formatted.

### Iterators

`display` is only defined for an `Iterable`. Displaying an `Iterator` would consume it, leaving
nothing for the next cell, so materialise it first with `.toVector` or `.toSeq`. `ptbln` still
accepts an `Iterator`, as it always has, and consumes it.

### `ptbln` in a notebook

`import notebook.given` brings in a notebook `TableSink`, which `ptbln` prefers over the default
console sink. Leave out `given` if you'd rather `ptbln` printed text.

### Updating a table in place

`notebookTable` builds the bundle without publishing it, so you can use almond's display ids:

```scala
val handle = data.take(10).notebookTable().displayData.withId("progress")
handle.show()
// ... later
data.take(20).notebookTable().displayData.withId("progress").update()
```

## In browser (currently untested with named tuples)

```scala
import io.github.quafadas.table.*

case class ScauTest(anInt: Int, aString: String)

val table = Seq(ScauTest(1, "one"), ScauTest(2, "two"), ScauTest(3, "booyakashaha!"))

println(table.consoleFormat(fancy = false))

```

On the JVM in particular, the ability to pop it open in the browser, see and search the actual data... can be useful. Particularly if you're working with a lot of messy, csv data for example.

```scala
import io.github.quafadas.table.*

case class ScauTest(anInt: Int, aString: String)
val soComplex = Seq(ScauTest(1, "one"), ScauTest(2, "two"))

scautable.desktopShow(soComplex)
```
Will pop open a browser... using https://datatables.net
![desktop](../_assets/desktop.png)

And your case classes are now easily visible and searchable.
