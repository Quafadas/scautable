import java.io.{BufferedWriter, FileWriter}
import io.github.quafadas.table.*

object CSVWriter {

  // Method to write Named Tuples to CSV
  def writeCsv[T <: Product](data: Seq[T], filePath: String): Unit = {
    val writer = new BufferedWriter(new FileWriter(filePath))

    // Extract headers from the first tuple
    val headers = data.head.productElementNames.mkString(",")
    writer.write(headers + "\n")

    // Write data rows
    data.foreach { tuple =>
      val row = tuple.productIterator.mkString(",")
      writer.write(row + "\n")
    }

    writer.close()
  }
}
