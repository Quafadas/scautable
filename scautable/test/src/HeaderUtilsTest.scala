package io.github.quafadas.scautable

import munit.FunSuite

class HeaderUtilsTest extends FunSuite {
  test("header normalization") {
    val headers = Seq(
      "First Name", 
      "Last Name", 
      "Age", 
      "First Name", 
      "123illegal", 
      "", 
      "email@address",
      "type", // reserved keyword
      "EMPLOYEE_ID"
    )
    
    val normalized = HeaderUtils.normalizeHeaders(headers)
    
    assertEquals(normalized(0), "First_Name")
    assertEquals(normalized(1), "Last_Name")
    assertEquals(normalized(2), "Age")
    assertEquals(normalized(3), "First_Name_1") // Duplicate handled
    assertEquals(normalized(4), "_123illegal") // Number prefix handled
    assertEquals(normalized(5), "column") // Empty handled
    assertEquals(normalized(6), "email_address") // Special chars handled
    assertEquals(normalized(7), "type_") // Reserved keyword handled
    assertEquals(normalized(8), "EMPLOYEE_ID") // ALL_CAPS preserved
  }
  
  test("header validation") {
    val headers = Seq("First Name", "Last Name", "Age", "First Name", "", "type")
    val result = HeaderUtils.validateHeaders(headers)
    
    assert(!result.isValid) // Should be invalid due to empty header
    assertEquals(result.problems.size, 4) // Empty, Duplicate, Invalid chars, and Reserved keyword
    
    // Check problem types
    assert(result.problems.exists(_.isInstanceOf[EmptyHeader]))
    assert(result.problems.exists(_.isInstanceOf[DuplicateHeader]))
    assert(result.problems.exists(_.isInstanceOf[InvalidCharactersHeader]))
    assert(result.problems.exists(_.message.contains("reserved Scala keywords")))
  }
  
  test("column name inference") {
    // Test numeric column
    assertEquals(HeaderUtils.inferColumnNameFromContent(Seq("1.2", "3.4", "5.6")), "amount")
    assertEquals(HeaderUtils.inferColumnNameFromContent(Seq("100", "200", "300")), "value")
    assertEquals(HeaderUtils.inferColumnNameFromContent(Seq("0", "1", "2")), "category")
    
    // Test date column
    assertEquals(HeaderUtils.inferColumnNameFromContent(Seq("2023-01-01", "2023-02-01")), "date")
    
    // Test email column
    assertEquals(HeaderUtils.inferColumnNameFromContent(Seq("user@example.com", "admin@test.org")), "email")
    
    // Test name column
    assertEquals(HeaderUtils.inferColumnNameFromContent(Seq("John Doe", "Jane Smith")), "name")
  }
  
  test("header suggestions") {
    val headers = Seq("fn", "ln", "ID", "DOB", "email")
    val sampleData = Seq(
      Seq("John", "Doe", "12345", "1980-01-01", "john@example.com"),
      Seq("Jane", "Smith", "67890", "1990-05-15", "jane@example.com")
    )
    
    val suggestions = HeaderUtils.suggestImprovedHeaders(headers, sampleData)
    
    // "fn", "ln", and "ID" should get suggestions as they are short (length <= 2)
    assertEquals(suggestions.size, 3)
    assertEquals(suggestions("fn")._1, "field") // Default for short headers
    assertEquals(suggestions("ln")._1, "field") // Default for short headers
    assertEquals(suggestions("ID")._1, "value") // "ID" gets "value" because it contains numeric content
  }
}