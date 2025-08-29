# Build Notes

## scala-cli

I found using the resource folder directive to be a convienient and portable way of working with CSV files

```scala sc:nocompile
//> using scala 3.7.1
//> using dep io.github.quafadas::scautable:{{latest}}
//> using resourceDir ./csvs

import io.github.quafadas.table.*

@main def checkCsv =
  def csv = CSV.resource("cereals.csv")

  csv.take(10).toSeq.ptbln

```

## Mill

Is my preferred build tool. Note that something like this;

```scala sc:nocompile
def csv : CsvIterator[("col1", "col2", "col3")] = CSV.resource("simple.csv")
```
May (confusingly) throw a compile error at you. Remember: scautable asks the _compiler_ to analyze the CSV file.

Mill seperates compile resources and run resources. From the compilers point of view, "simple.csv" is indeed _not_ a resource by default in mill.

Now that we know this, it's easy enough to work around in a few ways. Here's one way that adds the runtime resources to the compilers resource path - thus ensuring that the CSV file is available to the compiler, at compile time.

```scala sc:nocompile
trait ShareCompileResources extends ScalaModule {
  override def compileResources = super.compileResources() ++ resources()
}
```