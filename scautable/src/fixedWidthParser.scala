package io.github.quafadas.scautable

/** Parser for fixed-width text files.
  *
  * Supports both explicit column widths and automatic width inference by detecting padding characters.
  */
private[scautable] object FixedWidthParser:

  /** Infer column widths from a line by finding runs of padding characters that separate fields.
    *
    * Strategy: A field ends where we find 2+ consecutive padding chars. The field width
    * includes all characters from the start of the field through the END of that padding run.
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
    var i = 0
    
    while i < line.length do
      // Look for 2+ consecutive padding chars
      if line.charAt(i) == paddingChar then
        var paddingCount = 1
        var j = i + 1
        while j < line.length && line.charAt(j) == paddingChar do
          paddingCount += 1
          j += 1
        end while
        
        // If we found 2+ padding chars, this ends the current field
        if paddingCount >= 2 then
          // Field includes everything from fieldStart through end of padding
          widths.append(j - fieldStart)
          // Next field starts after all the padding
          fieldStart = j
          i = j
        else
          // Single padding char - part of field content, continue
          i += 1
        end if
      else
        i += 1
      end if
    end while
    
    // Handle the last field (everything remaining from fieldStart to end)
    if fieldStart < line.length then
      widths.append(line.length - fieldStart)
    end if
    
    widths.toSeq
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
