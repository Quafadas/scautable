Scautable: One line CSV import and dataframe utilities based on scala's `NamedTuple`.

[docs](https://quafadas.github.io/scautable/docs/index.html)

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



## Infrequently Asked Questions
### Is this project a good idea
Idea yes. Getting to a one line, strongly typed CSV import ala Pandas has got to be a good idea.

The implementation is somewhat metaprogamming / `.asInstanceOf` heavy, so the execution is what it is.

So unclear. One of it's purposes is to push the boundary of metaprogramming knowledge. If you use this, it exposes you to the very real risk of the reality that this is an educational project I run on my own time.

### How does it work

A combination of match types and a macro which infers the types / headers _at compile time_.
