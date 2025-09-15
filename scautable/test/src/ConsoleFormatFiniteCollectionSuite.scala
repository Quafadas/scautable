package io.github.quafadas.scautable

import munit.FunSuite

class ConsoleFormatFiniteCollectionSuite extends FunSuite:

  test("ptbl should work with Array") {
    val arrayData = Array(("Alice", 25), ("Bob", 30))
    
    // Import the extension methods
    import io.github.quafadas.scautable.ConsoleFormat._
    
    // This should now work
    arrayData.ptbl
    
    // Also test that we can get the formatted string
    val formatted = arrayData.consoleFormat
    assert(formatted.contains("Alice"))
    assert(formatted.contains("Bob"))
  }

  test("ptbl should work with ArraySeq") {
    val arraySeqData = scala.collection.mutable.ArraySeq(("Alice", 25), ("Bob", 30))
    
    import io.github.quafadas.scautable.ConsoleFormat._
    
    // This should now work
    arraySeqData.ptbl
    
    val formatted = arraySeqData.consoleFormat
    assert(formatted.contains("Alice"))
    assert(formatted.contains("Bob"))
  }

  test("ptbln should work with Array of NamedTuple") {
    val namedTupleArray = Array((name = "Alice", age = 25), (name = "Bob", age = 30))
    
    import io.github.quafadas.scautable.ConsoleFormat._
    
    // This should now work
    namedTupleArray.ptbln
    
    val formatted = namedTupleArray.consoleFormatNt
    assert(formatted.contains("Alice"))
    assert(formatted.contains("Bob"))
    assert(formatted.contains("name"))
    assert(formatted.contains("age"))
  }

  test("ptbln should work with ArraySeq of NamedTuple") {
    val namedTupleArraySeq = scala.collection.mutable.ArraySeq((name = "Alice", age = 25), (name = "Bob", age = 30))
    
    import io.github.quafadas.scautable.ConsoleFormat._
    
    // This should now work
    namedTupleArraySeq.ptbln
    
    val formatted = namedTupleArraySeq.consoleFormatNt
    assert(formatted.contains("Alice"))
    assert(formatted.contains("Bob"))
    assert(formatted.contains("name"))
    assert(formatted.contains("age"))
  }

  test("ptbl should work with Vector") {
    val vectorData = Vector(("Alice", 25), ("Bob", 30))
    
    import io.github.quafadas.scautable.ConsoleFormat._
    
    // This should now work
    vectorData.ptbl
    
    val formatted = vectorData.consoleFormat
    assert(formatted.contains("Alice"))
    assert(formatted.contains("Bob"))
  }

  test("ptbln should work with Vector of NamedTuple") {
    val namedTupleVector = Vector((name = "Alice", age = 25), (name = "Bob", age = 30))
    
    import io.github.quafadas.scautable.ConsoleFormat._
    
    // This should now work
    namedTupleVector.ptbln
    
    val formatted = namedTupleVector.consoleFormatNt
    assert(formatted.contains("Alice"))
    assert(formatted.contains("Bob"))
    assert(formatted.contains("name"))
    assert(formatted.contains("age"))
  }

  test("existing Seq functionality still works") {
    val seqData = Seq(("Alice", 25), ("Bob", 30))
    val namedTupleSeq = Seq((name = "Alice", age = 25), (name = "Bob", age = 30))
    
    import io.github.quafadas.scautable.ConsoleFormat._
    
    // Ensure existing Seq functionality is preserved
    seqData.ptbl
    namedTupleSeq.ptbln
    
    val formatted1 = seqData.consoleFormat
    val formatted2 = namedTupleSeq.consoleFormatNt
    
    assert(formatted1.contains("Alice"))
    assert(formatted2.contains("Alice"))
  }

end ConsoleFormatFiniteCollectionSuite