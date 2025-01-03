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

  test("showable") {

    val nt = Seq(
      (col1 = "a", col2 = 1, col3 =  2.0),
      (col1 = "b", col2 =  2,col3 =  3.0),
      (col1 = "c", col2 =  3,col3 =  4.0)
    )

    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>col1</th><th>col2</th><th>col3</th></tr></thead><tbody><tr><td>a</td><td>1</td><td>2.0</td></tr><tr><td>b</td><td>2</td><td>3.0</td></tr><tr><td>c</td><td>3</td><td>4.0</td></tr></tbody></table>""",
      scautable.nt(nt).toString()
    )
  }

  test("csv from resource compiles and typechecks") {
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 +"simple.csv")

    val titanic: CsvIterator[("PassengerId", "Survived", "Pclass", "Name", "Sex", "Age", "SibSp", "Parch", "Ticket", "Fare", "Cabin", "Embarked")] = CSV.absolutePath(Generated.resourceDir0 + "titanic.csv")
    // val wide = CSV.absolutePath(Generated.resourceDir0 + "wide.csv")
  }

  test("column safety") {
    def csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    assert(
      !compileErrors("csv.column[\"notcol\"]").isEmpty()
    )
  }


  test("column") {
    def csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    val column2 = csv.column["col2"]
    val col2 = column2.toArray
    assertEquals(col2.head, "2")
    assertEquals(col2.tail.head, "4")
    assertEquals(col2.last, "6")
  }

  test("drop column") {
    def csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    def csv2: Iterator[(col1 : String, col2 : String, col3 : String)] = csv.take(3)

    val dropped = csv.dropColumn["col2"]
    val out = dropped.toArray
    assertEquals(out.head, ("1", "7"))
    assertEquals(out.tail.head, ("3", "8"))
    assertEquals(out.last, ("5", "9"))

    val dropFirst = csv2.dropColumn["col1"]
    val out2 = dropFirst.toArray
    assertEquals(out2.head, ("2", "7"))
    assertEquals(out2.tail.head, ("4", "8"))
    assertEquals(out2.last, ("6", "9"))

  }

  test("Drop column, mapColumn, then select another") {
    def csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    def dropped = csv.dropColumn["col2"].mapColumn["col3", Int](_.toInt)

    val out = dropped.column["col3"].toArray
    assertEquals(out.head, 7)
    assertEquals(out.tail.head, 8)
    assertEquals(out.last, 9)

    val out2 = dropped.column["col1"].toArray
    assertEquals(out2.head, "1")
    assertEquals(out2.tail.head, "3")
    assertEquals(out2.last, "5")

  }

  test("wide load") {
    val wide22: CsvIterator[("Column1", "Column2", "Column3", "Column4", "Column5", "Column6", "Column7", "Column8", "Column9", "Column10", "Column11", "Column12", "Column13", "Column14", "Column15", "Column16", "Column17", "Column18", "Column19", "Column20", "Column21", "Column22")]= CSV.absolutePath(Generated.resourceDir0 + "wide22.csv")
    val wide23= CSV.absolutePath(Generated.resourceDir0 + "wide23.csv")
    val out: Array[String] = wide22.column["Column21"].toArray
    val out23: Array[String] = wide23.column["Column_21"].toArray

    assertEquals(out.mkString(","), "Data21_1,Data21_2,Data21_3,Data21_4,Data21_5,Data21_6,Data21_7,Data21_8,Data21_9,Data21_10")
    assertEquals(out23.mkString(","), "Data_21_Row1,Data_21_Row2,Data_21_Row3")

    // assertEquals(out.head, "Data_62_Row1")
    // assertEquals(out.tail.head, "Data_62_Row2")
    // assertEquals(out.tail.last, "Data_62_Row3")
  }

  test("reading data") {
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    assertEquals(csv.toArray.mkString(","), """(1,2,7),(3,4,8),(5,6,9)"""  )
  }

  test("add columns") {
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    val added: Iterator[(col2Times10: Int, col3Times3 : Int, col1 : String, col2 : String, col3 : String)]= csv
      .addColumn["col3Times3", Int](_.col3.toInt * 3)
      .addColumn["col2Times10", Int](_.col2.toInt * 10)


    val again = added.addColumn["col2Times10Times2", Int](_.col2Times10 * 2)

    val out = again.toArray
    val check = out.map(_.col2Times10)
    val check2 = out.map(_.col2Times10Times2)
    val check3 = out.map(_.col3Times3)
    // val outCheck = again.map(_.col2Times10Times2).toArray

    assertEquals(check.head, 20)
    assertEquals(check2.head, 40)
    assertEquals(check3.head, 21)
    assertEquals(check.tail.head, 40)
    assertEquals(check.last, 60)

  }

  test("rename column") {
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    val renamed: Iterator[(col1 : String, col2Renamed : String, col3 : String)]= csv.renameColumn["col2", "col2Renamed"]
    val out = renamed.toArray
    assertEquals(out.head.col2Renamed, "2")
    assertEquals(out.tail.head.col2Renamed, "4")
    assertEquals(out.last.col2Renamed, "6")
  }

  test("force column type") {
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    val renamed: Iterator[(col1 : String, col2Renamed : String, col3 : String)]= csv.renameColumn["col2", "col2Renamed"].forceColumnType["col2Renamed", String]
    val out = renamed.toArray
    assertEquals(out.head.col2Renamed, "2")
    assertEquals(out.tail.head.col2Renamed, "4")
    assertEquals(out.last.col2Renamed, "6")
  }

  test("map column") {
    def csv = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")
    def csvDrop1 = csv

    def mapCol2 = csv.mapColumn["col2", Int]((s: String) => s.toInt)
    val result = mapCol2.toArray
    assertEquals(result.toArray.head.col2, 2)
    assertEquals(result.toArray.tail.head.col2, 4)
    assertEquals(result.toArray.last.col2, 6)

    def getCol = mapCol2.column["col2"]
    val result2 = getCol.toArray
    assertEquals(result2.head, 2)
    assertEquals(result2.tail.head, 4)
    assertEquals(result2.last, 6)

  }

  test("console print") {
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")
    assertNoDiff(
      csv.toArray.consoleFormatNt(),
      """| |col1|col2|col3|
+-+----+----+----+
|0|   1|   2|   7|
|1|   3|   4|   8|
|2|   5|   6|   9|
+-+----+----+----+""".trim()
    )

    val titanic = CSV.absolutePath(Generated.resourceDir0 + "titanic.csv").toArray
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