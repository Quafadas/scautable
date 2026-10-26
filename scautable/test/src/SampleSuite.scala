package io.github.quafadas.scautable

import io.github.quafadas.table.*

class SampleSuite extends munit.FunSuite:

  private def rows(n: Int) = (0 until n).iterator.map(i => (idx = i, label = s"r$i"))

  private def detIdx(n: Int, frac: Double): List[Int] =
    rows(n).sample(frac, deterministic = true).map(_.idx).toList

  test("deterministic sample keeps exactly floor(n * frac) rows"):
    assertEquals(detIdx(100, 0.3).size, 30)
    assertEquals(detIdx(100, 0.25).size, 25)
    assertEquals(detIdx(100, 0.5).size, 50)
    assertEquals(detIdx(100, 0.1).size, 10)
    assertEquals(detIdx(100, 0.75).size, 75)

  test("deterministic sample with frac 1.0 keeps all rows in order"):
    assertEquals(detIdx(100, 1.0), (0 until 100).toList)

  test("deterministic sample with frac 0.0 keeps no rows"):
    assertEquals(detIdx(100, 0.0), Nil)

  test("deterministic sample is repeatable"):
    assertEquals(detIdx(100, 0.3), detIdx(100, 0.3))
    assertEquals(detIdx(1000, 0.37), detIdx(1000, 0.37))

  test("deterministic sample is spread evenly, not just the first N"):
    val kept = detIdx(100, 0.3)
    assert(kept.max >= 90, s"expected samples near the end, got $kept")
    // Every block of 10 consecutive rows contributes exactly 3 rows
    kept.groupBy(_ / 10).values.foreach(block => assertEquals(block.size, 3))
    // Gaps between kept rows are at most ceil(1 / frac)
    kept.sliding(2).foreach { case List(a, b) => assert(b - a <= 4, s"gap $a -> $b too wide") }

  test("deterministic sample with frac 0.25 keeps every fourth row"):
    val kept = detIdx(100, 0.25)
    assertEquals(kept.sliding(2).map { case List(a, b) => b - a }.toSet, Set(4))

  test("deterministic sample on an empty iterator is empty"):
    assertEquals(detIdx(0, 0.5), Nil)

  test("frac above 1 is clamped to keep all rows"):
    assertEquals(detIdx(50, 1.5), (0 until 50).toList)
    assertEquals(rows(50).sample(1.5).size, 50)

  test("negative frac is clamped to keep no rows"):
    assertEquals(detIdx(50, -0.2), Nil)
    assertEquals(rows(50).sample(-0.2).size, 0)

  test("random sample with frac 0 and 1 is exact"):
    assertEquals(rows(100).sample(0.0).size, 0)
    assertEquals(rows(100).sample(1.0).size, 100)

end SampleSuite
