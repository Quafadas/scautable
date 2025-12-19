package io.github.quafadas.scautable

import io.github.quafadas.table.*

class CsvDenseArraySuite extends munit.FunSuite:

  test("simple.csv - ArrayDenseColMajor[Int] basic structure") {
    val result = CSV.resource("simple.csv", CsvOpts(readAs = ReadAs.ArrayDenseColMajor[Int]()))

    // Check that we get the expected fields
    val data: Array[Int] = result.data
    val rowStride: Int = result.rowStride
    val colStride: Int = result.colStride
    val rows: Int = result.rows
    val cols: Int = result.cols

    // Verify dimensions
    assertEquals(rows, 3)
    assertEquals(cols, 3)
    assertEquals(data.length, 9)

    // In column-major order: colStride = 1 (next element in same column), rowStride = rows (jump to next column)
    assertEquals(colStride, 1)
    assertEquals(rowStride, 3)
  }

  test("simple.csv - ArrayDenseColMajor[Int] data layout") {
    val result = CSV.resource("simple.csv", CsvOpts(readAs = ReadAs.ArrayDenseColMajor[Int]()))
    val data = result.data

    // Data layout in column-major order:
    // Column 0: [1, 3, 5] (indices 0, 1, 2)
    // Column 1: [2, 4, 6] (indices 3, 4, 5)
    // Column 2: [7, 8, 9] (indices 6, 7, 8)
    
    // Check column 0 (col1)
    assertEquals(data(0), 1)
    assertEquals(data(1), 3)
    assertEquals(data(2), 5)

    // Check column 1 (col2)
    assertEquals(data(3), 2)
    assertEquals(data(4), 4)
    assertEquals(data(5), 6)

    // Check column 2 (col3)
    assertEquals(data(6), 7)
    assertEquals(data(7), 8)
    assertEquals(data(8), 9)
  }

  test("simple.csv - ArrayDenseColMajor[Int] access with strides") {
    val result = CSV.resource("simple.csv", CsvOpts(readAs = ReadAs.ArrayDenseColMajor[Int]()))
    val data = result.data
    val rowStride = result.rowStride
    val colStride = result.colStride

    // Access element at row 1, col 1 (should be 4)
    // In column-major: index = col * rowStride + row * colStride = 1 * 3 + 1 * 1 = 4
    val row = 1
    val col = 1
    val index = col * rowStride + row * colStride
    assertEquals(data(index), 4)

    // Access element at row 2, col 0 (should be 5)
    assertEquals(data(0 * rowStride + 2 * colStride), 5)

    // Access element at row 0, col 2 (should be 7)
    assertEquals(data(2 * rowStride + 0 * colStride), 7)
  }

  test("simple.csv - ArrayDenseRowMajor[Int] basic structure") {
    val result = CSV.resource("simple.csv", CsvOpts(readAs = ReadAs.ArrayDenseRowMajor[Int]()))

    // Check that we get the expected fields
    val data: Array[Int] = result.data
    val rowStride: Int = result.rowStride
    val colStride: Int = result.colStride
    val rows: Int = result.rows
    val cols: Int = result.cols

    // Verify dimensions
    assertEquals(rows, 3)
    assertEquals(cols, 3)
    assertEquals(data.length, 9)

    // In row-major order: rowStride = 1 (next element in same row), colStride = cols (jump to next row)
    assertEquals(rowStride, 1)
    assertEquals(colStride, 3)
  }

  test("simple.csv - ArrayDenseRowMajor[Int] data layout") {
    val result = CSV.resource("simple.csv", CsvOpts(readAs = ReadAs.ArrayDenseRowMajor[Int]()))
    val data = result.data

    // Data layout in row-major order:
    // Row 0: [1, 2, 7] (indices 0, 1, 2)
    // Row 1: [3, 4, 8] (indices 3, 4, 5)
    // Row 2: [5, 6, 9] (indices 6, 7, 8)
    
    // Check row 0
    assertEquals(data(0), 1)
    assertEquals(data(1), 2)
    assertEquals(data(2), 7)

    // Check row 1
    assertEquals(data(3), 3)
    assertEquals(data(4), 4)
    assertEquals(data(5), 8)

    // Check row 2
    assertEquals(data(6), 5)
    assertEquals(data(7), 6)
    assertEquals(data(8), 9)
  }

  test("simple.csv - ArrayDenseRowMajor[Int] access with strides") {
    val result = CSV.resource("simple.csv", CsvOpts(readAs = ReadAs.ArrayDenseRowMajor[Int]()))
    val data = result.data
    val rowStride = result.rowStride
    val colStride = result.colStride

    // Access element at row 1, col 1 (should be 4)
    // In row-major: index = row * colStride + col * rowStride = 1 * 3 + 1 * 1 = 4
    val row = 1
    val col = 1
    val index = row * colStride + col * rowStride
    assertEquals(data(index), 4)

    // Access element at row 2, col 0 (should be 5)
    assertEquals(data(2 * colStride + 0 * rowStride), 5)

    // Access element at row 0, col 2 (should be 7)
    assertEquals(data(0 * colStride + 2 * rowStride), 7)
  }

  test("ArrayDenseColMajor[Double] with fromString") {
    val result = CSV.fromString(
      """a,b
1.5,2.5
3.5,4.5""",
      CsvOpts(readAs = ReadAs.ArrayDenseColMajor[Double]())
    )

    assertEquals(result.rows, 2)
    assertEquals(result.cols, 2)
    assertEquals(result.data.toSeq, Seq(1.5, 3.5, 2.5, 4.5)) // column-major: [col_a, col_b]
    assertEquals(result.colStride, 1)
    assertEquals(result.rowStride, 2)
  }

  test("ArrayDenseRowMajor[Double] with fromString") {
    val result = CSV.fromString(
      """a,b
1.5,2.5
3.5,4.5""",
      CsvOpts(readAs = ReadAs.ArrayDenseRowMajor[Double]())
    )

    assertEquals(result.rows, 2)
    assertEquals(result.cols, 2)
    assertEquals(result.data.toSeq, Seq(1.5, 2.5, 3.5, 4.5)) // row-major: [row_0, row_1]
    assertEquals(result.rowStride, 1)
    assertEquals(result.colStride, 2)
  }

  test("ArrayDenseColMajor[String] for string data") {
    val result = CSV.fromString(
      """name,city
Alice,NYC
Bob,LA""",
      CsvOpts(readAs = ReadAs.ArrayDenseColMajor[String]())
    )

    assertEquals(result.rows, 2)
    assertEquals(result.cols, 2)
    // Column-major: [Alice, Bob, NYC, LA]
    assertEquals(result.data.toSeq, Seq("Alice", "Bob", "NYC", "LA"))
  }

  test("ArrayDenseRowMajor[String] for string data") {
    val result = CSV.fromString(
      """name,city
Alice,NYC
Bob,LA""",
      CsvOpts(readAs = ReadAs.ArrayDenseRowMajor[String]())
    )

    assertEquals(result.rows, 2)
    assertEquals(result.cols, 2)
    // Row-major: [Alice, NYC, Bob, LA]
    assertEquals(result.data.toSeq, Seq("Alice", "NYC", "Bob", "LA"))
  }

end CsvDenseArraySuite
