package io.github.quafadas.scautable

import scala.NamedTuple.*
import scala.compiletime.constValueTuple

import fansi.EscapeAttr
import fansi.Str

object ConsoleFormat:

  extension [C <: IterableOnce[Product]](s: C)
    def consoleFormat(fancy: Boolean): String =
      if fancy then consoleFormat
      else consoleFormat_(s.iterator.toSeq, false)

    def consoleFormat: String =
      val materialise = s.iterator.toSeq
      val headers = if materialise.isEmpty then Seq.empty else (0 until materialise.head.productArity).map(i => s"col${i + 1}")
      TerminalTable.render(
        headers,
        materialise.map(row => TableRow(row.productIterator.toSeq.map(_.toString))).toSeq
      )
    end consoleFormat
    def ptbl: Unit = println(consoleFormat)
  end extension

  private val colours: List[EscapeAttr] = List(
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

  extension [A](a: A)(using numA: Numeric[A]) def formatAsPercentage: String = CellFormat.percentage(numA.toDouble(a), 2)
  end extension

  extension [K <: Tuple, V <: Tuple, C <: IterableOnce[NamedTuple[K, V]]](nt: C)

    inline def consoleFormatNt: String =
      val rows: Seq[Product] = nt.iterator.map(_.toTuple).toSeq
      TerminalTable.render(
        constValueTuple[K].toList.map(_.toString()),
        formatRows[V](rows)
      )
    end consoleFormatNt

    inline def ptbln: Unit = println(nt.consoleFormatNt)

    inline def html: String = HtmlRenderer.nt(nt).render

    inline def consoleFormatNt(headers: Option[List[String]] = None, fansi: Boolean = true): String =
      if fansi then consoleFormatNt
      else
        val foundHeaders = constValueTuple[K].toList.map(_.toString())
        val values = nt.iterator.map(_.toTuple).toSeq
        ConsoleFormat.consoleFormat_(formatProducts[V](values), fansi, headers.getOrElse(foundHeaders))
    end consoleFormatNt
  end extension

  /** Render each cell of `rows` through the `CellFormat` for its column type, falling back to `toString`. Formatters are resolved once, not per row. */
  private inline def formatRows[V <: Tuple](rows: Seq[Product]): Seq[TableRow] =
    val fmts = CellFormat.formattersFor[V].toArray
    rows.map(row => TableRow(row.productIterator.toSeq.zipWithIndex.map((cell, i) => if i < fmts.length then fmts(i)(cell) else CellFormat.toStringFormat(cell))))
  end formatRows

  /** As [[formatRows]], but yielding `Product`s of pre-formatted strings for the legacy `consoleFormat_` path. */
  private inline def formatProducts[V <: Tuple](rows: Seq[Product]): Seq[Product] =
    formatRows[V](rows).map(r => Tuple.fromArray(r.cells.toArray))

  private def makeFancy(s: String, i: Int): Str =
    val idx = i % colours.length
    colours(idx)(s)
  end makeFancy

  def printlnConsole_(table: Seq[Product], fancy: Boolean = false) = println(consoleFormat_(table, fancy))

  def consoleFormat_(table: Seq[Product], fancy: Boolean = true): String =
    consoleFormat_(table, fancy, table.head.productElementNames.toList)

  def consoleFormat_(table: Seq[Product], fancy: Boolean, headers: List[String]): String = table match
    case Seq() => ""
    case _     =>
      val indexLen = table.length.toString.length
      val sizes =
        for row <- table
        yield (for cell <- row.productIterator.toSeq yield if cell == null then 0 else cell.toString.length)
      val headSizes = for i <- headers yield headers.toString()
      val colSizes =
        for (col, header) <- sizes.transpose.zip(headers) yield Seq(header.toString().length(), col.max).max
      val colSizesWithIndex = indexLen +: colSizes
      val rows =
        for (row, i) <- table.zipWithIndex
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

  private def formatRows(rowSeparator: String, rows: Seq[String]): String = (rowSeparator ::
    rows.head ::
    rows.tail.toList :::
    rowSeparator ::
    List()).mkString("\n")

  private def formatFancyRows(rowSeparator: Str, rows: Seq[String]): String = (rowSeparator ::
    rows.head ::
    rows.tail.toList :::
    rowSeparator ::
    List()).mkString("\n")

  private def formatRow(row: Seq[Any], colSizes: Seq[Int]) =
    val cells =
      (for (item, size) <- row.zip(colSizes) yield if size == 0 then "" else ("%" + size + "s").format(item))
    cells.mkString("|", "|", "|")
  end formatRow

  private def formatFancyRow(row: Seq[(Any, Int)], colSizes: Seq[Int]) =
    val cells = (for (item, size) <- row.zip(colSizes) yield
      val raw = if size == 0 then "" else ("%" + size + "s").format(item._1)
      makeFancy(raw, item._2)
    )

    cells.mkString("|", "|", "|")
  end formatFancyRow

  private def formatFancyHeader(row: Seq[(Str, Int)], colSizes: Seq[Int]) =
    val cells = (for (item, size) <- row.zip(colSizes) yield
      val raw = if size == 0 then "" else ("%" + size + "s").format(item._1)
      makeFancy(raw, item._2)
    )

    cells.mkString("|", "|", "|") + "\n"
  end formatFancyHeader

  private def formatHeader(row: Seq[String], colSizes: Seq[Int]) =
    val cells =
      (for (item, size) <- row.zip(colSizes) yield if size == 0 then "" else ("%" + size + "s").format(item))
    cells.mkString("|", "|", "|") + "\n"
  end formatHeader

  private def rowSeparator(colSizes: Seq[Int]) = colSizes map { "-" * _ } mkString ("|", "|", "|")

end ConsoleFormat
