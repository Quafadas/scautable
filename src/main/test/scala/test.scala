
class MySuite extends munit.FunSuite {
  test("hello") {
    val obtained = 42
    val expected = 42
    assertEquals(obtained, expected)
  }
}