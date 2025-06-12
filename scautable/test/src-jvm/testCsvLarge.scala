package io.github.quafadas.scautable

import java.time.LocalDate
import io.github.quafadas.table.*

import NamedTuple.*

import scala.compiletime.ops.int.S

class CSVBigSuite extends munit.FunSuite:

  test("type test") {
    def csv = CSV.resource("mnist_mini.csv")
  }

  // test("5000 cols") {
  //   def csv = CSV.resource("testFile/5000Cols.csv")    
  //   assert(csv.headers.length == 5001)      
  // }

end CSVBigSuite
