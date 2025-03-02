import io.circe.syntax._
import io.circe.generic.auto._
import java.io.{BufferedWriter, FileWriter}

object JSONWriter {

  // Method to write Named Tuples to JSON
  def writeJson[T <: Product](data: Seq[T], filePath: String): Unit = {
    val writer = new BufferedWriter(new FileWriter(filePath))

    // Convert data to JSON array and write to file
    val jsonArray = data.asJson
    writer.write(jsonArray.spaces2)

    writer.close()
  }
}
