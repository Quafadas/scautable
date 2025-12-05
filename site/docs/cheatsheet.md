# Cheatsheet

```bash
scala-cli repl --dep io.github.quafadas::scautable::@VERSION@ \
  --scalac-option -Xmax-inlines --scalac-option 2048 \
  --java-opt -Xss4m \
  --repl-init-script 'import io.github.quafadas.table.{*, given}'
```

## Reading CSV Files

| Csv Available As | CSV Size | Columns | Hints |
|-|-|-|-|
| Inlined in code | <1Kb | < 100 |  `inline val s= "aHeader\nr1c1\nr2c1"` <br><br> `CSV.fromString(s)` |
| File - Compile Time | < 20Mb | <100 |  `CSV.pwd("a.csv")` <br><br> `CSV.resource("a.csv")` <br><br> `CSV.absolutePath("/abs/path/to/a.csv")` |
| File - Compile Time | > 20Mb & < 250 Mb  |  |  Dont attempt to infer types at compile time. <br><br>`val inferOpts = CsvOpts(TypeInferrer.FirstN(20000))` <br><br> `val knownOpts = CsvOpts(TypeInferrer.FromTuple[(Int, String, Double)])` <br><br>  `CSV.pwd("/path/a.csv", inferOpts)` <br><br> `CSV.absolutePath("/path/a.csv", knownOpts)` <br><br> `CSV.resource("a.csv", knownOpts)` |
| File - Run Time | < 250Mb | <100 |  `val reader = CSV.fromTyped[("col1", "col2", "col3"), (String, Int, Double)]` <br><br> `val data = reader(os.pwd / "simple.csv")` |
| File  | > 250 Mb  or | >100 columns | Scautable is not the right thing to analyse this file. Consider a proper query optimised dataframe library such as Spark or Polars |

## Displaying Tables

Assuming you have an `Iterator` / `Iterable` of named tuples.

e.g. `val data : Seq[(col1 : String, col2 : Int, col3 : Double)] = ???`

| Target | Hints |
|-|-|
| repl print / markdown | `data.ptbln` |
| String | `data.consoleFormatNt(fansi = false)` |
| html | `HtmlRenderer.nt(data)` |
| Almond | `Html(HtmlRenderer.nt(data))` |
| browser window | `HtmlRenderer.desktopShowNt(data)` |


## Excel Operations (JVM only)

TBD


## Type Inference

| Inferrer | Use Case |
|----------|----------|
| `FromAllRows` | Most accurate type detection (default) |
| `StringType` | Fast parsing when types not needed |
| `FromFirstNRows(n)` | Balance between accuracy and speed |


