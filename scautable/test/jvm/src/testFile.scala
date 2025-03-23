//> using scala 3.6.4
//> using dep io.github.quafadas::scautable:0.0.19

//> using resourceDir ./scuatable/test/resources

//> using options "-experimental" "-language:experimental.namedTuples"

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
    println(s"Resources directory not found at ${resourcesDir.toAbsolutePath}")
    System.exit(1)
  end if

  // Get sorted files with metadata
  val files = Files
    .list(resourcesDir)
    .filter(Files.isRegularFile(_))
    .toScala(List)
    .sortBy(_.getFileName.toString)
    .map(p => (p, Try(Files.size(p)).getOrElse(0L)))

  // Process files with error handling
  val report = files.map { case (path, size) =>
    val fileName = path.getFileName.toString
    val (status, preview, error) = Try {
      val source = Source.fromFile(path.toFile)
      try
        val lines = source.getLines().take(5).toList
        val contentPreview =
          if lines.isEmpty then "<EMPTY FILE>"
          else lines.map(_.take(50)).mkString("|") // Truncate long lines
        (s"(${size}B)", contentPreview, "")
      finally source.close()
      end try
    } match
      case Failure(ex) =>
        (s"(${size}B)", "", s"${ex.getClass.getSimpleName}: ${ex.getMessage}")
      case Success((s, p, e)) => (s, p, e)

    s"""$fileName
       │Status: $status
       │${if error.nonEmpty then s"Error: $error" else s"Preview: $preview"}
       │${"-" * 60}""".stripMargin
  }

  // Write formatted report
  val outputFile = new PrintWriter("file_check_report.txt")
  try
    outputFile.println(s"""
      |Resources Directory: ${resourcesDir.toAbsolutePath}
      |Total Files Scanned: ${files.size}
      |${"-" * 60}
      |${report.mkString("\n")}
      |""".stripMargin.trim)
  finally outputFile.close()
  end try

  println(s"Report generated: file_check_report.txt")
end testFile
