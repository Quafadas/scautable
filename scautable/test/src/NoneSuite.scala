package io.github.quafadas.scautable

import scala.NamedTuple.NamedTuple

import io.github.quafadas.table.*

class NoneSuite extends munit.FunSuite:

  val people = Seq(
    (name = "ada", age = Option(36), nick = Option("a")),
    (name = "bob", age = None, nick = Option("b")),
    (name = "cy", age = Option(7), nick = None)
  )

  test("dropNone drops the None rows and narrows the column") {
    val out = people.dropNone["age"]
    summon[out.type <:< Seq[NamedTuple[("name", "age", "nick"), (String, Int, Option[String])]]]
    assertEquals(out.toList, List((name = "ada", age = 36, nick = Option("a")), (name = "cy", age = 7, nick = None)))
  }

  test("dropNone only looks at the named column") {
    assertEquals(people.dropNone["nick"].map(_.name).toList, List("ada", "bob"))
  }

  test("dropNone on an Iterator streams and narrows") {
    val out = people.iterator.dropNone["age"]
    summon[out.type <:< Iterator[NamedTuple[("name", "age", "nick"), (String, Int, Option[String])]]]
    assertEquals(out.map(_.age).toList, List(36, 7))
  }

  test("dropNone chains across columns") {
    val out = people.dropNone["age"].dropNone["nick"]
    summon[out.type <:< Seq[NamedTuple[("name", "age", "nick"), (String, Int, String)]]]
    assertEquals(out.toList, List((name = "ada", age = 36, nick = "a")))
  }

  test("fillNone replaces None, keeps every row and narrows the column") {
    val out = people.fillNone["age"](0)
    summon[out.type <:< Seq[NamedTuple[("name", "age", "nick"), (String, Int, Option[String])]]]
    assertEquals(out.map(_.age).toList, List(36, 0, 7))
    assertEquals(out.map(_.nick).toList, people.map(_.nick).toList)
  }

  test("fillNone on an Iterator") {
    val out = people.iterator.fillNone["nick"]("?")
    summon[out.type <:< Iterator[NamedTuple[("name", "age", "nick"), (String, Option[Int], String)]]]
    assertEquals(out.map(_.nick).toList, List("a", "b", "?"))
  }

  test("fillNone works on the first column") {
    val first = Seq((x = Option(1), y = "a"), (x = None, y = "b"))
    assertEquals(first.fillNone["x"](-1).toList, List((x = 1, y = "a"), (x = -1, y = "b")))
  }

  test("a missing column or a non-Option column is a compile error") {
    val setup = """val p = Seq((name = "ada", age = Option(36)))
"""
    assert(compileErrors(setup + """p.dropNone["nope"]""").contains("dropNone: column"), "dropNone missing")
    assert(compileErrors(setup + """p.dropNone["name"]""").contains("is not an Option"), "dropNone non-option")
    assert(compileErrors(setup + """p.fillNone["name"]("x")""").contains("is not an Option"), "fillNone non-option")
    assert(compileErrors(setup + """p.fillNone["age"]("x")""").nonEmpty, "fillNone wrong fill type")
  }

end NoneSuite
