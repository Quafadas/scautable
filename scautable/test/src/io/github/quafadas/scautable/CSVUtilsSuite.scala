package io.github.quafadas.scautable

class CSVUtilsSuite extends munit.FunSuite:
  test("uniquifyHeaders should suffix duplicates with index") {
    val headers = List("id", "name", "name", "email", "id", "name")
    val result = CSVUtils.uniquifyHeaders(headers)
    assertEquals(result, List("id", "name", "name_1", "email", "id_1", "name_2"))
  }

  test("uniquifyHeaders should correctly suffix duplicates even when original headers contain numbered suffixes") {
    val headers = List("test", "test_1", "test", "test_1", "test_2")
    val result = CSVUtils.uniquifyHeaders(headers)
    assertEquals(result, List("test", "test_1", "test_1_1", "test_1_2", "test_2"))
  }
end CSVUtilsSuite
