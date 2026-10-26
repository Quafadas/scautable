package io.github.quafadas.scautable

import io.github.quafadas.table.*
import scala.compiletime.testing.typeChecks

/** Rows whose width differs from the header, and blank lines, must be handled identically by the row, column and dense-array read modes.
  *
  *   - A fully blank (empty or whitespace-only) line is skipped.
  *   - A non-blank row with the wrong number of cells throws, naming the 1-based data line, the expected and the actual column count.
  */
class RaggedRowsSuite extends munit.FunSuite:

  inline val shortRow = "a,b\n1,2\n3\n4,5"
  inline val longRow = "a,b\n1,2\n3,4,5\n6,7"
  inline val blankMiddle = "a,b\n1,2\n\n3,4"
  inline val whitespaceMiddle = "a,b\n1,2\n   \n3,4"
  inline val trailingBlank = "a,b\n1,2\n3,4\n\n"

  private def assertWidthError(e: Throwable, dataLine: Int, expected: Int, actual: Int)(using munit.Location): Unit =
    val msg = e.getMessage
    assert(msg.contains(s"data line $dataLine"), s"message should name data line $dataLine: $msg")
    assert(msg.contains(s"expected $expected"), s"message should name expected width $expected: $msg")
    assert(msg.contains(s"found $actual"), s"message should name actual width $actual: $msg")
  end assertWidthError

  // ---------- Columns ----------

  test("Columns - short row throws with line number") {
    val e = intercept[Exception](CSV.fromString(shortRow, CsvOpts(TypeInferrer.StringType, ReadAs.Columns)))
    assertWidthError(e, 2, 2, 1)
  }

  test("Columns - long row throws with line number") {
    val e = intercept[Exception](CSV.fromString(longRow, CsvOpts(TypeInferrer.StringType, ReadAs.Columns)))
    assertWidthError(e, 2, 2, 3)
  }

  test("Columns - blank line in the middle is skipped") {
    val cols = CSV.fromString(blankMiddle, CsvOpts(TypeInferrer.StringType, ReadAs.Columns))
    assertEquals(cols.a.toSeq, Seq("1", "3"))
    assertEquals(cols.b.toSeq, Seq("2", "4"))
  }

  test("Columns - whitespace-only line is skipped") {
    val cols = CSV.fromString(whitespaceMiddle, CsvOpts(TypeInferrer.StringType, ReadAs.Columns))
    assertEquals(cols.a.toSeq, Seq("1", "3"))
    assertEquals(cols.b.toSeq, Seq("2", "4"))
  }

  test("Columns - trailing blank line is skipped") {
    val cols = CSV.fromString(trailingBlank, CsvOpts(TypeInferrer.StringType, ReadAs.Columns))
    assertEquals(cols.a.toSeq, Seq("1", "3"))
    assertEquals(cols.b.toSeq, Seq("2", "4"))
  }

  test("Columns - type inference ignores blank lines") {
    assert(typeChecks("""val c: (a: Array[Int], b: Array[Int]) = CSV.fromString("a,b\n1,2\n\n3,4\n\n", CsvOpts(ReadAs.Columns))"""))
  }

  test("Columns - inferred types with blank lines") {
    val cols = CSV.fromString("a,b\n1,2\n\n3,4\n\n", CsvOpts(ReadAs.Columns))
    assertEquals(cols.a.toSeq, Seq(1, 3))
    assertEquals(cols.b.toSeq, Seq(2, 4))
  }

  test("Columns - a delimiter-only line is a row of empty cells, not a blank line") {
    val cols = CSV.fromString("a\tb\n1\t2\n\t\n3\t4", CsvOpts(HeaderOptions.Default, TypeInferrer.StringType, '\t', ReadAs.Columns))
    assertEquals(cols.a.toSeq, Seq("1", "", "3"))
    assertEquals(cols.b.toSeq, Seq("2", "", "4"))
  }

  test("Columns - error reports the exception type and fields") {
    val e = intercept[CsvRowWidthException](CSV.fromString(shortRow, CsvOpts(TypeInferrer.StringType, ReadAs.Columns)))
    assertEquals((e.dataLine, e.expected, e.actual), (2, 2, 1))
  }

  test("Columns - line number counts skipped blank lines") {
    val e = intercept[CsvRowWidthException](CSV.fromString("a,b\n1,2\n\n3", CsvOpts(TypeInferrer.StringType, ReadAs.Columns)))
    assertEquals(e.dataLine, 3)
  }

  // ---------- Dense, column major ----------

  test("ArrayDenseColMajor - short row throws with line number") {
    val e = intercept[Exception](CSV.fromString(shortRow, CsvOpts(readAs = ReadAs.ArrayDenseColMajor[String]())))
    assertWidthError(e, 2, 2, 1)
  }

  test("ArrayDenseColMajor - long row throws with line number") {
    val e = intercept[Exception](CSV.fromString(longRow, CsvOpts(readAs = ReadAs.ArrayDenseColMajor[String]())))
    assertWidthError(e, 2, 2, 3)
  }

  test("ArrayDenseColMajor - blank line in the middle is skipped") {
    val r = CSV.fromString(blankMiddle, CsvOpts(readAs = ReadAs.ArrayDenseColMajor[Int]()))
    assertEquals(r.rows, 2)
    assertEquals(r.cols, 2)
    assertEquals(r.data.toSeq, Seq(1, 3, 2, 4))
  }

  test("ArrayDenseColMajor - trailing blank line is skipped") {
    val r = CSV.fromString(trailingBlank, CsvOpts(readAs = ReadAs.ArrayDenseColMajor[Int]()))
    assertEquals(r.rows, 2)
    assertEquals(r.data.toSeq, Seq(1, 3, 2, 4))
  }

  // ---------- Dense, row major ----------

  test("ArrayDenseRowMajor - short row throws with line number") {
    val e = intercept[Exception](CSV.fromString(shortRow, CsvOpts(readAs = ReadAs.ArrayDenseRowMajor[String]())))
    assertWidthError(e, 2, 2, 1)
  }

  test("ArrayDenseRowMajor - long row throws with line number") {
    val e = intercept[Exception](CSV.fromString(longRow, CsvOpts(readAs = ReadAs.ArrayDenseRowMajor[String]())))
    assertWidthError(e, 2, 2, 3)
  }

  test("ArrayDenseRowMajor - blank line in the middle is skipped") {
    val r = CSV.fromString(blankMiddle, CsvOpts(readAs = ReadAs.ArrayDenseRowMajor[Int]()))
    assertEquals(r.rows, 2)
    assertEquals(r.cols, 2)
    assertEquals(r.data.toSeq, Seq(1, 2, 3, 4))
  }

  test("ArrayDenseRowMajor - trailing blank line is skipped") {
    val r = CSV.fromString(trailingBlank, CsvOpts(readAs = ReadAs.ArrayDenseRowMajor[Int]()))
    assertEquals(r.rows, 2)
    assertEquals(r.data.toSeq, Seq(1, 2, 3, 4))
  }

  // ---------- Rows (iterator), for consistency ----------

  test("Rows - short row throws with line number") {
    val iter = CSV.fromString(shortRow, CsvOpts(TypeInferrer.StringType))
    val e = intercept[Exception](iter.toList)
    assertWidthError(e, 2, 2, 1)
  }

  test("Rows - long row throws with line number") {
    val iter = CSV.fromString(longRow, CsvOpts(TypeInferrer.StringType))
    val e = intercept[Exception](iter.toList)
    assertWidthError(e, 2, 2, 3)
  }

  test("Rows - blank line in the middle is skipped") {
    val rows = CSV.fromString(blankMiddle, CsvOpts(TypeInferrer.StringType)).toList
    assertEquals(rows.map(_.a), List("1", "3"))
    assertEquals(rows.map(_.b), List("2", "4"))
  }

  test("Rows - trailing blank line is skipped") {
    val rows = CSV.fromString(trailingBlank, CsvOpts(TypeInferrer.StringType)).toList
    assertEquals(rows.map(_.a), List("1", "3"))
  }

  test("Rows - type inference ignores blank lines") {
    assert(typeChecks("""val r: List[(a: Int, b: Int)] = CSV.fromString("a,b\n1,2\n\n3,4\n\n").toList"""))
  }

  test("Rows - inferred types with blank lines") {
    val rows = CSV.fromString("a,b\n1,2\n\n3,4\n\n").toList
    assertEquals(rows.map(_.a), List(1, 3))
    assertEquals(rows.map(_.b), List(2, 4))
  }

end RaggedRowsSuite
