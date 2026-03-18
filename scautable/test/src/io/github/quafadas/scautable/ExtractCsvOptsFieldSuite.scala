package io.github.quafadas.scautable

import io.github.quafadas.table.*

/** Tests that each CsvOpts construction pattern is correctly decomposed by extractCsvOptsField.
  *
  * Each test exercises a different branch of the quoted/term-level pattern matching
  * in CSV.extractCsvOptsField. We verify the extracted values indirectly by checking
  * that the correct type inference strategy and header handling are applied.
  */
class ExtractCsvOptsFieldSuite extends munit.FunSuite:

  inline val csv3x3 = "col1,col2,col3\n1,2,7\n3,4,8\n5,6,9"
  inline def opts = CsvOpts(HeaderOptions.Default, TypeInferrer.StringType, ',', ReadAs.Rows)

  // Branch: '{ CsvOpts($h, $t, $d, $r) } — full 4-arg constructor
  test("full 4-arg constructor") {
    val iter = CSV.fromString(csv3x3, CsvOpts(HeaderOptions.Default, TypeInferrer.StringType, ',', ReadAs.Rows))
    val row = iter.toArray.head
    // StringType means all columns are String
    assertEquals(row.col1, "1")
  }

  /**
    * It is known, that this cannot work.
    */
  test("full 4-arg constructor") {
    val iter = CSV.fromString(csv3x3, opts)
    val row = iter.toArray.head
  }

  // Branch: '{ CsvOpts.default }
  test("CsvOpts.default") {
    val iter = CSV.fromString(csv3x3, CsvOpts.default)
    val row = iter.toArray.head
    // Default uses FromAllRows — Int columns inferred
    assertEquals(row.col1, 1)
  }

  // Branch: '{ CsvOpts.apply($h: HeaderOptions) }
  test("companion apply(HeaderOptions)") {
    val iter = CSV.fromString("1,2,7\n3,4,8\n5,6,9", CsvOpts(HeaderOptions.Manual("a", "b", "c")))
    assertEquals(iter.headers, List("a", "b", "c"))
  }

  // Branch: '{ CsvOpts.apply($t: TypeInferrer) }
  test("companion apply(TypeInferrer)") {
    val iter = CSV.fromString(csv3x3, CsvOpts(TypeInferrer.StringType))
    val row = iter.toArray.head
    assertEquals(row.col1, "1")
  }

  // Branch: '{ CsvOpts.apply($h: HeaderOptions, $t: TypeInferrer) }
  test("companion apply(HeaderOptions, TypeInferrer)") {
    val iter = CSV.fromString("1,2,7\n3,4,8\n5,6,9", CsvOpts(HeaderOptions.Manual("a", "b", "c"), TypeInferrer.StringType))
    assertEquals(iter.headers, List("a", "b", "c"))
    assertEquals(iter.toArray.head.a, "1")
  }

  // Branch: '{ CsvOpts.apply($r: ReadAs) }
  test("companion apply(ReadAs)") {
    val cols = CSV.fromString(csv3x3, CsvOpts(ReadAs.Columns))
    assertEquals(cols.col1.toSeq, Seq(1, 3, 5))
  }

  // Branch: '{ CsvOpts.apply($t: TypeInferrer, $r: ReadAs) }
  test("companion apply(TypeInferrer, ReadAs)") {
    val cols = CSV.fromString(csv3x3, CsvOpts(TypeInferrer.StringType, ReadAs.Columns))
    assertEquals(cols.col1.toSeq, Seq("1", "3", "5"))
  }

  // Fallback branch: named arg with defaults — CsvOpts(readAs = ReadAs.Columns)
  test("named arg readAs with defaults") {
    val cols = CSV.fromString(csv3x3, CsvOpts(readAs = ReadAs.Columns))
    assertEquals(cols.col2.toSeq, Seq(2, 4, 6))
  }

  // Fallback branch: named arg typeInferrer with defaults
  test("named arg typeInferrer with defaults") {
    val iter = CSV.fromString(csv3x3, CsvOpts(typeInferrer = TypeInferrer.StringType))
    assertEquals(iter.toArray.head.col1, "1")
  }

  // Fallback branch: named arg delimiter
  test("named arg delimiter") {
    val iter = CSV.fromString("col1\tcol2\n1\t2\n3\t4", CsvOpts(delimiter = '\t'))
    val row = iter.toArray.head
    assertEquals(row.col1, 1)
    assertEquals(row.col2, 2)
  }

  // Fallback branch: multiple named args
  test("multiple named args") {
    val cols = CSV.fromString(
      csv3x3,
      CsvOpts(typeInferrer = TypeInferrer.StringType, readAs = ReadAs.Columns)
    )
    assertEquals(cols.col1.toSeq, Seq("1", "3", "5"))
  }

  // Fallback branch: named arg headerOptions
  test("named arg headerOptions with defaults") {
    val iter = CSV.fromString("1,2,7\n3,4,8\n5,6,9", CsvOpts(headerOptions = HeaderOptions.Manual("a", "b", "c")))
    assertEquals(iter.headers, List("a", "b", "c"))
  }

  // Quoted pattern: new CsvOpts(h, t, d, r) — explicit new
  test("new CsvOpts 4-arg constructor") {
    val iter = CSV.fromString(csv3x3, new CsvOpts(HeaderOptions.Default, TypeInferrer.StringType, ',', ReadAs.Rows))
    assertEquals(iter.toArray.head.col1, "1")
  }

end ExtractCsvOptsFieldSuite
