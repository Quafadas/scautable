package io.github.quafadas.scautable

class FixedWidthIteratorSuite extends munit.FunSuite:

  test("FixedWidthIterator - basic functionality with automatic width inference") {
    val testData = Seq(
      "John      25   NYC      ",
      "Alice     30   LA       "
    )
    val columnWidths = FixedWidthParser.inferColumnWidths(testData.head, ' ')
    val lines = testData.iterator
    val headers = Seq("Name", "Age", "City")

    val iterator = new FixedWidthIterator[
      ("Name", "Age", "City"),
      (String, String, String)
    ](lines, headers, columnWidths, ' ', trimFields = true)

    val data = iterator.toList
    assertEquals(data.size, 2)

    // Verify data as tuples
    val first = data(0).toTuple
    assertEquals(first._1, "John")
    assertEquals(first._2, "25")
    assertEquals(first._3, "NYC")

    val second = data(1).toTuple
    assertEquals(second._1, "Alice")
    assertEquals(second._2, "30")
    assertEquals(second._3, "LA")
  }

  test("FixedWidthIterator - with automatic inference") {
    val testData = Seq(
      "John      25   NYC      ",
      "Alice     30   LA       "
    )
    val columnWidths = FixedWidthParser.inferColumnWidths(testData.head, ' ')
    val lines = testData.iterator
    val headers = Seq("Name", "Age", "City")

    val iterator = new FixedWidthIterator[
      ("Name", "Age", "City"),
      (String, String, String)
    ](lines, headers, columnWidths, ' ', trimFields = true)

    val data = iterator.toList
    assertEquals(data.size, 2)

    // Check data was parsed correctly
    val first = data(0).toTuple
    assertEquals(first._1, "John")
    assertEquals(first._2, "25")
  }

  test("FixedWidthIterator - hasNext and next") {
    val testData = Seq("John      25   ", "Alice     30   ")
    val columnWidths = FixedWidthParser.inferColumnWidths(testData.head, ' ')
    val lines = testData.iterator
    val headers = Seq("Name", "Age")

    val iterator = new FixedWidthIterator[
      ("Name", "Age"),
      (String, String)
    ](lines, headers, columnWidths, ' ', trimFields = true)

    assert(iterator.hasNext)
    val first = iterator.next()
    assertEquals(first.toTuple._1, "John")

    assert(iterator.hasNext)
    val second = iterator.next()
    assertEquals(second.toTuple._1, "Alice")

    assert(!iterator.hasNext)
  }

  test("FixedWidthIterator - without trimming") {
    val testData = Seq("John      25   ")
    val columnWidths = FixedWidthParser.inferColumnWidths(testData.head, ' ')
    val lines = testData.iterator
    val headers = Seq("Name", "Age")

    val iterator = new FixedWidthIterator[
      ("Name", "Age"),
      (String, String)
    ](lines, headers, columnWidths, ' ', trimFields = false)

    val data = iterator.next()
    val tuple = data.toTuple
    assertEquals(tuple._1, "John      ")
    assertEquals(tuple._2, "25   ")
  }

  test("FixedWidthIterator - schemaGen") {
    val lines = Iterator.empty[String]
    val headers = Seq("firstName", "age", "city")
    val columnWidths = Seq(10, 5, 10) // Dummy widths for empty iterator

    val iterator = new FixedWidthIterator[
      ("firstName", "age", "city"),
      (String, String, String)
    ](lines, headers, columnWidths, ' ', trimFields = true)

    val schema = iterator.schemaGen
    assert(schema.contains("object FixedWidthSchema:"), "Should contain schema object")
    assert(schema.contains("type firstName = \"firstName\""), "Should contain firstName type")
    assert(schema.contains("type age = \"age\""), "Should contain age type")
    assert(schema.contains("type city = \"city\""), "Should contain city type")
  }

  test("FixedWidthIterator - headerIndex by string") {
    val lines = Iterator.empty[String]
    val headers = Seq("Name", "Age", "City")
    val columnWidths = Seq(10, 5, 10) // Dummy widths for empty iterator

    val iterator = new FixedWidthIterator[
      ("Name", "Age", "City"),
      (String, String, String)
    ](lines, headers, columnWidths, ' ', trimFields = true)

    assertEquals(iterator.headerIndex("Name"), 0, "Name should be at index 0")
    assertEquals(iterator.headerIndex("Age"), 1, "Age should be at index 1")
    assertEquals(iterator.headerIndex("City"), 2, "City should be at index 2")
  }

  test("FixedWidthIterator - custom padding character") {
    val testData = Seq("Name__Age___City__")
    val columnWidths = FixedWidthParser.inferColumnWidths(testData.head, '_')
    val lines = testData.iterator
    val headers = Seq("Name", "Age", "City")

    val iterator = new FixedWidthIterator[
      ("Name", "Age", "City"),
      (String, String, String)
    ](lines, headers, columnWidths, '_', trimFields = false)

    val data = iterator.next()
    val tuple = data.toTuple
    assertEquals(tuple._1, "Name__")
    assertEquals(tuple._2, "Age___")
    assertEquals(tuple._3, "City__")
  }

end FixedWidthIteratorSuite
