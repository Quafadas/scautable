package io.github.quafadas.scautable

import io.github.quafadas.table.TypeInferrer

/** Main Excel API object providing transparent inline methods for reading Excel files
  */
object Excel:
  import ExcelMacros.*

  /** Read Excel file from an absolute path with compile-time type inference
    *
    * @param filePath
    *   Absolute path to the Excel file
    * @param sheetName
    *   Name of the Excel sheet to read
    * @param range
    *   Optional cell range (e.g., "A1:C10"), empty string reads entire sheet
    * @param typeInferrer
    *   Type inference strategy (StringType or FromTuple supported)
    * @return
    *   ExcelIterator with inferred types
    */
  transparent inline def absolutePath[K](filePath: String, sheetName: String, range: String = "", inline typeInferrer: TypeInferrer = TypeInferrer.StringType) =
    ${ readExcelAbsolutePath('filePath, 'sheetName, 'range, 'typeInferrer) }

  /** Read Excel file from the classpath with compile-time type inference
    *
    * @param filePath
    *   Path to the Excel file in the classpath
    * @param sheetName
    *   Name of the Excel sheet to read
    * @param range
    *   Optional cell range (e.g., "A1:C10"), empty string reads entire sheet
    * @param typeInferrer
    *   Type inference strategy (StringType or FromTuple supported)
    * @return
    *   ExcelIterator with inferred types
    */
  transparent inline def resource[K](filePath: String, sheetName: String, range: String = "", inline typeInferrer: TypeInferrer = TypeInferrer.StringType) =
    ${ readExcelResource('filePath, 'sheetName, 'range, 'typeInferrer) }

  transparent inline def resource[K](filePath: String, sheetName: String, inline typeInferrer: TypeInferrer) =
    ${ readExcelResource('filePath, 'sheetName, '{""}, 'typeInferrer) }  

  transparent inline def resource[K](filePath: String, sheetName: String) =
    ${ readExcelResource('filePath, 'sheetName, '{""}, '{TypeInferrer.FromAllRows}) }

  /** Cleanup cached workbook resources for a specific file.
    * 
    * Call this method when you know that no more operations will be performed
    * on a specific Excel file to free up memory immediately.
    * 
    * @param filePath Path to the Excel file to cleanup
    */
  // def cleanup(filePath: String): Unit =
    // ExcelResourceManager.cleanup(filePath)

  /** Cleanup all cached Excel workbook resources.
    * 
    * This method is useful for application shutdown or when you want to
    * free up all Excel-related memory immediately.
    */
  // def cleanupAll(): Unit =
    // ExcelResourceManager.cleanupAll()
 
end Excel
