package io.github.quafadas.scautable

import io.github.quafadas.table.*

class CollectionGenericExtensionsSuite extends munit.FunSuite:

  test("column extraction preserves collection type"):
    val seqData = Seq(
      (name = "Alice", age = 25),
      (name = "Bob", age = 30)
    )
    val listData = List(
      (name = "Alice", age = 25),
      (name = "Bob", age = 30)
    )
    val lazyListData = LazyList(
      (name = "Alice", age = 25),
      (name = "Bob", age = 30)
    )

    val seqNames: Seq[String] = seqData.column["name"]
    val listNames: List[String] = listData.column["name"]
    val lazyListNames: LazyList[String] = lazyListData.column["name"]

    // Verify the values are correct
    assertEquals(seqNames.toList, List("Alice", "Bob"))
    assertEquals(listNames, List("Alice", "Bob"))
    assertEquals(lazyListNames.toList, List("Alice", "Bob"))

    // Verify types are preserved (runtime checks)
    assert(seqNames.isInstanceOf[Seq[String]])
    assert(listNames.isInstanceOf[List[String]])
    assert(lazyListNames.isInstanceOf[LazyList[String]])

  test("dropColumn preserves collection type"):
    val seqData = Seq(
      (name = "Alice", age = 25, score = 95.0),
      (name = "Bob", age = 30, score = 87.0)
    )
    val listData = List(
      (name = "Alice", age = 25, score = 95.0),
      (name = "Bob", age = 30, score = 87.0)
    )

    val seqWithoutAge = seqData.dropColumn["age"]
    val listWithoutAge = listData.dropColumn["age"]

    // Verify structure and content
    assertEquals(seqWithoutAge.toList.map(_.name), List("Alice", "Bob"))
    assertEquals(listWithoutAge.map(_.name), List("Alice", "Bob"))
    assertEquals(seqWithoutAge.toList.map(_.score), List(95.0, 87.0))
    assertEquals(listWithoutAge.map(_.score), List(95.0, 87.0))

    // Verify types are preserved
    assert(seqWithoutAge.isInstanceOf[Seq[?]])
    assert(listWithoutAge.isInstanceOf[List[?]])

  test("columns selection preserves collection type"):
    val seqData = Seq(
      (name = "Alice", age = 25, score = 95.0),
      (name = "Bob", age = 30, score = 87.0)
    )
    val listData = List(
      (name = "Alice", age = 25, score = 95.0),
      (name = "Bob", age = 30, score = 87.0)
    )

    val seqSubset = seqData.columns[("name", "score")]
    val listSubset = listData.columns[("name", "score")]

    // Verify structure and content
    assertEquals(seqSubset.toList.map(_.name), List("Alice", "Bob"))
    assertEquals(listSubset.map(_.name), List("Alice", "Bob"))
    assertEquals(seqSubset.toList.map(_.score), List(95.0, 87.0))
    assertEquals(listSubset.map(_.score), List(95.0, 87.0))

    // Verify types are preserved
    assert(seqSubset.isInstanceOf[Seq[?]])
    assert(listSubset.isInstanceOf[List[?]])

  test("LazyList preserves laziness"):
    // Create an infinite LazyList
    val infiniteLazyList = LazyList.from(1).map(i => (id = i, value = s"item$i"))

    // Extract a column - should still be lazy
    val values = infiniteLazyList.column["value"]

    // Verify it's still a LazyList
    assert(values.isInstanceOf[LazyList[String]])

    // Take only first 3 values to avoid infinite computation
    val first3 = values.take(3).toList
    assertEquals(first3, List("item1", "item2", "item3"))

  test("transposeColumns basic functionality"):
    val data = List(
      (name = "Alice", age = 25, score = 95.0),
      (name = "Bob", age = 30, score = 87.0),
      (name = "Charlie", age = 35, score = 92.0)
    )

    val transposed = data.toColumnOriented

    // Verify the transposed structure has the right column names
    assertEquals(transposed.name, List("Alice", "Bob", "Charlie"))
    assertEquals(transposed.age, List(25, 30, 35))
    assertEquals(transposed.score, List(95.0, 87.0, 92.0))

  test("transposeColumns preserves collection type"):
    val seqData = Seq(
      (x = 1, y = 2),
      (x = 3, y = 4)
    )
    val listData = List(
      (x = 1, y = 2),
      (x = 3, y = 4)
    )

    val seqTransposed = seqData.toColumnOriented
    val listTransposed = listData.toColumnOriented

    // Verify content
    assertEquals(seqTransposed.x.toList, List(1, 3))
    assertEquals(seqTransposed.y.toList, List(2, 4))
    assertEquals(listTransposed.x, List(1, 3))
    assertEquals(listTransposed.y, List(2, 4))

    // Verify collection types are preserved
    assert(seqTransposed.x.isInstanceOf[Seq[Int]], "seq x column should be Seq[Int]")
    assert(seqTransposed.y.isInstanceOf[Seq[Int]], "seq y column should be Seq[Int]")
    assert(listTransposed.x.isInstanceOf[List[Int]], "list x column should be List[Int]")
    assert(listTransposed.y.isInstanceOf[List[Int]], "list y column should be List[Int]")

  test("transposeColumns with mixed types"):
    val data = List(
      (id = 1, name = "Alice", active = true),
      (id = 2, name = "Bob", active = false),
      (id = 3, name = "Charlie", active = true)
    )

    val transposed = data.toColumnOriented

    assertEquals(transposed.id, List(1, 2, 3))
    assertEquals(transposed.name, List("Alice", "Bob", "Charlie"))
    assertEquals(transposed.active, List(true, false, true))

  test("transposeColumns with single row"):
    val data = List(
      (a = "hello", b = 42, c = 3.14)
    )

    val transposed = data.toColumnOriented

    assertEquals(transposed.a, List("hello"))
    assertEquals(transposed.b, List(42))
    assertEquals(transposed.c, List(3.14))

  test("transposeColumns with empty collection"):
    val data: List[(name: String, age: Int)] = List.empty

    val transposed = data.toColumnOriented

    assertEquals(transposed.name, List.empty)
    assertEquals(transposed.age, List.empty)

  test("toColumnOrientedAs with target collection type"):
    val data = List(
      (name = "Alice", age = 25, score = 95.0),
      (name = "Bob", age = 30, score = 87.0),
      (name = "Charlie", age = 35, score = 92.0)
    )

    // Transpose to Array
    val transposedToArray = data.toColumnOrientedAs[Array]
    assertEquals(transposedToArray.name.toList, List("Alice", "Bob", "Charlie"))
    assertEquals(transposedToArray.age.toList, List(25, 30, 35))
    assertEquals(transposedToArray.score.toList, List(95.0, 87.0, 92.0))

    // Verify it's actually an Array
    assert(transposedToArray.name.isInstanceOf[Array[String]], "name column should be Array[String]")
    assert(transposedToArray.age.isInstanceOf[Array[Int]], "age column should be Array[Int]")
    assert(transposedToArray.score.isInstanceOf[Array[Double]], "score column should be Array[Double]")

    // Transpose to Vector
    val transposedToVector = data.toColumnOrientedAs[Vector]
    assertEquals(transposedToVector.name, Vector("Alice", "Bob", "Charlie"))
    assertEquals(transposedToVector.age, Vector(25, 30, 35))
    assertEquals(transposedToVector.score, Vector(95.0, 87.0, 92.0))

    // Verify it's actually a Vector
    assert(transposedToVector.name.isInstanceOf[Vector[String]], "name column should be Vector[String]")
    assert(transposedToVector.age.isInstanceOf[Vector[Int]], "age column should be Vector[Int]")
    assert(transposedToVector.score.isInstanceOf[Vector[Double]], "score column should be Vector[Double]")

  test("toColumnOrientedAs with target collection type for empty data"):
    val data: List[(name: String, age: Int)] = List.empty

    val transposedToArray = data.toColumnOrientedAs[Array]
    val transposedToVector = data.toColumnOrientedAs[Vector]

    assertEquals(transposedToArray.name.length, 0)
    assertEquals(transposedToArray.age.length, 0)
    assertEquals(transposedToVector.name.size, 0)
    assertEquals(transposedToVector.age.size, 0)

    // Verify types
    assert(transposedToArray.name.isInstanceOf[Array[String]], "empty name column should be Array[String]")
    assert(transposedToArray.age.isInstanceOf[Array[Int]], "empty age column should be Array[Int]")
    assert(transposedToVector.name.isInstanceOf[Vector[String]], "empty name column should be Vector[String]")
    assert(transposedToVector.age.isInstanceOf[Vector[Int]], "empty age column should be Vector[Int]")

  test("toColumnOrientedAs target type with mixed data types"):
    val data = Seq(
      (id = 1, name = "Alice", active = true),
      (id = 2, name = "Bob", active = false)
    )

    val transposedToArray = data.toColumnOrientedAs[Array]

    assertEquals(transposedToArray.id.toList, List(1, 2))
    assertEquals(transposedToArray.name.toList, List("Alice", "Bob"))
    assertEquals(transposedToArray.active.toList, List(true, false))

    // Verify all columns have the target type
    assert(transposedToArray.id.isInstanceOf[Array[Int]], "id column should be Array[Int]")
    assert(transposedToArray.name.isInstanceOf[Array[String]], "name column should be Array[String]")
    assert(transposedToArray.active.isInstanceOf[Array[Boolean]], "active column should be Array[Boolean]")

  test("toColumnOrientedAs example usage"):
    // Example showing different target collection types
    val data = List(
      (name = "Alice", age = 25),
      (name = "Bob", age = 30)
    )

    // Using the original method (preserves original collection type)
    val transposedList = data.toColumnOriented
    assertEquals(transposedList.name, List("Alice", "Bob"))
    assert(transposedList.name.isInstanceOf[List[String]], "should preserve List type")

    // Using the new method to convert to Array
    val transposedArray = data.toColumnOrientedAs[Array]
    assertEquals(transposedArray.name.toList, List("Alice", "Bob"))
    assert(transposedArray.name.isInstanceOf[Array[String]], "should convert to Array")

    // Using the new method to convert to Vector
    val transposedVector = data.toColumnOrientedAs[Vector]
    assertEquals(transposedVector.name, Vector("Alice", "Bob"))
    assert(transposedVector.name.isInstanceOf[Vector[String]], "should convert to Vector")


end CollectionGenericExtensionsSuite
