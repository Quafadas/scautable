import scalatags.Text.all.*
import java.time.LocalDate
class MySuite extends munit.FunSuite {

  import scautable.*
  import scautable.{given}

  case class ScauTest(anInt: Int, aString: String)

  test("one row") {
    val start    = ScauTest(1, "2")
    val startSeq = Seq(start)    
    assertEquals("<table><tbody><tr><th>anInt</th><th>aString</th></tr><tr><td>1</td><td>2</td></tr></tbody></table>", scautable(startSeq).toString())
  }
  test("three rows") {    
    val start    = ScauTest(1, "2")
    val startSeq = Seq.fill(3)(start)    
    assertEquals("<table><tbody><tr><th>anInt</th><th>aString</th></tr><tr><td>1</td><td>2</td></tr><tr><td>1</td><td>2</td></tr><tr><td>1</td><td>2</td></tr></tbody></table>", scautable(startSeq).toString())
  }
  test("built in types") {
    case class EasyTypes(s: String, i: Int, l:Long, d:Double, b:Boolean)
    val startSeq = Seq(EasyTypes("hi",1, 2, 3.0, false))
    assertEquals("<table><tbody><tr><th>s</th><th>i</th><th>l</th><th>d</th><th>b</th></tr><tr><td>hi</td><td>1</td><td>2</td><td>3.0</td><td>false</td></tr></tbody></table>", scautable(startSeq).toString())
  }
  test("extendable") {  
    given dateT: HtmlTableRender[LocalDate] = new HtmlTableRender[LocalDate] {
      override def tableCell(a: LocalDate) = td(
        s"$a"
      )    
    }
    case class Customize(t: LocalDate, i: Int)
    val custom = Seq(Customize(LocalDate.of(2025,1,1), 1))
    assertEquals("<table><tbody><tr><th>t</th><th>i</th></tr><tr><td>2025-01-01</td><td>1</td></tr></tbody></table>", scautable(custom).toString())

  }
  test("not compoundable - alas") {
    case class Address(num: Int, street: String)
    case class Person(n: String, age: Int, a:Address)
    
    val one = Person("me", 5, Address(0, "happyland"))
    val listOne = Seq(one)
    interceptMessage[Exception]("compound case classes not foreseen"){scautable(listOne).toString()}

  }


}
