package io.github.quafadas.scautable

import io.github.quafadas.table.*

import munit.FunSuite

class StyleSuite extends FunSuite:

  type PriceSheet = (
      `Bid Spread`: Decimals[2],
      `Offer Spread`: Decimals[2],
      EL: Percent[1],
      AP: Currency["$", 2]
  )

  test("a spec applies the same tags as the equivalent formatColumn chain") {
    val data = Seq((`Bid Spread` = 1.23456, `Offer Spread` = 2.34567, EL = 0.0425, AP = 1234.5678, note = "n/a"))

    val viaSpec = data.style[PriceSheet].consoleFormatNt
    val viaChain = data
      .formatColumn["Bid Spread", Decimals[2]]
      .formatColumn["Offer Spread", Decimals[2]]
      .formatColumn["EL", Percent[1]]
      .formatColumn["AP", Currency["$", 2]]
      .consoleFormatNt

    assertEquals(viaSpec, viaChain)
    assert(viaSpec.contains("1.23"), viaSpec)
    assert(viaSpec.contains("4.3%"), viaSpec)
    assert(viaSpec.contains("$1,234.57"), viaSpec)
  }

  test("a spec is order independent and may be a subset of the columns") {
    val data = Seq((a = 1.5, b = 2.5, c = 3.5))
    val out = data.style[(c: Decimals[3], a: Decimals[1])].consoleFormatNt
    assert(out.contains("3.500"), out)
    assert(out.contains("1.5"), out)
    assert(out.contains("2.5"), out)
  }

  test("works on an Iterator too, and on Option-wrapped columns") {
    val data = Iterator((x = Option(1.23456), y = 9.0))
    val out = data.style[(x: Decimals[2])].consoleFormatNt
    assert(out.contains("1.23"), out)
  }

  test("a spec naming a missing column does not compile") {
    assert(compileErrors("""
      Seq((a = 1.5)).style[(nope: Decimals[2])]
    """).contains("no column named nope"))
  }

  test("a spec with an incompatible tag does not compile") {
    val errs = compileErrors("""
      Seq((a = "text")).style[(a: Decimals[2])]
    """)
    assert(errs.contains("not compatible"), errs)
  }

  /** The fold underneath `style` is not specific to formatting - only its `Step` is.
    *
    * With `Step = Tagged` it attaches display tags; with `[Old, New] =>> New` the same spec machinery retypes a set of columns, which is
    * what a named-tuple `forceColumnType` would be built from.
    */
  test("FoldSpec generalises past formatting - the same spec retypes columns") {
    type K = ("a", "b", "c")
    type V = (String, String, Int)
    type Retyped[Spec <: scala.NamedTuple.AnyNamedTuple] =
      ColumnTyped.FoldSpec[K, V, scala.NamedTuple.Names[Spec], scala.NamedTuple.DropNames[Spec], [Old, New] =>> New]

    summon[Retyped[(a: Double, c: Long)] =:= (Double, String, Long)]

    // and the styling instantiation of the very same fold
    summon[ColumnTyped.Styled[K, V, (c: ColumnFormat.IntThousands)] =:= (String, String, ColumnFormat.IntThousands)]
  }

end StyleSuite

class SpecOpsSuite extends FunSuite:

  test("mapColumns maps several columns and leaves the rest alone") {
    val raw = Seq((price = "1.5", qty = "3", name = "widget"))
    val out = raw.mapColumns((price = (s: String) => s.toDouble, qty = (s: String) => s.toInt))

    assertEquals(out.head.price, 1.5)
    assertEquals(out.head.qty, 3)
    assertEquals(out.head.name, "widget")
  }

  test("mapColumns agrees with the equivalent mapColumn chain") {
    val raw = Seq((a = "1", b = "2", c = "3"), (a = "4", b = "5", c = "6"))
    val viaSpec = raw.mapColumns((a = (s: String) => s.toInt, c = (s: String) => s.toDouble))
    val viaChain = raw.mapColumn["a", Int](_.toInt).mapColumn["c", Double](_.toDouble)

    assertEquals(viaSpec.toList, viaChain.toList)
  }

  test("mapColumns is order independent and works on an Iterator") {
    val raw = Iterator((a = "1", b = "2"))
    val out = raw.mapColumns((b = (s: String) => s.toInt, a = (s: String) => s.toDouble)).toList
    assertEquals(out.head.a, 1.0)
    assertEquals(out.head.b, 2)
  }

  test("mapColumns keeps a LazyList lazy") {
    var forced = 0
    val raw = LazyList.from(1).take(5).map(i => (a = { forced += 1; i.toString }))
    val out = raw.mapColumns((a = (s: String) => s.toInt))
    assertEquals(forced, 0)
    assertEquals(out.head.a, 1)
    assert(forced < 5, s"forced $forced elements to read the head")
  }

  test("retype forces several column types at once") {
    val raw = Seq((a = "1", b = "2", c = "3"))
    val out = raw.retype[(a: Int, c: Double)]
    // a cast, so this is a type-level claim about the shape, not about the data
    summon[out.type <:< Seq[NamedTuple.NamedTuple[("a", "b", "c"), (Int, String, Double)]]]
    assertEquals(out.head.b, "2")
  }

  test("a spec naming a missing column does not compile, and says which method") {
    assert(compileErrors("""Seq((a = "1")).retype[(nope: Int)]""").contains("retype: no column named nope"))
    assert(compileErrors("""Seq((a = "1")).mapColumns((nope = (s: String) => s.toInt))""").contains("mapColumns: no column named nope"))
  }

  test("a mapColumns function that does not accept its column does not compile") {
    val errs = compileErrors("""Seq((a = "1")).mapColumns((a = (i: Int) => i + 1))""")
    assert(errs.contains("does not accept its type"), errs)
  }

end SpecOpsSuite
