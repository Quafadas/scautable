package io.github.quafadas.scautable

import scala.compiletime.*

/** Display tags for table columns.
  *
  * Each tag is a *bounded opaque type* over the value it decorates - e.g. `Decimals[2]` is a `Double` at runtime and erases to one, but is a distinct type to the compiler. That
  * gives us three things at once:
  *
  *   - a `given CellFormat[Decimals[2]]` can be found for it, so the column renders differently from a plain `Double` sitting next to it;
  *   - the bound (`<: Double`) means match types such as [[ColumnTyped.IsNumeric]] still reduce and arithmetic on the value still compiles;
  *   - there is no runtime cost - attaching a tag is a cast, and the underlying value never changes.
  *
  * Note that a trait-based tag (`Double & Money`) cannot work here: `Double` is final, so the compiler proves the intersection uninhabited and refuses to reduce match types over
  * it.
  *
  * Define your own the same way:
  * {{{
  * object MyTags:
  *   opaque type Money <: Double = Double
  *   given CellFormat[Money] = d => f"$$$d%,.2f"
  * }}}
  */
object ColumnFormat:

  /** Fixed number of decimal places, e.g. `Decimals[2]` renders `1234.5678` as `1234.57`. */
  opaque type Decimals[N <: Int] <: Double = Double

  /** Fixed number of significant figures, e.g. `SigFigs[4]` renders `29.69911764705882` as `29.70`. */
  opaque type SigFigs[N <: Int] <: Double = Double

  /** Value scaled by 100 with a trailing `%`, e.g. `Percent[2]` renders `0.0425` as `4.25%`. */
  opaque type Percent[N <: Int] <: Double = Double

  /** Grouping separators and two decimal places, e.g. `9876543.21` as `9,876,543.21`. */
  opaque type Thousands <: Double = Double

  /** Grouped, `N` decimal places, prefixed with `Sym` - e.g. `Currency["$", 2]` renders `1234.5678` as `$1,234.57`. */
  opaque type Currency[Sym <: String, N <: Int] <: Double = Double

  /** Grouping separators on a whole number, e.g. `9876543` as `9,876,543`. */
  opaque type LongThousands <: Long = Long

  /** Grouping separators on a whole number, e.g. `9876543` as `9,876,543`. */
  opaque type IntThousands <: Int = Int

  /** Value divided by a thousand with a ` k` suffix, e.g. `InThousands[3]` renders `1234.0` as `1.234 k`.
    *
    * Not to be confused with [[Thousands]], which adds grouping separators at full scale rather than rescaling.
    */
  opaque type InThousands[N <: Int] <: Double = Double

  /** Value divided by a million with a ` M` suffix, e.g. `InMillions[4]` renders `123400.0` as `0.1234 M`. */
  opaque type InMillions[N <: Int] <: Double = Double

  /** Value divided by a billion (1e9) with a ` B` suffix, e.g. `InBillions[2]` renders `2500000000.0` as `2.50 B`. */
  opaque type InBillions[N <: Int] <: Double = Double

  /** Value scaled by 10,000 with a ` bps` suffix, e.g. `BasisPoints[0]` renders `0.0425` as `425 bps`. */
  opaque type BasisPoints[N <: Int] <: Double = Double

end ColumnFormat

/** How a single cell's value is rendered as text.
  *
  * Invariant: an instance is found by exact type match, so a tagged column (see [[ColumnFormat]]) resolves its own instance while an untagged column of the same underlying type
  * falls back to `toString`.
  */
trait CellFormat[A]:
  def format(a: A): String
end CellFormat

object CellFormat:

  import ColumnFormat.*

  def apply[A](f: A => String): CellFormat[A] = (a: A) => f(a)

  private val thousandth = BigDecimal(1) / 1_000
  private val millionth = BigDecimal(1) / 1_000_000
  private val billionth = BigDecimal(1) / 1_000_000_000
  private val tenThousand = BigDecimal(10_000)

  /** Round to `places` decimals, half-up, and render without scientific notation.
    *
    * `BigDecimal` rather than `String.format`: the latter needs `java.util.Locale` (absent on Scala.js) and would otherwise render a `,` decimal separator under a European default
    * locale on the JVM. This way the output is identical on every platform and locale.
    */
  private[scautable] def fixed(d: Double, places: Int): String =
    if d.isNaN || d.isInfinite then d.toString
    else BigDecimal(d).setScale(places, BigDecimal.RoundingMode.HALF_UP).bigDecimal.toPlainString

  /** Insert `,` every three digits of the integer part of an already-formatted number.
    *
    * Done by hand rather than with `String.format`'s `%,` flag, which needs `java.text.DecimalFormat` and so does not link on Scala.js.
    */
  private[scautable] def addGrouping(formatted: String): String =
    val (sign, unsigned) = if formatted.startsWith("-") then ("-", formatted.tail) else ("", formatted)
    val dot = unsigned.indexOf('.')
    val (intPart, rest) = if dot < 0 then (unsigned, "") else (unsigned.take(dot), unsigned.drop(dot))
    sign + intPart.reverse.grouped(3).mkString(",").reverse + rest
  end addGrouping

  /** Round to `places` decimals, half-up, with grouping separators. */
  private[scautable] def grouped(d: Double, places: Int): String =
    if d.isNaN || d.isInfinite then d.toString
    else addGrouping(fixed(d, places))

  /** Rescale by `factor` (multiplying), round half-up to `places` decimals and append `suffix`.
    *
    * The arithmetic is done in `BigDecimal` rather than on the `Double`, so e.g. `InMillions[4]` of `123400.0` is exactly `0.1234 M` and not `0.1233 M`.
    */
  private[scautable] def rescaled(d: Double, factor: BigDecimal, places: Int, suffix: String): String =
    if d.isNaN || d.isInfinite then d.toString
    else (BigDecimal(d) * factor).setScale(places, BigDecimal.RoundingMode.HALF_UP).bigDecimal.toPlainString + suffix

  /** Scale by 100, round half-up to `places` decimals, append `%` - the same policy as [[ConsoleFormat.formatAsPercentage]]. */
  private[scautable] def percentage(d: Double, places: Int): String =
    if d.isNaN || d.isInfinite then d.toString
    else fixed(BigDecimal(d * 100).setScale(places, BigDecimal.RoundingMode.HALF_UP).doubleValue, places) + "%"

  /** Round to `figures` significant figures. */
  private[scautable] def sigFigs(d: Double, figures: Int): String =
    if d.isNaN || d.isInfinite || d == 0 then d.toString
    else BigDecimal(d).round(new java.math.MathContext(figures)).bigDecimal.toPlainString

  given decimals[N <: Int](using n: ValueOf[N]): CellFormat[Decimals[N]] = (d: Decimals[N]) => fixed(d, n.value)

  given sigFigsFormat[N <: Int](using n: ValueOf[N]): CellFormat[SigFigs[N]] = (d: SigFigs[N]) => sigFigs(d, n.value)

  given percent[N <: Int](using n: ValueOf[N]): CellFormat[Percent[N]] = (d: Percent[N]) => percentage(d, n.value)

  given thousands: CellFormat[Thousands] = (d: Thousands) => grouped(d, 2)

  given currency[Sym <: String, N <: Int](using sym: ValueOf[Sym], n: ValueOf[N]): CellFormat[Currency[Sym, N]] =
    (d: Currency[Sym, N]) => sym.value + grouped(d, n.value)

  given longThousands: CellFormat[LongThousands] = (l: LongThousands) => addGrouping(l.toString)

  given intThousands: CellFormat[IntThousands] = (i: IntThousands) => addGrouping(i.toString)

  given inThousands[N <: Int](using n: ValueOf[N]): CellFormat[InThousands[N]] = (d: InThousands[N]) => rescaled(d, thousandth, n.value, " k")

  given inMillions[N <: Int](using n: ValueOf[N]): CellFormat[InMillions[N]] = (d: InMillions[N]) => rescaled(d, millionth, n.value, " M")

  given inBillions[N <: Int](using n: ValueOf[N]): CellFormat[InBillions[N]] = (d: InBillions[N]) => rescaled(d, billionth, n.value, " B")

  given basisPoints[N <: Int](using n: ValueOf[N]): CellFormat[BasisPoints[N]] = (d: BasisPoints[N]) => rescaled(d, tenThousand, n.value, " bps")

  /** A tagged column read from a CSV with blanks arrives as `Option[Tag]`; render through the inner format. `None` stays `"None"` so untagged output is unchanged. */
  given option[A](using inner: CellFormat[A]): CellFormat[Option[A]] = (o: Option[A]) =>
    o match
      case None    => "None"
      case Some(a) => inner.format(a)

  /** Used for every column with no `CellFormat` in scope - i.e. all existing tables. */
  private[scautable] val toStringFormat: Any => String = a => if a == null then "" else a.toString

  /** One formatter per element of `T`, positionally aligned with a row tuple.
    *
    * Resolved once per render call (not per row) - `summonFrom` rather than `summonInline` so that an untagged column falls back to `toString` instead of failing to compile.
    */
  inline def formattersFor[T <: Tuple]: List[Any => String] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (h *: t)   =>
        val f: Any => String = summonFrom {
          case fmt: CellFormat[`h`] => (a: Any) => if a == null then "" else fmt.format(a.asInstanceOf[h])
          case _                    => toStringFormat
        }
        f :: formattersFor[t]

end CellFormat
