package io.github.quafadas.scautable

/** Parser for fixed-width text files.
  *
  * Supports both explicit column widths and automatic width inference by detecting padding characters.
  */
private[scautable] object FixedWidthParser:

  /** Infer column widths from multiple lines by finding positions that are consistently padding across all lines.
    *
    * Strategy: Analyze all lines to find positions where ALL lines have padding characters.
    * These positions are the column separators. This allows fields to contain single spaces
    * (like "New York") while correctly detecting column boundaries.
    *
    * @param lines The lines to analyze for column widths
    * @param paddingChar The character used for padding (default: space)
    * @return Sequence of column widths
    */
  def inferColumnWidthsFromMultipleLines(lines: Seq[String], paddingChar: Char = ' '): Seq[Int] =
    if lines.isEmpty then return Seq.empty

    val maxLen = lines.map(_.length).maxOption.getOrElse(0)
    if maxLen == 0 then return Seq.empty

    // Find positions that are consistently padding across ALL lines
    val separatorPositions = scala.collection.mutable.ListBuffer.empty[Int]
    for pos <- 0 until maxLen do
      val allPadding = lines.forall { line =>
        pos >= line.length || line.charAt(pos) == paddingChar
      }
      if allPadding then
        separatorPositions.append(pos)
    end for

    // Convert separator positions to column widths
    if separatorPositions.isEmpty then
      // No consistent separators found, treat as single column
      return Seq(maxLen)

    // Build column widths from separator positions.
    // Separators are regions where ALL lines have the padding character.
    // Each field width should include both the field content AND the separator that follows it.
    // This matches the parseLineWithWidths expectation that widths tell us where to jump to next.
    val widths = scala.collection.mutable.ListBuffer.empty[Int]
    var fieldStart = 0
    var inSeparator = false

    for pos <- 0 until maxLen do
      if separatorPositions.contains(pos) then
        inSeparator = true
      else
        if inSeparator then
          // Exiting a separator means we've found the end of the previous field+separator
          // Record the width from fieldStart to here (includes field content + separator)
          if pos > fieldStart then
            widths.append(pos - fieldStart)
          fieldStart = pos
          inSeparator = false
        end if
      end if
    end for

    // Add final field if we ended in content (not in a separator)
    if !inSeparator && fieldStart < maxLen then
      widths.append(maxLen - fieldStart)
    end if

    if widths.isEmpty then Seq(maxLen) else widths.toSeq
  end inferColumnWidthsFromMultipleLines

  /** Infer column widths from a line by finding runs of padding characters that separate fields.
    *
    * Strategy: A field ends where we find 2+ consecutive padding chars. The field width
    * includes all characters from the start of the field through the END of that padding run.
    *
    * This is for single-line inference. For better results with multiple lines,
    * use inferColumnWidthsFromMultipleLines.
    *
    * Example:
    * {{{
    * "Name      Age  City     "
    * // "Name" + "      " (6 spaces) -> double-space at position 4-5 ends first field
    * // First field width = from 0 through all the padding = 10
    * // "Age" + "  " (2 spaces) -> double space ends second field
    * // Second field width = 5
    * // "City" + "     " (trailing) -> end of line
    * // Third field width = 9
    * }}}
    *
    * @param line The line to analyze for column widths
    * @param paddingChar The character used for padding (default: space)
    * @return Sequence of column widths
    */
  def inferColumnWidths(line: String, paddingChar: Char = ' '): Seq[Int] =
    if line.isEmpty then return Seq.empty

    val widths = scala.collection.mutable.ListBuffer.empty[Int]
    var fieldStart = 0
    var consecutivePadding = 0
    var i = 0

    while i < line.length do
      if line(i) == paddingChar then
        consecutivePadding += 1
        // Check if we have 2+ consecutive padding (end of field)
        if consecutivePadding >= 2 then
          // Skip ahead to end of padding run
          while i + 1 < line.length && line(i + 1) == paddingChar do
            i += 1
            consecutivePadding += 1
          // Record field width (includes field content + all trailing padding)
          widths.append(i + 1 - fieldStart)
          fieldStart = i + 1
          consecutivePadding = 0
        end if
      else
        consecutivePadding = 0
      end if
      i += 1
    end while

    // Add final field if there's content after the last separator
    if fieldStart < line.length then
      widths.append(line.length - fieldStart)
    end if

    if widths.isEmpty then Seq(line.length) else widths.toSeq
  end inferColumnWidths

  /** Parse a line using explicit column widths.
    *
    * @param line The line to parse
    * @param columnWidths The width of each column
    * @param trimFields Whether to trim whitespace from fields
    * @return List of field values
    */
  def parseLineWithWidths(
      line: String,
      columnWidths: Seq[Int],
      trimFields: Boolean = true
  ): List[String] =
    var position = 0
    columnWidths.map { width =>
      val end = Math.min(position + width, line.length)
      val field = if position < line.length then
        line.substring(position, end)
      else
        "" // Past end of line
      position = end
      if trimFields then field.trim else field
    }.toList
  end parseLineWithWidths

  /** Parse a line using automatic width inference.
    *
    * Infers column widths by detecting padding character runs, then parses the line.
    *
    * @param line The line to parse
    * @param paddingChar The character used for padding (default: space)
    * @param trimFields Whether to trim whitespace from fields
    * @return List of field values
    */
  def parseLineWithInference(
      line: String,
      paddingChar: Char = ' ',
      trimFields: Boolean = true
  ): List[String] =
    val widths = inferColumnWidths(line, paddingChar)
    parseLineWithWidths(line, widths, trimFields)
  end parseLineWithInference

  /** Main parsing entry point - chooses inference or explicit width parsing.
    *
    * @param line The line to parse
    * @param columnWidths Optional explicit column widths (empty Seq triggers inference)
    * @param paddingChar The character used for padding (default: space)
    * @param trimFields Whether to trim whitespace from fields
    * @return List of field values
    */
  def parseLine(
      line: String,
      columnWidths: Seq[Int] = Seq.empty,
      paddingChar: Char = ' ',
      trimFields: Boolean = true
  ): List[String] =
    if columnWidths.isEmpty then
      parseLineWithInference(line, paddingChar, trimFields)
    else
      parseLineWithWidths(line, columnWidths, trimFields)
    end if
  end parseLine

end FixedWidthParser
