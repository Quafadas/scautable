package io.github.quafadas.scautable

import io.github.quafadas.scautable.json.JsonTable
import io.github.quafadas.table.*

/** Excel and JSON counterparts to [[CsvPathAnchoringSuite]].
  *
  * The test sources live in `scautable/test/src-jvm`, so `../resources/x` from here is `scautable/test/resources/x` - which is what the project-root anchored calls address by
  * their full path.
  */
class AnchoredPathSuite extends munit.FunSuite:

  test("Excel.relativeToSource resolves path from call-site source directory") {
    val sheet = Excel.relativeToSource("../resources/SimpleTable.xlsx", "Sheet1", TypeInferrer.StringType)
    assertEquals(sheet.column["Column 1"].toList.head, "Row 1, Col 1")
  }

  test("Excel.projectRoot resolves path from marker-discovered project root") {
    val sheet = Excel.projectRoot("scautable/test/resources/SimpleTable.xlsx", "Sheet1", TypeInferrer.StringType)
    assertEquals(sheet.column["Column 1"].toList.head, "Row 1, Col 1")
  }

  test("Excel anchored constructors treat a leading separator as anchor-relative") {
    val sheet = Excel.projectRoot("/scautable/test/resources/SimpleTable.xlsx", "Sheet1", TypeInferrer.StringType)
    assertEquals(sheet.column["Column 1"].toList.head, "Row 1, Col 1")
  }

  test("JsonTable.relativeToSource resolves path from call-site source directory") {
    val json = JsonTable.relativeToSource("../resources/simple.json")
    assertEquals(json.toSeq.length, 3)
  }

  test("JsonTable.projectRoot resolves path from marker-discovered project root") {
    val json = JsonTable.projectRoot("scautable/test/resources/simple.json")
    assertEquals(json.toSeq.length, 3)
  }

  test("JsonTable anchored constructors treat a leading separator as anchor-relative") {
    val json = JsonTable.projectRoot("/scautable/test/resources/simple.json")
    assertEquals(json.toSeq.length, 3)
  }

end AnchoredPathSuite
