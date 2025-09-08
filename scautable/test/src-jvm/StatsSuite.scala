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

    val result = data.numericSummary

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

    val result = data.numericSummary

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

    val result = data.numericSummary

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

    val result = data.numericSummary
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

    val result = data.numericSummary

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

    val result = data.numericSummary
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

    val result = data.numericSummary
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

    val result = data.numericSummary
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

    val result = data.numericSummary

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

    val result = data.numericSummary
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

    val result = data.numericSummary

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

    val result = data.numericSummary

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

    val result = data.numericSummary

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

    val result = data.numericSummary

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

    val result = data.numericSummary

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

    val result = data.numericSummary

    // Find the 'score' column statistics (name column won't be processed as numeric)
    val scoreStats = result.find(_.name == "score").get
    assertEquals(scoreStats.typ, "Double")
    assertEquals(scoreStats.mean, 91.15, 0.01)
    assertEquals(scoreStats.min, 87.2, 0.01)
    assertEquals(scoreStats.max, 95.5, 0.01)

  test("Iterator summary should handle empty iterator"):
    val data = List((id = 1, value = 42.0)).iterator.take(0) // Create an empty iterator with known types
    val result = data.numericSummary
    assertEquals(result.length, 0)

  test("Iterator summary should handle single element"):
    val data = List((id = 1, value = 42.0)).iterator
    val result = data.numericSummary
    
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

    val result = data.numericSummary

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
    
    val iterableResult = originalData.numericSummary
    val iteratorResult = originalData.iterator.numericSummary
    
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

  test("Iterator nonNumericSummary should compute basic statistics for string data"):
    val data = Iterator(
      (name = "Alice", city = "New York"),
      (name = "Bob", city = "Boston"),
      (name = "Charlie", city = "New York"),
      (name = "Diana", city = "Chicago"),
      (name = "Eve", city = "New York")
    )

    val result = data.nonNumericSummary

    // Check that we have results for both columns
    assertEquals(result.length, 2)

    // Find the 'city' column statistics
    val cityStats = result.find(_.name == "city").get
    assertEquals(cityStats.uniqueEntries, 3) // New York, Boston, Chicago
    assertEquals(cityStats.mostFrequent, Some("New York"))
    assertEquals(cityStats.frequency, 3)
    assert(cityStats.sample.contains("New York"))

    // Find the 'name' column statistics
    val nameStats = result.find(_.name == "name").get
    assertEquals(nameStats.uniqueEntries, 5) // All unique names
    assert(nameStats.frequency <= 1) // Each name appears once

  test("Iterator nonNumericSummary should handle empty iterator"):
    val data = Iterator.empty[(name: String, city: String)]

    val result = data.nonNumericSummary

    assertEquals(result, List.empty)

  test("Iterator nonNumericSummary should handle single element"):
    val data = Iterator((name = "Alice", city = "New York"))

    val result = data.nonNumericSummary

    assertEquals(result.length, 2)
    val nameStats = result.find(_.name == "name").get
    assertEquals(nameStats.uniqueEntries, 1)
    assertEquals(nameStats.mostFrequent, Some("Alice"))
    assertEquals(nameStats.frequency, 1)

  test("Iterator nonNumericSummary should handle Option values with None"):
    val data = Iterator(
      (name = "Alice", category = Some("A")),
      (name = "Bob", category = Some("B")),
      (name = "Charlie", category = None),
      (name = "Diana", category = Some("A"))
    )

    val result = data.nonNumericSummary

    // Find the 'category' column statistics
    val categoryStats = result.find(_.name == "category").get
    assertEquals(categoryStats.uniqueEntries, 2) // A, B (None is excluded)
    assertEquals(categoryStats.mostFrequent, Some("A"))
    assertEquals(categoryStats.frequency, 2)

  test("Iterator nonNumericSummary results should match Iterable nonNumericSummary results"):
    val baseData = List(
      (name = "Alice", city = "New York", department = "Engineering"),
      (name = "Bob", city = "Boston", department = "Sales"),
      (name = "Charlie", city = "New York", department = "Engineering"),
      (name = "Diana", city = "Chicago", department = "Marketing")
    )

    val iterableResult = baseData.nonNumericSummary
    val iteratorResult = baseData.iterator.nonNumericSummary

    assertEquals(iterableResult.length, iteratorResult.length)

    for ((iterable, iterator) <- iterableResult.zip(iteratorResult)) {
      assertEquals(iterable.name, iterator.name)
      assertEquals(iterable.uniqueEntries, iterator.uniqueEntries)
      assertEquals(iterable.mostFrequent, iterator.mostFrequent)
      assertEquals(iterable.frequency, iterator.frequency)
      // Note: Sample might differ due to random shuffle, so we don't test exact equality
      assert(iterable.sample.nonEmpty == iterator.sample.nonEmpty)
    }

  test("nonNumericSummary should compute basic statistics for string data"):
    val data = List(
      (name = "Alice", city = "New York"),
      (name = "Bob", city = "Boston"),
      (name = "Charlie", city = "New York"),
      (name = "Diana", city = "Chicago"),
      (name = "Eve", city = "New York")
    )

    val result = data.nonNumericSummary

    // Check that we have results for both columns
    assertEquals(result.length, 2)

    // Find the 'city' column statistics
    val cityStats = result.find(_.name == "city").get
    assertEquals(cityStats.uniqueEntries, 3) // New York, Boston, Chicago
    assertEquals(cityStats.mostFrequent, Some("New York"))
    assertEquals(cityStats.frequency, 3)
    assert(cityStats.sample.contains("New York"))
    assert(cityStats.sample.contains("Boston") || cityStats.sample.contains("Chicago"))

    // Find the 'name' column statistics
    val nameStats = result.find(_.name == "name").get
    assertEquals(nameStats.uniqueEntries, 5) // All unique names
    assert(nameStats.frequency <= 1) // Each name appears once
    assert(nameStats.sample.nonEmpty)

  test("nonNumericSummary should handle Option types and None values"):
    val data = List(
      (name = "Alice", category = Some("A")),
      (name = "Bob", category = Some("B")),
      (name = "Charlie", category = None),
      (name = "Diana", category = Some("A")),
      (name = "Eve", category = Some("A"))
    )

    val result = data.nonNumericSummary

    // Find the 'category' column statistics
    val categoryStats = result.find(_.name == "category").get
    assertEquals(categoryStats.uniqueEntries, 2) // A, B (None is excluded)
    assertEquals(categoryStats.mostFrequent, Some("A"))
    assertEquals(categoryStats.frequency, 3)

  test("nonNumericSummary should handle empty and all-None data"):
    val data = List(
      (name = "Alice", category = None),
      (name = "Bob", category = None),
      (name = "Charlie", category = None)
    )

    val result = data.nonNumericSummary

    // Find the 'category' column statistics
    val categoryStats = result.find(_.name == "category").get
    assertEquals(categoryStats.uniqueEntries, 0) // No non-None values
    assertEquals(categoryStats.mostFrequent, None)
    assertEquals(categoryStats.frequency, 0)
    assertEquals(categoryStats.sample, "")

  test("nonNumericSummary should truncate long samples"):
    // Create data with long string values to test truncation
    val longString1 = "ThisIsAVeryLongStringThatShouldBeTruncatedWhenUsedInSample"
    val longString2 = "AnotherVeryLongStringForTestingPurposes"
    val longString3 = "YetAnotherLongStringToMakeSureTruncationWorks"
    
    val data = List(
      (id = 1, description = longString1),
      (id = 2, description = longString2),
      (id = 3, description = longString3)
    )

    val result = data.nonNumericSummary
    val descStats = result.find(_.name == "description").get
    
    assertEquals(descStats.uniqueEntries, 3)
    assert(descStats.sample.length <= 75, s"Sample should be truncated to 75 chars, but was ${descStats.sample.length}")
    if descStats.sample.length == 75 then
      assert(descStats.sample.endsWith("..."), "Long samples should end with '...'")

  test("summary should compute both numeric and non-numeric statistics for Iterable"):
    val data = List(
      (name = "Alice", age = 25, city = "New York", salary = Some(50000.0)),
      (name = "Bob", age = 30, city = "Boston", salary = Some(60000.0)),
      (name = "Charlie", age = 35, city = "New York", salary = Some(70000.0)),
      (name = "Diana", age = 28, city = "Chicago", salary = Option.empty[Double])
    )

    val result = data.summary

    // Check numeric results (age and salary columns)
    assertEquals(result.numeric.length, 2)
    val ageStats = result.numeric.find(_.name == "age").get
    assertEquals(ageStats.typ, "Int")
    assertEquals(ageStats.mean, 29.5, 0.1)

    val salaryStats = result.numeric.find(_.name == "salary").get
    assertEquals(salaryStats.typ, "Double")
    assertEquals(salaryStats.mean, 60000.0, 0.1)

    // Check non-numeric results (name and city columns)
    assertEquals(result.nonNumeric.length, 4) // All columns get analyzed for non-numeric stats
    val cityStats = result.nonNumeric.find(_.name == "city").get
    assertEquals(cityStats.uniqueEntries, 3) // New York, Boston, Chicago
    assertEquals(cityStats.mostFrequent, Some("New York"))
    assertEquals(cityStats.frequency, 2)

    val nameStats = result.nonNumeric.find(_.name == "name").get
    assertEquals(nameStats.uniqueEntries, 4) // All unique names

  test("summary should handle data with only non-numeric columns"):
    val data = List(
      (name = "Alice", city = "New York", status = "Active"),
      (name = "Bob", city = "Boston", status = "Inactive")
    )

    val result = data.summary

    assertEquals(result.numeric, List.empty) // No numeric columns
    assertEquals(result.nonNumeric.length, 3) // name, city, status

    val nameStats = result.nonNumeric.find(_.name == "name").get
    assertEquals(nameStats.uniqueEntries, 2)
    assertEquals(nameStats.frequency, 1) // Each name appears once

end StatsSuite
