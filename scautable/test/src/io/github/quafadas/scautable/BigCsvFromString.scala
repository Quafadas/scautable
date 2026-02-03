package io.github.quafadas.scautable

import java.math.BigInteger

import scala.compiletime.testing.*

import io.github.quafadas.scautable.HeaderOptions.*
import io.github.quafadas.table.TypeInferrer
import munit.FunSuite

class CsvFromBigStringSuite extends FunSuite:

  test("fromString should handle duplicated headers by parsing correctly but warn at compile time") {
    inline val csvContent =
      "colA,col2,col3,col4,col5,col6,col7,col8,col9,col10,col11,col12,col13,col14,col15,col116,col17,col18,col19,col20,col21,col22,col23,col24,col25,colA,col2,col3,col4,col5,col6,col7,col8,col9,col10\n1,2,3,4,5,6,7,8,1,2,3,4,5,6,7,8,1,2,3,4,5,6,7,8,1,2,3,4,5,6,7,8,1,2,3"
    val csvIterator = CSV.fromString(
      csvContent,
      HeaderOptions.FromRows(1),
      TypeInferrer.FromTuple[
        (
            Int,
            String,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int
        )
      ]()
    )
    assert(csvIterator.hasNext)
    val row = csvIterator.next()
    assertEquals(row.colA, 1)
    assert(!csvIterator.hasNext)
  }
end CsvFromBigStringSuite
