# Type Inference

Scautable supports a bouqet of type inference strategies via it's `TypeInferrer` enum. The varying strategies are discussed below. 

## String Type

This is essentially "Safe Mode". it does nothing other than read the strings. It should fail, _only_ if the CSV is poorly formed. 

```scala mdoc
import io.github.quafadas.table.*

inline val csvContent = "Name,Age\nAlice,30\nBob,24\nJim,50"

val c: CsvIterator[("Name", "Age"), (String, String)] = CSV.fromString(csvContent, TypeInferrer.StringType)

c.foreach(println)
```

## FirstN

The compiler will read the firstN rows of the CSV file. It will test every cell, in every column, for the firstN rows, whether they can be decoded as 

- Boolean
- Int
- Long
- Double

```scala mdoc
CSV.fromString(csvContent, TypeInferrer.FirstN(2))
```
## FirstRow

Is firstN, but with Rows set to be 1 🤷

```scala mdoc

CSV.fromString(csvContent, TypeInferrer.FirstRow)
```

## FromAllRows

Is firstN, but with Rows set to be `Int.MaxValue`.

```scala mdoc
val tmp: CsvIterator[("Name", "Age"), (String, Int)]= CSV.fromString(csvContent, TypeInferrer.FromAllRows)

tmp.foreach(println)
```
## FromTuple

You take "manual" control of the decoding process.

```scala mdoc
val csv1: CsvIterator[("Name", "Age"), (String, Option[Double])] = CSV.fromString(
  csvContent,
  TypeInferrer.FromTuple[(String, Option[Double])]()
)
csv1.foreach(println)

```

Using this strategy, is it possible to decode the CSV to custom types. 

```scala mdoc
import io.github.quafadas.scautable.Decoder

enum Status:
      case Active, Inactive
      
inline given Decoder[Status] with
  def decode(str: String): Option[Status] =
    str match
      case "Active"   => Some(Status.Active)
      case "Inactive" => Some(Status.Inactive)
      case _          => None

val csv: CsvIterator[("name", "active", "status"), (String, Boolean, Status)] =
      CSV.fromString("name,active,status\nAlice,true,Active\nBob,false,Inactive", TypeInferrer.FromTuple[(String, Boolean, Status)]())

csv.foreach(println)
```

