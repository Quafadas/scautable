Scautable: One line CSV import and dataframe utilities based on scala's `NamedTuple`.

[docs](https://quafadas.github.io/scautable/)

# SCala AUto TABLE

- Strongly typed compile-time CSV
- pretty printing to console for `Product` types
- Auto-magically generate html tables from case classes
- Searchable, sortable browser GUI for your tables

## Elevator Pitch
One line CSV import.

```scala
import io.github.quafadas.table.*

val csv : CsvIterator[("col1", "col2", "col3"), (Int, Int, Int)] = CSV.resource("simple.csv", TypeInferrer.FromAllRows)
val  data = LazyList.from(csv).take(2)

data.ptbln
// | |col1|col2|col3|
// +-+----+----+----+
// |0|   1|   2|   7|
// |1|   3|   4|   8|
// +-+----+----+----+
```

## Path anchors

`CSV`, `Excel`, `JsonTable` and `Parquet` all resolve paths the same way:

- `relativeToSource("file.csv")`: resolves from the directory of the source file that contains the macro call.
- `projectRoot("path/to/file.csv")`: resolves from the first ancestor containing a project marker (`build.sbt`, `build.sc`, `build.mill`, `.scala-build`, `.git`, ...).
- `resource("file.csv")`: resolves from the runtime classpath.
- `absolutePath("/abs/path/file.csv")`: resolves from an explicit absolute file path.

`pwd(...)` has been removed - it anchored to the _compiler's_ working directory, which is rarely where you think it is. Prefer `relativeToSource(...)`.

In a notebook or REPL (almond, ammonite) there is no source file on disk, and behind a build server the compiler runs in a daemon whose working directory is a cache directory. For those, declare the anchor outright and it takes priority over anything the two anchored constructors would otherwise infer:

- `-Xmacro-settings:scautable.root=/path/to/dir` travels with the compile request, so it reaches a build server daemon.
- `System.setProperty("scautable.root", "/path/to/dir")` from an earlier cell, for a notebook kernel that compiles in its own JVM.

Without one, the anchored constructors fall back to the working directory and emit a compile time warning saying so. See [Workbooks](site/docs/cookbook/workbooks.md).



## Infrequently Asked Questions
### Is this project a good idea
Idea yes. Getting to a one line, strongly typed CSV import ala Pandas has got to be a good idea.

The implementation is somewhat metaprogamming / `.asInstanceOf` heavy, so the execution is what it is.

So unclear. One of it's purposes is to push the boundary of metaprogramming knowledge. If you use this, it exposes you to the very real risk of the reality that this is an educational project I run on my own time.

### How does it work

A combination of match types and a macro which infers the types / headers _at compile time_.
