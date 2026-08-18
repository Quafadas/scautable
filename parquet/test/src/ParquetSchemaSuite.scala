package io.github.quafadas.scautable.parquet

import io.github.quafadas.table.ReadAs

import scala.NamedTuple.NamedTuple

class ParquetSchemaSuite extends munit.FunSuite:

  // The titanic.parquet resource was written by DuckDB. Every column is `optional`,
  // and the integer columns are int64 — so the inferred shape is Option-wrapped Longs.
  type TitanicNames = (
      "PassengerId",
      "Survived",
      "Pclass",
      "Name",
      "Sex",
      "Age",
      "SibSp",
      "Parch",
      "Ticket",
      "Fare",
      "Cabin",
      "Embarked"
  )

  type TitanicTypes = (
      Option[Long],
      Option[Long],
      Option[Long],
      Option[String],
      Option[String],
      Option[Double],
      Option[Long],
      Option[Long],
      Option[String],
      Option[Double],
      Option[String],
      Option[String]
  )

  test("the schema is inferred at compile time from the parquet footer"):
    // If the macro inferred anything else, this ascription fails to compile.
    val titanic: ParquetIterator[TitanicNames, TitanicTypes] = Parquet.resource("titanic.parquet")

    assertEquals(
      titanic.headers,
      Seq("PassengerId", "Survived", "Pclass", "Name", "Sex", "Age", "SibSp", "Parch", "Ticket", "Fare", "Cabin", "Embarked")
    )

  test("column names are singleton literal types, so `.column` resolves at compile time"):
    val titanic = Parquet.resource("titanic.parquet")
    val first = titanic.next()

    assertEquals(first.PassengerId, Some(1L))
    assertEquals(first.Name, Some("Braund, Mr. Owen Harris"))
    assertEquals(first.Sex, Some("male"))
    assertEquals(first.Age, Some(22.0))
    assertEquals(first.Survived, Some(0L))
    assertEquals(first.Cabin, None)

    titanic.close()

  test("rows are read out of every row group"):
    val rows = Parquet.resource("titanic.parquet").toVector

    assertEquals(rows.size, 891)
    assertEquals(rows.last.Name, Some("Dooley, Mr. Patrick"))
    assertEquals(rows.count(_.Survived.contains(1L)), 342)
    // 177 passengers have no recorded age — `optional` in parquet becomes `None`.
    assertEquals(rows.count(_.Age.isEmpty), 177)

  test("the iterator is single use and closes its file handle"):
    val titanic = Parquet.resource("titanic.parquet")
    assertEquals(titanic.toVector.size, 891)
    assert(!titanic.hasNext)
    intercept[NoSuchElementException](titanic.next())

  test("a missing resource is a compile-time error"):
    val err = compileErrors("""Parquet.resource("nope.parquet")""")
    assert(err.contains("nope.parquet"), s"unexpected error message: $err")

  test("ReadAs.Columns hands back one typed array per column"):
    // Parquet is stored column major, so this is the shape the file is already in - each
    // column chunk is decoded straight into its final array, with no row stitching.
    val titanic: NamedTuple[TitanicNames, Tuple.Map[TitanicTypes, Array]] =
      Parquet.resource("titanic.parquet", ReadAs.Columns)

    assertEquals(titanic.PassengerId.length, 891)
    assertEquals(titanic.Name.length, 891)

    assertEquals(titanic.PassengerId(0), Some(1L))
    assertEquals(titanic.Name(0), Some("Braund, Mr. Owen Harris"))
    assertEquals(titanic.Cabin(0), None)
    assertEquals(titanic.Name(890), Some("Dooley, Mr. Patrick"))

  test("the columnar read agrees with the row oriented read"):
    val byRow = Parquet.resource("titanic.parquet").toVector
    val byColumn = Parquet.resource("titanic.parquet", ReadAs.Columns)

    assertEquals(byColumn.Survived.count(_.contains(1L)), byRow.count(_.Survived.contains(1L)))
    assertEquals(byColumn.Age.count(_.isEmpty), byRow.count(_.Age.isEmpty))
    assertEquals(byColumn.Fare.toSeq, byRow.map(_.Fare))
    assertEquals(byColumn.Embarked.toSeq, byRow.map(_.Embarked))

  test("columns are cheap to aggregate over"):
    val titanic = Parquet.resource("titanic.parquet", ReadAs.Columns)
    val ages = titanic.Age.flatten

    assertEquals(ages.length, 891 - 177)
    assertEqualsDouble(ages.sum / ages.length, 29.699, 0.001)

end ParquetSchemaSuite
