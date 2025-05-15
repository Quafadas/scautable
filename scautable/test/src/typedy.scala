package io.github.quafadas.scautable

import scala.collection.immutable.Stream.Empty

class NamedTupleTypeTest extends munit.FunSuite:

  test("IsColumn") {

    type Cols = "a" *: "b" *: "c" *: EmptyTuple

    summon[ColumnTyped.IsColumn["a", Cols] =:= true]
    summon[ColumnTyped.IsColumn["f", Cols] =:= false]

  }

  test("Negate") {

    type Cols = true *: false *: true *: EmptyTuple

    summon[ColumnTyped.Negate[Cols] =:= false *: true *: false *: EmptyTuple]

  }

  test("Replace One Name") {

    type Cols = "a" *: "b" *: "c" *: EmptyTuple

    summon[ColumnTyped.ReplaceOneName[Cols, "a", "d"] =:= "d" *: "b" *: "c" *: EmptyTuple]

  }

  test("Replace One Type At Name") {

    type Cols = "a" *: "b" *: "c" *: EmptyTuple
    type Types = Int *: String *: Double *: EmptyTuple

    summon[ColumnTyped.ReplaceOneTypeAtName[Cols, "a", Types, Boolean] =:= Boolean *: String *: Double *: EmptyTuple]

  }

  test("Drop One Type At Name") {

    type Cols = "a" *: "b" *: "c" *: EmptyTuple
    type Types = Int *: String *: Double *: EmptyTuple

    summon[ColumnTyped.DropOneTypeAtName[Cols, "a", Types] =:= String *: Double *: EmptyTuple]

    summon[ColumnTyped.DropOneTypeAtName[Cols, "b", Types] =:= Int *: Double *: EmptyTuple]

    summon[ColumnTyped.DropOneTypeAtName[Cols, "c", Types] =:= Int *: String *: EmptyTuple]

  }

  test("Get Types At Names") {

    type Cols = "a" *: "b" *: "c" *: EmptyTuple
    type Types = Int *: String *: Double *: EmptyTuple

    summon[ColumnTyped.GetTypesAtNames[Cols, "a" *: EmptyTuple, Types] =:= Int *: EmptyTuple]

    summon[ColumnTyped.GetTypesAtNames[Cols, "a" *: "b" *: EmptyTuple, Types] =:= Int *: String *: EmptyTuple]

    summon[ColumnTyped.GetTypesAtNames[Cols, "a" *: "b" *: "c" *: EmptyTuple, Types] =:= Int *: String *: Double *: EmptyTuple]

  }

  test("Get Type At Name") {

    type Cols = "a" *: "b" *: "c" *: EmptyTuple
    type Types = Int *: String *: Double *: EmptyTuple

    summon[ColumnTyped.GetTypeAtName[Cols, "a", Types] =:= Int]

    summon[ColumnTyped.GetTypeAtName[Cols, "b", Types] =:= String]

    summon[ColumnTyped.GetTypeAtName[Cols, "c", Types] =:= Double]

  }

  test("Drop After Name") {

    type Cols = "a" *: "b" *: "c" *: EmptyTuple
    type Types = Int *: String *: Double *: EmptyTuple

    summon[ColumnTyped.DropAfterName[Cols, "a"] =:= EmptyTuple]

    summon[ColumnTyped.DropAfterName[Cols, "b"] =:= "a" *: EmptyTuple]

    summon[ColumnTyped.DropAfterName[Cols, "c"] =:= "a" *: "b" *: EmptyTuple]

  }

  test("Drop One Name") {

    type Cols = "a" *: "b" *: "c" *: EmptyTuple

    summon[ColumnTyped.DropOneName[Cols, "a"] =:= "b" *: "c" *: EmptyTuple]

    summon[ColumnTyped.DropOneName[Cols, "b"] =:= "a" *: "c" *: EmptyTuple]

    summon[ColumnTyped.DropOneName[Cols, "c"] =:= "a" *: "b" *: EmptyTuple]

  }

  test("IsMatch") {

    type Cols = "a" *: "b" *: "c" *: EmptyTuple

    summon[ColumnTyped.IsMatch["a", "a"] =:= true]
    summon[ColumnTyped.IsMatch["a", "b"] =:= false]
    summon[ColumnTyped.IsMatch["a", "c"] =:= false]

  }

  test("IsNumeric") {

    type Cols = "a" *: "b" *: "c" *: EmptyTuple
    type Types = Int *: String *: Double *: EmptyTuple

    summon[ColumnTyped.IsNumeric[Int] =:= true]
    summon[ColumnTyped.IsNumeric[String] =:= false]
    summon[ColumnTyped.IsNumeric[Double] =:= true]
    summon[ColumnTyped.IsNumeric[Option[Double]] =:= true]

  }

  test("Numeric Cols Index") {

    type Cols = "a" *: "b" *: "c" *: EmptyTuple
    type Types = Int *: String *: Double *: EmptyTuple
    type Types2 = Option[Double] *: Int *: String *: Double *: EmptyTuple
    type Types3 = Option[String] *: Int *: String *: Double *: String *: EmptyTuple

    summon[ColumnTyped.NumericColsIdx[Types] =:= true *: false *: true *: EmptyTuple]
    summon[ColumnTyped.NumericColsIdx[Types2] =:= true *: true *: false *: true *: EmptyTuple]
    summon[ColumnTyped.NumericColsIdx[Types3] =:= false *: true *: false *: true *: false *: EmptyTuple]

  }

  test("All are Columns") {

    type Cols = "a" *: "b" *: "c" *: "d" *: "e" *: EmptyTuple

    type t2 = "a" *: EmptyTuple
    type t3 = "f" *: EmptyTuple
    type t4 = "a" *: "b" *: "c" *: EmptyTuple
    type t5 = "a" *: "b" *: "f" *: "d" *: EmptyTuple

    summon[ColumnTyped.AllAreColumns[Cols, Cols] =:= true]
    summon[ColumnTyped.AllAreColumns[EmptyTuple, Cols] =:= true]
    summon[ColumnTyped.AllAreColumns[t2, Cols] =:= true]
    summon[ColumnTyped.AllAreColumns[t3, Cols] =:= false]
    summon[ColumnTyped.AllAreColumns[t4, Cols] =:= true]
    summon[ColumnTyped.AllAreColumns[t5, Cols] =:= false]

    type Cols2 = "col1" *: "col2" *: "col3" *: EmptyTuple

    summon[ColumnTyped.AllAreColumns["col1" *: "col3" *: EmptyTuple, Cols2] =:= true]

  }

end NamedTupleTypeTest
