package io.github.quafadas.scautable

import fansi.Str
import scala.NamedTuple.*
import scala.compiletime.constValueTuple
import scala.annotation.experimental
import scala.math.Numeric.Implicits.*

@experimental
object ConsoleFormat:

  extension(s : Seq[Product])
    def consoleFormat(fancy: Boolean): String = consoleFormat_(s, fancy)
    def consoleFormat: String = consoleFormat_(s, true)

  private val colours = List(
    fansi.Color.Green,
    fansi.Color.White,
    fansi.Color.Red,
    fansi.Color.Blue,
    fansi.Color.Magenta,
    fansi.Color.Cyan,
    fansi.Color.White,
    fansi.Color.Green,
    fansi.Color.Magenta,
    fansi.Color.White
  )

  extension[A](a: A)(using numA: Numeric[A])
    inline def formatAsPercentage: String =
      if (a == 0)
        "0.00%"
      else
        val a100 = BigDecimal(numA.toDouble(a) * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP)
        f"$a100%.2f%%"

  extension [K <: Tuple, V <: Tuple](nt: Seq[NamedTuple[K, V]])

    inline def consoleFormatNt: String=
      consoleFormatNt(None, true)
    end consoleFormatNt

    inline def consoleFormatNt(headers: Option[List[String]] = None, fansi: Boolean = true): String =
      val foundHeaders = constValueTuple[K].toList.map(_.toString())
      val values = nt.map(_.toTuple)
      ConsoleFormat.consoleFormat_(values, fansi, headers.getOrElse(foundHeaders))
    end consoleFormatNt
  end extension

  inline def makeFancy(s: String, i: Int): Str =
    val idx = i % colours.length
    colours(idx)(s)
  end makeFancy

  inline def printlnConsole_(table: Seq[Product], fancy: Boolean = false) = println(consoleFormat_(table, fancy))

  inline def consoleFormat_(table: Seq[Product], fancy: Boolean = true): String =
    consoleFormat_(table, fancy, table.head.productElementNames.toList)

  inline def consoleFormat_(table: Seq[Product], fancy: Boolean, headers: List[String]): String = table match
    case Seq() => ""
    case _ =>
      val indexLen = table.length.toString.length
      val sizes =
        for (row <- table)
          yield (for (cell <- row.productIterator.toSeq) yield if cell == null then 0 else cell.toString.length)
      val headSizes = for (i <- headers) yield headers.toString()
      val colSizes =
        for ((col, header) <- sizes.transpose.zip(headers)) yield Seq(header.toString().length(), col.max).max
      val colSizesWithIndex = indexLen +: colSizes
      val rows =
        for ((row, i) <- table.zipWithIndex)
          yield
            if fancy then formatFancyRow((i +: row.productIterator.toSeq).zipWithIndex, colSizesWithIndex)
            else formatRow(i +: row.productIterator.toSeq, colSizesWithIndex)

      if fancy then
        formatFancyHeader((Str("") +: headers.map(Str(_))).zipWithIndex, colSizesWithIndex) ++ formatRows(
          rowSeparator(colSizesWithIndex),
          rows
        )
      else formatHeader("" +: headers, colSizesWithIndex) ++ formatRows(rowSeparator(colSizesWithIndex), rows)
      end if

  inline private def formatRows(rowSeparator: String, rows: Seq[String]): String = (rowSeparator ::
    rows.head ::
    rows.tail.toList :::
    rowSeparator ::
    List()).mkString("\n")

  inline private def formatFancyRows(rowSeparator: Str, rows: Seq[String]): String = (rowSeparator ::
    rows.head ::
    rows.tail.toList :::
    rowSeparator ::
    List()).mkString("\n")

  private def formatRow(row: Seq[Any], colSizes: Seq[Int]) =
    val cells =
      (for ((item, size) <- row.zip(colSizes)) yield if size == 0 then "" else ("%" + size + "s").format(item))
    cells.mkString("|", "|", "|")
  end formatRow

  inline private def formatFancyRow(row: Seq[(Any, Int)], colSizes: Seq[Int]) =
    val cells = (for ((item, size) <- row.zip(colSizes)) yield
      val raw = if size == 0 then "" else ("%" + size + "s").format(item._1)
      makeFancy(raw, item._2)
    )

    cells.mkString("|", "|", "|")
  end formatFancyRow

  inline private def formatFancyHeader(row: Seq[(Str, Int)], colSizes: Seq[Int]) =
    val cells = (for ((item, size) <- row.zip(colSizes)) yield
      val raw = if size == 0 then "" else ("%" + size + "s").format(item._1)
      makeFancy(raw, item._2)
    )

    cells.mkString("|", "|", "|") + "\n"
  end formatFancyHeader

  inline private def formatHeader(row: Seq[String], colSizes: Seq[Int]) =
    val cells =
      (for ((item, size) <- row.zip(colSizes)) yield if size == 0 then "" else ("%" + size + "s").format(item))
    cells.mkString("|", "|", "|") + "\n"
  end formatHeader

  inline private def rowSeparator(colSizes: Seq[Int]) = colSizes map { "-" * _ } mkString ("+", "+", "+")

end ConsoleFormat

