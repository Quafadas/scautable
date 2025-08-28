package io.github.quafadas.scautable

import io.github.quafadas.table.*
import java.time.LocalDate
import scalatags.Text.all.*
import NamedTuple.*
import scala.compiletime.ops.int.S

class ExtendSuite extends munit.FunSuite:

  import HtmlRenderer.*
  import HtmlRenderer.given

  test("extendable") {

    given dateT: HtmlTableRender[LocalDate] = new HtmlTableRender[LocalDate]:
      override def tableCell(a: LocalDate) = td(
        s"$a"
      )
    case class Customize(t: LocalDate, i: Int)
    val custom = Seq(Customize(LocalDate.of(2025, 1, 1), 1))
    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>t</th><th>i</th></tr></thead><tbody><tr><td>2025-01-01</td><td>1</td></tr></tbody></table>""",
      HtmlRenderer(custom).toString()
    )
  }

  test("showable") {

    val nt = Seq(
      (col1 = "a", col2 = 1, col3 = 2.0),
      (col1 = "b", col2 = 2, col3 = 3.0),
      (col1 = "c", col2 = 3, col3 = 4.0)
    )

    assertEquals(
      """<table id="scautable" class="display"><thead><tr><th>col1</th><th>col2</th><th>col3</th></tr></thead><tbody><tr><td>a</td><td>1</td><td>2.0</td></tr><tr><td>b</td><td>2</td><td>3.0</td></tr><tr><td>c</td><td>3</td><td>4.0</td></tr></tbody></table>""",
      HtmlRenderer.nt(nt).toString()
    )
  }

end ExtendSuite
