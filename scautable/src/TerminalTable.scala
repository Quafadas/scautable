package io.github.quafadas.scautable

/** A single row of a [[TerminalTable]], i.e. one cell of text per column. */
case class TableRow(cells: Seq[String])

/** A width-aware terminal table renderer.
  *
  * Unlike [[ConsoleFormat]], which always renders every cell at its natural width (and can therefore overflow arbitrarily wide terminals), `TerminalTable` shrinks columns to fit
  * the detected (or supplied) terminal width, truncating overflowing cell content with an ellipsis (`…`) rather than wrapping it onto additional lines.
  */
object TerminalTable:

  private val ellipsis = "…"

  /** Render `headers` + `rows` as a width-fitted, single-line-per-row table.
    *
    * @param headers
    *   column headers
    * @param rows
    *   data rows - each row's `cells` should have the same arity as `headers`, missing cells are treated as empty
    * @param widthOverride
    *   optional override instead of detecting the terminal width (useful for tests / non-tty output)
    */
  def render(headers: Seq[String], rows: Seq[TableRow], widthOverride: Option[Int] = None): String =
    val numCols = headers.length
    if numCols == 0 then ""
    else
      val width = widthOverride.getOrElse(detectWidth())

      val naturalWidths = headers.indices.map { i =>
        val headerLen = headers(i).length
        val cellsLen = rows.map(r => if i < r.cells.length then r.cells(i).length else 0)
        (headerLen +: cellsLen).max
      }

      // overhead: "| " + "cell" + " | " + "cell" + ... + " |" => 3 * numCols + 1
      val overhead = 3 * numCols + 1
      val budget = width - overhead

      val colWidths = allocateWidths(naturalWidths, budget)

      val sb = new StringBuilder
      sb.append(separatorLine(colWidths))
      sb.append('\n')
      sb.append(renderRow(headers, colWidths))
      sb.append('\n')
      sb.append(separatorLine(colWidths))
      sb.append('\n')
      for row <- rows do
        val cells = headers.indices.map(i => if i < row.cells.length then row.cells(i) else "")
        sb.append(renderRow(cells, colWidths))
        sb.append('\n')
      end for
      sb.append(separatorLine(colWidths))

      sb.toString
    end if
  end render

  /** Detect the terminal width, falling back to `fallback` when not attached to a real tty (or when detection isn't supported on the current platform).
    */
  def detectWidth(fallback: Int = 120): Int = platformDetectWidth(fallback)

  /** Allocate column widths given natural (max content) widths and a total character budget for content (i.e. excluding separators/padding), following a max-min fair-share
    * ("water-filling") allocation - modeled on comfy-table's `dynamic::arrange` (see the issue this implements for full rationale). "Water-filling" here means columns that don't
    * need much space are left untouched at their natural width, while the remaining budget is "poured" evenly over the columns that do need more, rather than shrinking every
    * column by the same proportion.
    *
    * The three phases below are applied in order, each removing columns from further consideration ("freezing" them at a fixed width) as they're decided:
    *
    *   1. '''Floor enforcement''': if the average budget-per-undecided-column would fall below `minColumnWidth`, freeze columns at `minColumnWidth` (not their natural width) until
    *      the average recovers or everything is frozen at the floor. This only bites when the terminal is genuinely too narrow for every column to have breathing room - in that
    *      case some/all columns are simply clamped at `minColumnWidth` (their content will then be truncated by [[truncateCell]]).
    *   1. '''Give away columns that already fit''': freeze any column whose natural width is `<=` the current average at its full natural width (never squeeze a column that
    *      already fits).
    *   1. '''Distribute what's left equally''': split the remaining budget evenly amongst columns still undecided, handing out any remainder (from integer division) one character
    *      at a time, left to right, so no single column absorbs the whole remainder.
    *
    * @return
    *   one width per input column, in the same order as `naturalWidths`; widths always sum to `<= max(totalBudget, 0)` and are always `>= minColumnWidth` (even if `totalBudget` is
    *   smaller than `minColumnWidth * naturalWidths.size`, in which case the budget is simply exceeded rather than raising an exception).
    */
  private[scautable] def allocateWidths(naturalWidths: Seq[Int], totalBudget: Int, minColumnWidth: Int = 3): Seq[Int] =
    if naturalWidths.isEmpty then Seq.empty
    else
      val n = naturalWidths.length
      val result = Array.fill(n)(-1)
      var remaining = math.max(totalBudget, 0)
      var undecided = (0 until n).toSet

      // 1. Enforce the minimum-width floor first.
      var floorChanged = true
      while floorChanged && undecided.nonEmpty do
        floorChanged = false
        val average = remaining / undecided.size
        if average < minColumnWidth then
          // Iterate a stable snapshot of the undecided set; each `i` here is only visited once and hasn't been
          // removed yet by this same loop, so no `undecided.contains(i)` guard is needed.
          for i <- undecided.toSeq.sorted do
            result(i) = minColumnWidth
            remaining -= minColumnWidth
            undecided -= i
            floorChanged = true
        end if
      end while

      // 2. Give away columns that already fit into the average.
      var foundFit = true
      while foundFit && undecided.nonEmpty do
        foundFit = false
        val average = remaining / undecided.size
        for i <- undecided.toSeq.sorted do
          if naturalWidths(i) <= average then
            result(i) = naturalWidths(i)
            remaining -= naturalWidths(i)
            undecided -= i
            foundFit = true
        end for
      end while

      // 3. Distribute what's left equally, with fair remainder handling.
      if undecided.nonEmpty then
        val ordered = undecided.toSeq.sorted
        val average = remaining / ordered.size
        val excess = remaining - average * ordered.size
        for (i, idx) <- ordered.zipWithIndex do result(i) = average + (if idx < excess then 1 else 0)
        end for
      end if

      result.toSeq
  end allocateWidths

  /** Truncate a single cell to `width`, appending an ellipsis if truncated. Content already within `width` is returned unchanged.
    */
  private[scautable] def truncateCell(content: String, width: Int): String =
    if width <= 0 then ""
    else if content.length <= width then content
    else if width == 1 then ellipsis
    else content.take(width - 1) + ellipsis
  end truncateCell

  private def renderRow(cells: Seq[String], colWidths: Seq[Int]): String =
    // Uses `String#length` (UTF-16 code units) for width/padding, not code-point-aware display width; per the
    // issue's non-goals, correctness for wide/CJK or zero-width/combining characters is not required for v1.
    val padded = cells.zip(colWidths).map { (cell, w) =>
      val truncated = truncateCell(if cell == null then "" else cell, w)
      truncated + (" " * (w - truncated.length))
    }
    padded.mkString("| ", " | ", " |")
  end renderRow

  private def separatorLine(colWidths: Seq[Int]): String =
    colWidths.map(w => "-" * (w + 2)).mkString("+", "+", "+")

end TerminalTable
