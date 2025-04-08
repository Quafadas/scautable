package io.github.quafadas.scautable

import munit.FunSuite
import io.github.quafadas.scautable.NamedTupleIteratorExtensions._

/**
 * Test suite specifically for CSV files with duplicate headers.
 * This demonstrates how the library handles duplicate headers with compile-time typing.
 */
class DuplicateHeadersTest extends FunSuite {

  /**
   * Test reading a CSV file with duplicate headers using absolutePathWithDuplicates.
   * Note that the duplicate headers are normalized in the type signature:
   * - "col1" (first occurrence) remains "col1"
   * - "col1" (second occurrence) becomes "col1_1"
   */
  test("reading with duplicate headers - explicit method") {
    // The type signature must use the normalized header names
    val csv: CsvIterator[("col1", "col2", "col1_1", "col3")] = 
      CSV.absolutePathWithDuplicates(Generated.resourceDir0 + "duplicate_headers.csv")

    // Verify data is correctly read
    val data = csv.toArray
    assertEquals(data.length, 3)
    
    // Access first row using normalized column names
    val firstRow = data(0)
    assertEquals(firstRow.col1, "1")
    assertEquals(firstRow.col2, "2")
    assertEquals(firstRow.col1_1, "3")  // Second "col1" is now "col1_1"
    assertEquals(firstRow.col3, "4")
    
    // Check the second row
    val secondRow = data(1)
    assertEquals(secondRow.col1, "5")
    assertEquals(secondRow.col2, "6")
    assertEquals(secondRow.col1_1, "7")
    assertEquals(secondRow.col3, "8")
  }
  
  /**
   * Test demonstrating the auto-detection of headers, which is the most user-friendly
   * approach and directly addresses Simon's feedback.
   */
  test("detectHeaders - automatic type inference") {
    // The most user-friendly approach - no need to specify header types!
    val csv = CSV.detectHeaders(Generated.resourceDir0 + "duplicate_headers.csv")
    
    // The type is automatically inferred at compile time, so we can directly use
    // the correct column names without manually specifying types
    
    // Verify data is read correctly
    val data = csv.toArray
    assertEquals(data.length, 3)
    
    // Access first row using normalized column names
    val firstRow = data(0)
    assertEquals(firstRow.col1, "1")
    assertEquals(firstRow.col2, "2")
    assertEquals(firstRow.col1_1, "3")  // Second "col1" is now "col1_1"
    assertEquals(firstRow.col3, "4")
    
    // Access second row with normalized column names
    val secondRow = data(1)
    assertEquals(secondRow.col1, "5")
    assertEquals(secondRow.col2, "6")
    assertEquals(secondRow.col1_1, "7")
    assertEquals(secondRow.col3, "8")
  }
  
  /**
   * Test demonstrating how a user would determine the correct type signature for a file
   * with duplicate headers using the utility methods.
   */
  test("using utility methods to determine correct type signatures") {
    val filePath = Generated.resourceDir0 + "duplicate_headers.csv"
    
    // Get the normalized header type signature as a string
    val normalizedHeadersStr = CSV.getNormalizedHeadersString(filePath)
    
    // This would print: ("col1", "col2", "col1_1", "col3")
    println(s"Normalized headers for type annotation: $normalizedHeadersStr")
    
    // Verify the string is correct
    assertEquals(normalizedHeadersStr, """("col1", "col2", "col1_1", "col3")""")
    
    // Get the original headers for comparison
    val originalHeadersStr = CSV.getOriginalHeadersString(filePath)
    assertEquals(originalHeadersStr, """("col1", "col2", "col1", "col3")""")
    
    // Generate a usage example (for documentation purposes)
    val example = CSV.generateDuplicateHeaderUsageExample(filePath)
    assert(example.contains("CSV.absolutePathWithDuplicates"))
    assert(example.contains(normalizedHeadersStr))
    
    // This demonstrates how a user would use the tools to correctly
    // set up their code with the proper type annotations
  }
  
  /**
   * Test that demonstrates the mapping between original and normalized headers.
   */
  test("header normalization report") {
    val csv = CSV.absolutePathWithDuplicates(Generated.resourceDir0 + "duplicate_headers.csv")
                .asInstanceOf[CsvIterator[("col1", "col2", "col1_1", "col3")]]
                
    // Verify original headers preserved
    assertEquals(csv.originalHeaders, List("col1", "col2", "col1", "col3"))
    
    // Verify normalized headers
    assertEquals(csv.headers, List("col1", "col2", "col1_1", "col3"))
    
    // Verify normalization was detected
    assert(csv.hasNormalizedHeaders)
    
    // Verify report contains mapping info
    val report = csv.headerNormalizationReport
    assert(report.isDefined)
    assert(report.get.contains("col1_1"))
  }
  
  /**
   * Test access to specific columns through the getNormalizedHeader utility
   */
  test("access columns by original name") {
    val csv: CsvIterator[("col1", "col2", "col1_1", "col3")] = 
      CSV.absolutePathWithDuplicates(Generated.resourceDir0 + "duplicate_headers.csv")
    
    // Test the utility to get a normalized name from original
    val firstCol1 = csv.getNormalizedHeader("col1")
    assertEquals(firstCol1, "col1")
    
    // This is a bit tricky since it always returns the first match
    // Real-world usage would need to consider index
    val index = csv.originalHeaders.indexOf("col1", 1) // Find second occurrence
    assertEquals(csv.headers(index), "col1_1")
  }
} 