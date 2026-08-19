package io.github.quafadas.scautable.parquet

import io.github.quafadas.table.ReadAs

import scala.NamedTuple.NamedTuple

/** Parquet itself has no notion of a multi-file dataset — each file is compiled independently, against its own footer.
  *
  * `sales_2021/2022/2023.parquet` share one schema (a small partitioned-by-year dataset), so the three `Parquet.resource` calls below infer the *identical* static
  * `ParquetIterator[K, V]` type. That's what actually makes combining them safe: ordinary `Iterator` operations like `++` just work, and the compiler rejects any attempt to
  * combine files whose schemas disagree.
  */
class ParquetMultiFileSuite extends munit.FunSuite:

  type SalesNames = ("order_id", "region", "amount", "quantity")
  type SalesTypes = (Long, String, Double, Option[Int])
  type SalesIterator = ParquetIterator[SalesNames, SalesTypes]

  test("three files with the same schema infer the identical static type"):
    // The ascription is the check - this wouldn't compile if any file's schema disagreed.
    val y2021: SalesIterator = Parquet.resource("sales/sales_2021.parquet")
    val y2022: SalesIterator = Parquet.resource("sales/sales_2022.parquet")
    val y2023: SalesIterator = Parquet.resource("sales/sales_2023.parquet")

    assertEquals(y2021.toVector.size, 3)
    assertEquals(y2022.toVector.size, 4)
    assertEquals(y2023.toVector.size, 2)

  test("same-schema files concatenate into one logical dataset with plain Iterator#++"):
    val all: Vector[NamedTuple[SalesNames, SalesTypes]] =
      (Parquet.resource("sales/sales_2021.parquet") ++ Parquet.resource("sales/sales_2022.parquet") ++ Parquet.resource("sales/sales_2023.parquet")).toVector

    assertEquals(all.size, 9)
    assertEquals(all.map(_.order_id).toSet.size, 9) // every id unique across the three files
    assertEqualsDouble(all.map(_.amount).sum, 2895.23, 0.001)
    assertEquals(all.count(_.quantity.isEmpty), 3)

  test("aggregating across files, e.g. total amount by region"):
    val all = (Parquet.resource("sales/sales_2021.parquet") ++ Parquet.resource("sales/sales_2022.parquet") ++ Parquet.resource("sales/sales_2023.parquet")).toVector
    val byRegion = all.groupBy(_.region).view.mapValues(_.map(_.amount).sum).toMap

    assertEqualsDouble(byRegion("EMEA"), 250.5 + 310.25 + 999.99 + 500.0, 0.001)
    assertEqualsDouble(byRegion("APAC"), 99.99 + 120.0, 0.001)
    assertEqualsDouble(byRegion("AMER"), 450.0 + 89.5 + 75.0, 0.001)

  test("ReadAs.Columns unifies the same way, one array-tuple per file"):
    val a = Parquet.resource("sales/sales_2021.parquet", ReadAs.Columns)
    val b = Parquet.resource("sales/sales_2022.parquet", ReadAs.Columns)

    assertEqualsDouble(a.amount.sum + b.amount.sum, 800.49 + 1519.74, 0.001)

  test("a schema mismatch across files is rejected at compile time, not silently coerced"):
    // titanic.parquet has a completely different schema - ascribing it to the sales shape
    // must fail to compile, so a file swap in a multi-file pipeline can't silently pass at runtime.
    val err = compileErrors(
      """val wrong: ParquetIterator[("order_id", "region", "amount", "quantity"), (Long, String, Double, Option[Int])] = Parquet.resource("titanic.parquet")"""
    )
    assert(err.nonEmpty, "expected a type mismatch compile error")

  test("resourceDir reads every file in a directory, in file-name order, as a single Iterator"):
    val all: ParquetDirIterator[SalesNames, SalesTypes] = Parquet.resourceDir("sales")

    val rows = all.toVector
    assertEquals(rows.size, 9)
    assertEquals(rows.map(_.order_id), Vector(1001L, 1002L, 1003L, 2001L, 2002L, 2003L, 2004L, 3001L, 3002L))
    assertEqualsDouble(rows.map(_.amount).sum, 2895.23, 0.001)
    assertEquals(rows.count(_.quantity.isEmpty), 3)

  test("resourceDir agrees with concatenating each file by hand"):
    val byDir = Parquet.resourceDir("sales").toVector
    val byHand = (Parquet.resource("sales/sales_2021.parquet") ++ Parquet.resource("sales/sales_2022.parquet") ++ Parquet.resource("sales/sales_2023.parquet")).toVector

    assertEquals(byDir, byHand)

  test("resourceDir is single use and closes the currently-open file's handle"):
    val all = Parquet.resourceDir("sales")
    assertEquals(all.toVector.size, 9)
    assert(!all.hasNext)
    intercept[NoSuchElementException](all.next())

  test("an empty or missing directory is a compile-time error"):
    val err = compileErrors("""Parquet.resourceDir("nope")""")
    assert(err.nonEmpty, "expected a compile error for a missing directory")

  test("a directory whose files disagree on schema is a compile-time error"):
    val err = compileErrors("""Parquet.resourceDir("mismatched")""")
    assert(err.contains("shares one schema"), s"unexpected error message: $err")

end ParquetMultiFileSuite
