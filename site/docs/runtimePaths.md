# Runtime Paths

There are usecases, where the runtime path of the CSV file may be unknown, but you may be willing to write out the structure of the CSV file in advance.

```scala
import io.github.quafadas.table.{*, given}

// A function, which when provided a path, reads the CSV file
val csvReader: Path => CsvIterator[("col1", "col2", "col3"), (String, String, String)] = CSV.fromTyped[("col1", "col2", "col3"), (String, Int, String)]

val couldBeAnywhere = os.pwd / "data" / "simple.csv"

val csv: CsvIterator[("col1", "col2", "col3"), (String, String, String)]  = csvReader(couldBeAnywhere)

```
In this case, you must provide the headers and columns types in advance. Scautable will thrown an error if the headers in the CSV file do not match the expected headers, or it cannot decode the values into the expected types.