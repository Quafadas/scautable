# Generate CSV

A short script you shoudl be able to generate a CSV file with random data.

```scala mdoc:compile-only
import io.github.quafadas.table.{*, given}

def gen1 = (
  year = scala.util.Random.nextInt(10000),
  day = scala.util.Random.nextInt(365),
  amount = scala.util.Random.nextDouble()*1e9
)

// to test on a small set
Vector.fill(10)(gen1).ptbln

val csv = Vector.fill(10)(gen1)
// Warning: may overflow with large data
println(csv.toCsvString())

// Writes line by line
val afile = csv.writeCsv()

// A neat version
Iterator.continually(gen1).take(100).writeCsvIterator()
```