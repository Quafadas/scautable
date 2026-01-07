package io.github.quafadas.scautable.json

import munit.FunSuite
import io.github.quafadas.table.TypeInferrer

class JsonSuite extends FunSuite:

  test("JSON.fromString should parse simple JSON array") {
    inline val jsonContent = """[{"a":1,"b":2},{"a":5,"b":3}]"""
    val result = JSON.fromString(jsonContent)
    val data = result.toSeq

    assertEquals(data.length, 2)
    assertEquals(data(0).a, 1)
    assertEquals(data(0).b, 2)
    assertEquals(data(1).a, 5)
    assertEquals(data(1).b, 3)
  }

  

  test("JSON.fromString should infer types correctly") {
    inline val jsonContent = """[{"active":true,"age":30,"name":"Alice"},{"active":false,"age":25,"name":"Bob"}]"""
    val result = JSON.fromString(jsonContent)
    val data = result.toSeq

    assertEquals(data.length, 2)
    assertEquals(data(0).name, "Alice")
    assertEquals(data(0).age, 30)
    assertEquals(data(0).active, true)
  }

  test("JSON.fromString should handle string type inference") {
    inline val jsonContent = """[{"a":"1","b":"hello"},{"a":"2","b":"world"}]"""
    val result = JSON.fromString(jsonContent, TypeInferrer.StringType)
    val data = result.toSeq

    assertEquals(data.length, 2)
    assertEquals(data(0).a, "1")
    assertEquals(data(0).b, "hello")
  }

  test("JSON.fromString should handle missing fields") {
    inline val jsonContent = """[{"a":1,"b":2},{"a":3}]"""
    val result = JSON.fromString(jsonContent)
    val data = result.toSeq

    assertEquals(data.length, 2)
    assertEquals(data(0).a, 1)
    assertEquals(data(0).b, Some(2))
    assertEquals(data(1).a, 3)
    // Missing field becomes None
    assertEquals(data(1).b, None)
  }

  test("JSON.fromString should handle double values") {
    inline val jsonContent = """[{"x":1.5,"y":2.7},{"x":3.14,"y":4.0}]"""
    val result = JSON.fromString(jsonContent)
    val data = result.toSeq

    assertEquals(data.length, 2)
    assertEquals(data(0).x, 1.5)
    assertEquals(data(0).y, 2.7)
  }

  test("JSON.fromString should handle boolean values") {
    inline val jsonContent = """[{"flag":true},{"flag":false}]"""
    val result = JSON.fromString(jsonContent)
    val data = result.toSeq

    assertEquals(data.length, 2)
    assertEquals(data(0).flag, true)
    assertEquals(data(1).flag, false)
  }

  test("JSON.fromString should extract all headers from all objects") {
    inline val jsonContent = """[{"a":1},{"b":2},{"c":3}]"""
    val result = JSON.fromString(jsonContent)
    val data = result.toSeq

    assertEquals(data.length, 3)
    // All objects should have all headers, missing ones default to None
    assertEquals(data(0).a, Some(1))
    assertEquals(data(0).b, None)
    assertEquals(data(0).c, None)
    assertEquals(data(1).a, None)
    assertEquals(data(1).b, Some(2))
    assertEquals(data(1).c, None)
    assertEquals(data(2).a, None)
    assertEquals(data(2).b, None)
    assertEquals(data(2).c, Some(3))
  }

  test("JSON.fromString should handle FirstRow type inference") {
    inline val jsonContent = """[{"a":1,"b":"text"},{"a":2,"b":"more"}]"""
    val result = JSON.fromString(jsonContent, TypeInferrer.FirstRow)
    val data = result.toSeq

    assertEquals(data.length, 2)
    assertEquals(data(0).a, 1)
    assertEquals(data(0).b, "text")
  }

  test("JSON.fromString should handle FirstN type inference") {
    inline val jsonContent = """[{"a":1},{"a":2},{"a":3}]"""
    val result = JSON.fromString(jsonContent, TypeInferrer.FirstN(2))
    val data = result.toSeq

    assertEquals(data.length, 3)
    assertEquals(data(0).a, 1)
    assertEquals(data(1).a, 2)
    assertEquals(data(2).a, 3)
  }

  test("JSON.fromString should handle null values") {
    inline val jsonContent = """[{"a":1,"b":null},{"a":2,"b":3}]"""
    val result = JSON.fromString(jsonContent)
    val data = result.toSeq

    assertEquals(data.length, 2)
    assertEquals(data(0).a, 1)
    // null should be treated as Option[Int] = None
    assertEquals(data(0).b, None)
    assertEquals(data(1).a, 2)
    assertEquals(data(1).b, Some(3))
  }

  test("JSON.fromString should handle long values") {
    inline val jsonContent = """[{"big":9223372036854775807}]"""
    val result = JSON.fromString(jsonContent)
    val data = result.toSeq

    assertEquals(data.length, 1)
    assertEquals(data(0).big, 9223372036854775807L)
  }

  test("JSON.resource should read from resources") {
    val result = JSON.resource("simple_numbers.json")
    val data = result.toSeq

    assertEquals(data.length, 2)
    assertEquals(data(0).a, 1)
    assertEquals(data(0).b, 2)
  }

  test("larger file") {
    val result = JSON.resource("mini-movies.json")
    val data = result.toSeq
    assert(data.length == 1214)

  }

  test("JSON.fromString should throw error for nested objects") {
    // Nested objects should cause an error during parsing
    val error = compileErrors("""
      inline val jsonContent = "[{\"a\":1,\"nested\":{\"x\":1,\"y\":2}}]"
      JSON.fromString(jsonContent)
    """)
    assert(error.contains("Nested objects are not supported") || error.contains("not supported"))
  }

  test("JSON.fromString should throw error for nested arrays") {
    // Nested arrays should cause an error during parsing
    val error = compileErrors("""
      inline val jsonContent = "[{\"a\":1,\"items\":[1,2,3]}]"
      JSON.fromString(jsonContent)
    """)
    assert(error.contains("Nested arrays are not supported") || error.contains("not supported"))
  }

  test("JSON.fromString should throw error for deeply nested objects") {
    // Deeply nested objects should cause an error
    val error = compileErrors("""
      inline val jsonContent = "[{\"a\":{\"b\":{\"c\":1}}}]"
      JSON.fromString(jsonContent)
    """)
    assert(error.contains("Nested objects are not supported") || error.contains("not supported"))
  }

  test("JSON.fromString should throw error for array of arrays") {
    // Array values should cause an error
    val error = compileErrors("""
      inline val jsonContent = "[{\"matrix\":[[1,2],[3,4]]}]"
      JSON.fromString(jsonContent)
    """)
    assert(error.contains("Nested arrays are not supported") || error.contains("not supported"))
  }

  test("JSON.fromString should throw error for mixed nested content") {
    // Mixed nested content with objects should cause an error
    val error = compileErrors("""
      inline val jsonContent = "[{\"a\":1,\"b\":\"hello\",\"nested\":{\"x\":1}}]"
      JSON.fromString(jsonContent)
    """)
    assert(error.contains("Nested objects are not supported") || error.contains("not supported"))
  }

  test("JSON.resource should throw runtime error when nesting appears after type inference rows") {
    // When using FirstRow or FirstN, nesting might only appear in later rows
    // which would cause a runtime error when iterating
    val result = JSON.resource("nested_later.json", TypeInferrer.FirstRow)

    val error = intercept[UnsupportedOperationException] {
      result.toSeq // Force iteration through all rows
    }
    assert(error.getMessage.contains("Nested") && error.getMessage.contains("not supported"))
  }

  test("JSON.resource should throw runtime error when nested array appears after type inference rows") {
    // Similar test but for nested arrays instead of objects
    val result = JSON.resource("nested_array_later.json", TypeInferrer.FirstRow)

    val error = intercept[UnsupportedOperationException] {
      result.toSeq // Force iteration through all rows
    }
    assert(error.getMessage.contains("Nested arrays are not supported"))
  }
end JsonSuite
