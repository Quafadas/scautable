# JsonTable

Scautable will read a JSON file and attempt to infer the structure of the data under the following assumptions:

- A single array of objects at the root of the file,
- No nesting in objects

```scala mdoc
import io.github.quafadas.table.{*, given}

inline val jsonContent = """[{"active":true,"age":30,"name":"Alice"},{"active":false,"age":25,"name":"Bob"}]"""
println(JsonTable.fromString(jsonContent).toVector.consoleFormatNt(fansi = false))

```

Other reading methods include `resource` and `absolutePath`:

```scala
import io.github.quafadas.table.{*, given}

val jsonFromResource = JsonTable.resource("mini-movies.json")
val jsonFromAbsolutePath = JsonTable.absolutePath("/absolute/path/to/file.json")
val jsonFromWorkingDir = JsonTable.pwd("relative/path/to/file.json")

```