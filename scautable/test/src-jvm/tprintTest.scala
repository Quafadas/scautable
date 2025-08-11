package io.github.quafadas.scautable

import pprint.TPrint
import pprint.TPrintColors

class TPrintSuite extends munit.FunSuite:

  test("CsvIterator TPrint should render type info") {    
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.resource("simple.csv")
    val tprint = summon[TPrint[CsvIterator[("col1", "col2", "col3")]]]
    implicit val colors: TPrintColors = TPrintColors.BlackWhite    
    val rendered = tprint.render.toString()    
    // Just verify the output contains CsvIterator - the exact format doesn't matter as much
    assert(rendered.contains("CsvIterator"), s"Expected 'CsvIterator' in '$rendered'")
  }

  test("CsvIterator prettyPrint extension should show column details") {
    import io.github.quafadas.scautable.CsvIteratorTPrint.*
    
    // Create a CSV iterator with known columns
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.resource("simple.csv")
    
    implicit val colors: TPrintColors = TPrintColors.BlackWhite
    
    // Use the extension method directly
    val prettyOutput = prettyPrint(csv).plainText

    
    // Verify the output contains the expected column information
    assert(prettyOutput.contains("CsvIterator"), s"Expected 'CsvIterator' in '$prettyOutput'")
    assert(prettyOutput.contains("\tcol1: String,"), s"Expected 'col1: String' in '$prettyOutput'") 
    assert(prettyOutput.contains("\tcol2: String,"), s"Expected 'col2: String' in '$prettyOutput'")
    assert(prettyOutput.contains("col3: String"), s"Expected 'col3: String' in '$prettyOutput'")
    
    // The prettyPrint output looks good from the debug output
    assert(prettyOutput.startsWith("CsvIterator["))
    assert(prettyOutput.endsWith("]"))
  }

  test("CsvIterator prettyPrint should work with different column names") {
    import io.github.quafadas.scautable.CsvIteratorTPrint.*
    
    // Test with different column names
    val csv: CsvIterator[("name", "age")] = CSV.fromString("name,age\nAlice,25\nBob,30")
    
    implicit val colors: TPrintColors = TPrintColors.BlackWhite
    
    val prettyOutput = prettyPrint(csv).plainText
    
    assert(prettyOutput.contains("CsvIterator"), s"Expected 'CsvIterator' in '$prettyOutput'")
    assert(prettyOutput.contains("\tname: String,"), s"Expected 'name: String' in '$prettyOutput'")
    assert(prettyOutput.contains("\tage: String"), s"Expected 'age: String' in '$prettyOutput'")
    
    assert(prettyOutput.startsWith("CsvIterator["))
    assert(prettyOutput.endsWith("]"))
  }

  test("CsvIterator prettyPrint should handle single column") {
    import io.github.quafadas.scautable.CsvIteratorTPrint.*
    
    // Test with a single column CSV from string  
    val csv = CSV.fromString("singleCol\nvalue1\nvalue2")
    
    implicit val colors: TPrintColors = TPrintColors.BlackWhite
    
    val prettyOutput = prettyPrint(csv).plainText
    
    assert(prettyOutput.contains("CsvIterator"), s"Expected 'CsvIterator' in '$prettyOutput'")
    assert(prettyOutput.contains("singleCol: String"), s"Expected 'singleCol: String' in '$prettyOutput'")
    
    assert(prettyOutput.startsWith("CsvIterator["))
    assert(prettyOutput.endsWith("]"))
  }
