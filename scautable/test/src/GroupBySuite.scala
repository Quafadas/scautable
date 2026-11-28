package groupbytest

import scala.NamedTuple.NamedTuple

import io.github.quafadas.table.*

/** Lives outside `io.github.quafadas.scautable` on purpose: `agg` is inline, so it must compile where users call it, without package-private access. */
class GroupBySuite extends munit.FunSuite:

  type Emp = (dept: String, region: String, salary: Int, bonus: Option[Double], name: String)

  val emps: List[Emp] = List(
    (dept = "eng", region = "eu", salary = 100, bonus = Some(10.0), name = "ann"),
    (dept = "ops", region = "us", salary = 70, bonus = None, name = "bob"),
    (dept = "eng", region = "us", salary = 120, bonus = None, name = "cat"),
    (dept = "eng", region = "eu", salary = 90, bonus = Some(5.0), name = "dan")
  )

  test("every reducer on a plain column, with typed output") {
    val out = emps.iterator
      .groupBy["dept"]
      .agg[(n: Agg.Count, total: Agg.Sum["salary"], avg: Agg.Mean["salary"], lo: Agg.Min["salary"], hi: Agg.Max["salary"], who: Agg.First["name"])]
    summon[out.type <:< Seq[NamedTuple[("dept", "n", "total", "avg", "lo", "hi", "who"), (String, Int, Int, Double, Int, Int, String)]]]
    assertEquals(
      out.toList,
      List(
        (dept = "eng", n = 3, total = 310, avg = 310.0 / 3, lo = 90, hi = 120, who = "ann"),
        (dept = "ops", n = 1, total = 70, avg = 70.0, lo = 70, hi = 70, who = "bob")
      )
    )
  }

  test("optional columns skip None; Sum unwraps, Min/Max/First stay optional") {
    val out = emps.iterator
      .groupBy["dept"]
      .agg[(total: Agg.Sum["bonus"], avg: Agg.Mean["bonus"], lo: Agg.Min["bonus"], hi: Agg.Max["bonus"], first: Agg.First["bonus"])]
    summon[out.type <:< Seq[NamedTuple[("dept", "total", "avg", "lo", "hi", "first"), (String, Double, Double, Option[Double], Option[Double], Option[Double])]]]
    val eng = out.head
    assertEquals((eng.total, eng.avg, eng.lo, eng.hi, eng.first), (15.0, 7.5, Some(5.0), Some(10.0), Some(10.0)))
    // a group whose values are all missing
    val ops = out(1)
    assertEquals((ops.total, ops.lo, ops.hi, ops.first), (0.0, None, None, None))
    assert(ops.avg.isNaN)
  }

  test("groups come out in the order their keys were first seen") {
    val keys = (1 to 50).map(i => (k = (i * 7) % 13, v = i)).iterator.groupBy["k"].agg[(n: Agg.Count)].map(_.k)
    assertEquals(keys.toList, (1 to 50).map(i => (i * 7) % 13).distinct.toList)
  }

  test("several keys") {
    val out = emps.iterator.groupBy[("dept", "region")].agg[(n: Agg.Count, lo: Agg.Min["salary"])]
    summon[out.type <:< Seq[NamedTuple[("dept", "region", "n", "lo"), (String, String, Int, Int)]]]
    assertEquals(out.toList.map(r => (r.dept, r.region, r.n, r.lo)), List(("eng", "eu", 2, 90), ("ops", "us", 1, 70), ("eng", "us", 1, 120)))
  }

  test("a None key is a group of its own") {
    val rows = Seq((k = Option(1), v = 1), (k = None, v = 2), (k = Option(1), v = 3), (k = None, v = 4))
    val out = rows.iterator.groupBy["k"].agg[(total: Agg.Sum["v"])]
    assertEquals(out.toList, List((k = Option(1), total = 4), (k = None, total = 6)))
  }

  test("a spec can be named and reused") {
    type Summary = (n: Agg.Count, mean_salary: Agg.Mean["salary"])
    val byRegion = emps.iterator.groupBy["region"].agg[Summary]
    assertEquals(byRegion.toList, List((region = "eu", n = 2, mean_salary = 95.0), (region = "us", n = 2, mean_salary = 95.0)))
  }

  test("empty input gives no groups") {
    assertEquals(Iterator.empty[Emp].groupBy["dept"].agg[(n: Agg.Count)].toList, Nil)
  }

  test("aggWith hands each group over as typed rows") {
    val out = emps.iterator.groupBy["dept"].aggWith(g => (payroll = g.map(_.salary).sum, names = g.map(_.name).mkString(",")))
    summon[out.type <:< Seq[NamedTuple[("dept", "payroll", "names"), (String, Int, String)]]]
    assertEquals(out.toList, List((dept = "eng", payroll = 310, names = "ann,cat,dan"), (dept = "ops", payroll = 70, names = "bob")))
  }

  test("straight from a CSV") {
    inline val csv = "dept,salary\neng,100\nops,70\neng,120"
    val out = CSV.fromString(csv).groupBy["dept"].agg[(n: Agg.Count, total: Agg.Sum["salary"])]
    assertEquals(out.toList, List((dept = "eng", n = 2, total = 220), (dept = "ops", n = 1, total = 70)))
  }

  test("misuse does not compile, and says why") {
    val setup = """
      val emps = Iterator((dept = "eng", salary = 1, name = "a"))
    """
    assert(compileErrors(setup + """emps.groupBy["dpt"]""").contains("groupBy: no column named dpt"))
    assert(compileErrors(setup + """emps.groupBy["dept"].agg[(t: Agg.Sum["salry"])]""").contains("agg: no column named salry"))
    assert(compileErrors(setup + """emps.groupBy["dept"].agg[(t: Agg.Sum["name"])]""").contains("""agg: Sum["name"] needs a numeric column"""))
    assert(compileErrors(setup + """emps.groupBy["dept"].agg[(dept: Agg.Count)]""").contains("agg: output column dept has the same name as a group key"))
    assert(compileErrors(setup + """emps.groupBy["dept"].aggWith(g => (dept = 1))""").contains("aggWith: output column dept has the same name as a group key"))
  }

end GroupBySuite
