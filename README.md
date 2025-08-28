
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
Idea yes - the implementation is somewhat metaprogamming / `.asInstanceOf` heavy.

So unclear. One of it's purposes is to push the boundary of my metaprogramming knowledge. If you use this, it exposes you to the very real risk of the reality that this is an educational project I run on my own time.

### How does it work

The macro stuff came from coffee and chatGPT.

The table derivation show from case class stuff comes from here;
I aggressively copy pasted everything from here and poked it with a sharp stick until it did what I wanted.
https://blog.philipp-martini.de/blog/magic-mirror-scala3/

### Limitations

For the desktop show part -
- Formatting is implied by the type. To format your own types, you'll need to write a given for it.
- Extension is through the type system, have a look at the JVM tests for an example if writing a given for your own custom type
- As I don't _really_ understand how it works, it's unlikely to get extended further...
- Extending it further is probably a really bad idea anyway

For the CSV part :
- It is assumed you have one header row, and that your headers are reasonably representaable by the compiler.
- As of early 2024 there is a compiler bug that reverses the order of large named tuples. CSV files over 22 might get weird - I don't believe the limitation to be fundaemntal, just need to wait for the fix.

// TODO: Docs

// TODO: Sample (graduated)
