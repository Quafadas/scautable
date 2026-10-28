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
    val formatted1 = seqData.consoleFormat
    val formatted2 = namedTupleSeq.consoleFormatNt
    assert(formatted1.contains("Alice"))
    assert(formatted2.contains("Alice"))
  }

  test("case classes render with field names as headers") {
    val rates = List(Rate("EUR", 1.08), Rate("GBP", 1.27))
    import io.github.quafadas.scautable.ConsoleFormat.*

    rates.ptblc
    val formatted = rates.consoleFormatCc
    assert(formatted.contains("currency"))
    assert(formatted.contains("rate"))
    assert(formatted.contains("GBP"))

    val plain = rates.consoleFormatCc(None, fansi = false)
    assert(plain.contains("currency"))

    val html = rates.toArray.htmlCc
    assert(html.contains("<table"))
    assert(html.contains("currency"))
    assert(html.contains("EUR"))
  }

  test("case classes convert to named tuples for the rest of the API") {
    val rates = Vector(Rate("EUR", 1.08), Rate("GBP", 1.27))
    import io.github.quafadas.table.*

    val nts = rates.toNamedTuples.toList
    assertEquals(nts.map(_.currency), List("EUR", "GBP"))
    assertEquals(nts.map(_.rate), List(1.08, 1.27))
  }

end ConsoleFormatFiniteCollectionSuite

case class Rate(currency: String, rate: Double)
