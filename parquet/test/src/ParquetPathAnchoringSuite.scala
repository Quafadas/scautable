package io.github.quafadas.scautable.parquet

/** The test sources live in `parquet/test/src`, so `../resources/x` from here is `parquet/test/resources/x` - which is
  * what the project-root anchored calls address by their full path.
  */
class ParquetPathAnchoringSuite extends munit.FunSuite:

  test("Parquet.relativeToSource resolves path from call-site source directory") {
    val titanic = Parquet.relativeToSource("../resources/titanic.parquet")
    assertEquals(titanic.toSeq.size, 891)
  }

  test("Parquet.projectRoot resolves path from marker-discovered project root") {
    val titanic = Parquet.projectRoot("parquet/test/resources/titanic.parquet")
    assertEquals(titanic.toSeq.size, 891)
  }

  test("Parquet anchored constructors treat a leading separator as anchor-relative") {
    val titanic = Parquet.projectRoot("/parquet/test/resources/titanic.parquet")
    assertEquals(titanic.toSeq.size, 891)
  }

end ParquetPathAnchoringSuite
