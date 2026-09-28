
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

`forceColumnType` is the escape hatch if you need to strip a tag back off:

```scala
tagged.forceColumnType["price", Double]
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
