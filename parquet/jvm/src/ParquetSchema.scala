package io.github.quafadas.scautable.parquet

import org.apache.parquet.schema.LogicalTypeAnnotation.*
import org.apache.parquet.schema.MessageType
import org.apache.parquet.schema.PrimitiveType
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName
import org.apache.parquet.schema.Type.Repetition

import scala.jdk.CollectionConverters.*

/** Raised when a parquet file's schema cannot be expressed as a flat `NamedTuple`. */
final class UnsupportedParquetSchemaException(msg: String) extends Exception(msg)

/** The Scala type a parquet column is surfaced as.
  *
  * This is the single source of truth shared by the compile-time macro (which turns it into a `TypeRepr`) and the runtime column reader (which produces values of the
  * corresponding class). Keeping both sides driven by the same enum means the inferred type and the decoded value can never drift apart.
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

/** Compile-time description of a single parquet column. */
private[scautable] final case class ParquetColumnMeta(
    name: String,
    scalaType: ParquetScalaType,
    nullable: Boolean
)

private[scautable] object ParquetSchema:

  /** Read the parquet footer schema without materialising any data. */
  def read(source: ParquetSource): MessageType =
    val reader = source.openReader()
    try reader.getFooter.getFileMetaData.getSchema
    finally reader.close()
    end try
  end read

  /** Flatten a parquet [[MessageType]] into column metadata, rejecting anything that cannot be modelled as a flat `NamedTuple`. */
  def columns(schema: MessageType): Vector[ParquetColumnMeta] =
    val fields = schema.getFields.asScala.toVector

    if fields.isEmpty then throw UnsupportedParquetSchemaException("Parquet schema has no columns.")
    end if

    fields.map { field =>
      if !field.isPrimitive then
        throw UnsupportedParquetSchemaException(
          s"Column '${field.getName}' is a nested group. scautable models a dataframe as a flat NamedTuple, so nested parquet schemas are not supported."
        )
      end if
      if field.getRepetition == Repetition.REPEATED then
        throw UnsupportedParquetSchemaException(
          s"Column '${field.getName}' is REPEATED (a list). scautable models a dataframe as a flat NamedTuple, so repeated parquet columns are not supported."
        )
      end if

      ParquetColumnMeta(
        name = field.getName,
        scalaType = scalaTypeOf(field.asPrimitiveType),
        nullable = field.getRepetition == Repetition.OPTIONAL
      )
    }
  end columns

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
