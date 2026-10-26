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

  test("Option columns: Some(x) writes x, None writes an empty field") {
    val data = List(
      (id = Option(3), name = Option("Alice")),
      (id = None, name = None),
      (id = Some(5), name = Some("Bob"))
    )

    val expected = """id,name
3,Alice
,
5,Bob"""

    assertNoDiff(data.toCsvString(true, ',', '"'), expected)
    assertEquals(
      data.toCsv(true, ',', '"').toSeq,
      Seq("id,name", "3,Alice", ",", "5,Bob")
    )
    assertEquals(
      data.iterator.toCsv(false, ',', '"').toSeq,
      Seq("3,Alice", ",", "5,Bob")
    )
  }

  test("Option columns: nested options and values needing quotes are unwrapped") {
    val data = List(
      (a = Option(Option(1)), b = Option("x,y")),
      (a = Option(Option.empty[Int]), b = Option.empty[String])
    )
    assertEquals(
      data.toCsv(false, ',', '"').toSeq,
      Seq("1,\"x,y\"", ",")
    )
  }

  test("Option columns round trip through writeCsv and CSV.fromTyped") {
    val data = List(
      (id = Option(3), name = Option("Alice"), score = 1.5),
      (id = Option.empty[Int], name = Option.empty[String], score = 2.5),
      (id = Option(7), name = Option("Bob"), score = 3.5)
    )
    val tmp = os.temp(suffix = ".csv", prefix = "scautable-roundtrip-")
    data.writeCsv(path = Some(tmp))

    assertEquals(os.read(tmp), "id,name,score\n3,Alice,1.5\n,,2.5\n7,Bob,3.5\n")

    val reader = CSV.fromTyped[("id", "name", "score"), (Option[Int], Option[String], Double)]
    val rows = reader(tmp).toList
    assertEquals(rows.map(_.id), List(Some(3), None, Some(7)))
    assertEquals(rows.map(_.name), List(Some("Alice"), None, Some("Bob")))
    assertEquals(rows.map(_.score), List(1.5, 2.5, 3.5))

    // The iterator writer behaves the same
    val tmp2 = os.temp(suffix = ".csv", prefix = "scautable-roundtrip-")
    data.iterator.writeCsvIterator(path = Some(tmp2))
    assertEquals(os.read(tmp2), os.read(tmp))
  }

  test("writeCsv with empty data replaces an existing file") {
    val tmp = os.temp(contents = "stale,content\n1,2\n", suffix = ".csv")
    val data: List[(col1: String, col2: String)] = List.empty
    data.writeCsv(path = Some(tmp), includeHeaders = false)
    assertEquals(os.read(tmp), "")

    data.writeCsv(path = Some(tmp))
    assertEquals(os.read(tmp), "col1,col2\n")
  }

  test("writeCsvIterator with empty data replaces an existing file") {
    val tmp = os.temp(contents = "stale,content\n1,2\n", suffix = ".csv")
    val data: Iterator[(col1: String, col2: String)] = Iterator.empty
    data.writeCsvIterator(path = Some(tmp), includeHeaders = false)
    assertEquals(os.read(tmp), "")
  }

end CSVWriterSuite
