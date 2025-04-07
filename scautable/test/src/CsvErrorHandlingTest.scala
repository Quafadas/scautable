package io.github.quafadas.scautable

import org.junit.Test
import org.junit.Assert._

class CsvErrorHandlingTest {
  @Test
  def testMalformedRowDetection(): Unit = {
    val csv = """a,b,c
1,2
3,4,5,6
7,8,9"""
    
    val lines = csv.split("\n").iterator
    val results = CSVParser.parseWithRecovery(lines).toList
    
    // First should be headers (Right)
    assertTrue(results(0).isRight)
    
    // Second line is missing a field (Left)
    assertTrue(results(1).isLeft)
    results(1) match {
      case Left(e: MalformedRowError) => 
        assertEquals(2, e.lineNumber)
        assertEquals(3, e.expectedFields)
        assertEquals(2, e.actualFields)
      case _ => fail("Expected MalformedRowError")
    }
    
    // Third line has too many fields (Left)
    assertTrue(results(2).isLeft)
    
    // Fourth line is correct (Right)
    assertTrue(results(3).isRight)
  }
  
  @Test
  def testRecoveryStrategyPadding(): Unit = {
    val csv = """a,b,c
1,2
3,4,5,6
7,8,9"""
    
    val lines = csv.split("\n").iterator
    val results = CSVParser.parseWithRecovery(
      lines, 
      recoveryStrategy = RecoveryStrategy.PadWithNulls
    ).toList
    
    // All entries should be successful with recovery
    assertTrue(results.forall(_.isRight))
    
    // Check that second row was padded
    results(1) match {
      case Right(row) => 
        assertEquals(3, row.length)
        assertEquals("", row(2))  // Padded with empty string
      case Left(_) => 
        fail("Expected Right but got Left")
    }
    
    // Check that third row was not recovered (default is Skip for too many fields)
    assertEquals(4, results.size)  // Header + 3 data rows
  }
  
  @Test
  def testErrorPatternDetection(): Unit = {
    val errors = Seq(
      MalformedRowError(2, 3, 2, "1,2"),
      MalformedRowError(4, 3, 2, "5,6"),
      TypeConversionError(3, "age", "abc", "Int", "john,abc,ny"),
      MissingValueError(5, "name", ",25,ny")
    )
    
    val patterns = CsvErrors.detectErrorPatterns(errors)
    
    assertEquals(3, patterns.size)
    assertEquals(2, patterns("STRUCTURE_ERROR"))
    assertEquals(1, patterns("TYPE_ERROR_age"))
    assertEquals(1, patterns("MISSING_VALUE_name"))
  }
}
