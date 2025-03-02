import org.scalatest.funsuite.AnyFunSuite
import io.github.quafadas.table.*

class CSVWriterTest extends AnyFunSuite {

  test("CSVWriter should correctly write Named Tuples to CSV") {
    val data = Seq(("Alice", 30, 5.5), ("Bob", 25, 6.0))
    CSVWriter.writeCsv(data, "test_output.csv")

    val result = scala.io.Source.fromFile("test_output.csv").getLines().toList
    assert(result.head == "Alice,30,5.5")
    assert(result(1) == "Bob,25,6.0")
  }
}
