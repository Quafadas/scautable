import org.scalatest.funsuite.AnyFunSuite
import io.github.quafadas.table.*

class JSONWriterTest extends AnyFunSuite {

  test("JSONWriter should correctly write Named Tuples to JSON") {
    val data = Seq(("Alice", 30, 5.5), ("Bob", 25, 6.0))
    JSONWriter.writeJson(data, "test_output.json")

    val result = scala.io.Source.fromFile("test_output.json").getLines().mkString
    assert(result.contains("\"Alice\""))
    assert(result.contains("\"Bob\""))
  }
}
