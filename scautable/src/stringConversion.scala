package io.github.quafadas.scautable

case class ConversionAcc(validInts: Long, validDoubles: Long, validLongs: Long)

def recommendConversion(acc: List[ConversionAcc], rowCount: Long): String = {
  def percentage(validCount: Long): Double = (validCount.toDouble / rowCount) * 100

  acc.map { case ConversionAcc(validInts, validDoubles, validLongs) =>
    val intPct = percentage(validInts)
    val longPct = percentage(validLongs)
    val doublePct = percentage(validDoubles)

    if (intPct >= 75.0 && intPct >= longPct && intPct >= doublePct) "Int"
    else if (longPct >= 75.0 && longPct >= doublePct) "Long"
    else if (doublePct >= 75.0) "Double"
    else "String"
  }.mkString(", ")
}

