package io.github.quafadas.scautable

import scala.compiletime.testing.typeChecks

import io.github.quafadas.table.*
import munit.FunSuite

/** RFC 4180 allows a quoted field to contain line breaks. A record is therefore not the same thing as a physical line, and the reader must not split a row in the middle of a
  * quoted field.
  */
class MultilineQuotedFieldSuite extends FunSuite:

  test("fromString: embedded newline in a quoted field stays in one row") {
    val csv = CSV.fromString("a,b\n1,\"x\ny\"\n3,4", HeaderOptions.Default, TypeInferrer.StringType)
    val rows = csv.toList
    assertEquals(rows.length, 2)
    assertEquals(rows.map(_.a), List("1", "3"))
    assertEquals(rows.map(_.b), List("x\ny", "4"))
  }

  test("fromString: compile-time inference sees whole records") {
    assert(
      typeChecks(
        """val csv: CsvIterator[("a", "b"), (Int, String)] = CSV.fromString("a,b\n1,\"x\ny\"\n3,4", HeaderOptions.Default, TypeInferrer.FromAllRows)"""
      )
    )
    assert(
      typeChecks(
        """val csv: CsvIterator[("a", "b"), (Int, String)] = CSV.fromString("a,b\n1,\"x\ny\"\n3,4", HeaderOptions.Default, TypeInferrer.FirstN(2))"""
      )
    )
  }

  test("fromString: decoding with a declared tuple type") {
    val csv = CSV.fromString("a,b\n1,\"x\ny\"\n3,4", HeaderOptions.Default, TypeInferrer.FromTuple[(Int, String)]())
    assertEquals(csv.toList, List((a = 1, b = "x\ny"), (a = 3, b = "4")))
  }

  test("fromString: doubled quotes and several line breaks inside one field") {
    val csv = CSV.fromString("a,b\n1,\"he said \"\"hi\"\"\n\nthen, \"\"bye\"\"\"\n2,z", HeaderOptions.Default, TypeInferrer.StringType)
    val rows = csv.toList
    assertEquals(rows.map(_.b), List("he said \"hi\"\n\nthen, \"bye\"", "z"))
  }

  test("fromString: a doubled quote at the end of a line does not close the field") {
    val csv = CSV.fromString("a,b\n1,\"ends with \"\"\nnext\"\n2,z", HeaderOptions.Default, TypeInferrer.StringType)
    assertEquals(csv.toList.map(_.b), List("ends with \"\nnext", "z"))
  }

  test("fromString: CRLF line endings, line break inside a quoted field") {
    val csv = CSV.fromString("a,b\r\n1,\"x\r\ny\"\r\n3,4\r\n", HeaderOptions.Default, TypeInferrer.StringType)
    val rows = csv.toList
    assertEquals(rows.length, 2)
    // Physical lines are split on either line ending, and re-joined with `\n`.
    assertEquals(rows.map(_.b), List("x\ny", "4"))
  }

  test("fromString: ReadAs.Columns keeps multiline fields together") {
    val cols = CSV.fromString("a,b\n1,\"x\ny\"\n3,4", CsvOpts(TypeInferrer.StringType, ReadAs.Columns))
    assertEquals(cols.a.toList, List("1", "3"))
    assertEquals(cols.b.toList, List("x\ny", "4"))
  }

  test("fromString: ReadAs.Columns with inferred types") {
    assert(
      typeChecks(
        """val (a: Array[Int], b: Array[String]) = CSV.fromString("a,b\n1,\"x\ny\"\n3,4", CsvOpts(readAs = ReadAs.Columns))"""
      )
    )
  }

  test("fromString: a header may contain a line break") {
    val csv = CSV.fromString("\"first\nname\",b\nann,1", HeaderOptions.Default, TypeInferrer.StringType)
    assertEquals(csv.headers, Seq("first\nname", "b"))
    assertEquals(csv.toList.length, 1)
  }

end MultilineQuotedFieldSuite
