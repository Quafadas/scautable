package io.github.quafadas.scautable.parquet

import org.apache.parquet.schema.LogicalTypeAnnotation.*
import org.apache.parquet.schema.MessageType
import org.apache.parquet.schema.PrimitiveType
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName
import org.apache.parquet.schema.Type.Repetition

import scala.jdk.CollectionConverters.*

/** Raised when a parquet schema contains a shape that scautable cannot represent. */
final class UnsupportedParquetSchemaException(msg: String) extends Exception(msg)

/** The Scala type a parquet column is surfaced as.
  *
  * This is the single source of truth shared by the compile-time macro (which turns it into a `TypeRepr`) and the runtime column reader (which produces values of the corresponding
  * class). Keeping both sides driven by the same enum means the inferred type and the decoded value can never drift apart.
  */
private[scautable] enum ParquetScalaType:
  case IntT
  case LongT
  case FloatT
  case DoubleT
  case BooleanT
  case StringT
  case BinaryT
  case DateT
  case InstantT
  case DecimalT
  case UuidT
end ParquetScalaType

/** Recursive compile-time description of a parquet field. */
private[scautable] enum ParquetField:
  case Primitive(
      name: String,
      scalaType: ParquetScalaType,
      nullable: Boolean,
      leafIndex: Int,
      presentDefinitionLevel: Int
  )
  case Struct(
      name: String,
      fields: Vector[ParquetField],
      nullable: Boolean,
      presentDefinitionLevel: Int
  )
end ParquetField

/** Physical parquet leaf metadata used to connect a logical field tree to column readers. */
private[scautable] final case class ParquetLeaf(
    path: Vector[String],
    scalaType: ParquetScalaType,
    columnIndex: Int,
    maxDefinitionLevel: Int
)

/** Flat column metadata retained for the current primitive-only readers. */
private[scautable] final case class ParquetColumnMeta(
    name: String,
    scalaType: ParquetScalaType,
    nullable: Boolean
)

object ParquetSchema:

  /** Read the parquet footer schema without materialising any data. */
  def read(source: ParquetSource): MessageType =
    val reader = source.openReader()
    try reader.getFooter.getFileMetaData.getSchema
    finally reader.close()
    end try
  end read

  /** Parse a parquet [[MessageType]] into a recursive logical field tree. */
  def fields(schema: MessageType): Vector[ParquetField] =
    val rootFields = schema.getFields.asScala.toVector
    if rootFields.isEmpty then throw UnsupportedParquetSchemaException("Parquet schema has no fields.")
    end if

    parseFields(rootFields, Vector.empty, 0, 0)._1
  end fields

  /** Return the physical primitive leaves in parquet column order. */
  def leaves(fields: Vector[ParquetField]): Vector[ParquetLeaf] =
    def loop(nodes: Vector[ParquetField], parentPath: Vector[String]): Vector[ParquetLeaf] =
      nodes.flatMap {
        case ParquetField.Primitive(name, scalaType, _, leafIndex, definitionLevel) =>
          Vector(ParquetLeaf(parentPath :+ name, scalaType, leafIndex, definitionLevel))
        case ParquetField.Struct(name, children, _, _) =>
          loop(children, parentPath :+ name)
      }
    end loop

    loop(fields, Vector.empty).sortBy(_.columnIndex)
  end leaves

  /** Whether a logical schema contains at least one struct field. */
  def isNested(fields: Vector[ParquetField]): Boolean =
    fields.exists {
      case _: ParquetField.Primitive => false
      case _: ParquetField.Struct    => true
    }
  end isNested

  /** Project a flat schema into the metadata consumed by the current readers. */
  def columns(schema: MessageType): Vector[ParquetColumnMeta] =
    fields(schema).map {
      case ParquetField.Primitive(name, scalaType, nullable, _, _) =>
        ParquetColumnMeta(name, scalaType, nullable)
      case ParquetField.Struct(name, _, _, _) =>
        throw UnsupportedParquetSchemaException(
          s"Field '$name' is a nested struct. Nested schema inference is available, but nested row decoding is not yet supported."
        )
    }
  end columns

  private def parseFields(
      parquetFields: Vector[org.apache.parquet.schema.Type],
      parentPath: Vector[String],
      parentDefinitionLevel: Int,
      firstLeafIndex: Int
  ): (Vector[ParquetField], Int) =
    parquetFields.foldLeft((Vector.empty[ParquetField], firstLeafIndex)) { case ((parsed, nextLeafIndex), field) =>
      val (parsedField, followingLeafIndex) = parseField(field, parentPath, parentDefinitionLevel, nextLeafIndex)
      (parsed :+ parsedField, followingLeafIndex)
    }
  end parseFields

  private def parseField(
      field: org.apache.parquet.schema.Type,
      parentPath: Vector[String],
      parentDefinitionLevel: Int,
      leafIndex: Int
  ): (ParquetField, Int) =
    val path = parentPath :+ field.getName
    val displayPath = path.mkString(".")

    if field.getRepetition == Repetition.REPEATED then
      throw UnsupportedParquetSchemaException(
        s"Field '$displayPath' is REPEATED. Nested structs are supported by schema inference, but repeated fields are not yet supported."
      )
    end if

    field.getLogicalTypeAnnotation match
      case _: ListLogicalTypeAnnotation => unsupportedCollection(displayPath, "LIST")
      case _: MapLogicalTypeAnnotation  => unsupportedCollection(displayPath, "MAP")
      case _                            => ()
    end match

    val nullable = field.getRepetition == Repetition.OPTIONAL
    val definitionLevel = parentDefinitionLevel + (if nullable then 1 else 0)

    if field.isPrimitive then (ParquetField.Primitive(field.getName, scalaTypeOf(field.asPrimitiveType), nullable, leafIndex, definitionLevel), leafIndex + 1)
    else
      val (children, nextLeafIndex) =
        parseFields(field.asGroupType.getFields.asScala.toVector, path, definitionLevel, leafIndex)
      (ParquetField.Struct(field.getName, children, nullable, definitionLevel), nextLeafIndex)
    end if
  end parseField

  private def unsupportedCollection(path: String, kind: String): Nothing =
    throw UnsupportedParquetSchemaException(
      s"Field '$path' is a parquet $kind. Nested structs are supported by schema inference, but $kind fields are not yet supported."
    )
  end unsupportedCollection

  /** Map a parquet primitive (plus its logical annotation) onto the Scala type we surface. */
  def scalaTypeOf(primitive: PrimitiveType): ParquetScalaType =
    import ParquetScalaType.*

    val annotation = primitive.getLogicalTypeAnnotation

    def isDecimal: Boolean = annotation.isInstanceOf[DecimalLogicalTypeAnnotation]

    primitive.getPrimitiveTypeName match
      case PrimitiveTypeName.BOOLEAN => BooleanT
      case PrimitiveTypeName.FLOAT   => FloatT
      case PrimitiveTypeName.DOUBLE  => DoubleT
      case PrimitiveTypeName.INT96   => InstantT

      case PrimitiveTypeName.INT32 =>
        if isDecimal then DecimalT
        else
          annotation match
            case _: DateLogicalTypeAnnotation => DateT
            case _                            => IntT

      case PrimitiveTypeName.INT64 =>
        if isDecimal then DecimalT
        else
          annotation match
            case _: TimestampLogicalTypeAnnotation => InstantT
            case _                                 => LongT

      case PrimitiveTypeName.BINARY | PrimitiveTypeName.FIXED_LEN_BYTE_ARRAY =>
        if isDecimal then DecimalT
        else
          annotation match
            case _: StringLogicalTypeAnnotation => StringT
            case _: EnumLogicalTypeAnnotation   => StringT
            case _: JsonLogicalTypeAnnotation   => StringT
            case _: UUIDLogicalTypeAnnotation   => UuidT
            case _                              => BinaryT
    end match
  end scalaTypeOf

end ParquetSchema
