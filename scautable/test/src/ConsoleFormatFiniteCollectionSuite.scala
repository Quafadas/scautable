package io.github.quafadas.scautable

import munit.FunSuite

class ConsoleFormatFiniteCollectionSuite extends FunSuite:

  test("ptbl should work with Array") {
    val arrayData = Array(("Alice", 25), ("Bob", 30))
    import io.github.quafadas.scautable.ConsoleFormat.*

    arrayData.ptbl
    val formatted = arrayData.consoleFormat
    assert(formatted.contains("Alice"))
    assert(formatted.contains("Bob"))
  }

  test("to html works") {
    val arrayData = Array((name = "Alice", age = 25), (name = "Bob", age = 30))
    import io.github.quafadas.scautable.ConsoleFormat.*

    val formatted = arrayData.html
    assert(formatted.contains("<table"))
    assert(formatted.contains("Alice"))
    assert(formatted.contains("Bob"))
  }

  test("ptbln should work with Array of NamedTuple") {
    val namedTupleArray = Array((name = "Alice", age = 25), (name = "Bob", age = 30))
    import io.github.quafadas.scautable.ConsoleFormat.*

    namedTupleArray.ptbln
    val formatted = namedTupleArray.consoleFormatNt
    assert(formatted.contains("Alice"))
    assert(formatted.contains("name"))
  }

  test("existing Seq functionality still works") {
    val seqData = Seq(("Alice", 25), ("Bob", 30))
    val namedTupleSeq = Seq((name = "Alice", age = 25), (name = "Bob", age = 30))
    import io.github.quafadas.scautable.ConsoleFormat.*

    seqData.ptbl
    namedTupleSeq.ptbln

    val formatted1 = seqData.consoleFormat
    val formatted2 = namedTupleSeq.consoleFormatNt
    assert(formatted1.contains("Alice"))
    assert(formatted2.contains("Alice"))
  }

end ConsoleFormatFiniteCollectionSuite
