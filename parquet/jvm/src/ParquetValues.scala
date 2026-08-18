package io.github.quafadas.scautable.parquet

import org.apache.parquet.column.ColumnReader
import org.apache.parquet.schema.LogicalTypeAnnotation.DecimalLogicalTypeAnnotation
import org.apache.parquet.schema.LogicalTypeAnnotation.TimeUnit
import org.apache.parquet.schema.LogicalTypeAnnotation.TimestampLogicalTypeAnnotation
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.*

import java.nio.ByteOrder
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Decodes a single value from a positioned [[ColumnReader]].
  *
  * Everything here is derived from the reader's own descriptor, so the row-oriented and column-oriented readers share one implementation of the fiddly cases (INT96 timestamps,
  * decimal scale, and so on) and cannot drift apart.
  */
private[scautable] object ParquetValues:

  /** Days between the Julian epoch and the Unix epoch, used to decode legacy INT96 timestamps. */
  private val JulianDayOfEpoch = 2440588

  def string(reader: ColumnReader): String = reader.getBinary.toStringUsingUTF8

  def binary(reader: ColumnReader): Array[Byte] = reader.getBinary.getBytes

  def date(reader: ColumnReader): LocalDate = LocalDate.ofEpochDay(reader.getInteger.toLong)

  def uuid(reader: ColumnReader): UUID =
    val buffer = reader.getBinary.toByteBuffer
    new UUID(buffer.getLong, buffer.getLong)
  end uuid

  def decimal(reader: ColumnReader): BigDecimal =
    val primitiveType = reader.getDescriptor.getPrimitiveType
    val scale = primitiveType.getLogicalTypeAnnotation match
      case d: DecimalLogicalTypeAnnotation => d.getScale
      case _                               => 0

    primitiveType.getPrimitiveTypeName match
      case INT32                         => BigDecimal(BigInt(reader.getInteger), scale)
      case INT64                         => BigDecimal(BigInt(reader.getLong), scale)
      case BINARY | FIXED_LEN_BYTE_ARRAY => BigDecimal(BigInt(reader.getBinary.getBytes), scale)
      case other                         =>
        throw UnsupportedParquetSchemaException(s"DECIMAL is not supported over parquet physical type $other.")
    end match
  end decimal

  def instant(reader: ColumnReader): Instant =
    val primitiveType = reader.getDescriptor.getPrimitiveType
    primitiveType.getPrimitiveTypeName match
      case INT96 =>
        // Legacy Impala/Hive encoding: 8 bytes of nanos-within-day followed by 4 bytes of Julian day, little endian.
        val buffer = reader.getBinary.toByteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val nanosOfDay = buffer.getLong
        val julianDay = buffer.getInt
        Instant.ofEpochSecond((julianDay - JulianDayOfEpoch).toLong * 86400L).plusNanos(nanosOfDay)

      case INT64 =>
        val raw = reader.getLong
        primitiveType.getLogicalTypeAnnotation match
          case ts: TimestampLogicalTypeAnnotation =>
            ts.getUnit match
              case TimeUnit.MILLIS => Instant.ofEpochMilli(raw)
              case TimeUnit.MICROS => Instant.EPOCH.plusNanos(raw * 1000L)
              case TimeUnit.NANOS  => Instant.EPOCH.plusNanos(raw)
          case _ => Instant.ofEpochMilli(raw)
        end match

      case other =>
        throw UnsupportedParquetSchemaException(s"TIMESTAMP is not supported over parquet physical type $other.")
    end match
  end instant

  /** Read a value as the Scala type the macro inferred for this column, boxed as `Any`. Used by the row-oriented reader. */
  def boxed(reader: ColumnReader, scalaType: ParquetScalaType): Any =
    import ParquetScalaType.*
    scalaType match
      case IntT     => reader.getInteger
      case LongT    => reader.getLong
      case FloatT   => reader.getFloat
      case DoubleT  => reader.getDouble
      case BooleanT => reader.getBoolean
      case StringT  => string(reader)
      case BinaryT  => binary(reader)
      case UuidT    => uuid(reader)
      case DateT    => date(reader)
      case DecimalT => decimal(reader)
      case InstantT => instant(reader)
    end match
  end boxed

end ParquetValues
