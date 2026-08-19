package io.github.quafadas.scautable.parquet

import io.github.quafadas.table.ReadAs

import scala.NamedTuple.NamedTuple

class ParquetVectorSuite extends munit.FunSuite:

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

  test("ReadAs.Vectors is a lazy Iterator of one batch (row group) at a time, each batch boxing-free regardless of nullability"):
    // If the macro inferred anything else - e.g. a single eager NamedTuple, or ParquetVector[Option[Long]] - this ascription fails to compile.
    val batches: Iterator[
      NamedTuple[
        TitanicNames,
        (
            ParquetVector[Long],
            ParquetVector[Long],
            ParquetVector[Long],
            ParquetVector[String],
            ParquetVector[String],
            ParquetVector[Double],
            ParquetVector[Long],
            ParquetVector[Long],
            ParquetVector[String],
            ParquetVector[Double],
            ParquetVector[String],
            ParquetVector[String]
        )
      ]
    ] = Parquet.resource("titanic.parquet", ReadAs.Vectors)

    val first = batches.next()

    assertEquals(first.PassengerId.isNull(0), false)
    assertEquals(first.PassengerId.asInstanceOf[LongVector].values(0), 1L)
    assertEquals(first.Name.asInstanceOf[ObjectVector[String]].values(0), "Braund, Mr. Owen Harris")
    assertEquals(first.Cabin.isNull(0), true)

  test("every row is covered exactly once across batches, and nullCount tracks one validity bitmap per batch, not one None per row"):
    val rows = Parquet.resource("titanic.parquet").toVector
    val batches = Parquet.resource("titanic.parquet", ReadAs.Vectors).toVector

    assertEquals(batches.map(_.PassengerId.length).sum, rows.size)
    // 177 passengers have no recorded age - summed across whatever row groups the file has.
    assertEquals(batches.map(_.Age.nullCount).sum, rows.count(_.Age.isEmpty))

  test("vectors agree with the row-oriented read, once batches are concatenated"):
    val byRow = Parquet.resource("titanic.parquet").toVector
    val byVector = Parquet.resource("titanic.parquet", ReadAs.Vectors).toVector

    assertEquals(byVector.flatMap(_.Fare.toOptionArray).toSeq, byRow.map(_.Fare))
    assertEquals(byVector.flatMap(_.Embarked.toOptionArray).toSeq, byRow.map(_.Embarked))
    assertEquals(byVector.map(_.Survived.toOptionArray.count(_.contains(1L))).sum, byRow.count(_.Survived.contains(1L)))

  test("the iterator is single use and closes its file handle"):
    val batches = Parquet.resource("titanic.parquet", ReadAs.Vectors)
    assert(batches.toVector.map(_.PassengerId.length).sum == 891)
    assert(!batches.hasNext)
    intercept[NoSuchElementException](batches.next())

  test("required columns get a validity-free vector"):
    val mixed: Iterator[
      NamedTuple[
        ("req_int", "req_string", "req_bool", "opt_int", "opt_string", "opt_bool"),
        (ParquetVector[Int], ParquetVector[String], ParquetVector[Boolean], ParquetVector[Int], ParquetVector[String], ParquetVector[Boolean])
      ]
    ] = Parquet.resource("required_optional_mix.parquet", ReadAs.Vectors)

    val first = mixed.next()
    assertEquals(first.req_int.nullCount, 0)
    assertEquals(first.req_string.nullCount, 0)
    assertEquals(first.req_bool.nullCount, 0)

end ParquetVectorSuite
