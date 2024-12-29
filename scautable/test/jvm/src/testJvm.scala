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

    // val tupleExample: (col1: String, col2: String, col3: String) = (col1 = "1", col2 = "2", col3 = "three")
    // val tupleExample2: (col1: String, col2: String)              = NamedTuple.build[("col1", "col2")]()(("a", "b"))

    // val c = ("a", "b").withNames[("col1", "col2")]

    // val temp = tupleExample.col1.toInt
    // println(temp)

    // val itr = List(tupleExample2, tupleExample, c).iterator
    // // println(itr.map(_.col1).toArray.mkString(","))

    // println(tupleExample.col1)
    // println(tupleExample2.col2)
    // println(c.col1)

    val csv: CsvIterator[("col1", "col2")] = CSV.readCsvAsNamedTupleType("/Users//simon//Code//scautable//simple.csv")

    val newItr: CsvIterator[("col1", "col2")] = csv.copy()
    // println(csv.drop(1).toArray.mkString("\n"))
    // csv.toList.map(x => x.col1)
    println(csv.headers)
    println(csv.headerIndex("col2"))

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
    println(values.mkString("\n"))

    println(newItr.toArray.mkString("\n"))

    // println(values.map(_.col1.toInt).mkString(","))

    // println(Seq(csv).pt)
    // println(csv.col1)
    // println(csv)
    // val a = NamedTuple.build[csv.type]()[Tuple2[String, String]]("a", "b")
    // val a = NamedTuple.build[csv.TYPE]()[Tuple2[String, String]]("a", "b")

  }
end JVMSuite
