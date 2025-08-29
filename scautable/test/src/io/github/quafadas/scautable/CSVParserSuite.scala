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

  test("parseLine should handle backslash-escaped quotes") {
    // Common alternative: quotes escaped with backslashes
    val line = "\"this is my \\\"Test String\\\"\""
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("this is my \"Test String\""))
  }

  test("parseLine should handle multiple fields with RFC 4180 escaping") {
    val line = "field1,\"field2 with \"\"quotes\"\"\",field3"
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("field1", "field2 with \"quotes\"", "field3"))
  }

  test("parseLine should handle multiple fields with backslash escaping") {
    val line = "field1,\"field2 with \\\"quotes\\\"\",field3"
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

  test("parseLine should handle complex backslash escape case") {
    // Complex case with multiple escaped quotes using backslashes
    val line = "\"He said \\\"Hello, \\\"World\\\"!\\\"\""
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("He said \"Hello, \"World\"!\""))
  }

  test("parseLine should handle mixed escaping in different fields") {
    // One field with RFC 4180 escaping, another with backslash escaping
    val line = "\"field with \"\"RFC escaping\"\"\",\"field with \\\"backslash escaping\\\"\""
    val result = CSVParser.parseLine(line)
    assertEquals(result, List("field with \"RFC escaping\"", "field with \"backslash escaping\""))
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
