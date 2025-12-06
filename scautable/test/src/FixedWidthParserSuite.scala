package io.github.quafadas.scautable

class FixedWidthParserSuite extends munit.FunSuite:

  test("parseLineWithWidths - basic parsing with explicit widths") {
    val line = "John      25   NYC       "
    val widths = Seq(10, 5, 10)
    val result = FixedWidthParser.parseLineWithWidths(line, widths, trimFields = true)
    assertEquals(result, List("John", "25", "NYC"))
  }

  test("parseLineWithWidths - without trimming") {
    val line = "John      25   NYC       "
    val widths = Seq(10, 5, 10)
    val result = FixedWidthParser.parseLineWithWidths(line, widths, trimFields = false)
    assertEquals(result, List("John      ", "25   ", "NYC       "))
  }

  test("parseLineWithWidths - line shorter than expected widths") {
    val line = "John"
    val widths = Seq(10, 5, 10)
    val result = FixedWidthParser.parseLineWithWidths(line, widths, trimFields = true)
    assertEquals(result, List("John", "", ""))
  }

  test("parseLineWithWidths - empty line") {
    val line = ""
    val widths = Seq(10, 5, 10)
    val result = FixedWidthParser.parseLineWithWidths(line, widths, trimFields = true)
    assertEquals(result, List("", "", ""))
  }

  test("parseLineWithWidths - exact width match") {
    val line = "1234567890ABCDE12345"
    val widths = Seq(10, 5, 5)
    val result = FixedWidthParser.parseLineWithWidths(line, widths, trimFields = false)
    assertEquals(result, List("1234567890", "ABCDE", "12345"))
  }

  test("inferColumnWidths - basic inference with double spaces") {
    val line = "Name      Age  City     "
    val widths = FixedWidthParser.inferColumnWidths(line, ' ')
    assertEquals(widths, Seq(10, 5, 9))
  }

  test("inferColumnWidths - single space within field is preserved") {
    val line = "First Last  Age  City State"
    val widths = FixedWidthParser.inferColumnWidths(line, ' ')
    // "First Last" has single space (kept as field content)
    // Double space after "Last" marks separator
    assertEquals(widths, Seq(12, 5, 10))
  }

  test("inferColumnWidths - empty line") {
    val line = ""
    val widths = FixedWidthParser.inferColumnWidths(line, ' ')
    assertEquals(widths, Seq.empty)
  }

  test("inferColumnWidths - no padding (single field)") {
    val line = "SingleField"
    val widths = FixedWidthParser.inferColumnWidths(line, ' ')
    assertEquals(widths, Seq(11))
  }

  test("inferColumnWidths - multiple consecutive spaces") {
    val line = "A    B     C"
    val widths = FixedWidthParser.inferColumnWidths(line, ' ')
    // 4 spaces after A (separator found), 5 spaces after B (separator found)
    // Field 1: "A    " = 5 chars, Field 2: "B     " = 6 chars, Field 3: "C" = 1 char
    assertEquals(widths, Seq(5, 6, 1))
  }

  test("inferColumnWidths - trailing spaces only") {
    val line = "Field     "
    val widths = FixedWidthParser.inferColumnWidths(line, ' ')
    assertEquals(widths, Seq(10))
  }

  test("inferColumnWidths - leading spaces") {
    val line = "  Field"
    val widths = FixedWidthParser.inferColumnWidths(line, ' ')
    // Leading double space is a separator, creates empty first field, then "Field"
    assertEquals(widths, Seq(2, 5))
  }

  test("parseLineWithInference - basic inference and parsing") {
    val line = "John      25   NYC       "
    val result = FixedWidthParser.parseLineWithInference(line, ' ', trimFields = true)
    assertEquals(result, List("John", "25", "NYC"))
  }

  test("parseLineWithInference - preserves single spaces in field names") {
    val line = "New York  100  USA       "
    val result = FixedWidthParser.parseLineWithInference(line, ' ', trimFields = true)
    assertEquals(result, List("New York", "100", "USA"))
  }

  test("parseLine - uses inference when widths empty") {
    val line = "John      25   NYC       "
    val result = FixedWidthParser.parseLine(line, Seq.empty, ' ', trimFields = true)
    assertEquals(result, List("John", "25", "NYC"))
  }

  test("parseLine - uses explicit widths when provided") {
    val line = "John      25   NYC       "
    val widths = Seq(10, 5, 10)
    val result = FixedWidthParser.parseLine(line, widths, ' ', trimFields = true)
    assertEquals(result, List("John", "25", "NYC"))
  }

  test("parseLineWithWidths - fields with no content") {
    val line = "          25            "
    val widths = Seq(10, 5, 9)
    val result = FixedWidthParser.parseLineWithWidths(line, widths, trimFields = true)
    assertEquals(result, List("", "25", ""))
  }

  test("parseLineWithInference - all spaces") {
    val line = "          "
    val result = FixedWidthParser.parseLineWithInference(line, ' ', trimFields = true)
    // All spaces - should infer as single field or no fields
    assert(result.nonEmpty)
  }

  test("inferColumnWidths - custom padding character") {
    val line = "Name______Age__City_____"
    val widths = FixedWidthParser.inferColumnWidths(line, '_')
    assertEquals(widths, Seq(10, 5, 9))
  }

  test("parseLine - custom padding character") {
    val line = "Name______Age__City_____"
    val result = FixedWidthParser.parseLine(line, Seq.empty, '_', trimFields = false)
    // With custom padding and no trimming
    assert(result.size > 0)
  }

  test("parseLineWithWidths - unicode characters") {
    // Create a properly aligned fixed-width line:  
    // Field 1 (10 chars): "日本" + 8 spaces
    // Field 2 (5 chars): "25" + 3 spaces  
    // Field 3 (10 chars): "東京" + 8 spaces
    val line = "日本        25   東京        "
    val widths = Seq(10, 5, 10)
    val result = FixedWidthParser.parseLineWithWidths(line, widths, trimFields = true)
    assertEquals(result, List("日本", "25", "東京"))
  }

  test("inferColumnWidths - tab as padding") {
    val line = "Name\t\tAge\tCity"
    val widths = FixedWidthParser.inferColumnWidths(line, '\t')
    assert(widths.size >= 2) // Should detect at least 2 fields
  }

  test("parseLineWithWidths - very long line") {
    val line = "A" * 1000 + " " * 10 + "B" * 1000
    val widths = Seq(1010, 1000)
    val result = FixedWidthParser.parseLineWithWidths(line, widths, trimFields = true)
    assertEquals(result.head, "A" * 1000)
    assertEquals(result.last, "B" * 1000)
  }

  test("inferColumnWidths - alternating single and double spaces") {
    val line = "A B  C D  E"
    val widths = FixedWidthParser.inferColumnWidths(line, ' ')
    // Single spaces preserved in fields, double spaces are separators
    assert(widths.size >= 2)
  }

  test("parseLineWithWidths - widths sum exceeds line length") {
    val line = "Short"
    val widths = Seq(10, 20, 30)
    val result = FixedWidthParser.parseLineWithWidths(line, widths, trimFields = true)
    assertEquals(result, List("Short", "", ""))
  }

  test("parseLineWithInference - numbers with padding") {
    val line = "00123     456   789      "
    val result = FixedWidthParser.parseLineWithInference(line, ' ', trimFields = true)
    assertEquals(result, List("00123", "456", "789"))
  }

end FixedWidthParserSuite
