package io.github.quafadas.scautable

import scalatags.Text.all.*
import java.time.LocalDate


class HtmlRenderSuite extends munit.FunSuite:

  import HtmlRenderer.*
  import HtmlRenderer.given

  case class ScauTest(anInt: Int, aString: String)

  test("console") {
    case class ScauTest(anInt: Int, aString: String)
    val start = ScauTest(1, "2")
    val start2 = ScauTest(2, "booyakashah")
    val startSeq = Seq(start, start2)
    val console = ConsoleFormat.consoleFormat_(startSeq, false)
    assertEquals(
      console,
      s"""| |anInt|    aString|\n+-+-----+-----------+\n|0|    1|          2|\n|1|    2|booyakashah|\n+-+-----+-----------+"""
    )
  }

  test("console_2") {
    case class ScauTest(anInt: Int, aString: String)
    val start = ScauTest(1, "2")
    val start2 = ScauTest(2, "booyakashah")
    val start3 = ScauTest(3, "boo")
    val start4 = ScauTest(4, "booy")
    val startSeq = Seq(start, start2, start3, start4)
    val console = ConsoleFormat.consoleFormat_(startSeq, false)
    assertEquals(
      console,
      s"""| |anInt|    aString|\n+-+-----+-----------+\n|0|    1|          2|\n|1|    2|booyakashah|\n|2|    3|        boo|\n|3|    4|       booy|\n+-+-----+-----------+"""
    )
  }

  // test("console_fancy") {
  //   case class ScauTest(anInt: Int, aString: String)
  //   val start    = ScauTest(1, "2")
  //   val start2   = ScauTest(2, "booyakashah")
  //   val start3   = ScauTest(3, "boo")
  //   val start4   = ScauTest(4, "booy")
  //   val startSeq = Seq(start, start2, start3, start4)
  //   val console  = scautable.consoleFormat(startSeq, true)
  //   scautable.printlnConsole(startSeq, true)
  //   assertEquals(
  //     console,
  //     s"""| |anInt|    aString|\n+-+-----+-----------+\n|0|    1|          2|\n|1|    2|booyakashah|\n|2|    3|        boo|\n|3|    4|       booy|\n+-+-----+-----------+"""
  //   )
  // }

  test("one row") {
    val start = ScauTest(1, "2")
    val startSeq = start
    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>anInt</th><th>aString</th></tr></thead><tbody><tr><td>1</td><td>2</td></tr></tbody></table>""",
      HtmlRenderer(startSeq, true).toString()
    )
  }

  test("one row as seq with custom headers") {
    val start = ScauTest(1, "2")
    val startSeq = Seq(start)
    val headers = List("firstHeader", "secondHeader")
    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>firstHeader</th><th>secondHeader</th></tr></thead><tbody><tr><td>1</td><td>2</td></tr></tbody></table>""",
      HtmlRenderer(startSeq, true, headers).toString()
    )
  }

  test("one row as seq") {
    val start = ScauTest(1, "2")
    val startSeq = Seq(start)
    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>anInt</th><th>aString</th></tr></thead><tbody><tr><td>1</td><td>2</td></tr></tbody></table>""",
      HtmlRenderer(startSeq).toString()
    )
  }
  test("tuple") {
    val start = (1, "2")
    val startSeq = Seq(start)
    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>_1</th><th>_2</th></tr></thead><tbody><tr><td>1</td><td>2</td></tr></tbody></table>""",
      HtmlRenderer(startSeq).toString()
    )
  }
  test("three rows") {
    val start = ScauTest(1, "2")
    val startSeq = Seq.fill(3)(start)
    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>anInt</th><th>aString</th></tr></thead><tbody><tr><td>1</td><td>2</td></tr><tr><td>1</td><td>2</td></tr><tr><td>1</td><td>2</td></tr></tbody></table>""",
      HtmlRenderer(startSeq).toString()
    )
  }
  test("built in types") {
    case class EasyTypes(s: String, i: Int, l: Long, d: Double, b: Boolean)
    val startSeq = Seq(EasyTypes("hi", 1, 2, 3.1, false))
    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>s</th><th>i</th><th>l</th><th>d</th><th>b</th></tr></thead><tbody><tr><td>hi</td><td>1</td><td>2</td><td>3.1</td><td>false</td></tr></tbody></table>""",
      HtmlRenderer(startSeq).toString()
    )
  }
  test("enums") {
    enum Env:
      case Prod, Dev, Test
    end Env

    case class EasyTypes(s: String, i: Env)
    val startSeq = Seq(EasyTypes("hi", Env.Dev))
    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>s</th><th>i</th></tr></thead><tbody><tr><td>hi</td><td>Dev</td></tr></tbody></table>""",
      HtmlRenderer(startSeq).toString()
    )
  }

  test("Seq") {
    case class SeqMe(s: Seq[String])
    val startSeq = SeqMe(Seq("happy", "land"))
    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>s</th></tr></thead><tbody><tr><td><table><tbody><tr><td>happy</td></tr><tr><td>land</td></tr></tbody></table></td></tr></tbody></table>""",
      HtmlRenderer(startSeq, true).toString()
    )
  }

  test("compoundable") {
    case class Address(num: Int, street: String)
    case class Person(n: String, age: Int, a: Address)

    val one = Person("me", 5, Address(0, "happyland"))
    val listOne = Seq(one)

    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>n</th><th>age</th><th>a</th></tr></thead><tbody><tr><td>me</td><td>5</td><td><table><thead><tr><th>num</th><th>street</th></tr></thead><tbody><tr><td>0</td><td>happyland</td></tr></tbody></table></td></tr></tbody></table>""",
      HtmlRenderer(listOne).toString()
    )
  }

  test("Compound Product Seq") {
    case class SeqMe(s: Seq[(Int, String)])
    val startSeq = SeqMe(Seq((1, "happy"), (2, "land")))
    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>s</th></tr></thead><tbody><tr><td><table><thead><tr><th>_1</th><th>_2</th></tr></thead><tbody><tr><td>1</td><td>happy</td></tr><tr><td>2</td><td>land</td></tr></tbody></table></td></tr></tbody></table>""",
      HtmlRenderer(startSeq, true).toString()
    )
  }

  test("optionable") {
    case class Address(num: Int, street: Option[String])
    val testMe = Address(0, None)
    val t = HtmlRenderer(testMe, true)
    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>num</th><th>street</th></tr></thead><tbody><tr><td>0</td><td></td></tr></tbody></table>""",
      t.toString()
    )

    val test2 = Address(1, Some("happyland"))
    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>num</th><th>street</th></tr></thead><tbody><tr><td>1</td><td>happyland</td></tr></tbody></table>""",
      HtmlRenderer(test2, true).toString()
    )
  }
