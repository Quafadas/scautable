package parquetusage

import io.github.quafadas.scautable.parquet.Parquet
import io.github.quafadas.table.ReadAs

import scala.compiletime.testing.typeChecks

/** This suite deliberately lives *outside* `io.github.quafadas.scautable`.
  *
  * The `Parquet` macros splice references to their supporting machinery into the caller's code. Anything package-private would compile happily inside the module's own tests, but
  * blow up with a reference error for a real user — which is exactly what happened in the REPL. Compiling the same calls from a foreign package is the cheap guard against that.
  */
class ParquetVisibilitySuite extends munit.FunSuite:

  test("the row oriented macro expands in a foreign package"):
    assert(typeChecks("""Parquet.resource("titanic.parquet")"""))    

  test("the column oriented macro expands in a foreign package"):
    assert(typeChecks("""Parquet.resource("titanic.parquet", ReadAs.Columns)"""))
    val titanic = Parquet.resource("titanic.parquet", ReadAs.Columns)
    assertEquals(titanic.PassengerId.length, 891)

end ParquetVisibilitySuite
