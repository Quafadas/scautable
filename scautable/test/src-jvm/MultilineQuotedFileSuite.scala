package io.github.quafadas.scautable

import scala.compiletime.testing.typeChecks

import io.github.quafadas.table.*

/** File-backed readers (compile-time resource reads and the runtime `fromTyped` reader) must treat a quoted line break as part of the field, not as the end of the record. */
class MultilineQuotedFileSuite extends munit.FunSuite:

  private val expectedNotes = List("line one\nline two", "plain", "has \"quotes\"\nand, a comma")

  test("resource: rows with embedded newlines in quoted fields") {
    val csv = CSV.resource("multiline_quoted.csv", TypeInferrer.StringType)
    val rows = csv.toList
    assertEquals(rows.length, 3)
    assertEquals(rows.map(_.id), List("1", "2", "3"))
    assertEquals(rows.map(_.note), expectedNotes)
    assertEquals(rows.map(_.score), List("1.5", "2.5", "3.5"))
  }

  test("resource: compile-time inference sees whole records") {
    assert(
      typeChecks(
        """val csv: CsvIterator[("id", "note", "score"), (Int, String, Double)] = CSV.resource("multiline_quoted.csv")"""
      )
    )
  }

  test("resource: decoding with a declared tuple type") {
    val csv = CSV.resource("multiline_quoted.csv", TypeInferrer.FromTuple[(Int, String, Double)]())
    assertEquals(csv.toList.map(_.score), List(1.5, 2.5, 3.5))
  }

  test("resource: ReadAs.Columns keeps multiline fields together") {
    val cols = CSV.resource("multiline_quoted.csv", CsvOpts(TypeInferrer.StringType, ReadAs.Columns))
    assertEquals(cols.id.toList, List("1", "2", "3"))
    assertEquals(cols.note.toList, expectedNotes)
  }

  test("resource: dense array keeps the right shape") {
    val dense = CSV.resource("multiline_quoted.csv", CsvOpts(TypeInferrer.StringType, ReadAs.ArrayDenseColMajor[String]()))
    assertEquals(dense.rows, 3)
    assertEquals(dense.cols, 3)
  }

  test("runtime fromTyped: write -> read round trip with embedded newlines") {
    val data = Seq(
      (id = 1, note = "first\nsecond"),
      (id = 2, note = "plain"),
      (id = 3, note = "quote \"inside\"\nand a, comma")
    )
    val dir = os.temp.dir()
    val path = dir / "roundtrip.csv"
    os.write(path, data.toCsvString(true, ',', '"'))

    val read = CSV.fromTyped[("id", "note"), (Int, String)](path).toList
    assertEquals(read, data.toList)
  }

  test("runtime fromTyped: CRLF file with a line break inside a quoted field") {
    val dir = os.temp.dir()
    val path = dir / "crlf.csv"
    os.write(path, "id,note\r\n1,\"x\r\ny\"\r\n2,z\r\n")

    val read = CSV.fromTyped[("id", "note"), (Int, String)](path).toList
    assertEquals(read, List((id = 1, note = "x\ny"), (id = 2, note = "z")))
  }

end MultilineQuotedFileSuite
