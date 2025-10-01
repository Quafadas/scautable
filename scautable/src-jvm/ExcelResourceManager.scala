package io.github.quafadas.scautable

/** Utility object for managing Excel workbook resources and lifecycle
  */
object ExcelResourceManager:
  
  /** Cleanup resources for a specific Excel file when no longer needed.
    * 
    * This method should be called when you know that no more operations
    * will be performed on a specific Excel file to free up memory immediately.
    * 
    * @param filePath Path to the Excel file to cleanup
    */
  def cleanup(filePath: String): Unit =
    ExcelWorkbookCache.closeAndRemove(filePath)
  end cleanup
  
  /** Cleanup all cached Excel workbook resources.
    * 
    * This method is useful for application shutdown or when you want to
    * free up all Excel-related memory immediately.
    */
  def cleanupAll(): Unit =
    ExcelWorkbookCache.clearAll()
  end cleanupAll

end ExcelResourceManager