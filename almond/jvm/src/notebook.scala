package io.github.quafadas.scautable

import scala.NamedTuple.*

import almond.interpreter.api.OutputHandler

/** Display named tuple tables in an [almond](https://almond.sh) notebook.
  *
  * {{{
  * import io.github.quafadas.table.*
  * import io.github.quafadas.scautable.notebook.{*, given}
  *
  * val data = CSV.resource("simple.csv").toSeq
  * data.display()     // head and tail if above 60 rows
  * data.displayAll    // every row
  * data.ptbln         // with `notebook.given` imported, the same as display()
  * }}}
  *
  * Headers are taken from the named tuple's type at the call site - at runtime the rows are plain tuples, which is why almond's `Displayers` can't do this.
  *
  * `display` is only offered on `Iterable`s. Displaying an `Iterator` would consume it, so materialise it first (`.toSeq`, `.toVector`). `ptbln` still accepts an `Iterator` (as it
  * always has), and will consume it.
  */
object notebook:

  extension [K <: Tuple, V <: Tuple](rows: Iterable[NamedTuple[K, V]])

    /** Build the [[NotebookTable]] that [[display]] would publish, without publishing it. Useful for `.displayData.withId(...)` and later `.update()`. */
    inline def notebookTable(
        maxRows: Int = NotebookTable.DefaultMaxRows,
        edgeRows: Int = NotebookTable.DefaultEdgeRows,
        dataResource: Boolean = false
    ): NotebookTable =
      NotebookTable.from(rows, maxRows, edgeRows, dataResource)

    /** Publish the table to the notebook as `text/html` and `text/plain` (and optionally `application/vnd.dataresource+json`).
      *
      * @param maxRows
      *   tables with more rows than this show only `edgeRows` from each end, with a "… n more rows" marker between
      * @param edgeRows
      *   rows shown at each end of a truncated table
      * @param dataResource
      *   also publish the first `maxRows` rows as a Frictionless data resource, for frontends with a data-explorer extension
      */
    inline def display(
        maxRows: Int = NotebookTable.DefaultMaxRows,
        edgeRows: Int = NotebookTable.DefaultEdgeRows,
        dataResource: Boolean = false
    )(using out: OutputHandler): Unit =
      out.display(NotebookTable.from(rows, maxRows, edgeRows, dataResource).displayData)

    /** Publish every row, with no truncation. */
    inline def displayAll(using out: OutputHandler): Unit =
      out.display(NotebookTable.from(rows, Int.MaxValue, 0, false).displayData)

  end extension

  /** Sends `ptbln` to the notebook (as [[display]] with its defaults) rather than the console. Brought in by `import notebook.given`. */
  given notebookSink(using out: OutputHandler): TableSink = (headers, formatters, rows) =>
    out.display(NotebookTable.build(headers, formatters, None, rows, NotebookTable.DefaultMaxRows, NotebookTable.DefaultEdgeRows).displayData)

end notebook
