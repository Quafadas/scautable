package io.github.quafadas.scautable

import java.time.LocalDate
import io.github.quafadas.table.*

import NamedTuple.*

import scala.compiletime.ops.int.S

class CSVSuite extends munit.FunSuite:

  test("type test") {
    def csv = CSV.absolutePath(Generated.resourceDir0 + "typeTest.csv")

    val tt = csv.headers.zip(csv.numericTypeTest._1)
    assert(tt.length == csv.headers.length)

    assert(tt.head._2 == ConversionAcc(3, 3, 3)) // All ints are valid double and long
    assert(tt(1)._2 == ConversionAcc(0, 3, 0))
    assert(tt(2)._2 == ConversionAcc(0, 3, 3))
    assert(tt(3)._2 == ConversionAcc(1, 1, 1))
    assert(tt.last._2 == ConversionAcc(0, 0, 0))

    assertNoDiff(
      csv.formatTypeTest,
      """| |conversion % to|   col1|   col2|   col3|  col4|  col5|
+-+---------------+-------+-------+-------+------+------+
|0|            int|100.00%|  0.00%|  0.00%|33.33%| 0.00%|
|1|        doubles|100.00%|100.00%|100.00%|33.33%| 0.00%|
|2|           long|100.00%|  0.00%|100.00%|33.33%| 0.00%|
|3| recommendation|    Int| Double|   Long|String|String|
+-+---------------+-------+-------+-------+------+------+"""
    )
  }

  test("csv has duplicate headers") {
    def csvD: CsvIterator[("col1", "col1", "col1", "col2", "col3", "col1")] = CSV.absolutePath(Generated.resourceDir0 + "dups.csv")

    // If the next two lines compile, this is a pretty good indicator that we've deduplicated the headers
    def ded1: CsvIterator[("col1", "col1_1", "col1_2", "col2", "col3", "col1_5")] = csvD.deduplicateHeaders
    val argy = ded1.drop(1).next().col1_5
    assert(argy == "5")

    // These lines check that the macro can be called with named arguments
    def noDedup: CsvIterator[("col1", "col1", "col1", "col2", "col3", "col1")] = CSV.absolutePath(Generated.resourceDir0 + "dups.csv", false)
    def atCallsite: CsvIterator[("col1", "col1_1", "col1_2", "col2", "col3", "col1_5")] = CSV.absolutePath(Generated.resourceDir0 + "dups.csv", true)
    def namedArg: CsvIterator[("col1", "col1_1", "col1_2", "col2", "col3", "col1_5")] = CSV.absolutePath(Generated.resourceDir0 + "dups.csv", dedupHeaders = true)
    val argy2 = namedArg.drop(1).next().col1_5
    assert(argy2 == "5")
  }

  test("csv from resource compiles and typechecks") {
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    val titanic: CsvIterator[("PassengerId", "Survived", "Pclass", "Name", "Sex", "Age", "SibSp", "Parch", "Ticket", "Fare", "Cabin", "Embarked")] =
      CSV.absolutePath(Generated.resourceDir0 + "titanic.csv")
    // val wide = CSV.absolutePath(Generated.resourceDir0 + "wide.csv")
  }

  test("column safety") {
    def csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    assert(
      !compileErrors("csv.column[\"notcol\"]").isEmpty()
    )

    assert(
      compileErrors("""csv.toSeq.column["notcol1"]""").contains("""Column ("notcol1" : String) not found""")
    )
    assert(
      compileErrors("""csv.column["notcol1"]""").contains("""Column ("notcol1" : String) not found""")
    )
    assert(
      compileErrors("""csv.dropColumn["notcol1"]""").contains("""Column ("notcol1" : String) not found""")
    )
    assert(
      compileErrors("""csv.toSeq.dropColumn["notcol1"]""").contains("""Column ("notcol1" : String) not found""")
    )
    assert(
      compileErrors("""csv.toSeq.mapColumn["notcol1", Int]""").contains("""Column ("notcol1" : String) not found""")
    )
    assert(
      compileErrors("""csv.columns[("col1", "notCol")]""").contains("""Not all columns in (("col1" : String), ("notCol" : String))""")
    )

  }

  test("sample") {
    def csv = CSV.absolutePath(Generated.resourceDir0 + "titanic.csv")

    val sample = csv.sample(0.1)
    assertEqualsDouble(sample.length.toDouble, 89, 30)

    val sampleRand = csv.sample(0.1, true)
    assertEqualsDouble(sampleRand.length.toDouble, 89, 1)

    assertEquals(csv.sample(1).length, 891)

  }

  test("columns") {
    def csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    assert(
      !compileErrors("csv.columns[(\"notcol\")]").isEmpty()
    )

    def cols = csv.mapColumn["col1", Int](_.toInt)

    def selectCols: Iterator[(col1: Int, col3: String)] = cols.columns[("col1", "col3")]

    assert(cols.toArray.head.col1 == 1)
    assert(cols.toArray.head.col3 == "7")
    assert(cols.toArray.last.col1 == 5)
    assert(cols.toArray.last.col3 == "9")

    def numerics: Iterator[(col1: Int)] = cols.numericCols
    def nonnumerics: Iterator[(col2: String, col3: String)] = cols.nonNumericCols

  }

  test("numeric and non numeric cols") {
    enum Gender:
      case Male, Female
    end Gender

    def titanic = CSV.absolutePath(Generated.resourceDir0 + "titanic.csv")
    def data = titanic
      .mapColumn["Sex", Gender]((x: String) => Gender.valueOf(x.capitalize))
      .dropColumn["PassengerId"]
      .mapColumn["Age", Option[Double]](_.toDoubleOption)
      .mapColumn["Survived", Boolean](_ == "1")
      .mapColumn["Pclass", Int](_.toInt)
      .mapColumn["SibSp", Int](_.toInt)
      .mapColumn["Parch", Int](_.toInt)
      .mapColumn["Fare", Double](_.toDouble)
      .toList

    // val k: ("Pclass", "Age", "SibSp", "Parch", "Fare") = data.resolve[("Fare", "Pclass", "Age", "SibSp", "Parch")]
    // val kT: (Int, Option[Double], Int, Int, Double) = data.resolveT[("Fare", "Pclass", "Age", "SibSp", "Parch")]
    // val kNT: (Pclass: Int, Age: Option[Double], SibSp: Int, Parch: Int, Fare: Double) = data.resolveNT[("Fare", "Pclass", "Age", "SibSp", "Parch")]
    // println(Array(kNT).consoleFormatNt)

    val numericol: Seq[(Pclass: Int, Age: Option[Double], SibSp: Int, Parch: Int, Fare: Double)] = data.numericCols

    val nonNumericCols: Seq[(Survived: Boolean, Name: String, Sex: Gender, Ticket: String, Cabin: String, Embarked: String)] = data.nonNumericCols

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

    def csv2: Iterator[(col1: String, col2: String, col3: String)] = csv.take(3)

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

  test("easy print") {
    def csv = CSV.absolutePath(Generated.resourceDir0 + "simple.csv").toVector
    csv.ptbln

    val seq2 = Vector((1, 2), (3, 4))
    seq2.ptbl

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
    val wide22: CsvIterator[
      (
          "Column1",
          "Column2",
          "Column3",
          "Column4",
          "Column5",
          "Column6",
          "Column7",
          "Column8",
          "Column9",
          "Column10",
          "Column11",
          "Column12",
          "Column13",
          "Column14",
          "Column15",
          "Column16",
          "Column17",
          "Column18",
          "Column19",
          "Column20",
          "Column21",
          "Column22"
      )
    ] = CSV.absolutePath(Generated.resourceDir0 + "wide22.csv")
    val wide23 = CSV.absolutePath(Generated.resourceDir0 + "wide23.csv")
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

    assertEquals(csv.toArray.mkString(","), """(1,2,7),(3,4,8),(5,6,9)""")
  }

  test("add columns") {
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    val added = csv
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

  test("schema gen") {
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")
    val schema = csv.schemaGen
    assertNoDiff(
      schema,
      """object CsvSchema:
  type col1 = "col1"
  type col2 = "col2"
  type col3 = "col3"

import CsvSchema.*"""
    )
    object CsvSchema:
      type col1 = "col1"
      type col2 = "col2"
      type col3 = "col3"
    end CsvSchema

    import CsvSchema.*

    csv.column[col1].toArray // This should compile _and_ you get type completion
  }

  test("rename column") {
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    val renamed: Iterator[(col1: String, col2Renamed: String, col3: String)] = csv.renameColumn["col2", "col2Renamed"]
    val out = renamed.toArray
    assertEquals(out.head.col2Renamed, "2")
    assertEquals(out.tail.head.col2Renamed, "4")
    assertEquals(out.last.col2Renamed, "6")
  }

  test("force column type") {
    val csv: CsvIterator[("col1", "col2", "col3")] = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    val renamed: Iterator[(col1: String, col2Renamed: String, col3: String)] = csv.renameColumn["col2", "col2Renamed"].forceColumnType["col2Renamed", String]
    val out = renamed.toArray
    assertEquals(out.head.col2Renamed, "2")
    assertEquals(out.tail.head.col2Renamed, "4")
    assertEquals(out.last.col2Renamed, "6")
  }

  test("map column") {
    def csv = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

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

  test("compose column operations 1") {

    /** If this compiles, then hopefully we have borked the typelevel bookkeeping
      */

    def csv = CSV.absolutePath(Generated.resourceDir0 + "simple.csv")

    def composed = csv
      .mapColumn["col2", Int]((s: String) => s.toInt)
      .addColumn["col2Times10", Int](_.col2 * 10)
      .dropColumn["col1"]
      .renameColumn["col3", "col3_renamed"]
      .mapColumn["col3_renamed", Int]((s: String) => s.toInt)
      .addColumn["argy", Double](_.col2Times10 * 2.0)
      .dropColumn["col2Times10"]
    val result = composed.toArray.map(_.col3_renamed)
    val result2 = composed.toArray.map(_.argy)

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

    // csv.filter{ x =>
    //   println(x.col1)
    //   ???
    // }.toVector
  }

  test("missing values") {
    def csv = CSV.absolutePath(Generated.resourceDir0 + "missing.csv")

    val missing = csv.toList

    assertEquals(missing.length, 4)
    for r <- missing do assertEquals(r.toTuple.productArity, 3)
    end for

    val check = csv.filterNot(_.col2.isEmpty).toVector

  }

  test("missing values wide") {
    def csv = CSV.absolutePath(Generated.resourceDir0 + "missing_wide.csv")

    val missing = csv.toList

    assertEquals(missing.length, 4)
    for r <- missing do assertEquals(r.toTuple.productArity, 22)
    end for

    val check = csv.filterNot(_.col18.isEmpty).toVector

    assertEquals(check.length, 2)

  }

  // test("url") {
  //   val csv = CSV.url("https://raw.githubusercontent.com/datasciencedojo/datasets/refs/heads/master/titanic.csv")
  // }
end CSVSuite
