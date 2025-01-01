package io.github.quafadas.scautable


import scalatags.Text.all.*
import java.time.LocalDate

import scala.annotation.experimental
import NamedTuple.*
import CSV.*
import scala.compiletime.ops.int.S

@experimental
class CSVSuite extends munit.FunSuite:

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

  test("csv from resource compiles and typechecks") {
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 +"simple.csv")

    val titanic: CsvIterator[("PassengerId", "Survived", "Pclass", "Name", "Sex", "Age", "SibSp", "Parch", "Ticket", "Fare", "Cabin", "Embarked")] = CSV.absolutePath(Generated.resourceDir0 + "titanic.csv")
    // val wide = CSV.absolutePath(Generated.resourceDir0 + "wide.csv")
  }


  test("column") {
    def csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    val column2 = csv.drop(1).column["col2"]
    val col2 = column2.toArray
    assertEquals(col2.head, "2")
    assertEquals(col2.tail.head, "4")
    assertEquals(col2.last, "6")

    val col2double = csv.drop(1).column["col2"]
    // assertEquals(col2double.head, 2.0)
    // assertEquals(col2double.tail.head, 4.0)
    // assertEquals(col2double.last, 6.0)


    assert(
      !compileErrors("csv.column[\"notcol\"]").isEmpty()
    )
  }

  test("drop column") {
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    val dropped: Iterator[("col1", "col3")] = csv.drop(1).dropColumn["col2"]
    val out = dropped.toArray
    println(out.mkString(","))
    assertEquals(out.head, ("2", "7"))
    assertEquals(out.tail.head, ("4", "8"))
    assertEquals(out.last, ("6", "9"))
  }

  test("wide load") {
    val wide22: Iterator[(Column1 : "Column1", Column2 : "Column2", Column3 : "Column3", Column4 : "Column4", Column5 : "Column5", Column6 : "Column6", Column7 : "Column7", Column8 : "Column8", Column9 : "Column9", Column10 : "Column10", Column11 : "Column11", Column12 : "Column12", Column13 : "Column13", Column14 : "Column14", Column15 : "Column15", Column16 : "Column16", Column17 : "Column17", Column18 : "Column18", Column19 : "Column19", Column20 : "Column20", Column21 : "Column21", Column22 : "Column22")] = CSV.absolutePath(Generated.resourceDir0 + "wide22.csv").drop(1)
    val wide23= CSV.absolutePath(Generated.resourceDir0 + "wide23.csv").drop(1)
    val out = wide22.column["Column21"].toArray
    val out23 = wide23.column["Column_21"].toArray

    assertEquals(out.mkString(","), "Data21_1,Data21_2,Data21_3,Data21_4,Data21_5,Data21_6,Data21_7,Data21_8,Data21_9,Data21_10")
    assertEquals(out23.mkString(","), "Data_21_Row1,Data_21_Row2,Data_21_Row3")

    // assertEquals(out.head, "Data_62_Row1")
    // assertEquals(out.tail.head, "Data_62_Row2")
    // assertEquals(out.tail.last, "Data_62_Row3")
  }

  test("reading data") {
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    assertEquals(csv.drop(1).toArray.mkString(","), """(1,2,7),(3,4,8),(5,6,9)"""  )
  }


  test("map rows") {
    def csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    val column2 = csv.mapRows(x => x.col2.toInt).toArray

    assertEquals(column2.head, 2)
    assertEquals(column2.tail.head, 4)
    assertEquals(column2.last, 6)

    val columns2n3 = csv.mapRows(x => (x.col2.toInt, x.col3.toInt)).toArray

    assertEquals(columns2n3.head, (2, 7))
    assertEquals(columns2n3.tail.head, (4, 8))
    assertEquals(columns2n3.last, (6, 9))
  }

  test("add columns") {
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    val added: Iterator[(col2Times10: Int, col3Times3 : Int, col1 : String, col2 : String, col3 : String)]= csv.drop(1)
      .addColumn["col3Times3", Int](_.col3.toInt * 3)
      .addColumn["col2Times10", Int](_.col2.toInt * 10)


    val again = added.addColumn["col2Times10Times2", Int](_.col2Times10 * 2)

    val out = again.toArray
    println(out.mkString(","))
    val check = out.map(_.col2Times10)
    val check2 = out.map(_.col2Times10Times2)
    val check3 = out.map(_.col3Times3)
    // val outCheck = again.map(_.col2Times10Times2).toArray

    assertEquals(check.head, 20)
    assertEquals(check2.head, 40)
    assertEquals(check3.head, 21)
    assertEquals(check.tail.head, 40)
    assertEquals(check.last, 60)

    // val titanic = CSV.absolutePath(Generated.resourceDir0 + "titanic.csv").drop(1)
    // val data = titanic.addColumn["SurvivedB", Boolean](_.Survived == "1")

    // println(data.toArray.consolePrint())
  }

  test("rename column") {
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    val renamed: Iterator[(col1 : "col1", col2Renamed : "col2", col3 : "col3")]= csv.drop(1).renameColumn["col2", "col2Renamed"]
    val out = renamed.toArray
    assertEquals(out.head.col2Renamed, "2")
    assertEquals(out.tail.head.col2Renamed, "4")
    assertEquals(out.last.col2Renamed, "6")
  }

  test("map column".only) {
    def csv = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    def mapCol2 = csv.drop(1).mapColumn["col2", Int](_.toInt)

    // println(mapCol2.toArray.mkString(","))
    // val result = mapCol2.toArray
    // result.tapEach(println)
    // assertEquals(result.toArray.head.col2, 2)

  }


  test("parse types preserve rows and column names") {
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    val values = csv
      .drop(1)
      .map { x =>
        x.toTuple
          .copy(
            _1 = x.col1.toInt,
            _3 = x.col3.toDouble
          )
          .withNames[csv.COLUMNS]
      }
      .toArray

    assertEquals(values.head, (1, "2", 7.0))
    assertEquals(values.tail.head, (3, "4", 8.0))
    assertEquals(values.last, (5, "6", 9.0))

    assertEquals(values.head.col1, 1)
    assertEquals(values.head.col2, "2")
    assertEquals(values.head.col3, 7.0)
  }

  test("console print") {
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")
    assertNoDiff(
      csv.drop(1).toArray.consolePrint(),
      """| |col1|col2|col3|
+-+----+----+----+
|0|   1|   2|   7|
|1|   3|   4|   8|
|2|   5|   6|   9|
+-+----+----+----+""".trim()
    )

    val titanic = CSV.absolutePath(Generated.resourceDir0 + "titanic.csv").drop(1).toArray
    assert(titanic.length == 891)



  }

  test("header indexes") {
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")
    assertEquals(csv.headerIndex("col1"), 0)
    assertEquals(csv.headerIndex("col2"), 1)
    assertEquals(csv.headerIndex("col3"), 2)
  }

  // test("url") {
  //   val csv = CSV.url("https://raw.githubusercontent.com/datasciencedojo/datasets/refs/heads/master/titanic.csv")
  // }