package io.github.quafadas.scautable

import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

import scala.util.Try

import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory

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
      val existingWorkbook = Option(cachedRef).flatMap(ref => Option(ref.get())).filter(isUsable)

      existingWorkbook match
        case Some(workbook) => workbook
        case None           =>
          // Either nothing cached, or what was cached has been garbage collected or closed underneath us.
          //
          // `compute` rather than get-then-put: the whole point of the cache is that one file yields one workbook, and a plain check-then-act lets two threads each open one, with
          // whichever loses the race left open and unreferenced. Each open holds an OS file handle, so losing that race leaks one.
          cache
            .compute(
              normalizedPath,
              (_, existing) =>
                val live = Option(existing).flatMap(ref => Option(ref.get())).filter(isUsable)
                live match
                  case Some(_) => existing // another thread got there first; keep its workbook
                  case None    => new WeakReference(WorkbookFactory.create(new File(normalizedPath), null, true))
                end match
            )
            .get()
      end match
    }
  end getOrCreate

  /** Whether a cached workbook can still be read from. A workbook closed behind the cache's back throws from any access, and has to be replaced rather than handed out. */
  private def isUsable(workbook: Workbook): Boolean =
    try
      workbook.getNumberOfSheets
      true
    catch case _: Exception => false
  end isUsable

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
