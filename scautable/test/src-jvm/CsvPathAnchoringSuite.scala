package io.github.quafadas.scautable

import io.github.quafadas.table.*

class CsvPathAnchoringSuite extends munit.FunSuite:

  test("CSV.relativeToSource resolves path from call-site source directory") {
    val csv: CsvIterator[("a", "b"), (Int, Int)] = CSV.relativeToSource("anchored.csv")
    assertEquals(csv.toList.map(_.a), List(1, 3))
  }

  test("CSV.projectRoot resolves path from marker-discovered project root") {
    val csv: CsvIterator[("col1", "col2", "col3"), (Int, Int, Int)] =
      CSV.projectRoot("scautable/test/resources/simple.csv")
    assertEquals(csv.toList.size, 3)
  }

  test("runtime fallback chain uses root-relative path when absolute path is unavailable") {
    val cwd = java.nio.file.Paths.get(".").toAbsolutePath.normalize
    val tempFile = java.nio.file.Files.createTempFile(cwd, "csv-path-fallback", ".csv")
    java.nio.file.Files.writeString(tempFile, "x,y\n9,8\n")

    val source = CSV.openSourceWithFallback(
      absolutePath = cwd.resolve("missing-absolute.csv").toString,
      rootRelativePath = tempFile.getFileName.toString,
      resourceName = "simple.csv"
    )
    try assertEquals(source.getLines().next(), "x,y")
    finally
      source.close()
      java.nio.file.Files.deleteIfExists(tempFile)
  }

  test("runtime fallback chain uses classpath resource when file paths are unavailable") {
    val source = CSV.openSourceWithFallback(
      absolutePath = "/definitely/missing/path.csv",
      rootRelativePath = "also/missing/path.csv",
      resourceName = "simple.csv"
    )
    try assertEquals(source.getLines().next(), "col1,col2,col3")
    finally source.close()
  }
end CsvPathAnchoringSuite
