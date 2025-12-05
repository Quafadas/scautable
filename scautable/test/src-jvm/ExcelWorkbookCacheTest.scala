package io.github.quafadas.scautable

import munit.FunSuite

class ExcelWorkbookCacheTest extends FunSuite:

  // Use classpath resource approach to get the correct file path
  val testExcelFile =
    val resourceUrl = getClass.getClassLoader.getResource("SimpleTable.xlsx")
    if resourceUrl == null then throw new RuntimeException("Test resource SimpleTable.xlsx not found")
    end if
    new java.io.File(resourceUrl.toURI).getAbsolutePath
  end testExcelFile

  override def beforeEach(context: BeforeEach): Unit =
    // Clear cache before each test
    ExcelWorkbookCache.clearAll()
  end beforeEach

  test("ExcelWorkbookCache should cache and reuse workbook instances") {
    // First call should create a new workbook
    val workbook1 = ExcelWorkbookCache.getOrCreate(testExcelFile)
    assert(workbook1.isSuccess, "First workbook creation should succeed")

    // Second call should return the same cached instance
    val workbook2 = ExcelWorkbookCache.getOrCreate(testExcelFile)
    assert(workbook2.isSuccess, "Second workbook retrieval should succeed")

    // They should be the same instance (reference equality)
    assert(workbook1.get eq workbook2.get, "Should return the same workbook instance")
  }

  test("ExcelWorkbookCache should handle non-existent files gracefully") {
    val result = ExcelWorkbookCache.getOrCreate("nonexistent/file.xlsx")
    assert(result.isFailure, "Should fail for non-existent files")
  }

  test("ExcelWorkbookCache should remove workbooks from cache") {
    // Create a workbook
    val workbook1 = ExcelWorkbookCache.getOrCreate(testExcelFile)
    assert(workbook1.isSuccess)

    // Remove it from cache
    ExcelWorkbookCache.closeAndRemove(testExcelFile)

    // Next call should create a new instance
    val workbook2 = ExcelWorkbookCache.getOrCreate(testExcelFile)
    assert(workbook2.isSuccess)

    // They should be different instances
    assert(!(workbook1.get eq workbook2.get), "Should create a new workbook instance after removal")
  }

  test("ExcelWorkbookCache should clear all cached workbooks") {
    // Create workbooks for multiple files
    val workbook1 = ExcelWorkbookCache.getOrCreate(testExcelFile)
    val testExcelFile2 =
      val resourceUrl = getClass.getClassLoader.getResource("Numbers.xlsx")
      if resourceUrl == null then throw new RuntimeException("Test resource Numbers.xlsx not found")
      end if
      new java.io.File(resourceUrl.toURI).getAbsolutePath
    end testExcelFile2
    val workbook2 = ExcelWorkbookCache.getOrCreate(testExcelFile2)

    assert(workbook1.isSuccess && workbook2.isSuccess)
    assert(ExcelWorkbookCache.cacheSize >= 2, "Cache should contain at least 2 workbooks")

    // Clear all
    ExcelWorkbookCache.clearAll()

    // Cache should be empty (though size might not be 0 due to weak references)
    // The important thing is that new instances are created
    val newWorkbook1 = ExcelWorkbookCache.getOrCreate(testExcelFile)
    assert(newWorkbook1.isSuccess)
    assert(!(workbook1.get eq newWorkbook1.get), "Should create new instance after clearAll")
  }

end ExcelWorkbookCacheTest
