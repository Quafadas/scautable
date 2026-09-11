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
    ${ readExcelResource('filePath, 'sheetName, '{ "" }, 'typeInferrer) }

  transparent inline def resource[K](filePath: String, sheetName: String) =
    ${ readExcelResource('filePath, 'sheetName, '{ "" }, '{ TypeInferrer.FromAllRows }) }

  /** Read an Excel file at a path relative to the source file this is called from, with compile-time type inference.
    *
    * The path is anchored to the calling source file rather than to the compiler's working directory, so it resolves the same way no matter where the build was invoked from. In a
    * notebook or REPL there is no source file on disk, so the path resolves against the working directory instead and a compile time warning says so.
    *
    * @param filePath
    *   Path to the Excel file, relative to the calling source file
    * @param sheetName
    *   Name of the Excel sheet to read
    * @param range
    *   Optional cell range (e.g., "A1:C10"), empty string reads entire sheet
    * @param typeInferrer
    *   Type inference strategy (StringType or FromTuple supported)
    * @return
    *   ExcelIterator with inferred types
    */
  transparent inline def relativeToSource[K](filePath: String, sheetName: String, range: String = "", inline typeInferrer: TypeInferrer = TypeInferrer.StringType) =
    ${ readExcelRelativeToSource('filePath, 'sheetName, 'range, 'typeInferrer) }

  transparent inline def relativeToSource[K](filePath: String, sheetName: String, inline typeInferrer: TypeInferrer) =
    ${ readExcelRelativeToSource('filePath, 'sheetName, '{ "" }, 'typeInferrer) }

  /** Read an Excel file at a path relative to the discovered project root, with compile-time type inference.
    *
    * The root is the first ancestor of the calling source file holding a build marker (`build.mill`, `build.sbt`, `.git`, ...). In a notebook or REPL there is no source file on
    * disk to search upwards from, so the root is discovered from the working directory instead and a compile time warning says so.
    *
    * @param filePath
    *   Path to the Excel file, relative to the project root
    * @param sheetName
    *   Name of the Excel sheet to read
    * @param range
    *   Optional cell range (e.g., "A1:C10"), empty string reads entire sheet
    * @param typeInferrer
    *   Type inference strategy (StringType or FromTuple supported)
    * @return
    *   ExcelIterator with inferred types
    */
  transparent inline def projectRoot[K](filePath: String, sheetName: String, range: String = "", inline typeInferrer: TypeInferrer = TypeInferrer.StringType) =
    ${ readExcelProjectRoot('filePath, 'sheetName, 'range, 'typeInferrer) }

  transparent inline def projectRoot[K](filePath: String, sheetName: String, inline typeInferrer: TypeInferrer) =
    ${ readExcelProjectRoot('filePath, 'sheetName, '{ "" }, 'typeInferrer) }

  /** Cleanup cached workbook resources for a specific file.
    *
    * Call this method when you know that no more operations will be performed on a specific Excel file to free up memory immediately.
    *
    * @param filePath
    *   Path to the Excel file to cleanup
    */
  // def cleanup(filePath: String): Unit =
  // ExcelResourceManager.cleanup(filePath)

  /** Cleanup all cached Excel workbook resources.
    *
    * This method is useful for application shutdown or when you want to free up all Excel-related memory immediately.
    */
  // def cleanupAll(): Unit =
  // ExcelResourceManager.cleanupAll()

end Excel
