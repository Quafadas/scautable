package io.github.quafadas.scautable

import java.time.LocalDate
import io.github.quafadas.table.*

import NamedTuple.*

import scala.compiletime.ops.int.S

class TypeInferrerSuite extends munit.FunSuite:

  // ---------------------------
  // TypeInferrer.StringType
  // ---------------------------

  test("TypeInferrer.StringType should allow any string transformations without errors") {
    val csv: CsvIterator[("name", "age", "profession"), (String, String, String)] =
      CSV.resource(
        "data_without_headers.csv",
        HeaderOptions.Manual("name", "age", "profession"),
        TypeInferrer.StringType
      )

    val upperNames = csv.column["name"].map(_.toUpperCase).toArray
    assertEquals(upperNames.head, "ALICE")
    assertEquals(upperNames.last, "CHARLIE")
  }

  test("TypeInferrer.StringType should treat all columns as String") {
    val csv: CsvIterator[("name", "age", "profession"), (String, String, String)] =
      CSV.resource(
        "data_without_headers.csv",
        HeaderOptions.Manual("name", "age", "profession"),
        TypeInferrer.StringType
      )

    assertEquals(csv.headers, List("name", "age", "profession"))

    val rows = csv.toArray
    assertEquals(rows.length, 3)

    assertEquals(rows(0).name, "Alice")
    assertEquals(rows(0).age, "25")           
    assertEquals(rows(0).profession, "Engineer")

    assertEquals(rows(1).age, "30")
    assertEquals(rows(2).age, "22")
  }

  // ---------------------------
  // TypeInferrer.Auto
  // ---------------------------

  test("TypeInferrer.Auto should automatically detect numeric and string columns") {
    val csv = CSV.resource(
      "data_without_headers.csv",
      HeaderOptions.Manual("name", "age", "profession"),
      TypeInferrer.Auto
    )

    assertEquals(csv.headers, List("name", "age", "profession"))

    val rows = csv.toArray
    assertEquals(rows.length, 3)

    assertEquals(rows(0).name, "Alice")
    assertEquals(rows(0).age, 25)            
    assertEquals(rows(0).profession, "Engineer")

    assertEquals(rows(1).name, "Bob")
    assertEquals(rows(1).age, 30)
    assertEquals(rows(1).profession, "Designer")

    assertEquals(rows(2).name, "Charlie")
    assertEquals(rows(2).age, 22)
    assertEquals(rows(2).profession, "Student")
  }

    test("TypeInferrer.Auto should fail compilation if accessed with wrong type") {
    val csv = CSV.resource(
      "data_without_headers.csv",
      HeaderOptions.Manual("name", "age", "profession"),
      TypeInferrer.Auto
    )

    assert(
      compileErrors("""csv.column["age"].map(_.toUpperCase)""")
        .contains("""value toUpperCase is not a member of Int""")
    )
  }

  // ---------------------------
  // TypeInferrer.fromTuple[T]
  // ---------------------------

  test("TypeInferrer.fromTuple should apply provided column types explicitly") {
    import io.github.quafadas.scautable.TypeInferrer
    val csv: CsvIterator[("name", "age", "profession"), (String, Int, String)] =
      CSV.resource(
        "data_without_headers.csv",
        HeaderOptions.Manual("name", "age", "profession"),
        TypeInferrer.fromTuple[(String, Int, String)]
      )

    assertEquals(csv.headers, List("name", "age", "profession"))

    val rows = csv.toArray
    assertEquals(rows.length, 3)

    assertEquals(rows(0).name, "Alice")
    assertEquals(rows(0).age, 25)            
    assertEquals(rows(0).profession, "Engineer")

    assertEquals(rows(1).name, "Bob")
    assertEquals(rows(1).age, 30)
    assertEquals(rows(1).profession, "Designer")

    assertEquals(rows(2).name, "Charlie")
    assertEquals(rows(2).age, 22)
    assertEquals(rows(2).profession, "Student")
  }

  test("TypeInferrer.fromTuple should work with Boolean and custom enum types") {
    import io.github.quafadas.scautable.TypeInferrer
    enum Status:
      case Active, Inactive
      
    inline given Decoder[Status] with
      def decode(str: String): Option[Status] =
        str match
          case "Active"   => Some(Status.Active)
          case "Inactive" => Some(Status.Inactive)
          case _          => None

    val csv: CsvIterator[("name", "active", "status"), (String, Boolean, Status)] =
      CSV.resource("custom_types.csv", TypeInferrer.fromTuple[(String, Boolean, Status)])

    assert(csv.hasNext)
    val row1 = csv.next()
    assertEquals(row1.name, "Alice")
    assertEquals(row1.active, true)
    assertEquals(row1.status, Status.Active)

    assert(csv.hasNext)
    val row2 = csv.next()
    assertEquals(row2.name, "Bob")
    assertEquals(row2.active, false)
    assertEquals(row2.status, Status.Inactive)

  }

  test("TypeInferrer.fromTuple should fail compilation if tuple types do not match CSV data") {
    assert(
      compileErrors("""
        val csv: CsvIterator[("name", "age"), (String, String)] =
          CSV.resource("data_without_headers.csv",
            HeaderOptions.Manual("name", "age"),
            TypeInferrer.fromTuple[(String, String)])
      """).nonEmpty
    )
  }

end TypeInferrerSuite
