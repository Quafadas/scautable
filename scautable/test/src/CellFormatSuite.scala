package io.github.quafadas.scautable

import io.github.quafadas.table.*

import munit.FunSuite

/** A user-defined tag, exactly as the docs describe it. */
object MyTags:
  opaque type Money <: Double = Double
  given CellFormat[Money] = (d: Money) => "USD " + CellFormat.grouped(d, 2)
end MyTags

class CellFormatSuite extends FunSuite:

  test("a tagged column formats while an untagged column of the same type does not") {
    val data = Seq((price = 1234.5678, ratio = 1234.5678))
    val out = data.formatColumn["price", Decimals[2]].consoleFormatNt

    assert(out.contains("1234.57"), out)
    assert(out.contains("1234.5678"), out)
  }

  test("Decimals pads as well as rounds") {
    val data = Seq((x = 1.5))
    assert(data.formatColumn["x", Decimals[3]].consoleFormatNt.contains("1.500"))
  }

  test("SigFigs") {
    val data = Seq((mean = 29.69911764705882))
    val out = data.formatColumn["mean", SigFigs[4]].consoleFormatNt
    assert(out.contains("29.70"), out)
    assert(!out.contains("29.699"), out)
  }

  test("Percent") {
    val data = Seq((rate = 0.0425))
    assert(data.formatColumn["rate", Percent[2]].consoleFormatNt.contains("4.25%"))
  }

  test("Percent of zero") {
    val data = Seq((rate = 0.0))
    assert(data.formatColumn["rate", Percent[1]].consoleFormatNt.contains("0.0%"))
  }

  test("Thousands") {
    val data = Seq((big = 9876543.21))
    assert(data.formatColumn["big", Thousands].consoleFormatNt.contains("9,876,543.21"))
  }

  test("Currency") {
    val data = Seq((price = 1234.5678))
    assert(data.formatColumn["price", Currency["$", 2]].consoleFormatNt.contains("$1,234.57"))
  }

  test("IntThousands and LongThousands") {
    val data = Seq((i = 9876543, l = 9876543210L))
    val out = data.formatColumn["i", IntThousands].formatColumn["l", LongThousands].consoleFormatNt
    assert(out.contains("9,876,543"), out)
    assert(out.contains("9,876,543,210"), out)
  }

  test("Option columns format through the inner tag, None unchanged") {
    val data = Seq((x = Some(1234.5678)), (x = None))
    val out = data.formatColumn["x", Decimals[2]].consoleFormatNt
    assert(out.contains("1234.57"), out)
    assert(out.contains("None"), out)
  }

  test("a user-defined tag resolves") {
    import MyTags.{Money, given}
    val data = Seq((salary = 65000.0))
    assert(data.formatColumn["salary", Money].consoleFormatNt.contains("USD 65,000.00"))
  }

  test("formatColumn does not alter the underlying values") {
    val data = Seq((price = 1234.5678), (price = 1.0))
    val tagged = data.formatColumn["price", Decimals[2]]
    assertEquals(tagged.map(_.price.toDouble).sum, 1235.5678)
  }

  test("a tagged numeric column is still numeric") {
    val data = Seq((price = 1234.5678, label = "a"))
    val tagged = data.formatColumn["price", Decimals[2]]
    assertEquals(tagged.numericCols.head.toTuple.size, 1)
    assertEquals(tagged.nonNumericCols.head.toTuple.size, 1)
  }

  test("formatting survives downstream column operations") {
    val data = Seq((price = 1234.5678, drop = 1))
    val out = data.formatColumn["price", Decimals[2]].dropColumn["drop"].consoleFormatNt
    assert(out.contains("1234.57"), out)
  }

  test("the non-fansi consoleFormatNt overload formats too") {
    val data = Seq((price = 1234.5678))
    val out = data.formatColumn["price", Decimals[2]].consoleFormatNt(fansi = false)
    assert(out.contains("1234.57"), out)
  }

  test("html honours the same tags as the console") {
    val data = Seq((price = 1234.5678, ratio = 1234.5678))
    val out = data.formatColumn["price", Decimals[2]].html
    assert(out.contains("<table"), out)
    assert(out.contains("<td>1234.57</td>"), out)
    assert(out.contains("<td>1234.5678</td>"), out)
    assert(out.contains("<th>price</th>"), out)
  }

  test("untagged tables render exactly as before") {
    val data = Seq((name = "Alice", age = 25), (name = "Bob", age = 30))
    val out = data.consoleFormatNt
    assert(out.contains("Alice"), out)
    assert(out.contains("25"), out)
  }

  test("null renders as empty rather than throwing") {
    val data = Seq((name = null.asInstanceOf[String], age = 25))
    val out = data.consoleFormatNt
    assert(out.contains("25"), out)
  }

  test("NaN and Infinity are not mangled by a format tag") {
    val data = Seq((x = Double.NaN), (x = Double.PositiveInfinity))
    val out = data.formatColumn["x", Decimals[2]].consoleFormatNt
    assert(out.contains("NaN"), out)
    assert(out.contains("Infinity"), out)
  }

  test("grouping handles negatives, short numbers and exact boundaries") {
    assertEquals(CellFormat.grouped(-9876543.21, 2), "-9,876,543.21")
    assertEquals(CellFormat.grouped(999.0, 2), "999.00")
    assertEquals(CellFormat.grouped(1000.0, 2), "1,000.00")
    assertEquals(CellFormat.grouped(0.0, 2), "0.00")
    assertEquals(CellFormat.addGrouping("-1234567"), "-1,234,567")
  }

  test("formatting is locale independent") {
    // BigDecimal rather than String.format, so a European default locale cannot produce "1234,57"
    assertEquals(CellFormat.fixed(1234.5678, 2), "1234.57")
    assertEquals(CellFormat.fixed(-0.005, 2), "-0.01")
    assertEquals(CellFormat.fixed(2.0, 0), "2")
  }

  test("formatAsPercentage still matches the CellFormat policy") {
    import io.github.quafadas.scautable.ConsoleFormat.formatAsPercentage
    assertEquals(0.0425.formatAsPercentage, "4.25%")
    assertEquals(0.0.formatAsPercentage, "0.00%")
  }

  test("the renderings documented in displayingTables.md are accurate") {
    val d = 1234.5678
    assertEquals(summon[CellFormat[Decimals[2]]].format(d.asInstanceOf[Decimals[2]]), "1234.57")
    assertEquals(summon[CellFormat[SigFigs[4]]].format(d.asInstanceOf[SigFigs[4]]), "1235")
    assertEquals(summon[CellFormat[Percent[2]]].format(d.asInstanceOf[Percent[2]]), "123456.78%")
    assertEquals(summon[CellFormat[Thousands]].format(d.asInstanceOf[Thousands]), "1,234.57")
    assertEquals(summon[CellFormat[Currency["$", 2]]].format(d.asInstanceOf[Currency["$", 2]]), "$1,234.57")
    assertEquals(summon[CellFormat[IntThousands]].format(9876543.asInstanceOf[IntThousands]), "9,876,543")
    assertEquals(summon[CellFormat[LongThousands]].format(9876543210L.asInstanceOf[LongThousands]), "9,876,543,210")
  }

  test("the worked example in displayingTables.md renders as documented") {
    val csv = Seq((product = "widget", price = 1234.5678, margin = 0.0425), (product = "gizmo", price = 99.5, margin = 0.113))
    val out = csv
      .formatColumn["price", Currency["$", 2]]
      .formatColumn["margin", Percent[2]]
      .consoleFormatNt
    assert(out.contains("$1,234.57"), out)
    assert(out.contains("$99.50"), out)
    assert(out.contains("4.25%"), out)
    assert(out.contains("11.30%"), out)
  }

end CellFormatSuite
