//> using scala 3.7.0-RC1
//> using dep io.github.quafadas::scautable:0.0.20
//> using resourceDir ./scuatable/test/resources/testFile/

import io.github.quafadas.table.*
import java.nio.file.{Files, Paths, Path}
import scala.jdk.StreamConverters.*
import scala.util.{Try, Success, Failure}
import scala.io.Source
import java.io.PrintWriter

@main def testFile =
  val resourcesDir = Paths.get("resources")

  // Validate resources directory
  if !Files.exists(resourcesDir) || !Files.isDirectory(resourcesDir) then
    println(s"Invalid resources directory: ${resourcesDir.toAbsolutePath}")
    System.exit(1)
  end if

  // List all files in the directory
  val files = Files
    .list(resourcesDir)
    .filter(Files.isRegularFile(_))
    .toScala(List)
    .sortBy(_.getFileName.toString)

  // Process each file and generate a report
  val report = files.map { path =>
    val fileName = path.getFileName.toString
    val fileSize = Try(Files.size(path)).getOrElse(0L)

    // Attempt to parse the file as a CSV using Scautable
    // val csvStatus = Try {
    // TODO: Biggest Hurdle is this Expected a Known Value [The value of: path.toString()]
    //   CSV.resource(path.toString).take(5).map(_.productIterator.mkString(",")).mkString("|")
    // } match {
    //   case Success(_) => "CSV_OK"
    //   case Failure(_) => "CSV_FAIL"
    // }

    val csvStatus = "Expected a Known value Error" // just a error skipper

    // Fallback to text reading if CSV parsing fails
    val textStatus = Try {
      Source.fromFile(path.toFile).getLines().take(5).mkString("|")
    } match
      case Success(_)  => "TEXT_OK"
      case Failure(ex) => s"TEXT_FAIL: ${ex.getMessage.take(100)}" // Include failure reason

    // Generate a report entry for the current file
    s"""$fileName
       │Size: ${fileSize}B
       │CSV Status: $csvStatus
       │Text Status: $textStatus
       │${"-" * 60}""".stripMargin
  }

  // Write the report to a file
  new PrintWriter("file_validation_report.txt"):
    try write(s"""File Validation Report
         |Date: ${java.time.LocalDate.now}
         |Scanned Directory: ${resourcesDir.toAbsolutePath}
         |Total Files: ${files.size}
         |${"-" * 60}
         |${report.mkString("\n")}""".stripMargin)
    finally close()
    end try
  end new

  println("File validation report generated: file_validation_report.txt")
end testFile
