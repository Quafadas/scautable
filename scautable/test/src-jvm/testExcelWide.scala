package io.github.quafadas.scautable

import scala.NamedTuple.*

import io.github.quafadas.table.{*, given}

class ExcelWideSuite extends munit.FunSuite:


  test("Wide XL file") {
    // Test reading a wide Excel file with many columns
    // val wideExcel = Excel.resource("wideXL.xlsx", "Sheet1", "A1:BA10", TypeInferrer.FromAllRows)
    // val rows = wideExcel.toList
    // val colA = rows.column["A"].toList
    // println(colA)
  }

end ExcelWideSuite
