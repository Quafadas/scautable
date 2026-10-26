package io.github.quafadas.scautable

import io.github.quafadas.table.*

/** File-backed counterpart of [[RaggedRowsSuite]]: the resource path has its own copies of the column / dense buffer loops. */
class RaggedRowsFileSuite extends munit.FunSuite:

  private def assertWidthError(e: Throwable, dataLine: Int, expected: Int, actual: Int)(using munit.Location): Unit =
    val msg = e.getMessage
    assert(msg.contains(s"data line $dataLine"), s"message should name data line $dataLine: $msg")
    assert(msg.contains(s"expected $expected"), s"message should name expected width $expected: $msg")
    assert(msg.contains(s"found $actual"), s"message should name actual width $actual: $msg")
  end assertWidthError

  test("Columns - short row in file throws") {
    val e = intercept[Exception](CSV.resource("ragged_short.csv", CsvOpts(TypeInferrer.StringType, ReadAs.Columns)))
    assertWidthError(e, 2, 2, 1)
  }

  test("Columns - long row in file throws") {
    val e = intercept[Exception](CSV.resource("ragged_long.csv", CsvOpts(TypeInferrer.StringType, ReadAs.Columns)))
    assertWidthError(e, 2, 2, 3)
  }

  test("Columns - blank lines in file are skipped") {
    val cols = CSV.resource("blank_lines.csv", CsvOpts(TypeInferrer.StringType, ReadAs.Columns))
    assertEquals(cols.a.toSeq, Seq("1", "3"))
    assertEquals(cols.b.toSeq, Seq("2", "4"))
  }

  test("ArrayDenseColMajor - short row in file throws") {
    val e = intercept[Exception](CSV.resource("ragged_short.csv", CsvOpts(readAs = ReadAs.ArrayDenseColMajor[String]())))
    assertWidthError(e, 2, 2, 1)
  }

  test("ArrayDenseColMajor - long row in file throws") {
    val e = intercept[Exception](CSV.resource("ragged_long.csv", CsvOpts(readAs = ReadAs.ArrayDenseColMajor[String]())))
    assertWidthError(e, 2, 2, 3)
  }

  test("ArrayDenseColMajor - blank lines in file are skipped") {
    val r = CSV.resource("blank_lines.csv", CsvOpts(readAs = ReadAs.ArrayDenseColMajor[Int]()))
    assertEquals(r.rows, 2)
    assertEquals(r.data.toSeq, Seq(1, 3, 2, 4))
  }

  test("ArrayDenseRowMajor - short row in file throws") {
    val e = intercept[Exception](CSV.resource("ragged_short.csv", CsvOpts(readAs = ReadAs.ArrayDenseRowMajor[String]())))
    assertWidthError(e, 2, 2, 1)
  }

  test("ArrayDenseRowMajor - blank lines in file are skipped") {
    val r = CSV.resource("blank_lines.csv", CsvOpts(readAs = ReadAs.ArrayDenseRowMajor[Int]()))
    assertEquals(r.rows, 2)
    assertEquals(r.data.toSeq, Seq(1, 2, 3, 4))
  }

  test("Rows - short row in file throws") {
    val e = intercept[Exception](CSV.resource("ragged_short.csv", CsvOpts(TypeInferrer.StringType)).toList)
    assertWidthError(e, 2, 2, 1)
  }

  test("Rows - blank lines in file are skipped") {
    val rows = CSV.resource("blank_lines.csv", CsvOpts(TypeInferrer.StringType)).toList
    assertEquals(rows.map(_.a), List("1", "3"))
  }

end RaggedRowsFileSuite
