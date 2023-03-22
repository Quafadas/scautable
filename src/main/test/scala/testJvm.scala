//> using target { platform "jvm" }
package scautable

import scalatags.Text.all.*
import java.time.LocalDate

class JVMSuite extends munit.FunSuite {

  import scautable.*
  import scautable.{given}  

  test("extendable") {
    given dateT: HtmlTableRender[LocalDate] = new HtmlTableRender[LocalDate] {
      override def tableCell(a: LocalDate) = td(
        s"$a"
      )
    }
    case class Customize(t: LocalDate, i: Int)
    val custom = Seq(Customize(LocalDate.of(2025, 1, 1), 1))
    assertEquals("""<table id="scautable" class="display"><thead><tr><th>t</th><th>i</th></tr></thead><tbody><tr><td>2025-01-01</td><td>1</td></tr></tbody></table>""", scautable(custom).toString())
  }
}
