package io.github.quafadas.scautable

import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

/** Whatever `CSVWriter` writes, the reader must give back unchanged.
  *
  * Fields are built from fragments chosen to hit every branch of the parser: quotes, doubled quotes, backslashes next to quotes and delimiters, every flavour of line break,
  * edge whitespace and non-ASCII text. The one documented normalisation is that a line break inside a quoted field (`\r\n`, `\r` or `\n`) is read back as `\n`.
  */
class CsvRoundTripPropertySuite extends ScalaCheckSuite:

  override def scalaCheckTestParameters = super.scalaCheckTestParameters.withMinSuccessfulTests(500)

  import CsvRoundTripPropertySuite.*

  property("parseLine reads back any single line formatLine writes") {
    forAll(delimiter, Gen.nonEmptyListOf(field.map(withoutLineBreaks))) { (d, fields) =>
      assertEquals(CSVParser.parseLine(CSVWriter.formatLine(fields, d), d), fields)
    }
  }

  property("records + parseLine read back any table, including fields that span lines") {
    forAll(delimiter, table, terminator) { (d, rows, term) =>
      val text = rows.map(CSVWriter.formatLine(_, d) + term).mkString
      val parsed = CSVParser.records(text.linesIterator).map(CSVParser.parseLine(_, d)).toList
      assertEquals(parsed, rows.map(_.map(normaliseLineBreaks)))
    }
  }

  test("a quoted field ending in a backslash closes, so the next line starts a new record") {
    val lines = Iterator("\"C:\\dir\\\",x", "y,z")
    assertEquals(CSVParser.records(lines).map(CSVParser.parseLine(_)).toList, List(List("C:\\dir\\", "x"), List("y", "z")))
  }

end CsvRoundTripPropertySuite

object CsvRoundTripPropertySuite:

  val delimiter: Gen[Char] = Gen.oneOf(',', ';', '\t', '|')

  val terminator: Gen[String] = Gen.oneOf("\n", "\r\n")

  private val tricky: Gen[String] =
    Gen.oneOf("\"", "\"\"", "\\", "\\\"", "\\\\", "\\n", "\n", "\r\n", "\r", " ", ",", ";", "\t", "|", "é", "日本", "😀", "C:\\new\\path")

  // Lone surrogates can't be encoded to UTF-8, and a BOM is stripped from the start of a file, so neither survives a trip through a file.
  private val anyChar: Gen[String] =
    Gen.choose(Char.MinValue, Char.MaxValue).filter(c => !c.isSurrogate && c != '\uFEFF').map(_.toString)

  private val fragment: Gen[String] = Gen.frequency(4 -> Gen.alphaNumStr.map(_.take(5)), 4 -> tricky, 1 -> anyChar)

  val field: Gen[String] = Gen.choose(0, 4).flatMap(Gen.listOfN(_, fragment)).map(_.mkString)

  /** 0 to 8 rows of 1 to 5 fields, all rows the same width. */
  val table: Gen[List[List[String]]] =
    for
      width <- Gen.choose(1, 5)
      height <- Gen.choose(0, 8)
      rows <- Gen.listOfN(height, Gen.listOfN(width, field))
    yield rows

  def withoutLineBreaks(s: String): String = s.filterNot(c => c == '\n' || c == '\r')

  def normaliseLineBreaks(s: String): String = s.replace("\r\n", "\n").replace('\r', '\n')

end CsvRoundTripPropertySuite
