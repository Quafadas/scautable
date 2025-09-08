package io.github.quafadas.scautable

import io.github.quafadas.table.*
import com.tdunning.math.stats.TDigest

class StatsSuite extends munit.FunSuite:

  test("summary should compute basic statistics for Integer data"):
    val data = List(
      (id = 1, value = 10),
      (id = 2, value = 20),
      (id = 3, value = 30),
      (id = 4, value = 40),
      (id = 5, value = 50)
    )

    val result = data.summary

    // Check that we have results for both columns
    assertEquals(result.length, 2)

    // Find the 'value' column statistics
    val valueStats = result.find(_.name == "value").get
    assertEquals(valueStats.typ, "Int")
    assertEquals(valueStats.mean, 30.0)
    assertEquals(valueStats.min, 10.0)
    assertEquals(valueStats.max, 50.0)
    assertEquals(valueStats.median, 30.0, 0.1) // TDigest is approximate

    // Find the 'id' column statistics
    val idStats = result.find(_.name == "id").get
    assertEquals(idStats.typ, "Int")
    assertEquals(idStats.mean, 3.0)
    assertEquals(idStats.min, 1.0)
    assertEquals(idStats.max, 5.0)

  test("summary should compute basic statistics for Double data"):
    val data = List(
      (name = "Alice", score = 95.5),
      (name = "Bob", score = 87.2),
      (name = "Charlie", score = 92.8),
      (name = "Diana", score = 89.1)
    )

    val result = data.summary

    // Find the 'score' column statistics (name column won't be processed as numeric)
    val scoreStats = result.find(_.name == "score").get
    assertEquals(scoreStats.typ, "Double")
    assertEquals(scoreStats.mean, 91.15, 0.01)
    assertEquals(scoreStats.min, 87.2, 0.01)
    assertEquals(scoreStats.max, 95.5, 0.01)

  test("summary should compute statistics for Long data"):
    val data = List(
      (id = 1L, population = 1000000L),
      (id = 2L, population = 2500000L),
      (id = 3L, population = 800000L),
      (id = 4L, population = 1500000L)
    )

    val result = data.summary

    val populationStats = result.find(_.name == "population").get
    assertEquals(populationStats.typ, "Long")
    assertEquals(populationStats.mean, 1450000.0, 0.1)
    assertEquals(populationStats.min, 800000.0, 0.1)
    assertEquals(populationStats.max, 2500000.0, 0.1)

  test("summary should compute percentiles correctly"):
    val data = List(
      (value = 1),
      (value = 2),
      (value = 3),
      (value = 4),
      (value = 5),
      (value = 6),
      (value = 7),
      (value = 8),
      (value = 9),
      (value = 10)
    )

    val result = data.summary
    val valueStats = result.find(_.name == "value").get

    // Check percentiles (allowing for TDigest approximation)
    assert(valueStats.`0.25` >= 2.0 && valueStats.`0.25` <= 4.0, s"25th percentile should be around 2.5-3.5, got ${valueStats.`0.25`}")
    assert(valueStats.median >= 4.5 && valueStats.median <= 6.5, s"Median should be around 5.5, got ${valueStats.median}")
    assert(valueStats.`0.75` >= 6.5 && valueStats.`0.75` <= 8.5, s"75th percentile should be around 7.5-8.5, got ${valueStats.`0.75`}")

  test("summary should handle mixed numeric types"):
    val data = List(
      (intVal = 10, doubleVal = 10.5, longVal = 100L),
      (intVal = 20, doubleVal = 20.5, longVal = 200L),
      (intVal = 30, doubleVal = 30.5, longVal = 300L)
    )

    val result = data.summary

    assertEquals(result.length, 3)

    val intStats = result.find(_.name == "intVal").get
    assertEquals(intStats.typ, "Int")
    assertEquals(intStats.mean, 20.0)

    val doubleStats = result.find(_.name == "doubleVal").get
    assertEquals(doubleStats.typ, "Double")
    assertEquals(doubleStats.mean, 20.5)

    val longStats = result.find(_.name == "longVal").get
    assertEquals(longStats.typ, "Long")
    assertEquals(longStats.mean, 200.0)

  test("summary should handle single row"):
    val data = List(
      (value = 42)
    )

    val result = data.summary
    val valueStats = result.find(_.name == "value").get

    assertEquals(valueStats.mean, 42.0)
    assertEquals(valueStats.min, 42.0)
    assertEquals(valueStats.max, 42.0)
    assertEquals(valueStats.median, 42.0)
    assertEquals(valueStats.`0.25`, 42.0)
    assertEquals(valueStats.`0.75`, 42.0)

  test("summary should handle negative numbers"):
    val data = List(
      (value = -10),
      (value = -5),
      (value = 0),
      (value = 5),
      (value = 10)
    )

    val result = data.summary
    val valueStats = result.find(_.name == "value").get

    assertEquals(valueStats.mean, 0.0)
    assertEquals(valueStats.min, -10.0)
    assertEquals(valueStats.max, 10.0)

  test("summary should handle floating point precision"):
    val data = List(
      (value = 0.1),
      (value = 0.2),
      (value = 0.3)
    )

    val result = data.summary
    val valueStats = result.find(_.name == "value").get

    // Use assertEqualsDouble for better floating point comparison
    assertEqualsDouble(valueStats.mean, 0.2, 0.01)
    assertEqualsDouble(valueStats.min, 0.1, 0.01)
    assertEqualsDouble(valueStats.max, 0.3, 0.01)

  test("summary should preserve column names"):
    val data = List(
      (temperature = 25.5, humidity = 60.0, pressure = 1013.25),
      (temperature = 26.2, humidity = 58.5, pressure = 1012.80),
      (temperature = 24.8, humidity = 62.1, pressure = 1014.10)
    )

    val result = data.summary

    assertEquals(result.length, 3)
    assert(result.exists(_.name == "temperature"))
    assert(result.exists(_.name == "humidity"))
    assert(result.exists(_.name == "pressure"))

  test("summary should handle identical values"):
    val data = List(
      (value = 100),
      (value = 100),
      (value = 100),
      (value = 100),
      (value = 100)
    )

    val result = data.summary
    val valueStats = result.find(_.name == "value").get

    assertEquals(valueStats.mean, 100.0)
    assertEquals(valueStats.min, 100.0)
    assertEquals(valueStats.max, 100.0)
    assertEquals(valueStats.median, 100.0)
    assertEquals(valueStats.`0.25`, 100.0)
    assertEquals(valueStats.`0.75`, 100.0)

  test("summary should handle optional columns"):
    val data = List(
      (id = 1, score = Some(85.5), bonus = Some(10)),
      (id = 2, score = Some(92.0), bonus = None),
      (id = 3, score = None, bonus = Some(15)),
      (id = 4, score = Some(78.5), bonus = Some(5)),
      (id = 5, score = Some(88.0), bonus = None)
    )

    val result = data.summary

    // Should have statistics for all columns that have numeric values
    val idStats = result.find(_.name == "id").get
    assertEquals(idStats.typ, "Int")
    assertEquals(idStats.mean, 3.0)
    assertEquals(idStats.min, 1.0)
    assertEquals(idStats.max, 5.0)

    // Score column should compute stats only for non-None values
    val scoreStats = result.find(_.name == "score").get
    assertEquals(scoreStats.typ, "Double")
    // Should compute stats for 85.5, 92.0, 78.5, 88.0 (4 values, ignoring None)
    assertEquals(scoreStats.mean, 86.0, 0.1) // (85.5 + 92.0 + 78.5 + 88.0) / 4 = 86.0
    assertEquals(scoreStats.min, 78.5, 0.1)
    assertEquals(scoreStats.max, 92.0, 0.1)

    // Bonus column should compute stats only for non-None values
    val bonusStats = result.find(_.name == "bonus").get
    assertEquals(bonusStats.typ, "Int")
    // Should compute stats for 10, 15, 5 (3 values, ignoring None)
    assertEquals(bonusStats.mean, 10.0, 0.1) // (10 + 15 + 5) / 3 = 10.0
    assertEquals(bonusStats.min, 5.0)
    assertEquals(bonusStats.max, 15.0)

  test("summary should handle optional Int and Long columns"):
    val data = List(
      (id = 1, optionalInt = Some(100), optionalLong = Some(1000000L)),
      (id = 2, optionalInt = None, optionalLong = Some(2500000L)),
      (id = 3, optionalInt = Some(200), optionalLong = None),
      (id = 4, optionalInt = Some(150), optionalLong = Some(1800000L)),
      (id = 5, optionalInt = None, optionalLong = None)
    )

    val result = data.summary

    // Should have statistics for all columns
    assertEquals(result.length, 3)

    // Regular id column
    val idStats = result.find(_.name == "id").get
    assertEquals(idStats.typ, "Int")
    assertEquals(idStats.mean, 3.0)
    assertEquals(idStats.min, 1.0)
    assertEquals(idStats.max, 5.0)

    // Optional Int column should compute stats only for non-None values
    val optIntStats = result.find(_.name == "optionalInt").get
    assertEquals(optIntStats.typ, "Int")
    // Should compute stats for 100, 200, 150 (3 values, ignoring None)
    assertEquals(optIntStats.mean, 150.0, 0.1) // (100 + 200 + 150) / 3 = 150.0
    assertEquals(optIntStats.min, 100.0)
    assertEquals(optIntStats.max, 200.0)

    // Optional Long column should compute stats only for non-None values
    val optLongStats = result.find(_.name == "optionalLong").get
    assertEquals(optLongStats.typ, "Long")
    // Should compute stats for 1000000, 2500000, 1800000 (3 values, ignoring None)
    assertEquals(optLongStats.mean, 1766666.6666666667, 10.0) // (1000000 + 2500000 + 1800000) / 3 ≈ 1766666.67
    assertEquals(optLongStats.min, 1000000.0, 0.1)
    assertEquals(optLongStats.max, 2500000.0, 0.1)

  test("summary should handle all None optional columns"):
    val data = List(
      (id = 1, optionalValue = None),
      (id = 2, optionalValue = None),
      (id = 3, optionalValue = None)
    )

    val result = data.summary

    // Should still have statistics for the id column
    val idStats = result.find(_.name == "id").get
    assertEquals(idStats.typ, "Int")
    assertEquals(idStats.mean, 2.0)

    // Optional column with all None values should still appear but with zero count
    val optStats = result.find(_.name == "optionalValue").get
    assertEquals(optStats.typ, "Unknown") // Can't determine type from all None values
    // No meaningful statistics can be computed from all None values
    assertEquals(optStats.mean, 0.0) // Because count is 0, mean should be 0.0 (no values to average)

  test("summary should handle first row None with Option type inference"):
    // This test specifically addresses the type inference issue when first row is None
    val data = List(
      (id = 1, score = None: Option[Double], count = None: Option[Int], population = None: Option[Long]),
      (id = 2, score = Some(95.5), count = Some(100), population = Some(1000000L)),
      (id = 3, score = Some(87.2), count = Some(150), population = Some(2500000L)),
      (id = 4, score = Some(92.8), count = Some(125), population = Some(1800000L))
    )

    val result = data.summary

    // Should have statistics for all columns
    assertEquals(result.length, 4)

    // Score column should be detected as Double even though first row is None
    val scoreStats = result.find(_.name == "score").get
    assertEquals(scoreStats.typ, "Double")
    // Should compute stats for 95.5, 87.2, 92.8 (3 values, ignoring first None)
    assertEquals(scoreStats.mean, 91.83333333333333, 0.1) // (95.5 + 87.2 + 92.8) / 3 ≈ 91.83
    assertEquals(scoreStats.min, 87.2, 0.1)
    assertEquals(scoreStats.max, 95.5, 0.1)

    // Count column should be detected as Int even though first row is None
    val countStats = result.find(_.name == "count").get
    assertEquals(countStats.typ, "Int")
    // Should compute stats for 100, 150, 125 (3 values, ignoring first None)
    assertEquals(countStats.mean, 125.0, 0.1) // (100 + 150 + 125) / 3 = 125.0
    assertEquals(countStats.min, 100.0)
    assertEquals(countStats.max, 150.0)

    // Population column should be detected as Long even though first row is None
    val populationStats = result.find(_.name == "population").get
    assertEquals(populationStats.typ, "Long")
    // Should compute stats for 1000000, 2500000, 1800000 (3 values, ignoring first None)
    assertEquals(populationStats.mean, 1766666.6666666667, 10.0)
    assertEquals(populationStats.min, 1000000.0, 0.1)
    assertEquals(populationStats.max, 2500000.0, 0.1)

  // Tests for Iterator extension
  test("Iterator summary should compute basic statistics for Integer data"):
    val data = List(
      (id = 1, value = 10),
      (id = 2, value = 20),
      (id = 3, value = 30),
      (id = 4, value = 40),
      (id = 5, value = 50)
    ).iterator

    val result = data.summary

    // Check that we have results for both columns
    assertEquals(result.length, 2)

    // Find the 'value' column statistics
    val valueStats = result.find(_.name == "value").get
    assertEquals(valueStats.typ, "Int")
    assertEquals(valueStats.mean, 30.0)
    assertEquals(valueStats.min, 10.0)
    assertEquals(valueStats.max, 50.0)
    assertEquals(valueStats.median, 30.0, 0.1) // TDigest is approximate

    // Find the 'id' column statistics
    val idStats = result.find(_.name == "id").get
    assertEquals(idStats.typ, "Int")
    assertEquals(idStats.mean, 3.0)
    assertEquals(idStats.min, 1.0)
    assertEquals(idStats.max, 5.0)

  test("Iterator summary should compute basic statistics for Double data"):
    val data = List(
      (name = "Alice", score = 95.5),
      (name = "Bob", score = 87.2),
      (name = "Charlie", score = 92.8),
      (name = "Diana", score = 89.1)
    ).iterator

    val result = data.summary

    // Find the 'score' column statistics (name column won't be processed as numeric)
    val scoreStats = result.find(_.name == "score").get
    assertEquals(scoreStats.typ, "Double")
    assertEquals(scoreStats.mean, 91.15, 0.01)
    assertEquals(scoreStats.min, 87.2, 0.01)
    assertEquals(scoreStats.max, 95.5, 0.01)

  test("Iterator summary should handle single element"):
    val data = List((id = 1, value = 42.0)).iterator
    val result = data.summary
    
    assertEquals(result.length, 2)
    
    val valueStats = result.find(_.name == "value").get
    assertEquals(valueStats.typ, "Double")
    assertEquals(valueStats.mean, 42.0)
    assertEquals(valueStats.min, 42.0)
    assertEquals(valueStats.max, 42.0)
    assertEquals(valueStats.median, 42.0)

  test("Iterator summary should handle Option values with None"):
    val data = List(
      (score = Some(95.5), count = Some(100), population = Some(1000000L)),
      (score = None, count = None, population = None),
      (score = Some(87.2), count = Some(150), population = Some(2500000L)),
      (score = Some(92.8), count = Some(125), population = Some(1800000L))
    ).iterator

    val result = data.summary

    // Score column should be detected as Double and handle None values
    val scoreStats = result.find(_.name == "score").get
    assertEquals(scoreStats.typ, "Double")
    // Should compute stats for 95.5, 87.2, 92.8 (3 values, ignoring None)
    assertEqualsDouble(scoreStats.mean, 91.83333333333333, 0.1)
    assertEqualsDouble(scoreStats.min, 87.2, 0.1)
    assertEqualsDouble(scoreStats.max, 95.5, 0.1)

  test("Iterator summary results should match Iterable summary results"):
    val originalData = List(
      (id = 1, value = 10.5, count = 100L),
      (id = 2, value = 20.5, count = 200L),
      (id = 3, value = 30.5, count = 300L),
      (id = 4, value = 40.5, count = 400L)
    )
    
    val iterableResult = originalData.summary
    val iteratorResult = originalData.iterator.summary
    
    assertEquals(iterableResult.length, iteratorResult.length)
    
    // Compare each column's statistics
    for ((iterable, iterator) <- iterableResult.zip(iteratorResult)) {
      assertEquals(iterable.name, iterator.name)
      assertEquals(iterable.typ, iterator.typ)
      assertEqualsDouble(iterable.mean, iterator.mean, 0.0001)
      assertEqualsDouble(iterable.min, iterator.min, 0.0001)
      assertEqualsDouble(iterable.max, iterator.max, 0.0001)
      assertEqualsDouble(iterable.median, iterator.median, 0.1) // TDigest approximation
      assertEqualsDouble(iterable.`0.25`, iterator.`0.25`, 0.1)
      assertEqualsDouble(iterable.`0.75`, iterator.`0.75`, 0.1)
    }

end StatsSuite
