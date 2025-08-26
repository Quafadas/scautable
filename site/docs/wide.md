# Wide Csv Files

For files with many columns, the strategy of injecting the column names into the compiler is somwehat risky, and as you get larger number of columns (cca 1000), you may begin to experience compiler crashes.

The project has successfully processed a 5000 column CSV with the following JVM settings

```sh
-Xss100m
-Xmx10G
```

and args to the scala compiler

```scala sc:nocompile
def scalacOptions: T[Seq[String]] = Seq("-Xmax-inlines:10000")
```

passed the below test.

```scala sc:nocompile
  test("5000 cols") {
    def csv = CSV.resource("testFile/5000Cols.csv")
    assert(csv.headers.length == 5000)
  }
```

Please bear in mind that scautable is targeted for the "small". Beyond this point, feel free to experiment but expect instability - scautable may not be the right strategy for your data analysis.

The number of rows should not be problematic. The design is lazy in nature - although again please bear in mind that we are targeting "small". Scautable is untested beyond 5 million rows 🤷.
