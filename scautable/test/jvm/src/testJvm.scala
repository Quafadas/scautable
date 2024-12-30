package io.github.quafadas.scautable

import scalatags.Text.all.*
import java.time.LocalDate

import scala.annotation.experimental
import NamedTuple.*
import CSV.*
import ColumnTypes.*

@experimental
class JVMSuite extends munit.FunSuite:

  import scautable.*
  import scautable.given

  test("extendable") {
    given dateT: HtmlTableRender[LocalDate] = new HtmlTableRender[LocalDate]:
      override def tableCell(a: LocalDate) = td(
        s"$a"
      )
    case class Customize(t: LocalDate, i: Int)
    val custom = Seq(Customize(LocalDate.of(2025, 1, 1), 1))
    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>t</th><th>i</th></tr></thead><tbody><tr><td>2025-01-01</td><td>1</td></tr></tbody></table>""",
      scautable(custom).toString()
    )
  }

  test("csv") {

    // TODO: Docs

    // TODO: Sources : URL, pwd, resource

    // TODO: Sample (graduated)

    // TODO : Desktop show

    val csv: CsvIterator[("col1", "col2")] = CSV.readCsvAsNamedTupleType("/Users//simon//Code//scautable//simple.csv")

    val col1 = csv.column(x => x.col1.toInt)

    println(col1.toArray.mkString(","))

    val values = csv
      .drop(1)
      .map { x =>
        x.toTuple
          .copy(_1 = x.col1 + "heeelllo", _2 = x.col2.toInt)
          .withNames[csv.COLUMNS]
      }
      .toArray

    println(values.consolePrint(csv.headers))

    values.consolePrint

    val newItr: CsvIterator[("col1", "col2")] = csv.copy()
    println(newItr.drop(1).toArray.consolePrint(csv.headers))
    desktopShowNt(values)
  }
end JVMSuite
