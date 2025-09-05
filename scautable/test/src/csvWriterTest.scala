package io.github.quafadas.scautable

import io.github.quafadas.table.*
import CSVWriterExtensions.*

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
    
    val expected = """col1,col2,col3
1,2,7
3,4,8
5,6,9"""
    
    assertEquals(data.toCsv(), expected)
  }

  test("Iterator[NamedTuple].toCsv without headers") {
    val data = Iterator(
      (col1 = "1", col2 = "2", col3 = "7"),
      (col1 = "3", col2 = "4", col3 = "8")
    )
    
    val expected = """1,2,7
3,4,8"""
    
    assertEquals(data.toCsv(includeHeaders = false), expected)
  }

  test("Iterator[NamedTuple].toCsv with special characters") {
    val data = Iterator(
      (name = "John,Doe", description = "says \"hello\"", value = "normal"),
      (name = "Jane\nSmith", description = "line break", value = " spaced ")
    )
    
    val expected = """name,description,value
"John,Doe","says ""hello""",normal
"Jane
Smith","line break"," spaced """"
    
    assertEquals(data.toCsv(), expected)
  }

  test("Iterator[NamedTuple].toCsv with custom delimiter") {
    val data = Iterator(
      (col1 = "1", col2 = "2"),
      (col1 = "3", col2 = "4")
    )
    
    val expected = """col1;col2
1;2
3;4"""
    
    assertEquals(data.toCsv(delimiter = ';'), expected)
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
    
    assertEquals(data.toCsv(), expected)
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
    
    assertEquals(data.toCsv(), expected)
  }

  test("Vector[NamedTuple].toCsv with numeric types") {
    val data = Vector(
      (id = 1, name = "Alice", score = 95.5),
      (id = 2, name = "Bob", score = 87.0)
    )
    
    val expected = """id,name,score
1,Alice,95.5
2,Bob,87.0"""
    
    assertEquals(data.toCsv(), expected)
  }

  test("empty collection toCsv") {
    // Note: This test requires the type to be explicit since we can't infer from empty collections
    val data: List[(col1: String, col2: String)] = List.empty
    
    val expected = "col1,col2"
    
    assertEquals(data.toCsv(), expected)
  }

  test("empty collection toCsv without headers") {
    val data: List[(col1: String, col2: String)] = List.empty
    
    assertEquals(data.toCsv(includeHeaders = false), "")
  }

  test("round-trip compatibility with simple CSV") {
    // Create data, write to CSV, then read back
    val originalData = Vector(
      (col1 = "1", col2 = "2", col3 = "7"),
      (col1 = "3", col2 = "4", col3 = "8"),
      (col1 = "5", col2 = "6", col3 = "9")
    )
    
    val csvString = originalData.toCsv()
    
    // Parse back using the existing parser
    val parsedBack = CSV.fromString[("col1", "col2", "col3")](csvString).toVector
    
    // Compare the data (convert to tuples for easy comparison)
    val originalTuples = originalData.map(_.toTuple)
    val parsedTuples = parsedBack.map(_.toTuple)
    
    assertEquals(parsedTuples, originalTuples)
  }

  test("round-trip compatibility with complex CSV") {
    val originalData = Vector(
      (name = "John,Doe", description = "A person", id = "123"),
      (name = "Jane Smith", description = "Another person", id = "456")
    )
    
    val csvString = originalData.toCsv()
    val parsedBack = CSV.fromString[("name", "description", "id")](csvString).toVector
    
    val originalTuples = originalData.map(_.toTuple)
    val parsedTuples = parsedBack.map(_.toTuple)
    
    assertEquals(parsedTuples, originalTuples)
  }

end CSVWriterSuite