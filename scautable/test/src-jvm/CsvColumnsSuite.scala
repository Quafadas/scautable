package io.github.quafadas.scautable

import io.github.quafadas.table.*

class CsvColumnsSuite extends munit.FunSuite:

  test("simple.csv - loads column data correctly") {
    val cols = CSV.resource("simple.csv", CsvOpts(readAs = ReadAs.Columns))

    // Check column access - columns should be arrays
    val col1: Array[Int] = cols.col1
    val col2: Array[Int] = cols.col2
    val col3: Array[Int] = cols.col3

    // Verify data
    assertEquals(col1.toSeq, Seq(1, 3, 5))
    assertEquals(col2.toSeq, Seq(2, 4, 6))
    assertEquals(col3.toSeq, Seq(7, 8, 9))
  }

  test("simple.csv - StringType inference") {
    val cols = CSV.resource("simple.csv", CsvOpts(TypeInferrer.StringType, ReadAs.Columns))

    val col1: Array[String] = cols.col1
    val col2: Array[String] = cols.col2
    val col3: Array[String] = cols.col3

    assertEquals(col1.toSeq, Seq("1", "3", "5"))
    assertEquals(col2.toSeq, Seq("2", "4", "6"))
    assertEquals(col3.toSeq, Seq("7", "8", "9"))
  }

  test("simple.csv - all columns have same length") {
    val cols = CSV.resource("simple.csv", CsvOpts(readAs = ReadAs.Columns))

    assertEquals(cols.col1.length, cols.col2.length)
    assertEquals(cols.col2.length, cols.col3.length)
    assertEquals(cols.col1.length, 3)
  }

  test("fromString - basic functionality") {
    // Note: string must be inline literal for compile-time processing
    val cols = CSV.fromString(
      """name,age,score
Alice,30,95.5
Bob,25,87.3
Charlie,35,92.1""",
      CsvOpts(readAs = ReadAs.Columns)
    )

    val names: Array[String] = cols.name
    val ages: Array[Int] = cols.age
    val scores: Array[Double] = cols.score

    assertEquals(names.toSeq, Seq("Alice", "Bob", "Charlie"))
    assertEquals(ages.toSeq, Seq(30, 25, 35))
    assertEquals(scores.toSeq, Seq(95.5, 87.3, 92.1))
  }

  test("cereals.csv - loads all 77 rows correctly") {
    val cols = CSV.resource("cereals.csv", CsvOpts(readAs = ReadAs.Columns))

    // Check all columns exist and have correct length (77 cereals)
    assertEquals(cols.name.length, 77)
    assertEquals(cols.mfr.length, 77)
    assertEquals(cols.calories.length, 77)
    assertEquals(cols.protein.length, 77)
    assertEquals(cols.rating.length, 77)

    // Check first row values
    assertEquals(cols.name(0), "100% Bran")
    assertEquals(cols.mfr(0), "N")
    assertEquals(cols.calories(0), 70)
    assertEquals(cols.protein(0), 4)

    // Check last row values (Wheaties Honey Gold)
    assertEquals(cols.name(76), "Wheaties Honey Gold")
    assertEquals(cols.calories(76), 110)
  }

  test("cereals.csv - numeric columns are typed correctly") {
    val cols = CSV.resource("cereals.csv", CsvOpts(readAs = ReadAs.Columns))

    // These should be Int arrays
    val calories: Array[Int] = cols.calories
    val protein: Array[Int] = cols.protein
    val sodium: Array[Int] = cols.sodium
    val sugars: Array[Int] = cols.sugars

    // rating should be Double
    val rating: Array[Double] = cols.rating

    // fiber, carbo, weight, cups should be Double (they have decimal values)
    val fiber: Array[Double] = cols.fiber
    val carbo: Array[Double] = cols.carbo

    // Verify some values
    assertEquals(calories.sum, 8230) // sum of all calories
    assert(rating(0) > 68.0 && rating(0) < 69.0) // 68.402973
  }

  test("titanic.csv - loads all 891 rows correctly") {
    val cols = CSV.resource("titanic.csv", CsvOpts(readAs = ReadAs.Columns))

    // Check all columns exist and have correct length (891 passengers)
    assertEquals(cols.PassengerId.length, 891)
    assertEquals(cols.Survived.length, 891)
    assertEquals(cols.Pclass.length, 891)
    assertEquals(cols.Name.length, 891)
    assertEquals(cols.Sex.length, 891)
    assertEquals(cols.Fare.length, 891)

    // Check first passenger
    assertEquals(cols.PassengerId(0), 1)
    assertEquals(cols.Survived(0), false) // 0 = did not survive = false
    assertEquals(cols.Pclass(0), 3)
    assertEquals(cols.Sex(0), "male")

    // Check a known survivor (row 2, index 1)
    assertEquals(cols.PassengerId(1), 2)
    assertEquals(cols.Survived(1), true) // 1 = survived = true
    assertEquals(cols.Pclass(1), 1)
    assertEquals(cols.Sex(1), "female")
  }

  test("titanic.csv - handles missing values with Option types") {
    val cols = CSV.resource("titanic.csv", CsvOpts(readAs = ReadAs.Columns))

    // Age has missing values, should be Option[Double] or Option[Int]
    val ages: Array[Option[Double]] = cols.Age

    // First passenger has age 22
    assertEquals(ages(0), Some(22.0))

    // Passenger 6 (index 5) has missing age
    assertEquals(ages(5), None)

    // Cabin has many missing values
    val cabins: Array[Option[String]] = cols.Cabin

    // First passenger has no cabin
    assertEquals(cabins(0), None)

    // Second passenger has cabin C85
    assertEquals(cabins(1), Some("C85"))
  }

  test("titanic.csv - survival statistics") {
    val cols = CSV.resource("titanic.csv", CsvOpts(readAs = ReadAs.Columns))

    val survived: Array[Boolean] = cols.Survived
    val totalSurvivors = survived.count(_ == true)
    val totalDeaths = survived.count(_ == false)

    // Known Titanic statistics
    assertEquals(totalSurvivors, 342)
    assertEquals(totalDeaths, 549)
    assertEquals(totalSurvivors + totalDeaths, 891)
  }

  // Tests for the unified CSV API with ReadAs.Columns

  test("CSV.resource with ReadAs.Columns - simple.csv") {
    val cols = CSV.resource("simple.csv", CsvOpts(readAs = ReadAs.Columns))

    val col1: Array[Int] = cols.col1
    val col2: Array[Int] = cols.col2
    val col3: Array[Int] = cols.col3

    assertEquals(col1.toSeq, Seq(1, 3, 5))
    assertEquals(col2.toSeq, Seq(2, 4, 6))
    assertEquals(col3.toSeq, Seq(7, 8, 9))
  }

  test("CSV.resource with ReadAs.Columns and TypeInferrer") {
    val cols = CSV.resource("simple.csv", CsvOpts(TypeInferrer.StringType, ReadAs.Columns))

    val col1: Array[String] = cols.col1
    val col2: Array[String] = cols.col2
    val col3: Array[String] = cols.col3

    assertEquals(col1.toSeq, Seq("1", "3", "5"))
    assertEquals(col2.toSeq, Seq("2", "4", "6"))
    assertEquals(col3.toSeq, Seq("7", "8", "9"))
  }

  test("CSV.fromString with ReadAs.Columns") {
    val cols = CSV.fromString(
      """name,age,score
Alice,30,95.5
Bob,25,87.3""",
      CsvOpts(readAs = ReadAs.Columns)
    )

    val names: Array[String] = cols.name
    val ages: Array[Int] = cols.age
    val scores: Array[Double] = cols.score

    assertEquals(names.toSeq, Seq("Alice", "Bob"))
    assertEquals(ages.toSeq, Seq(30, 25))
    assertEquals(scores.toSeq, Seq(95.5, 87.3))
  }

  test("CSV.resource with ReadAs.Rows returns CsvIterator (default behavior)") {
    // This should work exactly as before
    val iter = CSV.resource("simple.csv")
    val rows = iter.toList

    assertEquals(rows.length, 3)
    assertEquals(rows.head.col1, 1)
    assertEquals(rows.head.col2, 2)
    assertEquals(rows.head.col3, 7)
  }

end CsvColumnsSuite
