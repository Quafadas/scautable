package io.github.quafadas.scautable

import munit.FunSuite

class CSVParserSuite extends FunSuite:

  test("parseLine should handle simple unquoted fields") {
    val line = "field1,field2,field3"
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("field1", "field2", "field3"))
  }

  test("parseLine should handle quoted fields") {
    val line = "\"field1\",\"field2\",\"field3\""
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("field1", "field2", "field3"))
  }

  test("parseLine should handle mixed quoted and unquoted fields") {
    val line = "field1,\"field2\",field3"
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("field1", "field2", "field3"))
  }

  test("parseLine should handle fields with commas inside quotes") {
    val line = "field1,\"field2,with,commas\",field3"
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("field1", "field2,with,commas", "field3"))
  }

  test("parseLine should handle RFC 4180 compliant double-quote escaping") {
    // RFC 4180: quotes inside quoted fields are escaped by doubling them
    val line = "\"this is my \"\"Test String\"\"\""
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("this is my \"Test String\""))
  }

  test("parseLine should not treat backslash-quote as an escaped quote (RFC 4180)") {
    // Previously \" was decoded as an escaped quote. Under RFC 4180 the backslash is literal and the quote closes the field.
    val line = "\"this is my \\\",next"
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("this is my \\", "next"))
  }

  test("parseLine should handle multiple fields with RFC 4180 escaping") {
    val line = "field1,\"field2 with \"\"quotes\"\"\",field3"
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("field1", "field2 with \"quotes\"", "field3"))
  }

  test("parseLine should handle empty fields") {
    val line = "field1,,field3"
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("field1", "", "field3"))
  }

  test("parseLine should handle empty quoted fields") {
    val line = "field1,\"\",field3"
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("field1", "", "field3"))
  }

  test("parseLine should handle newlines inside quoted fields") {
    val line = "field1,\"field2\nwith\nnewlines\",field3"
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("field1", "field2\nwith\nnewlines", "field3"))
  }

  test("parseLine should handle custom delimiter") {
    val line = "field1;field2;field3"
    val result = CSVParser.parseLine(line, delimiter = ';')
    assertEquals(result, List("field1", "field2", "field3"))
  }

  test("parseLine should handle custom quote character") {
    val line = "field1,'field2 with ''spaces''',field3"
    val result = CSVParser.parseLine(line, quote = '\'')
    assertEquals(result, List("field1", "field2 with 'spaces'", "field3"))
  }

  test("parseLine should handle complex RFC 4180 case") {
    // Complex case with multiple escaped quotes
    val line = "\"He said \"\"Hello, \"\"World\"\"!\"\"\""
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("He said \"Hello, \"World\"!\""))
  }

  test("parseLine should combine RFC 4180 quote escaping with literal backslashes") {
    val line = "\"say \"\"C:\\temp\\\"\"\",\"x\\\"\"y\""
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("say \"C:\\temp\\\"", "x\\\"y"))
  }

  test("parseLine should handle trailing empty field") {
    val line = "field1,field2,"
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("field1", "field2", ""))
  }

  test("parseLine should handle leading empty field") {
    val line = ",field2,field3"
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("", "field2", "field3"))
  }

  test("parseLine should keep backslash-n and backslash-r literal") {
    val line = "field1,\"field2 with \\n newline \\r return\",field3"
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("field1", "field2 with \\n newline \\r return", "field3"))
  }

  test("parseLine should keep doubled backslashes literal") {
    val line = "field1,\"field2 with \\\\ backslash\",field3"
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("field1", "field2 with \\\\ backslash", "field3"))
  }

  test("parseLine should keep backslash before delimiter literal (custom delimiter)") {
    val line = "field1;\"field2 with \\; semicolon\";field3"
    val result = CSVParser.parseLine(line, delimiter = ';')
    assertEquals(result, List("field1", "field2 with \\; semicolon", "field3"))
  }

  test("parseLine should keep a trailing doubled backslash literal") {
    val line = "field1,\"field2 ends with \\\\\",field3"
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("field1", "field2 ends with \\\\", "field3"))
  }

  test("parseLine should handle invalid escape sequences by treating backslash literally") {
    val line = "field1,\"field2 with \\z invalid escape\",field3"
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("field1", "field2 with \\z invalid escape", "field3"))
  }
  // RFC 4180 has no backslash escaping: backslashes inside quoted fields are literal characters.
  test("parseLine should keep backslashes literal in quoted Windows paths") {
    val line = "id,\"C:\\new\\path\\to\\file.csv\",\"\\\\server\\share\""
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("id", "C:\\new\\path\\to\\file.csv", "\\\\server\\share"))
  }

  test("parseLine should keep a literal backslash-n two-char sequence in a quoted field") {
    val line = "a,\"line1\\nline2\\r\",b"
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("a", "line1\\nline2\\r", "b"))
    assertEquals(result(1).length, 14)
  }

  test("parseLine should not collapse doubled backslashes in a quoted field") {
    val line = "\"a\\\\b\",c"
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("a\\\\b", "c"))
  }

  test("parseLine should close a quoted field that ends in a backslash") {
    val line = "\"ends with \\\",next,\"C:\\dir\\\""
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("ends with \\", "next", "C:\\dir\\"))
  }

  test("parseLine should treat backslash before delimiter as literal inside quotes") {
    val line = "\"a\\,b\";c"
    val result = CSVParser.parseLine(line, delimiter = ';')
    assertEquals(result, List("a\\,b", "c"))
  }
end CSVParserSuite
