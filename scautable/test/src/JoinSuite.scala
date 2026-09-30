package io.github.quafadas.scautable

import scala.NamedTuple.NamedTuple

import io.github.quafadas.table.*

class JoinSuite extends munit.FunSuite:

  val orders = Seq((custId = 1, qty = 5), (custId = 2, qty = 7), (custId = 1, qty = 9))
  val customers = Seq((custId = 1, name = "ada"), (custId = 2, name = "bob"))

  test("inner join, one to one") {
    val out = Seq((custId = 1, qty = 5)).join(customers)["custId"]
    summon[out.type <:< Seq[NamedTuple[("custId", "qty", "name"), (Int, Int, String)]]]
    assertEquals(out.toList, List((custId = 1, qty = 5, name = "ada")))
  }

  test("inner join fans a left row out over every matching right row") {
    val many = Seq((custId = 1, item = "a"), (custId = 1, item = "b"), (custId = 2, item = "c"))
    val out = Seq((custId = 1, qty = 5)).join(many)["custId"].toList
    // right hand rows keep their encounter order
    assertEquals(out.map(_.item), List("a", "b"))
  }

  test("inner join drops rows that match nothing, on either side") {
    val out = Seq((custId = 1, qty = 5), (custId = 99, qty = 1)).join(customers)["custId"].toList
    assertEquals(out.map(_.custId), List(1))
  }

  test("join preserves left order and multiplies matches") {
    val out = orders.join(customers)["custId"].toList
    assertEquals(out.map(r => (r.custId, r.qty, r.name)), List((1, 5, "ada"), (2, 7, "bob"), (1, 9, "ada")))
  }

  test("joinOn with differing key names agrees with renameColumn + join") {
    val rightNamed = Seq((id = 1, name = "ada"), (id = 2, name = "bob"))
    val viaJoinOn = orders.joinOn(rightNamed)["custId", "id"].toList
    val viaChain = orders.join(rightNamed.renameColumn["id", "custId"])["custId"].toList
    assertEquals(viaJoinOn, viaChain)
    // the output keeps the LEFT key's name
    summon[viaJoinOn.type <:< List[NamedTuple[("custId", "qty", "name"), (Int, Int, String)]]]
  }

  test("leftJoin keeps unmatched left rows and optionalises the right") {
    val out = Seq((custId = 1, qty = 5), (custId = 99, qty = 1)).leftJoin(customers)["custId"]
    summon[out.type <:< Seq[NamedTuple[("custId", "qty", "name"), (Int, Int, Option[String])]]]
    assertEquals(out.toList.map(_.name), List(Some("ada"), None))
  }

  test("leftJoin does not nest an already optional right column") {
    val right = Seq((custId = 1, nick = Option("a")), (custId = 2, nick = Option.empty[String]))
    val out = Seq((custId = 1, qty = 5), (custId = 2, qty = 1), (custId = 9, qty = 0)).leftJoin(right)["custId"]
    summon[out.type <:< Seq[NamedTuple[("custId", "qty", "nick"), (Int, Int, Option[String])]]]
    // and the runtime agrees with that type - no Some(None) anywhere
    assertEquals(out.toList.map(_.nick), List(Some("a"), None, None))
  }

  test("leftJoinOn with differing key names") {
    val rightNamed = Seq((id = 1, name = "ada"))
    val out = orders.leftJoinOn(rightNamed)["custId", "id"].toList
    assertEquals(out.map(_.name), List(Some("ada"), None, Some("ada")))
  }

  test("a right table that is nothing but the key still joins") {
    val keyOnly = Seq((custId = 1))
    val out = Seq((custId = 1, qty = 5), (custId = 2, qty = 7)).join(keyOnly)["custId"]
    summon[out.type <:< Seq[NamedTuple[("custId", "qty"), (Int, Int)]]]
    assertEquals(out.toList, List((custId = 1, qty = 5)))
  }

  test("the Iterator overload is lazy - building a join drains neither side") {
    var forcedLeft = 0
    val left = LazyList((custId = 1, qty = 5), (custId = 2, qty = 7)).map { r => forcedLeft += 1; r }
    var forcedRight = 0
    val right = LazyList((custId = 1, name = "ada")).map { r => forcedRight += 1; r }

    val joined = left.iterator.join(right)["custId"]
    assertEquals(forcedLeft, 0)
    assertEquals(forcedRight, 0)

    assertEquals(joined.next().name, "ada")
    assertEquals(forcedRight, 1)
  }

  test("the Iterable overload preserves the collection type") {
    val out = Vector((custId = 1, qty = 5)).join(customers)["custId"]
    summon[out.type <:< Vector[NamedTuple[("custId", "qty", "name"), (Int, Int, String)]]]
    assertEquals(out, Vector((custId = 1, qty = 5, name = "ada")))
  }

  test("keys are matched with ==, so None matches None") {
    val left = Seq((k = Option.empty[Int], a = 1))
    val right = Seq((k = Option.empty[Int], b = 2))
    assertEquals(left.join(right)["k"].toList.map(_.b), List(2))
  }

  test("a column on both sides does not compile, and says which one") {
    val errs = compileErrors("""
      val l = Seq((custId = 1, name = "x"))
      val r = Seq((custId = 1, name = "ada"))
      l.join(r)["custId"]
    """)
    assert(errs.contains("join: column name exists on both sides"), errs)
  }

  test("an unknown key does not compile, and says which side") {
    val l = """val l = Seq((custId = 1, qty = 2)); val r = Seq((id = 1, name = "a")); """
    assert(compileErrors(l + """l.joinOn(r)["nope", "id"]""").contains("joinOn: no column named"), "left key")
    assert(compileErrors(l + """l.joinOn(r)["custId", "nope"]""").contains("joinOn: no column named"), "right key")
  }

  test("keys whose types differ do not compile") {
    val errs = compileErrors("""
      val l = Seq((custId = 1, qty = 2))
      val r = Seq((id = "1", name = "a"))
      l.joinOn(r)["custId", "id"]
    """)
    assert(errs.contains("have different types"), errs)
  }

end JoinSuite
