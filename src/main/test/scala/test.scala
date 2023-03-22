import scalatags.Text.all.*
import java.time.LocalDate
class MySuite extends munit.FunSuite {

  import scautable.*
  import scautable.{given}

  case class ScauTest(anInt: Int, aString: String)

  test("one row") {
    val start    = ScauTest(1, "2")
    val startSeq = start
    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>anInt</th><th>aString</th></tr></thead><tbody><tr><td>1</td><td>2</td></tr></tbody></table>""",
      scautable(startSeq, true).toString()
    )
  }
  test("one row as seq") {
    val start    = ScauTest(1, "2")
    val startSeq = Seq(start)
    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>anInt</th><th>aString</th></tr></thead><tbody><tr><td>1</td><td>2</td></tr></tbody></table>""",
      scautable(startSeq).toString()
    )
  }
  test("tuple") {
    val start    = (1, "2")
    val startSeq = Seq(start)
    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>_1</th><th>_2</th></tr></thead><tbody><tr><td>1</td><td>2</td></tr></tbody></table>""",
      scautable(startSeq).toString()
    )
  }
  test("three rows") {
    val start    = ScauTest(1, "2")
    val startSeq = Seq.fill(3)(start)
    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>anInt</th><th>aString</th></tr></thead><tbody><tr><td>1</td><td>2</td></tr><tr><td>1</td><td>2</td></tr><tr><td>1</td><td>2</td></tr></tbody></table>""",
      scautable(startSeq).toString()
    )
  }
  test("built in types") {
    case class EasyTypes(s: String, i: Int, l: Long, d: Double, b: Boolean)
    val startSeq = Seq(EasyTypes("hi", 1, 2, 3.1, false))
    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>s</th><th>i</th><th>l</th><th>d</th><th>b</th></tr></thead><tbody><tr><td>hi</td><td>1</td><td>2</td><td>3.1</td><td>false</td></tr></tbody></table>""",
      scautable(startSeq).toString()
    )
  }

  test("Seq") {
    case class SeqMe(s: Seq[String])
    val startSeq = SeqMe(Seq("happy", "land"))
    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>s</th></tr></thead><tbody><tr><td><table><tbody><tr><td>happy</td></tr><tr><td>land</td></tr></tbody></table></td></tr></tbody></table>""",
      scautable(startSeq, true).toString()
    )
  }

  test("compoundable") {
    case class Address(num: Int, street: String)
    case class Person(n: String, age: Int, a: Address)

    val one     = Person("me", 5, Address(0, "happyland"))
    val listOne = Seq(one)

    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>n</th><th>age</th><th>a</th></tr></thead><tbody><tr><td>me</td><td>5</td><td><table><thead><tr><th>num</th><th>street</th></tr></thead><tbody><tr><td>0</td><td>happyland</td></tr></tbody></table></td></tr></tbody></table>""",
      scautable(listOne).toString()
    )
  }

  test("Compound Product Seq") {
    case class SeqMe(s: Seq[(Int, String)])
    val startSeq = SeqMe(Seq((1, "happy"), (2, "land")))
    println(startSeq)
    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>s</th></tr></thead><tbody><tr><td><table><thead><tr><th>_1</th><th>_2</th></tr></thead><tbody><tr><td>1</td><td>happy</td></tr><tr><td>2</td><td>land</td></tr></tbody></table></td></tr></tbody></table>""",
      scautable(startSeq, true).toString()
    )
  }

  test("optionable") {
    case class Address(num: Int, street: Option[String])
    val testMe = Address(0, None)
    val t      = scautable(testMe, true)
    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>num</th><th>street</th></tr></thead><tbody><tr><td>0</td><td></td></tr></tbody></table>""",
      t.toString()
    )

    val test2 = Address(1, Some("happyland"))
    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>num</th><th>street</th></tr></thead><tbody><tr><td>1</td><td>happyland</td></tr></tbody></table>""",
      scautable(test2, true).toString()
    )
  }
}
