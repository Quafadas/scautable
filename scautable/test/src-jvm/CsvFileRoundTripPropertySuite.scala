package io.github.quafadas.scautable

import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

import io.github.quafadas.table.*

/** End-to-end version of [[CsvRoundTripPropertySuite]]: write a table to a real file with `CSVWriter` and read it back through the public runtime reader, `CSV.fromTyped`, which
  * goes through `Source.getLines`, header handling and decoding.
  */
class CsvFileRoundTripPropertySuite extends ScalaCheckSuite:

  import CsvRoundTripPropertySuite.*

  private val rows: Gen[List[List[String]]] = Gen.choose(0, 8).flatMap(Gen.listOfN(_, Gen.listOfN(3, field)))

  property("CSV.fromTyped reads back any table CSVWriter writes to a file") {
    val reader = CSV.fromTyped[("a", "b", "c"), (String, String, String)]
    forAll(rows, terminator) { (rows, term) =>
      val file = os.temp(suffix = ".csv")
      try
        os.write.over(file, (List("a", "b", "c") :: rows).map(CSVWriter.formatLine(_) + term).mkString)
        val read = reader(file).map(r => List(r.a, r.b, r.c)).toList
        assertEquals(read, rows.map(_.map(normaliseLineBreaks)))
      finally os.remove(file)
      end try
    }
  }

end CsvFileRoundTripPropertySuite
