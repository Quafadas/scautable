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

end CollectionGenericExtensionsSuite
