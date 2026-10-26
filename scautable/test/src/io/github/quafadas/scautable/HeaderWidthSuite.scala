package io.github.quafadas.scautable

import io.github.quafadas.table.*
import munit.FunSuite

/** The header line and the data rows must agree on how many columns there are. If they don't, the NamedTuple's names and values have different lengths - which is unsound - so it
  * must be a compile error.
  */
class HeaderWidthSuite extends FunSuite:

  test("matching header and data still compiles") {
    assertNoDiff(compileErrors("""CSV.fromString("a,b,c\n1,2,3")"""), "")
  }

  test("Manual headers with fewer names than data columns is a compile error") {
    val err = compileErrors("""CSV.fromString("1,2,3\n4,5,6", HeaderOptions.Manual("a", "b"))""")
    assert(err.contains("Header has 2 columns (a, b) but data rows have 3 columns"), err)
  }

  test("Manual headers with more names than data columns is a compile error") {
    val err = compileErrors("""CSV.fromString("1,2\n4,5", HeaderOptions.Manual("a", "b", "c"))""")
    assert(err.contains("Header has 3 columns (a, b, c) but data rows have 2 columns"), err)
  }

  test("header with fewer names than data columns is a compile error") {
    val err = compileErrors("""CSV.fromString("a,b\n1,2,3\n4,5,6")""")
    assert(err.contains("Header has 2 columns (a, b) but data rows have 3 columns"), err)
  }

  test("header with a trailing delimiter is a compile error that names the trailing delimiter") {
    val err = compileErrors("""CSV.fromString("a,b,c,\n1,2,3\n4,5,6")""")
    assert(err.contains("Header has 4 columns (a, b, c, <empty>) but data rows have 3 columns"), err)
    assert(err.contains("trailing delimiter"), err)
  }

  test("width mismatch is caught with FirstRow") {
    val err = compileErrors("""CSV.fromString("a,b\n1,2,3", TypeInferrer.FirstRow)""")
    assert(err.contains("Header has 2 columns (a, b) but data rows have 3 columns"), err)
  }

  test("width mismatch is caught with FirstN") {
    val err = compileErrors("""CSV.fromString("a,b\n1,2,3", TypeInferrer.FirstN(1))""")
    assert(err.contains("Header has 2 columns (a, b) but data rows have 3 columns"), err)
  }

  test("width mismatch is caught with StringType, which reads no data for inference") {
    val err = compileErrors("""CSV.fromString("a,b,c,\n1,2,3", TypeInferrer.StringType)""")
    assert(err.contains("Header has 4 columns (a, b, c, <empty>) but data rows have 3 columns"), err)
  }

  test("width mismatch is caught with FromTuple, even when the tuple agrees with the header") {
    val err = compileErrors("""CSV.fromString("a,b\n1,2,3", TypeInferrer.FromTuple[(Int, Int)]())""")
    assert(err.contains("Header has 2 columns (a, b) but data rows have 3 columns"), err)
  }

  test("FromTuple arity must match the header count") {
    val err = compileErrors("""CSV.fromString("a,b,c\n1,2,3", TypeInferrer.FromTuple[(Int, Int)]())""")
    assert(err.contains("FromTuple declares 2 column types but the header has 3 columns (a, b, c)"), err)
  }

  test("width mismatch is caught when reading as columns") {
    val err = compileErrors("""CSV.fromString("a,b\n1,2,3", CsvOpts(TypeInferrer.FromAllRows, ReadAs.Columns))""")
    assert(err.contains("Header has 2 columns (a, b) but data rows have 3 columns"), err)
  }

  test("width mismatch is caught when reading as a dense array") {
    val err = compileErrors("""CSV.fromString("a,b\n1,2,3", CsvOpts(readAs = ReadAs.ArrayDenseColMajor[Int]()))""")
    assert(err.contains("Header has 2 columns (a, b) but data rows have 3 columns"), err)
  }

end HeaderWidthSuite
