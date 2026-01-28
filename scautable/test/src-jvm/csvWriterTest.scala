package io.github.quafadas.scautable

import io.github.quafadas.table.*

class CSVWriterSuite extends munit.FunSuite:

  test("CSVWriter.formatField handles simple fields") {
    assertEquals(CSVWriter.formatField("hello"), "hello")
    assertEquals(CSVWriter.formatField("123"), "123")
    assertEquals(CSVWriter.formatField(""), "")
  }

  test("CSVWriter.formatField quotes fields with commas") {
    assertEquals(CSVWriter.formatField("hello,world"), "\"hello,world\"")
    assertEquals(CSVWriter.formatField("a,b,c"), "\"a,b,c\"")
  }

  test("CSVWriter.formatField escapes quotes") {
    assertEquals(CSVWriter.formatField("say \"hello\""), "\"say \"\"hello\"\"\"")
    assertEquals(CSVWriter.formatField("\"quoted\""), "\"\"\"quoted\"\"\"")
  }

  test("CSVWriter.formatField quotes fields with newlines") {
    assertEquals(CSVWriter.formatField("line1\nline2"), "\"line1\nline2\"")
    assertEquals(CSVWriter.formatField("line1\rline2"), "\"line1\rline2\"")
  }

  test("CSVWriter.formatField quotes fields with leading/trailing spaces") {
    assertEquals(CSVWriter.formatField(" hello"), "\" hello\"")
    assertEquals(CSVWriter.formatField("hello "), "\"hello \"")
    assertEquals(CSVWriter.formatField(" hello "), "\" hello \"")
  }

  test("CSVWriter.formatLine formats simple line") {
    assertEquals(CSVWriter.formatLine(Seq("a", "b", "c")), "a,b,c")
    assertEquals(CSVWriter.formatLine(Seq("1", "2", "3")), "1,2,3")
  }

  test("CSVWriter.formatLine handles complex line") {
    assertEquals(
      CSVWriter.formatLine(Seq("hello,world", "say \"hi\"", "normal")),
      "\"hello,world\",\"say \"\"hi\"\"\",normal"
    )
  }

  test("CSVWriter.formatLine with custom delimiter") {
    assertEquals(CSVWriter.formatLine(Seq("a", "b", "c"), delimiter = ';'), "a;b;c")
    assertEquals(CSVWriter.formatLine(Seq("a;b", "c"), delimiter = ';'), "\"a;b\";c")
  }

  test("Iterator[NamedTuple].toCsv basic functionality") {
    val data = Iterator(
      (col1 = "1", col2 = "2", col3 = "7"),
      (col1 = "3", col2 = "4", col3 = "8"),
      (col1 = "5", col2 = "6", col3 = "9")
    )

    val expected = Seq("col1,col2,col3", "1,2,7", "3,4,8", "5,6,9")

    assertEquals(data.toCsv(includeHeaders = true, ',', '"').toSeq, expected)
  }

  test("Iterator[NamedTuple].toCsv without headers") {
    val data = Iterator(
      (col1 = "1", col2 = "2", col3 = "7"),
      (col1 = "3", col2 = "4", col3 = "8")
    )

    val expected = Seq("1,2,7", "3,4,8")

    assertEquals(
      data.toCsv(includeHeaders = false, ',', '"').toSeq,
      expected
    )
  }

  test("Iterator[NamedTuple].toCsv with custom delimiter") {
    val data = Iterator(
      (col1 = "1", col2 = "2"),
      (col1 = "3", col2 = "4")
    )

    val expected = Seq("""col1;col2""", """1;2""", """3;4""")

    assertEquals(data.toCsv(delimiter = ';', includeHeaders = true, quote = '"').toSeq, expected)
  }

  test("Seq[NamedTuple].toCsv basic functionality") {
    val data = Seq(
      (col1 = "1", col2 = "2", col3 = "7"),
      (col1 = "3", col2 = "4", col3 = "8"),
      (col1 = "5", col2 = "6", col3 = "9")
    )

    val expected = """col1,col2,col3
1,2,7
3,4,8
5,6,9"""

    assertNoDiff(data.toCsvString(true, ',', '"'), expected)
  }

  test("List[NamedTuple].toCsv with empty values") {
    val data = List(
      (col1 = "", col2 = "2", col3 = "7"),
      (col1 = "3", col2 = "", col3 = "8"),
      (col1 = "5", col2 = "6", col3 = "")
    )

    val expected = """col1,col2,col3
,2,7
3,,8
5,6,"""

    assertNoDiff(data.toCsvString(true, ',', '"'), expected)
  }

  test("Vector[NamedTuple].toCsv with numeric types") {
    val data = Vector(
      (id = 1, name = "Alice", score = 95.5),
      (id = 2, name = "Bob", score = 87.1)
    )

    val expected = """id,name,score
1,Alice,95.5
2,Bob,87.1"""

    assertNoDiff(data.toCsvString(true, ',', '"'), expected)
  }

  test("empty collection toCsv") {
    // Note: This test requires the type to be explicit since we can't infer from empty collections
    val data: List[(col1: String, col2: String)] = List.empty
    val expected = "col1,col2"

    assertEquals(data.toCsvString(true, ',', '"'), expected)
  }

  test("empty collection toCsv without headers") {
    val data: List[(col1: String, col2: String)] = List.empty
    assertEquals(data.toCsvString(false, ',', '"'), "")
  }

end CSVWriterSuite
