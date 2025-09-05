package io.github.quafadas.scautable

import io.github.quafadas.table.*
import CSVWriterExtensions.*

class CSVIntegrationSuite extends munit.FunSuite:

  test("complete workflow: read CSV, enrich data, write CSV") {
    // Simulate the workflow described in the issue:
    // read a CSV, enrich it with data, store it
    
    // First, create some test data and write it
    val originalData = Vector(
      (Name = "Alice", Age = "25", Profession = "Engineer"),
      (Name = "Bob", Age = "30", Profession = "Designer"),
      (Name = "Charlie", Age = "22", Profession = "Student")
    )
    
    val tempInputFile = os.temp(prefix = "input", suffix = ".csv")
    val tempOutputFile = os.temp(prefix = "output", suffix = ".csv")
    
    try {
      // Write initial data
      originalData.writeCsv(tempInputFile)
      
      // Read the CSV
      val csvData = CSV.absolutePath[("Name", "Age", "Profession")](tempInputFile.toString)
      
      // Enrich the data (add a new column and filter)
      val enrichedData = csvData
        .addColumn["AgeGroup", String] { row =>
          val age = row.Age.toInt
          if age < 25 then "Young" else "Adult"
        }
        .filter(_.Profession != "Student")  // Filter out students
        .toVector
      
      // Write enriched data to new file
      enrichedData.writeCsv(tempOutputFile)
      
      // Verify the output
      val outputContent = os.read(tempOutputFile)
      val expectedLines = Seq(
        "Name,Age,Profession,AgeGroup",
        "Alice,25,Engineer,Adult",
        "Bob,30,Designer,Adult"
      )
      
      assertEquals(outputContent, expectedLines.mkString("\n"))
      
      // Also verify we can read it back
      val readBack = CSV.absolutePath[("Name", "Age", "Profession", "AgeGroup")](tempOutputFile.toString).toVector
      assertEquals(readBack.length, 2)
      assertEquals(readBack.head.Name, "Alice")
      assertEquals(readBack.head.AgeGroup, "Adult")
      assertEquals(readBack.last.Name, "Bob")
      assertEquals(readBack.last.AgeGroup, "Adult")
      
    } finally {
      if (os.exists(tempInputFile)) os.remove(tempInputFile)
      if (os.exists(tempOutputFile)) os.remove(tempOutputFile)
    }
  }

  test("workflow with complex data requiring escaping") {
    // Test workflow with data that requires CSV escaping
    val complexData = Vector(
      (Company = "Acme, Inc.", Description = "Makes \"quality\" products", Revenue = "1000000"),
      (Company = "Tech Corp", Description = "Software\nDevelopment", Revenue = "2000000"),
      (Company = "Data Ltd", Description = " Analytics & AI ", Revenue = "1500000")
    )
    
    val tempFile = os.temp(prefix = "complex", suffix = ".csv")
    
    try {
      // Write complex data
      complexData.writeCsv(tempFile)
      
      // Read it back
      val readBack = CSV.absolutePath[("Company", "Description", "Revenue")](tempFile.toString).toVector
      
      // Verify data integrity
      assertEquals(readBack.length, 3)
      assertEquals(readBack(0).Company, "Acme, Inc.")
      assertEquals(readBack(0).Description, "Makes \"quality\" products")
      assertEquals(readBack(1).Description, "Software\nDevelopment")
      assertEquals(readBack(2).Description, " Analytics & AI ")
      
      // Transform and write again
      val transformed = readBack
        .mapColumn["Revenue", Int](_.toInt)
        .addColumn["Category", String] { row =>
          if row.Revenue > 1500000 then "Large" else "Medium"
        }
        .toVector
      
      val tempFile2 = os.temp(prefix = "transformed", suffix = ".csv")
      try {
        transformed.writeCsv(tempFile2)
        
        // Verify transformed output
        val transformedContent = os.read(tempFile2)
        val lines = transformedContent.split("\n")
        
        // Check headers
        assertEquals(lines(0), "Company,Description,Revenue,Category")
        
        // Check that complex values are properly escaped
        assert(lines(1).contains("\"Acme, Inc.\""))
        assert(lines(1).contains("\"Makes \"\"quality\"\" products\""))
        assert(lines(2).contains("\"Software\nDevelopment\""))
        
      } finally {
        if (os.exists(tempFile2)) os.remove(tempFile2)
      }
      
    } finally {
      if (os.exists(tempFile)) os.remove(tempFile)
    }
  }

  test("demonstrate API from issue example") {
    // This test demonstrates the usage shown in the issue
    val data = Vector(
      (origIdx = 0, Name = "Rose", Pclass = "1", Ticket = "PC123"),
      (origIdx = 1, Name = "Jack", Pclass = "3", Ticket = "CA456"),
      (origIdx = 2, Name = "Cal", Pclass = "1", Ticket = "PC789")
    )
    
    // Filter and select columns as in the issue example
    type myCols = ("Name", "Pclass", "Ticket")
    val subset = data
      .filter(_.Pclass == "1")  // Filter for first class
      .columns[myCols]
      .toVector
    
    // The new API - much simpler than the manual string joining in the issue
    val csvOutput = subset.toCsv()
    
    val expectedLines = Seq(
      "Name,Pclass,Ticket",
      "Rose,1,PC123",
      "Cal,1,PC789"
    )
    
    assertEquals(csvOutput, expectedLines.mkString("\n"))
    
    // Also test writing to file
    val tempFile = os.temp(prefix = "subset", suffix = ".csv")
    try {
      subset.writeCsv(tempFile)
      val fileContent = os.read(tempFile)
      assertEquals(fileContent, csvOutput)
    } finally {
      if (os.exists(tempFile)) os.remove(tempFile)
    }
  }

  test("Iterator vs Iterable consistency") {
    val data = Vector(
      (col1 = "a", col2 = "b"),
      (col1 = "c", col2 = "d")
    )
    
    val iteratorOutput = data.iterator.toCsv()
    val iterableOutput = data.toCsv()
    
    assertEquals(iteratorOutput, iterableOutput)
    
    // Test different collection types
    val listOutput = data.toList.toCsv()
    val seqOutput = data.toSeq.toCsv()
    
    assertEquals(listOutput, iterableOutput)
    assertEquals(seqOutput, iterableOutput)
  }

end CSVIntegrationSuite