package io.github.quafadas.scautable.parquet

import org.apache.parquet.schema.MessageTypeParser

import scala.NamedTuple.NamedTuple

class ParquetNestedSchemaSuite extends munit.FunSuite:

  type CoordinateNames = ("latitude", "longitude")
  type CoordinateValues = (Double, Double)
  type Coordinates = NamedTuple[CoordinateNames, CoordinateValues]

  type AddressNames = ("street", "postcode", "coordinates")
  type AddressValues = (String, Option[Int], Option[Coordinates])
  type Address = NamedTuple[AddressNames, AddressValues]

  type NestedNames = ("id", "address")
  type NestedValues = (Long, Option[Address])
  type ConcreteNestedValues = (Long, Address)

  type Identity = NamedTuple[Tuple1["name"], Tuple1[String]]
  type Preferences = NamedTuple[("theme", "alerts"), (Option[String], Option[Int])]
  type CoverageNames = ("id", "identity", "preferences")
  type CoverageValues = (Long, Identity, Option[Preferences])

  test("nested structs are represented as a recursive field tree"):
    val schema = MessageTypeParser.parseMessageType("""
      message nested {
        required int64 id;
        optional group address {
          required binary street (STRING);
          optional int32 postcode;
          optional group coordinates {
            required double latitude;
            required double longitude;
          }
        }
      }
    """)

    import ParquetField.*
    import ParquetScalaType.*

    assertEquals(
      ParquetSchema.fields(schema),
      Vector(
        Primitive("id", LongT, nullable = false, leafIndex = 0, presentDefinitionLevel = 0),
        Struct(
          "address",
          Vector(
            Primitive("street", StringT, nullable = false, leafIndex = 1, presentDefinitionLevel = 1),
            Primitive("postcode", IntT, nullable = true, leafIndex = 2, presentDefinitionLevel = 2),
            Struct(
              "coordinates",
              Vector(
                Primitive("latitude", DoubleT, nullable = false, leafIndex = 3, presentDefinitionLevel = 2),
                Primitive("longitude", DoubleT, nullable = false, leafIndex = 4, presentDefinitionLevel = 2)
              ),
              nullable = true,
              presentDefinitionLevel = 2
            )
          ),
          nullable = true,
          presentDefinitionLevel = 1
        )
      )
    )

  test("physical leaves retain their paths, order, and definition levels"):
    val schema = MessageTypeParser.parseMessageType("""
      message nested {
        required int64 id;
        optional group address {
          required binary street (STRING);
          optional int32 postcode;
        }
      }
    """)

    import ParquetScalaType.*

    assertEquals(
      ParquetSchema.leaves(ParquetSchema.fields(schema)),
      Vector(
        ParquetLeaf(Vector("id"), LongT, columnIndex = 0, maxDefinitionLevel = 0),
        ParquetLeaf(Vector("address", "street"), StringT, columnIndex = 1, maxDefinitionLevel = 1),
        ParquetLeaf(Vector("address", "postcode"), IntT, columnIndex = 2, maxDefinitionLevel = 2)
      )
    )

  test("repeated fields are rejected with their full path"):
    val schema = MessageTypeParser.parseMessageType("""
      message repeated_values {
        required group payload {
          repeated int64 values;
        }
      }
    """)

    val error = intercept[UnsupportedParquetSchemaException](ParquetSchema.fields(schema))
    assert(error.getMessage.contains("payload.values"))
    assert(error.getMessage.contains("REPEATED"))

  test("LIST and MAP logical types have explicit unsupported diagnostics"):
    val listSchema = MessageTypeParser.parseMessageType("""
      message lists {
        optional group tags (LIST) {
          repeated group list {
            required binary element (STRING);
          }
        }
      }
    """)
    val mapSchema = MessageTypeParser.parseMessageType("""
      message maps {
        optional group attributes (MAP) {
          repeated group key_value {
            required binary key (STRING);
            optional binary value (STRING);
          }
        }
      }
    """)

    val listError = intercept[UnsupportedParquetSchemaException](ParquetSchema.fields(listSchema))
    val mapError = intercept[UnsupportedParquetSchemaException](ParquetSchema.fields(mapSchema))

    assert(listError.getMessage.contains("tags"))
    assert(listError.getMessage.contains("LIST"))
    assert(mapError.getMessage.contains("attributes"))
    assert(mapError.getMessage.contains("MAP"))

  test("row types contain recursively inferred optional NamedTuples"):
    val nested: ParquetIterator[NestedNames, NestedValues] = Parquet.resource("nested_struct.parquet")

    assertEquals(nested.headers, Seq("id", "address"))
    nested.close()

  test("explicit ReadAs.Rows supports nested structs"):
    val nested: ParquetIterator[NestedNames, NestedValues] =
      Parquet.resource("nested_struct.parquet", io.github.quafadas.table.ReadAs.Rows)

    assertEquals(nested.next().address.map(_.street), Some("Main Street"))
    nested.close()

  test("nested rows distinguish populated, absent, and partially populated structs"):
    val rows = Parquet.resource("nested_struct.parquet").toVector

    assertEquals(rows.map(_.id), Vector(1L, 2L, 3L))
    assertEquals(rows(0).address.map(_.street), Some("Main Street"))
    assertEquals(rows(0).address.flatMap(_.postcode), Some(12345))
    assertEquals(rows(0).address.flatMap(_.coordinates).map(_.latitude), Some(51.5))
    assertEquals(rows(0).address.flatMap(_.coordinates).map(_.longitude), Some(-0.1))
    assertEquals(rows(1).address, None)
    assertEquals(rows(2).address.map(_.street), Some("Side Street"))
    assertEquals(rows(2).address.flatMap(_.postcode), None)
    assertEquals(rows(2).address.flatMap(_.coordinates), None)

  test("required structs and present structs with all children absent retain their shapes"):
    val nested: ParquetIterator[CoverageNames, CoverageValues] = Parquet.resource("nested_coverage.parquet")
    val rows = nested.toVector

    assertEquals(rows.map(_.identity.name), Vector("Alice", "Bob", "Carol"))
    assert(rows(0).preferences.nonEmpty)
    assertEquals(rows(0).preferences.map(_.theme), Some(None))
    assertEquals(rows(0).preferences.map(_.alerts), Some(None))
    assertEquals(rows(1).preferences, None)
    assertEquals(rows(2).preferences.flatMap(_.theme), Some("dark"))
    assertEquals(rows(2).preferences.flatMap(_.alerts), Some(3))

  test("nested rows are decoded across every row group"):
    val source = ParquetSource.Resource("nested_multi_row_group.parquet")
    val reader = source.openReader()
    try assert(reader.getFooter.getBlocks.size > 1)
    finally reader.close()
    end try

    val rows = Parquet.resource("nested_multi_row_group.parquet").toVector

    assertEquals(rows.size, 300)
    assertEquals(rows.map(_.id), (0L until 300L).toVector)
    assertEquals(rows.count(_.address.isEmpty), 75)
    assertEquals(rows(100).address.map(_.street.take(10)), Some("Street-100"))
    assertEquals(rows(100).address.flatMap(_.postcode), Some(10100))
    assertEquals(rows(297).address, None)
    assertEquals(rows(299).address.map(_.street.take(10)), Some("Street-299"))
    assertEquals(rows(299).address.flatMap(_.postcode), Some(10299))

  test("NoOptions removes options recursively from nested row types"):
    type ConcreteCoordinateValues = (Double, Double)
    type ConcreteCoordinates = NamedTuple[CoordinateNames, ConcreteCoordinateValues]
    type ConcreteAddressValues = (String, Int, ConcreteCoordinates)
    type ConcreteAddress = NamedTuple[AddressNames, ConcreteAddressValues]

    val nested: ParquetIterator[NestedNames, (Long, ConcreteAddress)] =
      Parquet.resource("nested_struct.parquet", ParquetOptionality.NoOptions)

    nested.close()

  test("NoOptions reports the full path of a missing nested struct"):
    val nested = Parquet.resource("nested_struct.parquet", ParquetOptionality.NoOptions)

    val firstError = intercept[ParquetDecodeException](nested.next())
    assert(firstError.getMessage.contains("Field 'address' is absent"))
    nested.close()

  test("NoOptions reports the full path of a missing nested child"):
    val nested = Parquet.resource("nested_coverage.parquet", ParquetOptionality.NoOptions)

    val error = intercept[ParquetDecodeException](nested.next())
    assert(error.getMessage.contains("Field 'preferences.theme' is absent"))
    nested.close()

  test("ReadAs.Columns rejects nested fields for both optionality modes"):
    val defaultError = compileErrors("""Parquet.resource("nested_struct.parquet", io.github.quafadas.table.ReadAs.Columns)""")
    val noOptionsError = compileErrors(
      """Parquet.resource("nested_struct.parquet", io.github.quafadas.table.ReadAs.Columns, ParquetOptionality.NoOptions)"""
    )

    assert(defaultError.contains("ReadAs.Columns does not yet support nested parquet fields"))
    assert(noOptionsError.contains("ReadAs.Columns does not yet support nested parquet fields"))

end ParquetNestedSchemaSuite
