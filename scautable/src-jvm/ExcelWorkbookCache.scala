package io.github.quafadas.scautable

import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import scala.util.Try

/** Thread-safe cache for Excel workbook instances to avoid file contention and improve performance.
  *
  * This cache uses weak references to allow workbooks to be garbage collected when no longer needed, while providing significant performance improvements when multiple operations
  * access the same Excel file.
  */
object ExcelWorkbookCache:

  // Thread-safe cache using weak references to allow GC when workbooks are no longer used
  private val cache = new ConcurrentHashMap[String, WeakReference[Workbook]]()

  /** Get or create a workbook for the specified file path.
    *
    * This method is thread-safe and will reuse existing workbook instances when possible. If a workbook is garbage collected, a new one will be created automatically.
    *
    * @param filePath
    *   The absolute path to the Excel file
    * @return
    *   A Try containing the Workbook instance, or a Failure if the file cannot be opened
    */
  def getOrCreate(filePath: String): Try[Workbook] =
    Try {
      // Normalize the file path to handle different path representations
      val normalizedPath = new File(filePath).getCanonicalPath

      // Try to get existing workbook from cache
      val cachedRef = cache.get(normalizedPath)
      val existingWorkbook = Option(cachedRef).flatMap(ref => Option(ref.get()))

      existingWorkbook match
        case Some(workbook) =>
          // Validate that the workbook is still usable (not closed)
          try
            // Simple validation - try to access the number of sheets
            workbook.getNumberOfSheets
            workbook
          catch
            case _: Exception =>
              // Workbook is no longer valid, remove from cache and create new one
              cache.remove(normalizedPath)
              WorkbookFactory.create(new File(normalizedPath))
        case None =>
          // No cached workbook or it was garbage collected
          val workbook = WorkbookFactory.create(new File(normalizedPath))
          cache.put(normalizedPath, new WeakReference(workbook))
          workbook
      end match
    }
  end getOrCreate

  /** Explicitly close and remove a workbook from the cache.
    *
    * This method should be called when you know a workbook will no longer be needed to free up resources immediately rather than waiting for garbage collection.
    *
    * @param filePath
    *   The path to the Excel file
    */
  def closeAndRemove(filePath: String): Unit =
    try
      val normalizedPath = new File(filePath).getCanonicalPath
      val cachedRef = cache.remove(normalizedPath)

      Option(cachedRef).flatMap(ref => Option(ref.get())).foreach { workbook =>
        try workbook.close()
        catch
          case _: Exception =>
          // Ignore close errors - workbook might already be corrupted/closed
          // This is expected for Excel files with certain drawing corruption issues
        end try
      }
    catch
      case _: Exception =>
      // Ignore errors in cleanup - this is a best-effort operation
    end try
  end closeAndRemove

  /** Clear the entire cache and attempt to close all cached workbooks.
    *
    * This is primarily useful for testing or application shutdown.
    */
  def clearAll(): Unit =
    val keys = cache.keySet().toArray(Array.empty[String])
    keys.foreach(closeAndRemove)
  end clearAll

  /** Get the current number of cached workbook references.
    *
    * Note that this includes weak references that may have been garbage collected. This method is primarily useful for testing and debugging.
    *
    * @return
    *   The number of cached workbook references
    */
  def cacheSize: Int = cache.size()

end ExcelWorkbookCache
