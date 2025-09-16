//> using scala 3.7.2
//> using dep io.github.quafadas::scautable::0.8.11
//> using resourceDir ./resources

import io.github.quafadas.table.*

/**
 * Example demonstrating Excel TypeInferrer functionality
 * 
 * This example shows how the TypeInferrer works with Excel files,
 * automatically detecting column types at compile time.
 */
@main def excelTypeInferrerExample =
  
  // Example 1: TypeInferrer.StringType - All columns as String
  println("=== TypeInferrer.StringType ===")
  val excelString = Excel.resource("SimpleTable.xlsx", "Sheet1", TypeInferrer.StringType)
  println(s"Headers: ${excelString.headers}")
  excelString.take(3).foreach(row => 
    println(s"${row.`Column 1`} | ${row.`Column 2`} | ${row.`Column 3`}")
  )
  
  // Example 2: TypeInferrer.FirstRow - Infer from first data row
  println("\n=== TypeInferrer.FirstRow ===")
  val excelFirstRow = Excel.resource("SimpleTable.xlsx", "Sheet1", TypeInferrer.FirstRow)
  excelFirstRow.take(3).foreach(row => 
    println(s"${row.`Column 1`} | ${row.`Column 2`} | ${row.`Column 3`}")
  )
  
  // Example 3: TypeInferrer.FirstN - Infer from first N rows
  println("\n=== TypeInferrer.FirstN(2) ===")
  val excelFirstN = Excel.resource("SimpleTable.xlsx", "Sheet1", TypeInferrer.FirstN(2))
  excelFirstN.take(3).foreach(row => 
    println(s"${row.`Column 1`} | ${row.`Column 2`} | ${row.`Column 3`}")
  )
  
  // Example 4: TypeInferrer.FromAllRows - Infer from all rows
  println("\n=== TypeInferrer.FromAllRows ===")
  val excelAllRows = Excel.resource("SimpleTable.xlsx", "Sheet1", TypeInferrer.FromAllRows)
  excelAllRows.take(3).foreach(row => 
    println(s"${row.`Column 1`} | ${row.`Column 2`} | ${row.`Column 3`}")
  )
  
  // Example 5: TypeInferrer.FromTuple - Explicit type specification
  println("\n=== TypeInferrer.FromTuple ===")
  val excelTyped: TypedExcelIterator[("Column 1", "Column 2", "Column 3"), (String, String, String)] = 
    Excel.resource("SimpleTable.xlsx", "Sheet1", TypeInferrer.FromTuple[(String, String, String)]())
  excelTyped.take(3).foreach(row => 
    println(s"${row.`Column 1`} | ${row.`Column 2`} | ${row.`Column 3`}")
  )
  
  // Example 6: Excel with range and TypeInferrer
  println("\n=== Excel with Range and TypeInferrer ===")
  val excelRange = Excel.resource("SimpleTableColOffset.xlsx", "Sheet1", "D1:F4", TypeInferrer.StringType)
  excelRange.take(3).foreach(row => 
    println(s"${row.`Column 1`} | ${row.`Column 2`} | ${row.`Column 3`}")
  )

  /*
   * If you had an Excel file with numeric data like:
   * 
   * Header1,Header2,Header3
   * 1,1.5,true
   * 2,2.5,false
   * 3,3.5,true
   * 
   * TypeInferrer.FirstRow would infer: (Int, Double, Boolean)
   * TypeInferrer.StringType would give: (String, String, String)
   * TypeInferrer.FromTuple[(Long, Float, String)]() would force: (Long, Float, String)
   */
  
  println("\n=== Functionality Complete! ===")
  println("Excel TypeInferrer now supports:")
  println("- TypeInferrer.StringType: All columns as String")
  println("- TypeInferrer.FirstRow: Infer from first data row") 
  println("- TypeInferrer.FirstN(n): Infer from first n rows")
  println("- TypeInferrer.FromAllRows: Infer from all data rows")
  println("- TypeInferrer.FromTuple[T]: Use explicit tuple type T")
  println("- Works with Excel ranges (e.g., 'A1:C10')")
  println("- Works with both Excel.resource() and Excel.absolutePath()")
  println("- Automatic type inference for Int, Long, Double, Boolean, String")
  println("- Handles missing/blank Excel cells with Option types")