# Column Orient

"Vector" style computation is beyond the scope of scautable itself. However, it's clear that a row oriented representation of the data, is not always the right construct - particularly for analysis type tasks. 

To note again: **statistics is beyond the scope of scautable**. 

It is encouraged to wheel in some other alternative mathematics / stats library (entirely at your own discretion / risk).

## Reading CSV directly as columns

Scautable can read CSV data directly into a columnar format using the `ReadAs.Columns` option. This is more efficient than reading rows and then converting, as it only requires a single pass through the data.

This will fire up a repl with necssary imports;

```sh
scala-cli repl --dep io.github.quafadas::scautable::@VERSION@ --dep io.github.quafadas::vecxt:0.0.35 --java-opt "--add-modules=jdk.incubator.vector" --scalac-option -Xmax-inlines --scalac-option 2048 --java-opt -Xss4m --repl-init-script 'import io.github.quafadas.table.{*, given}; import vecxt.all.{*, given}'
```

```scala mdoc

import io.github.quafadas.table.*

// Read directly as columns - returns NamedTuple of Arrays
// lazy - useful to prevent printing repl
lazy val simpleCols = CSV.resource("simple.csv", CsvOpts(readAs = ReadAs.Columns))

// Access columns directly as typed arrays
val col1: Array[Int] = simpleCols.col1
val col2: Array[Int] = simpleCols.col2
val col3: Array[Int] = simpleCols.col3

// With vecxt, we get optimsed vector operations too.
// simpleCols.col1 + simpleCols.cols2

// Works with type inference
val titanicCols = CSV.resource("titanic.csv", CsvOpts(TypeInferrer.FromAllRows, ReadAs.Columns))
val ages: Array[Option[Double]] = titanicCols.Age
val survived: Array[Boolean] = titanicCols.Survived


```


## Converting row-oriented data to columns

Alternatively, you can read data as rows (the default) and then convert to columnar format:

```scala mdoc

//> using dep io.github.quafadas::vecxt:0.0.31

import io.github.quafadas.table.*
import vecxt.all.cumsum
import vecxt.BoundsCheck.DoBoundsCheck.yes

type ColSubset = ("Name", "Sex", "Age")

val data = CSV.resource("titanic.csv", TypeInferrer.FromAllRows)
            .take(3)
            .columns[ColSubset]

val colData = LazyList.from(data).toColumnOrientedAs[Array]

colData.Age

colData.Age.map(_.get).cumsum

```

The direct columnar reading (first approach) is recommended when you know upfront that you need columnar access, as it's more efficient.

## Reading CSV as Dense Arrays

For interoperability with numerical libraries (e.g., BLAS, LAPACK) or when you need a single contiguous memory layout, scautable provides dense array reading modes. These modes read all CSV data into a single flat array with stride information for accessing rows and columns.

### Column-Major Dense Arrays

Column-major layout stores data column-by-column in memory, which is the standard layout for Fortran and mathematical libraries like BLAS/LAPACK.

```scala mdoc

import io.github.quafadas.table.*

// Read as column-major dense array
val colMajor = CSV.resource("simple.csv", CsvOpts(readAs = ReadAs.ArrayDenseColMajor[Int]()))

// Access the fields
val cmData: Array[Int] = colMajor.data        // The flat array containing all data
val cmRowStride: Int = colMajor.rowStride     // Stride to next row = numRows
val cmColStride: Int = colMajor.colStride     // Stride to next column = 1
val cmRows: Int = colMajor.rows               // Number of rows
val cmCols: Int = colMajor.cols               // Number of columns

// Access element at row i, col j
def getElementColMajor(i: Int, j: Int): Int = 
  cmData(j * cmRowStride + i * cmColStride)

// Example: get element at row 1, col 1
val cmElement = getElementColMajor(1, 1)

```

In column-major layout:
- `colStride = 1` (next element in the same column)
- `rowStride = numRows` (jump to the next row)
- Data is stored: `[col0_row0, col0_row1, ..., col1_row0, col1_row1, ...]`

### Row-Major Dense Arrays

Row-major layout stores data row-by-row in memory, which is the standard layout for C and most programming languages.

```scala mdoc

import io.github.quafadas.table.*

// Read as row-major dense array
val rowMajor = CSV.resource("simple.csv", CsvOpts(readAs = ReadAs.ArrayDenseRowMajor[Double]()))

// Access the fields
val rmData: Array[Double] = rowMajor.data     // The flat array containing all data
val rmRowStride: Int = rowMajor.rowStride     // Stride to next row = 1
val rmColStride: Int = rowMajor.colStride     // Stride to next column = numCols
val rmRows: Int = rowMajor.rows               // Number of rows
val rmCols: Int = rowMajor.cols               // Number of columns

// Access element at row i, col j
def getElementRowMajor(i: Int, j: Int): Double = 
  rmData(i * rmColStride + j * rmRowStride)

// Example: get element at row 0, col 2
val rmElement = getElementRowMajor(0, 2)

```

In row-major layout:
- `rowStride = 1` (next element in the same row)
- `colStride = numCols` (jump to the next column)
- Data is stored: `[row0_col0, row0_col1, ..., row1_col0, row1_col1, ...]`

### Type Safety

The dense array modes require a type parameter specifying the array element type:

```scala
// Strongly typed as Array[Int]
val intArray = CSV.resource("data.csv", CsvOpts(readAs = ReadAs.ArrayDenseColMajor[Int]()))

// Strongly typed as Array[Double]
val doubleArray = CSV.resource("data.csv", CsvOpts(readAs = ReadAs.ArrayDenseRowMajor[Double]()))

// Strongly typed as Array[String]
val stringArray = CSV.fromString("a,b\nfoo,bar", CsvOpts(readAs = ReadAs.ArrayDenseColMajor[String]()))
```

The type conversion is handled automatically using scautable's `ColumnDecoder` infrastructure, which supports `Int`, `Long`, `Double`, `Boolean`, `String`, and `Option` types.

### Use Cases

Dense arrays are particularly useful for:

- **Numerical computing**: Passing data to BLAS/LAPACK or other numerical libraries
- **Machine learning**: Preparing data for algorithms that expect contiguous arrays
- **Performance**: Single memory allocation and cache-friendly access patterns
- **Interop**: Integration with libraries expecting specific memory layouts (column-major for Fortran/R, row-major for C/Python)