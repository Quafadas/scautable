# Parquet

The `scautable-parquet` module reads a Parquet file's schema from its footer at compile time. Rows are exposed as typed `NamedTuple` values, so column names, primitive types, optional fields, and nested structs are known to the Scala compiler.

## Setup

For Scala CLI:

```scala
//> using dep io.github.quafadas::scautable-parquet::@VERSION@
```

For Mill:

```scala
mvn"io.github.quafadas::scautable-parquet::@VERSION@"
```

Import the Parquet API and the regular table operations:

```scala
import io.github.quafadas.scautable.parquet.*
import io.github.quafadas.table.*
```

## Reading rows

Use `resource` for files on the classpath:

```scala
val passengers = Parquet.resource("titanic.parquet")

val first = passengers.next()
println(first.Name)
println(first.Age)
```

The return type is inferred from the footer. An optional Parquet `INT64` becomes `Option[Long]`, for example, while a required `BINARY (STRING)` becomes `String`.

Other source methods mirror the CSV API:

```scala
val absolute = Parquet.absolutePath("/data/passengers.parquet")
val relative = Parquet.pwd("data/passengers.parquet")
```

The path must be a compile-time constant because the macro reads the schema while compiling. The same file must also be available when the resulting program runs.

## Nested structs

Parquet groups are represented as nested `NamedTuple` values. Given this schema:

```text
message people {
  required int64 id;
  optional group address {
    required binary street (STRING);
    optional int32 postcode;
    optional group coordinates {
      required double latitude;
      required double longitude;
    }
  }
}
```

Scautable infers the equivalent of:

```scala
type Coordinates =
  NamedTuple[("latitude", "longitude"), (Double, Double)]

type Address = NamedTuple[
  ("street", "postcode", "coordinates"),
  (String, Option[Int], Option[Coordinates])
]

type Row = NamedTuple[
  ("id", "address"),
  (Long, Option[Address])
]
```

Nested names retain compile-time field selection:

```scala
val people = Parquet.resource("people.parquet")
val person = people.next()

val street: Option[String] = person.address.map(_.street)
val postcode: Option[Int] = person.address.flatMap(_.postcode)
val latitude: Option[Double] =
  person.address.flatMap(_.coordinates).map(_.latitude)
```

Nested structs can be required or optional and may be nested to arbitrary depth. A missing optional parent is distinct from a present parent whose optional children are all missing:

```scala
person.address == None

person.address == Some((
  street = "Main Street",
  postcode = None,
  coordinates = None
))
```

## Optional fields

The default, `ParquetOptionality.FromSchema`, preserves optionality throughout the nested structure:

```scala
val people = Parquet.resource(
  "people.parquet",
  ParquetOptionality.FromSchema
)
```

`ParquetOptionality.NoOptions` removes `Option` from both top-level and nested fields:

```scala
val people = Parquet.resource(
  "people.parquet",
  ParquetOptionality.NoOptions
)
```

If the file contains an absent value, reading fails with a `ParquetDecodeException`. Errors for nested values include the full field path, such as `address.coordinates`.

## Rows and columns

Nested structs are supported by the row-oriented reader:

```scala
val rows = Parquet.resource("people.parquet", ReadAs.Rows)
```

`ReadAs.Columns` remains available for flat schemas and returns one typed array per column:

```scala
val columns = Parquet.resource("titanic.parquet", ReadAs.Columns)
val ages: Array[Option[Double]] = columns.Age
```

Requesting `ReadAs.Columns` for a nested schema is rejected at compile time. A useful nested column representation needs child arrays and parent validity information rather than an `Array` of row-shaped structs, so this is kept as an explicit API boundary.

## Type mapping

| Parquet type | Scala type |
| --- | --- |
| `BOOLEAN` | `Boolean` |
| `INT32` | `Int` |
| `INT32 (DATE)` | `java.time.LocalDate` |
| `INT64` | `Long` |
| `INT64 (TIMESTAMP)` or `INT96` | `java.time.Instant` |
| `FLOAT` | `Float` |
| `DOUBLE` | `Double` |
| `BINARY (STRING, ENUM, JSON)` | `String` |
| `BINARY (UUID)` | `java.util.UUID` |
| unannotated `BINARY` | `Array[Byte]` |
| `DECIMAL` | `BigDecimal` |
| required group | nested `NamedTuple` |
| optional group | `Option[nested NamedTuple]` |

## Current limitations

- `LIST`, `MAP`, and other `REPEATED` fields are not yet supported.
- Nested schemas currently require `ReadAs.Rows`.
- Resources packaged inside a JAR are not supported because Parquet footer access requires random access. Use `absolutePath` for such deployments.
- Row iteration is single-use and should be closed early when it is not consumed to exhaustion.

```scala
val rows = Parquet.resource("people.parquet")
try rows.take(10).toVector
finally rows.close()
```

Use `Parquet.schemaOf` when inspecting an unsupported file:

```scala
val schema = Parquet.schemaOf(
  ParquetSource.AbsolutePath("/data/people.parquet")
)
println(schema)
```