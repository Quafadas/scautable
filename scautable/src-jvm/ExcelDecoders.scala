package io.github.quafadas.scautable

/** Excel-specific decoders that can handle numeric strings like "1.0" These decoders are designed to work with Excel's tendency to format integers as doubles (e.g., "1.0" instead
  * of "1")
  */
object ExcelDecoders:

  /** Decoder for Int that can handle Excel's numeric formatting Attempts to parse as Double first, then converts to Int if it's a whole number Falls back to regular Int parsing if
    * Double parsing fails
    */
  inline given excelIntDecoder: Decoder[Int] with
    def decode(str: String): Option[Int] =
      str.toDoubleOption
        .flatMap { d =>
          if d.isWhole && d >= Int.MinValue && d <= Int.MaxValue then Some(d.toInt)
          else None
        }
        .orElse(str.toIntOption) // fallback to regular int parsing
  end excelIntDecoder

  /** Decoder for Long that can handle Excel's numeric formatting Attempts to parse as Double first, then converts to Long if it's a whole number Falls back to regular Long parsing
    * if Double parsing fails
    */
  inline given excelLongDecoder: Decoder[Long] with
    def decode(str: String): Option[Long] =
      str.toDoubleOption
        .flatMap { d =>
          if d.isWhole && d >= Long.MinValue && d <= Long.MaxValue then Some(d.toLong)
          else None
        }
        .orElse(str.toLongOption) // fallback to regular long parsing
  end excelLongDecoder

end ExcelDecoders
