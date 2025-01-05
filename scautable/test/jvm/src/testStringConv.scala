package io.github.quafadas.scautable

import munit.FunSuite

class StringConversionTests extends FunSuite {

  test("recommendConversion should recommend Int when validInts percentage is highest and >= 75%") {
    val acc = List(ConversionAcc(80, 10, 10))
    val rowCount = 100
    assertEquals(recommendConversion(acc, rowCount), "Int")
  }

  test("recommendConversion should recommend Long when validLongs percentage is highest and >= 75%") {
    val acc = List(ConversionAcc(10, 10, 80))
    val rowCount = 100
    assertEquals(recommendConversion(acc, rowCount), "Long")
  }

  test("recommendConversion should recommend Double when validDoubles percentage is highest and >= 75%") {
    val acc = List(ConversionAcc(10, 80, 10))
    val rowCount = 100
    assertEquals(recommendConversion(acc, rowCount), "Double")
  }

  test("recommendConversion should recommend String when no type has >= 75% valid counts") {
    val acc = List(ConversionAcc(30, 30, 30))
    val rowCount = 100
    assertEquals(recommendConversion(acc, rowCount), "String")
  }

  test("recommendConversion should handle multiple ConversionAcc entries correctly") {
    val acc = List(
      ConversionAcc(80, 10, 10),
      ConversionAcc(10, 80, 10),
      ConversionAcc(10, 10, 80)
    )
    val rowCount = 100
    assertEquals(recommendConversion(acc, rowCount), "Int, Double, Long")
  }
}