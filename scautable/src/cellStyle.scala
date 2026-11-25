package io.github.quafadas.scautable

import scala.NamedTuple.*
import scala.annotation.implicitNotFound
import scala.annotation.publicInBinary
import scala.compiletime.constValueTuple

import io.github.quafadas.scautable.ColumnTyped.*

/** A colour for [[CellStyle]].
  *
  * The named colours map to the terminal's own ANSI palette, so they follow its theme; in HTML they map to fixed values. `Rgb` is exact in both, and needs a truecolor terminal.
  */
enum Colour:
  case Black, Red, Green, Yellow, Blue, Magenta, Cyan, White, Grey
  case Rgb(r: Int, g: Int, b: Int)

  /** The colour as RGB - what HTML uses, and what contrast is judged on. */
  def rgb: (Int, Int, Int) = this match
    case Black        => (0, 0, 0)
    case Red          => (214, 39, 40)
    case Green        => (44, 160, 44)
    case Yellow       => (255, 221, 87)
    case Blue         => (31, 119, 180)
    case Magenta      => (196, 72, 196)
    case Cyan         => (23, 190, 207)
    case White        => (255, 255, 255)
    case Grey         => (190, 190, 190)
    case Rgb(r, g, b) => (r, g, b)

  def css: String =
    val (r, g, b) = rgb
    s"rgb($r,$g,$b)"
  end css

  /** Whether dark text reads better than light text on this colour, by relative luminance. */
  def isLight: Boolean =
    val (r, g, b) = rgb
    0.299 * r + 0.587 * g + 0.114 * b > 150
  end isLight

  private[scautable] def fg: fansi.EscapeAttr = this match
    case Black        => fansi.Color.Black
    case Red          => fansi.Color.Red
    case Green        => fansi.Color.Green
    case Yellow       => fansi.Color.Yellow
    case Blue         => fansi.Color.Blue
    case Magenta      => fansi.Color.Magenta
    case Cyan         => fansi.Color.Cyan
    case White        => fansi.Color.White
    case Grey         => fansi.Color.LightGray
    case Rgb(r, g, b) => fansi.Color.True(r, g, b)

  private[scautable] def bg: fansi.EscapeAttr = this match
    case Black        => fansi.Back.Black
    case Red          => fansi.Back.Red
    case Green        => fansi.Back.Green
    case Yellow       => fansi.Back.Yellow
    case Blue         => fansi.Back.Blue
    case Magenta      => fansi.Back.Magenta
    case Cyan         => fansi.Back.Cyan
    case White        => fansi.Back.White
    case Grey         => fansi.Back.LightGray
    case Rgb(r, g, b) => fansi.Back.True(r, g, b)
end Colour

/** How a cell looks, as opposed to what it says - that is [[CellFormat]]'s job.
  *
  * Renderer-neutral: the terminal maps it to ANSI escapes, HTML to an inline `style`. Combine with `++`, where the right-hand side wins property by property, so a later rule can
  * recolour a cell without dropping an earlier rule's bold.
  */
final case class CellStyle(
    fg: Option[Colour] = None,
    bg: Option[Colour] = None,
    bold: Boolean = false,
    underline: Boolean = false
):
  def ++(that: CellStyle): CellStyle =
    CellStyle(that.fg.orElse(fg), that.bg.orElse(bg), bold || that.bold, underline || that.underline)

  def isEmpty: Boolean = this == CellStyle.empty

  /** A background with no foreground picks black or white text, so it stays readable on both light and dark themes. */
  private def effectiveFg: Option[Colour] = fg.orElse(bg.map(b => if b.isLight then Colour.Black else Colour.White))

  /** The style as an inline CSS declaration, empty for an unstyled cell. */
  def css: String =
    Seq(
      effectiveFg.map(c => s"color:${c.css}"),
      bg.map(c => s"background-color:${c.css}"),
      Option.when(bold)("font-weight:bold"),
      Option.when(underline)("text-decoration:underline")
    ).flatten.mkString(";")

  /** Apply the style to already padded text, with ANSI escapes. */
  def ansi(text: String): String =
    val attrs = Seq(effectiveFg.map(_.fg), bg.map(_.bg), Option.when(bold)(fansi.Bold.On), Option.when(underline)(fansi.Underlined.On)).flatten
    if attrs.isEmpty then text else fansi.Attrs(attrs*)(fansi.Str(text)).render
    end if
  end ansi
end CellStyle

object CellStyle:
  val empty: CellStyle = CellStyle()
  def fg(c: Colour): CellStyle = CellStyle(fg = Some(c))
  def bg(c: Colour): CellStyle = CellStyle(bg = Some(c))
  val bold: CellStyle = CellStyle(bold = true)
  val underline: CellStyle = CellStyle(underline = true)
end CellStyle

/** One conditional styling rule: when `test` holds for a row, `style` is laid over the cells at `cols` (every cell, if `None`). */
private[scautable] final case class StyleRule(cols: Option[IArray[Int]], test: Product => Boolean, style: CellStyle)

private[scautable] object StyleRule:

  /** The style of each cell of `row`, folding `rules` in order so that later rules win. */
  def resolve(rules: Vector[StyleRule], arity: Int)(row: Product): IndexedSeq[CellStyle] =
    val out = Array.fill(arity)(CellStyle.empty)
    for r <- rules if r.test(row) do
      r.cols match
        case None       => for i <- out.indices do out(i) = out(i) ++ r.style
        case Some(cols) => for i <- cols do out(i) = out(i) ++ r.style
    end for
    out.toIndexedSeq
  end resolve
end StyleRule

/** A table plus a set of conditional styling rules - the analogue of a pandas `Styler`.
  *
  * Obtained from `.styler` on any table, and terminal: it renders (`ptbln`, `consoleFormatNt`, `html`) rather than transforms. Rules apply in the order given and later ones win,
  * so a broad row rule followed by a narrow cell rule behaves as you'd expect.
  * {{{
  * data
  *   .style[PriceSheet]                                         // number formats, as before
  *   .styler
  *   .row(r => r.Region == "EU", CellStyle.bg(Colour.Grey))
  *   .cell["EL"](_ > 0.05, CellStyle.fg(Colour.Red) ++ CellStyle.bold)
  *   .ptbln
  * }}}
  * Headers and cell formatters are resolved where `.styler` is called, so format tags carry through. A styler built over an `Iterator` consumes it when rendered.
  */
final class Styler[K <: Tuple, V <: Tuple] @publicInBinary private[scautable] (
    source: IterableOnce[NamedTuple[K, V]],
    val headers: List[String],
    formatters: Array[Any => String],
    rules: Vector[StyleRule]
):

  private def withRule(rule: StyleRule): Styler[K, V] = new Styler(source, headers, formatters, rules :+ rule)

  private def indexOf(name: String): Int = headers.indexOf(name)

  /** Style cells of column `S` whose value satisfies `pred`. The predicate is typed by the column, format tags included - `Percent[1]` is still a `Double`. */
  def cell[S <: String](using
      @implicitNotFound("cell: ${S} is not a column of this table")
      isCol: NameIn[K, S] =:= true,
      name: ValueOf[S]
  )(pred: ColTypeAtName[K, V, S] => Boolean, style: CellStyle): Styler[K, V] =
    val i = indexOf(name.value)
    withRule(StyleRule(Some(IArray(i)), row => pred(row.productElement(i).asInstanceOf[ColTypeAtName[K, V, S]]), style))
  end cell

  /** Style every cell of rows satisfying `pred`. */
  def row(pred: NamedTuple[K, V] => Boolean, style: CellStyle): Styler[K, V] =
    withRule(StyleRule(None, row => pred(row.asInstanceOf[NamedTuple[K, V]]), style))

  /** Style the cells of columns `Cols` in rows satisfying `pred`, e.g. `cells[("Bid", "Offer")](r => r.Stale, CellStyle.fg(Colour.Grey))`. */
  inline def cells[Cols <: Tuple](using
      @implicitNotFound("cells: every name in ${Cols} must be a column of this table")
      allCols: AllNamesIn[K, Cols] =:= true
  )(pred: NamedTuple[K, V] => Boolean, style: CellStyle): Styler[K, V] =
    val idx = IArray.from(constValueTuple[Cols].toList.map(n => indexOf(n.toString)))
    withRule(StyleRule(Some(idx), row => pred(row.asInstanceOf[NamedTuple[K, V]]), style))
  end cells

  /** The style of each cell of a row, as the renderers see it. */
  def stylesOf: Product => IndexedSeq[CellStyle] = StyleRule.resolve(rules, headers.size)

  /** Print the table, styled. Goes to the console unless another [[TableSink]] is in scope, e.g. a notebook. */
  def ptbln(using sink: TableSink): Unit =
    sink.showStyled(headers, formatters, stylesOf, source.iterator.map(_.toTuple))

  /** The table as a terminal string. With `colour = false` styles are dropped, as for any output not bound for a terminal. */
  def consoleFormatNt(colour: Boolean = true): String =
    val materialised = source.iterator.map(_.toTuple).toSeq
    TerminalTable.render(
      headers,
      materialised.map(r => TableRow(CellFormat.formatRow(r, formatters))),
      colour = colour,
      styles = materialised.map(stylesOf)
    )
  end consoleFormatNt

  /** The table as HTML, with each styled cell carrying an inline `style`. */
  def html: String =
    val tuples = source.iterator.map(_.toTuple)
    import scalatags.Text.all.*
    val body = tuples.map { row =>
      tr(CellFormat.formatRow(row, formatters).zip(stylesOf(row)).map((text, s) => if s.isEmpty then td(text) else td(style := s.css, text)))
    }.toSeq
    table(thead(tr(headers.map(th(_)))), tbody(body), id := "scautable", cls := "display").render
  end html

end Styler
