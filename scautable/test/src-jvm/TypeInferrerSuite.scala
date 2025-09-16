package io.github.quafadas.scautable
import scala.NamedTuple.*

import io.github.quafadas.table.*

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
  // TypeInferrer.FirstRow
  // ---------------------------

  test("TypeInferrer.FirstRow should automatically detect numeric and string columns") {
    val csv = CSV.resource(
      "data_without_headers.csv",
      HeaderOptions.Manual("name", "age", "profession"),
      TypeInferrer.FirstRow
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

    test("TypeInferrer.FirstRow should fail compilation if accessed with wrong type") {
    val csv = CSV.resource(
      "data_without_headers.csv",
      HeaderOptions.Manual("name", "age", "profession"),
      TypeInferrer.FirstRow
    )

    assert(
      compileErrors("""csv.column["age"].map(_.toUpperCase)""")
        .contains("""value toUpperCase is not a member of Int""")
    )
  }

  // ---------------------------
  // TypeInferrer.FirstN
  // ---------------------------

  test("TypeInferrer.FirstN should detect Int, Double, Long, and String types automatically") {
    val csv = CSV.resource(
      "typeTest.csv",
      HeaderOptions.Default,
      TypeInferrer.FirstN(3)
    )

    val rows = csv.toArray
    assertEquals(rows.length, 3) 

    assertEquals(rows(0).col1, 1)
    assertEquals(rows(1).col1, 2)
    assertEquals(rows(2).col1, 3)

    assertEquals(rows(0).col2, 1.0)
    assertEquals(rows(1).col2, 2.0)
    assertEquals(rows(2).col2, 3.0)

    assertEquals(rows(0).col3, 2147483648L)
    assertEquals(rows(1).col3, 2147483649L)
    assertEquals(rows(2).col3, 2147483650L)

    assertEquals(rows(0).col4, "1")   
    assertEquals(rows(1).col4, "b") 
    assertEquals(rows(2).col4, "\\") 

    assertEquals(rows(0).col5, "a")
    assertEquals(rows(1).col5, "b")
    assertEquals(rows(2).col5, "c")
  }

  test("TypeInferrer.FirstN should detect Int, Double, Long, and String types with missing values") {
    val csv = CSV.resource(
      "typeTestWithMissing.csv",
      HeaderOptions.Default,
      TypeInferrer.FirstN(3)
    )

    val rows = csv.toArray
    assertEquals(rows.length, 3) 

    assertEquals(rows(0).col1, Some(1))
    assertEquals(rows(1).col1, Some(2))
    assertEquals(rows(2).col1, None)

    assertEquals(rows(0).col2, Some(1.0))
    assertEquals(rows(1).col2, None)
    assertEquals(rows(2).col2, Some(3.5))

    assertEquals(rows(0).col3, Some(2147483648L))
    assertEquals(rows(1).col3, Some(2147483649L))
    assertEquals(rows(2).col3, None)

    assertEquals(rows(0).col4, "hello")
    assertEquals(rows(1).col4, "world")
    assertEquals(rows(2).col4, "!")
  }
  
  // ---------------------------
  // TypeInferrer.FromTuple[T]()
  // ---------------------------

  test("TypeInferrer.fromTuple should apply provided column types explicitly") {
    
    val csv: CsvIterator[("name", "age", "profession"), (String, Int, String)] =
      CSV.resource(
        "data_without_headers.csv",
        HeaderOptions.Manual("name", "age", "profession"),
        TypeInferrer.FromTuple[(String, Int, String)]()
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
    
    enum Status:
      case Active, Inactive
      
    inline given Decoder[Status] with
      def decode(str: String): Option[Status] =
        str match
          case "Active"   => Some(Status.Active)
          case "Inactive" => Some(Status.Inactive)
          case _          => None

    val csv: CsvIterator[("name", "active", "status"), (String, Boolean, Status)] =
      CSV.resource("custom_types.csv", TypeInferrer.FromTuple[(String, Boolean, Status)]())

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

  test("That boolean is preferred to Int where set") {
    // These two imports are equivalent
    val csvAll: CsvIterator[("c1", "c2"), (Int, Boolean)] = CSV.fromString("c1,c2\n0,0\n1,1\n2,1", TypeInferrer.FromAllRows)
    val csv: CsvIterator[("c1", "c2"), (Int, Boolean)] = CSV.fromString("c1,c2\n0,0\n1,1\n2,1", TypeInferrer.FirstN(Int.MaxValue , false))    
    val row1 = csv.next()
    assertEquals(row1.c1, 0)
    assertEquals(row1.c2, false)
  }


end TypeInferrerSuite
