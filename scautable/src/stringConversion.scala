package io.github.quafadas.scautable

private case class ConversionAcc(validInts: Long, validDoubles: Long, validLongs: Long)

private def recommendConversion(acc: List[ConversionAcc], rowCount: Long): String =
  def percentage(validCount: Long): Double = (validCount.toDouble / rowCount) * 100

  acc
    .map { case ConversionAcc(validInts, validDoubles, validLongs) =>
      val intPct = percentage(validInts)
      val longPct = percentage(validLongs)
      val doublePct = percentage(validDoubles)

      if intPct >= 75.0 && intPct >= longPct && intPct >= doublePct then "Int"
      else if longPct >= 75.0 && longPct >= doublePct then "Long"
      else if doublePct >= 75.0 then "Double"
      else "String"
      end if
    }
    .mkString(", ")
end recommendConversion
