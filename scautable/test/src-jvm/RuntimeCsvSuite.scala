package io.github.quafadas.scautable

import io.github.quafadas.table.*
import munit.FunSuite
import os.Path

class RuntimeCsvSuite extends FunSuite:

  test("fromTyped creates a function that can read CSV from runtime path") {
    val csvReader: Path => CsvIterator[("col1", "col2", "col3"), (String, String, String)] = CSV.fromTyped[("col1", "col2", "col3"), (String, String, String)]

    // Get the resource path at runtime
    val resourcePath = os.Path(this.getClass.getClassLoader.getResource("simple.csv").getPath)

    val csv = csvReader(resourcePath)
    val rows = csv.toArray

    assertEquals(rows.length, 3)
    assertEquals(rows(0).col1, "1")
    assertEquals(rows(0).col2, "2")
    assertEquals(rows(0).col3, "7")
    assertEquals(rows(1).col1, "3")
    assertEquals(rows(1).col2, "4")
    assertEquals(rows(1).col3, "8")
    assertEquals(rows(2).col1, "5")
    assertEquals(rows(2).col2, "6")
    assertEquals(rows(2).col3, "9")
  }

  test("fromTyped with mixed types reads and decodes CSV correctly") {
    val csvReader = CSV.fromTyped[("col1", "col2", "col3"), (Int, String, Double)]

    val resourcePath = os.Path(this.getClass.getClassLoader.getResource("simple.csv").getPath)

    val csv = csvReader(resourcePath)
    val rows = csv.toArray

    assertEquals(rows.length, 3)
    assertEquals(rows(0).col1, 1)
    assertEquals(rows(0).col2, "2")
    assertEquals(rows(0).col3, 7.0)
  }

  test("fromTyped can be reused for multiple files with same schema") {
    val csvReader = CSV.fromTyped[("col1", "col2", "col3"), (String, String, String)]

    val resourcePath = os.Path(this.getClass.getClassLoader.getResource("simple.csv").getPath)

    // Read the same file twice using the same reader function
    val csv1 = csvReader(resourcePath)
    val rows1 = csv1.toArray

    val csv2 = csvReader(resourcePath)
    val rows2 = csv2.toArray

    assertEquals(rows1.length, rows2.length)
    assertEquals(rows1(0).col1, rows2(0).col1)
    assertEquals(rows1(0).col2, rows2(0).col2)
    assertEquals(rows1(0).col3, rows2(0).col3)
  }

  test("exception is thrown if headers do not match expected") {
    val csvReader = CSV.fromTyped[("col1", "colB", "colC"), (String, String, String)]

    val resourcePath = os.Path(this.getClass.getClassLoader.getResource("simple.csv").getPath)

    val thrown = intercept[IllegalStateException] {
      val csv = csvReader(resourcePath)
      csv.toArray
    }
    assert(thrown.getMessage.contains("CSV headers do not match expected headers"))
    assert(thrown.getMessage.contains("colB"))

    val sizeCheck = CSV.fromTyped[("col1", "col2", "col3", "col4"), (String, String, String, String)]
    val thrownSize = intercept[IllegalStateException] {
      val csv = sizeCheck(resourcePath)
      csv.toArray
    }
    assert(thrownSize.getMessage.contains("You provided: 4 but 3 headers were found in the file"))

    val sizeCheck2 = CSV.fromTyped[("col1", "col2", "col3"), (String, String, String, String)]
    val thrownSize2 = intercept[IllegalStateException] {
      val csv = sizeCheck2(resourcePath)
      csv.toArray
    }
    assert(thrownSize2.getMessage.contains("Number of headers in CSV (3) does not match number (4) of types provided for decoding."))

  }

end RuntimeCsvSuite
