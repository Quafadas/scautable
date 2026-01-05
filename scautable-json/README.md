# Scautable JSON

JSON support for Scautable with compile-time type inference.

## Features

- Parse JSON arrays of flat objects at compile time
- Automatic type inference from JSON values
- Support for `Int`, `Long`, `Double`, `Boolean`, and `String` types
- Handle `null` values as `Option` types
- Extract headers from all objects in the array
- Multiple type inference strategies (FromAllRows, FirstRow, FirstN, StringType, FromTuple)

## Usage

### Basic Example

```scala
import io.github.quafadas.scautable.json.*

val jsonContent = """[{"a":1,"b":2},{"a":5,"b":3}]"""
val result = JSON.fromString(jsonContent)
val data = result.toSeq

data(0).a // 1
data(0).b // 2
data(1).a // 5
data(1).b // 3
```

### Reading from Resources

```scala
// From classpath resources
val json = JSON.resource("data.json")

// From absolute path
val json = JSON.absolutePath("/path/to/data.json")

// From current directory (compiler working directory)
val json = JSON.pwd("data.json")

// From URL
val json = JSON.url("https://example.com/data.json")
```

### Type Inference Strategies

```scala
// Infer from all rows (default, most accurate)
JSON.fromString(content)
JSON.fromString(content, TypeInferrer.FromAllRows)

// Infer from first row only (faster)
JSON.fromString(content, TypeInferrer.FirstRow)

// Infer from first N rows
JSON.fromString(content, TypeInferrer.FirstN(100))

// Treat all columns as String
JSON.fromString(content, TypeInferrer.StringType)

// Use explicit types
JSON.fromString[("name", "age"), (String, Int)](content, TypeInferrer.FromTuple[(String, Int)]())
```

### Handling Missing Fields

When objects in the array have different keys, missing fields are automatically handled as `Option` types:

```scala
val jsonContent = """[{"a":1,"b":2},{"a":3}]"""
val result = JSON.fromString(jsonContent)
val data = result.toSeq

data(0).a // 1
data(0).b // Some(2)
data(1).a // 3
data(1).b // None
```

### Supported JSON Structure

The JSON must be:
- An array of objects
- Objects must be flat (no nesting)
- All objects are expected to have similar structure

Example valid JSON:
```json
[
  {"name": "Alice", "age": 30, "active": true},
  {"name": "Bob", "age": 25, "active": false}
]
```

## Type Inference

Type inference follows these rules:
1. Numbers without decimals are inferred as `Int` (if in range), then `Long`, then `Double`
2. `true`/`false` values are inferred as `Boolean`
3. Text values are inferred as `String`
4. `null` values cause the type to be wrapped in `Option`
5. Mixed types default to `String`

## Installation

Add to your `build.sbt`:
```scala
libraryDependencies += "io.github.quafadas" %% "scautable-json" % "version"
```

Or in Mill:
```scala
ivy"io.github.quafadas::scautable-json:version"
```
