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

  test("IdxAtName") {

    type Cols = "a" *: "b" *: "c" *: "d" *: EmptyTuple

    // Test finding indices of existing columns
    summon[ColumnTyped.IdxAtName["a", Cols] =:= 0]
    summon[ColumnTyped.IdxAtName["b", Cols] =:= 1] 
    summon[ColumnTyped.IdxAtName["c", Cols] =:= 2]
    summon[ColumnTyped.IdxAtName["d", Cols] =:= 3]

    // Test with single element tuple
    type SingleCol = "x" *: EmptyTuple
    summon[ColumnTyped.IdxAtName["x", SingleCol] =:= 0]

    // Test with longer column names
    type LongCols = "firstName" *: "lastName" *: "age" *: "email" *: EmptyTuple
    summon[ColumnTyped.IdxAtName["firstName", LongCols] =:= 0]
    summon[ColumnTyped.IdxAtName["lastName", LongCols] =:= 1]
    summon[ColumnTyped.IdxAtName["age", LongCols] =:= 2]
    summon[ColumnTyped.IdxAtName["email", LongCols] =:= 3]

    // Note: Empty tuple case now returns a ColumnNotFoundError instead of 0
    // This provides better compile-time safety by catching non-existent columns

  }

  test("IndexesAtNames") {

    type Cols = "a" *: "b" *: "c" *: "d" *: "e" *: EmptyTuple

    // Test getting indexes of multiple existing columns
    summon[ColumnTyped.IndexesAtNames["a" *: EmptyTuple, Cols] =:= 0 *: EmptyTuple]
    summon[ColumnTyped.IndexesAtNames["a" *: "c" *: EmptyTuple, Cols] =:= 0 *: 2 *: EmptyTuple]
    summon[ColumnTyped.IndexesAtNames["b" *: "d" *: EmptyTuple, Cols] =:= 1 *: 3 *: EmptyTuple]
    summon[ColumnTyped.IndexesAtNames["e" *: "a" *: "c" *: EmptyTuple, Cols] =:= 4 *: 0 *: 2 *: EmptyTuple]

    // Test with all columns in order
    summon[ColumnTyped.IndexesAtNames[Cols, Cols] =:= 0 *: 1 *: 2 *: 3 *: 4 *: EmptyTuple]

    // Test with columns in reverse order
    summon[ColumnTyped.IndexesAtNames["e" *: "d" *: "c" *: "b" *: "a" *: EmptyTuple, Cols] =:= 4 *: 3 *: 2 *: 1 *: 0 *: EmptyTuple]

    // Test with empty tuple of names
    summon[ColumnTyped.IndexesAtNames[EmptyTuple, Cols] =:= EmptyTuple]

    // Test with single name
    summon[ColumnTyped.IndexesAtNames["c" *: EmptyTuple, Cols] =:= 2 *: EmptyTuple]

    // Test with longer column names
    type LongCols = "firstName" *: "lastName" *: "age" *: "email" *: EmptyTuple
    summon[ColumnTyped.IndexesAtNames["lastName" *: "firstName" *: EmptyTuple, LongCols] =:= 1 *: 0 *: EmptyTuple]
    summon[ColumnTyped.IndexesAtNames["email" *: "age" *: "firstName" *: EmptyTuple, LongCols] =:= 3 *: 2 *: 0 *: EmptyTuple]

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
