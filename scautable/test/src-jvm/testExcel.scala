// package io.github.quafadas.scautable

// import io.github.quafadas.table.*
// import java.time.LocalDate

// import NamedTuple.*
// import scala.compiletime.ops.int.S

// import Excel.BadTableException

// class ExcelSuite extends munit.FunSuite:

//   test("excel provider compiles and typechecks") {
//     def csv: ExcelIterator[("Column 1", "Column 2", "Column 3")] = Excel.resource("SimpleTable.xlsx", "Sheet1")

//     assertEquals(csv.column[("Column 1")].toList.head, "Row 1, Col 1")
//     assertEquals(csv.column[("Column 1")].toList.last, "Row 3, Col 1")

//   }

//   test("excel provider throws on duplicated header") {
//     assert(
//       compileErrors(
//         """Excel.resource("SimpleDup.xlsx", "Sheet1")"""
//       ).contains("Duplicate header found: Column 3")
//     )

//   }

//   test("excel provider compiles but throws on malformed table") {
//     val csv: ExcelIterator[("Column 1", "Column 2", "Column 3")] = Excel.resource("SimpleTableWithExtendedRow.xlsx", "Sheet1")

//     intercept[BadTableException] {
//       println(csv.toList)
//     }

//   }

//   test("ExcelIterator with colStart parameter") {
//     def csv: ExcelIterator[("Column 1", "Column 2", "Column 3")] = Excel.resource("SimpleTableColOffset.xlsx", "Sheet1", "D1:F4")

//     val csvSeq = csv.toSeq
//     assertEquals(csvSeq.column["Column 1"].toList.head, "Row 1, Col 1")
//     assertEquals(csvSeq.column["Column 1"].toList.last, "Row 3, Col 1")
//     assertEquals(csvSeq.column["Column 3"].toList.last, "Row 3, Col 3")

//   }

//   test("ExcelIterator range") {
//     def csv: ExcelIterator[("Column 1", "Column 2", "Column 3")] = Excel.resource("Offset.xlsx", "Sheet1", "E3:G6")
//     val csvSeq = csv.toSeq
//     // csvSeq.ptbln
//     assertEquals(csv.column["Column 2"].toList.head, "Row 1, Col 2")
//     assertEquals(csv.column["Column 2"].toList.last, "Row 3, Col 2")
//   }

//   test("ExcelIterator Missing and blank values") {
//     // Checks that we've set the Missing cell policy correctly
//     def csv: ExcelIterator[("Column 1", "Column 2", "Column 3")] = Excel.resource("Missing.xlsx", "Sheet1", "A1:C4")
//     val csvSeq = csv.toSeq
//     // csvSeq.ptbln
//     assertEquals(csv.column["Column 2"].toList.drop(1).head, "") // blank
//     assertEquals(csv.column["Column 3"].toList.drop(2).head, "") // missing

//   }

// end ExcelSuite
