package io.github.quafadas.scautable

import io.github.quafadas.table.*
import munit.FunSuite

class HeaderWidthFileSuite extends FunSuite:

  test("resource: header with a trailing delimiter is a compile error") {
    val err = compileErrors("""CSV.resource("header_trailing_delimiter.csv")""")
    assert(err.contains("Header has 4 columns (a, b, c, <empty>) but data rows have 3 columns"), err)
    assert(err.contains("trailing delimiter"), err)
  }

  test("resource: header narrower than the data is a compile error") {
    val err = compileErrors("""CSV.resource("header_short.csv", TypeInferrer.StringType)""")
    assert(err.contains("Header has 2 columns (a, b) but data rows have 3 columns"), err)
  }

  test("resource: Manual headers narrower than the data is a compile error") {
    val err = compileErrors("""CSV.resource("simple.csv", HeaderOptions.Manual("a", "b"))""")
    assert(err.contains("Header has 2 columns (a, b) but data rows have 3 columns"), err)
  }

  test("resource: FromTuple arity must match the header count") {
    val err = compileErrors("""CSV.resource("simple.csv", TypeInferrer.FromTuple[(Int, Int)]())""")
    assert(err.contains("FromTuple declares 2 column types but the header has 3 columns"), err)
  }

  test("fromTyped: data rows wider than the header fail clearly at runtime") {
    val reader = CSV.fromTyped[("col1", "col2", "col3"), (Int, Int, Int)]
    val path = os.Path(this.getClass.getClassLoader.getResource("data_wider_than_header.csv").getPath)
    val ex = intercept[IllegalArgumentException](reader(path))
    assert(ex.getMessage.contains("Header has 3 columns (col1, col2, col3) but data rows have 4 columns"), ex.getMessage)
  }

  test("fromTyped: header with a trailing delimiter fails clearly at runtime") {
    val reader = CSV.fromTyped[("a", "b", "c", ""), (Int, Int, Int, Int)]
    val path = os.Path(this.getClass.getClassLoader.getResource("header_trailing_delimiter.csv").getPath)
    val ex = intercept[IllegalArgumentException](reader(path))
    assert(ex.getMessage.contains("Header has 4 columns (a, b, c, <empty>) but data rows have 3 columns"), ex.getMessage)
    assert(ex.getMessage.contains("trailing delimiter"), ex.getMessage)
  }

end HeaderWidthFileSuite
