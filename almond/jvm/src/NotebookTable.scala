package io.github.quafadas.scautable

import scala.NamedTuple.*
import scala.collection.mutable
import scala.compiletime.constValueTuple
import scala.compiletime.erasedValue

import almond.interpreter.api.DisplayData

/** A table, already reduced to what a notebook will show: headers, formatted cells for the head (and, when truncated, the tail), and the total row count.
  *
  * Built at the call site by [[NotebookTable.from]], so the headers come from the named tuple's type and are present even when there are no rows. Rendering is plain runtime code
  * from here on.
  *
  * @param headers
  *   column names, in order
  * @param head
  *   formatted rows shown from the start of the table. When not truncated, this is every row.
  * @param tail
  *   formatted rows shown from the end of the table. Empty unless truncated.
  * @param totalRows
  *   the number of rows in the source collection
  * @param dataResource
  *   an `application/vnd.dataresource+json` payload, if requested
  */
final case class NotebookTable(
    headers: Seq[String],
    head: Seq[Seq[String]],
    tail: Seq[Seq[String]],
    totalRows: Int,
    dataResource: Option[String] = None
):
  /** Rows not shown, i.e. those replaced by the "… n more rows" marker. */
  def omittedRows: Int = totalRows - head.size - tail.size

  def isTruncated: Boolean = omittedRows > 0

  private def tailStart: Int = totalRows - tail.size

  /** The `text/html` rendering: a scrollable container with a sticky header row, styled through a scoped class and no external CSS or JS. */
  def html: String =
    import scalatags.Text.all.*
    val (headRows, tailRows) = (this.head, this.tail) // scalatags exports its own `head`
    def dataRow(idx: Int, cells: Seq[String]) = tr(th(idx), cells.map(td(_)))
    val gap =
      if isTruncated then Seq(tr(cls := "scautable-nb-gap", td(colspan := headers.size + 1, s"… $omittedRows more rows")))
      else Seq.empty
    val body =
      headRows.zipWithIndex.map((r, i) => dataRow(i, r)) ++ gap ++ tailRows.zipWithIndex.map((r, i) => dataRow(tailStart + i, r))
    val footer =
      if isTruncated then Seq(p(cls := "scautable-nb-dims", s"$totalRows rows × ${headers.size} columns")) else Seq.empty
    div(cls := "scautable-nb")(
      tag("style")(raw(NotebookTable.css)),
      div(cls := "scautable-nb-scroll")(
        table(thead(tr(th(), headers.map(th(_)))), tbody(body))
      ),
      footer
    ).render
  end html

  /** The `text/plain` rendering, for consoles, exports and `nbconvert`. Cells are never truncated. */
  def text: String =
    val idxRows = head.zipWithIndex.map((r, i) => TableRow(i.toString +: r))
    val gap = if isTruncated then Seq(TableRow(Seq.fill(headers.size + 1)("…"))) else Seq.empty
    val tailRows = tail.zipWithIndex.map((r, i) => TableRow((tailStart + i).toString +: r))
    val rendered = TerminalTable.render("" +: headers, idxRows ++ gap ++ tailRows, widthOverride = Some(Int.MaxValue / 2), colour = false)
    if isTruncated then s"$rendered\n… $omittedRows more rows [$totalRows rows × ${headers.size} columns]"
    else rendered
    end if
  end text

  /** The full MIME bundle published to the notebook. */
  def displayData: DisplayData =
    val base = DisplayData(Map("text/html" -> html, "text/plain" -> text))
    dataResource.fold(base)(json => base.addStringifiedJson(NotebookTable.DataResourceMime, json))
  end displayData

end NotebookTable

object NotebookTable:

  /** Above this many rows, a table is truncated. Matches pandas' `display.max_rows`. */
  val DefaultMaxRows = 60

  /** How many rows to show at each end of a truncated table. Matches pandas' `display.min_rows` of 10, split head and tail. */
  val DefaultEdgeRows = 5

  val DataResourceMime = "application/vnd.dataresource+json"

  private val css =
    """.scautable-nb .scautable-nb-scroll{max-height:400px;overflow:auto;}
      |.scautable-nb table{border-collapse:collapse;font-size:12px;font-variant-numeric:tabular-nums;}
      |.scautable-nb th,.scautable-nb td{padding:4px 8px;text-align:right;white-space:nowrap;border-bottom:1px solid rgba(127,127,127,0.25);}
      |.scautable-nb thead th{position:sticky;top:0;z-index:1;background:var(--jp-layout-color0,var(--vscode-editor-background,#fff));border-bottom:1px solid rgba(127,127,127,0.6);}
      |.scautable-nb tbody th{font-weight:bold;opacity:0.7;}
      |.scautable-nb tbody tr:hover{background:rgba(127,127,127,0.1);}
      |.scautable-nb .scautable-nb-gap td{text-align:center;font-style:italic;opacity:0.7;}
      |.scautable-nb .scautable-nb-dims{margin:4px 0;font-size:12px;opacity:0.7;}""".stripMargin

  /** Reduce `rows` to a [[NotebookTable]] in a single pass.
    *
    * At most `maxRows` rows are held (by reference) while counting, plus a ring of the last `edgeRows`, so only the rows actually shown are ever formatted. Cells go through the
    * same [[CellFormat]] lookup as `ptbln` and `html`, so format tags apply here too.
    *
    * @param maxRows
    *   tables with more rows than this are truncated
    * @param edgeRows
    *   rows shown at each end of a truncated table
    * @param dataResource
    *   if true, also produce an `application/vnd.dataresource+json` payload of the first `maxRows` rows (as pandas does), for frontends with a data explorer
    */
  inline def from[K <: Tuple, V <: Tuple](
      rows: Iterable[NamedTuple[K, V]],
      maxRows: Int,
      edgeRows: Int,
      dataResource: Boolean
  ): NotebookTable =
    val headers = constValueTuple[K].toList.map(_.toString)
    val types = if dataResource then Some(schemaTypes[V]) else None
    build(headers, CellFormat.formattersFor[V].toArray, types, rows.iterator.map(_.toTuple), maxRows, edgeRows)
  end from

  private[scautable] def build(
      headers: List[String],
      fmts: Array[Any => String],
      schemaTypes: Option[List[String]],
      rows: Iterator[Product],
      maxRows: Int,
      edgeRows: Int
  ): NotebookTable =
    require(maxRows >= 0, s"maxRows must be non-negative, was $maxRows")
    require(edgeRows >= 0, s"edgeRows must be non-negative, was $edgeRows")

    val prefix = mutable.ArrayBuffer.empty[Product]
    val ring = new Array[Product](edgeRows)
    var total = 0
    for row <- rows do
      if total < maxRows then prefix += row
      end if
      if edgeRows > 0 then ring(total % edgeRows) = row
      end if
      total += 1
    end for

    def format(row: Product): Seq[String] = CellFormat.formatRow(row, fmts)

    val resource = schemaTypes.map(types => dataResourceJson(headers, types, prefix.toSeq))

    if total <= maxRows then NotebookTable(headers, prefix.toSeq.map(format), Seq.empty, total, resource)
    else
      // Always hide at least one row, otherwise the marker would claim "0 more rows".
      val edge = math.min(edgeRows, (total - 1) / 2)
      val tail = (total - edge until total).map(i => ring(i % edgeRows))
      NotebookTable(headers, prefix.take(edge).toSeq.map(format), tail.map(format), total, resource)
    end if
  end build

  /** Frictionless Table Schema field types for each element of `T`. Format tags are bounded opaque types, so they resolve to the type underneath. */
  inline def schemaTypes[T <: Tuple]: List[String] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (h *: t)   => schemaType[h] :: schemaTypes[t]

  private inline def schemaType[A]: String =
    inline erasedValue[A] match
      case _: Option[a]               => schemaType[a]
      case _: Int                     => "integer"
      case _: Long                    => "integer"
      case _: Short                   => "integer"
      case _: Byte                    => "integer"
      case _: BigInt                  => "integer"
      case _: Double                  => "number"
      case _: Float                   => "number"
      case _: BigDecimal              => "number"
      case _: Boolean                 => "boolean"
      case _: java.time.LocalDate     => "date"
      case _: java.time.LocalDateTime => "datetime"
      case _: java.time.LocalTime     => "time"
      case _                          => "string"

  private def dataResourceJson(headers: List[String], types: List[String], rows: Seq[Product]): String =
    val fields = headers.zip(types).map((h, t) => s"""{"name":${jsonString(h)},"type":${jsonString(t)}}""").mkString("[", ",", "]")
    val data = rows
      .map(row => headers.iterator.zip(row.productIterator).map((h, v) => s"${jsonString(h)}:${jsonValue(v)}").mkString("{", ",", "}"))
      .mkString("[", ",", "]")
    s"""{"schema":{"fields":$fields},"data":$data}"""
  end dataResourceJson

  private def jsonValue(v: Any): String = v match
    case null | None                                     => "null"
    case Some(inner)                                     => jsonValue(inner)
    case d: Double if d.isNaN || d.isInfinite            => "null"
    case f: Float if f.isNaN || f.isInfinite             => "null"
    case n: (Int | Long | Short | Byte | Double | Float) => n.toString
    case n: (BigInt | BigDecimal)                        => n.toString
    case b: Boolean                                      => b.toString
    case other                                           => jsonString(other.toString)

  private def jsonString(s: String): String =
    val sb = new StringBuilder(s.length + 2)
    sb += '"'
    s.foreach {
      case '"'          => sb ++= "\\\""
      case '\\'         => sb ++= "\\\\"
      case '\n'         => sb ++= "\\n"
      case '\r'         => sb ++= "\\r"
      case '\t'         => sb ++= "\\t"
      case c if c < ' ' => sb ++= f"\\u${c.toInt}%04x"
      case c            => sb += c
    }
    sb += '"'
    sb.toString
  end jsonString

end NotebookTable
