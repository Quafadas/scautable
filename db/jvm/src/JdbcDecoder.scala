package io.github.quafadas.scautable.db

import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/** Typeclass for reading a single column from a [[java.sql.ResultSet]] by 1-based index.
  *
  * Instances are provided for all types supported by the [[Flavour]] type mapping. Custom types can be supported by providing a given instance.
  */
trait JdbcDecoder[T]:
  def decode(rs: ResultSet, index: Int): T
end JdbcDecoder

object JdbcDecoder:

  /** Returns the column name for diagnostic messages, falling back to the 1-based index string on error. */
  private def columnLabel(rs: ResultSet, index: Int): String =
    try rs.getMetaData.getColumnLabel(index)
    catch case _: Exception => index.toString

  given JdbcDecoder[Int] with
    def decode(rs: ResultSet, index: Int): Int = rs.getInt(index)
  end given

  given JdbcDecoder[Long] with
    def decode(rs: ResultSet, index: Int): Long = rs.getLong(index)
  end given

  given JdbcDecoder[Double] with
    def decode(rs: ResultSet, index: Int): Double = rs.getDouble(index)
  end given

  given JdbcDecoder[Float] with
    def decode(rs: ResultSet, index: Int): Float = rs.getFloat(index)
  end given

  given JdbcDecoder[Boolean] with
    def decode(rs: ResultSet, index: Int): Boolean = rs.getBoolean(index)
  end given

  given JdbcDecoder[String] with
    def decode(rs: ResultSet, index: Int): String =
      val v = rs.getString(index)
      // If the DB returns NULL for a column typed as String (not Option[String]),
      // that means the schema is inconsistent. Throw rather than silently returning "".
      // For nullable columns, use Option[String] (which uses the JdbcDecoder[Option[T]] instance).
      if rs.wasNull() then
        throw new java.sql.SQLDataException(
          s"Column \"${columnLabel(rs, index)}\" (index $index) is NULL but was mapped to a non-nullable String. Use Option[String] for nullable columns."
        )
      end if
      v
    end decode
  end given

  given JdbcDecoder[BigDecimal] with
    def decode(rs: ResultSet, index: Int): BigDecimal =
      val v = rs.getBigDecimal(index)
      if rs.wasNull() then
        throw new java.sql.SQLDataException(
          s"Column \"${columnLabel(rs, index)}\" (index $index) is NULL but was mapped to a non-nullable BigDecimal. Use Option[BigDecimal] for nullable columns."
        )
      end if
      BigDecimal(v)
    end decode
  end given

  given JdbcDecoder[Array[Byte]] with
    def decode(rs: ResultSet, index: Int): Array[Byte] =
      val v = rs.getBytes(index)
      if rs.wasNull() then
        throw new java.sql.SQLDataException(
          s"Column \"${columnLabel(rs, index)}\" (index $index) is NULL but was mapped to a non-nullable Array[Byte]. Use Option[Array[Byte]] for nullable columns."
        )
      end if
      v
    end decode
  end given

  given JdbcDecoder[LocalDate] with
    def decode(rs: ResultSet, index: Int): LocalDate =
      rs.getObject(index, classOf[LocalDate])
  end given

  given JdbcDecoder[LocalDateTime] with
    def decode(rs: ResultSet, index: Int): LocalDateTime =
      rs.getObject(index, classOf[LocalDateTime])
  end given

  given JdbcDecoder[Instant] with
    def decode(rs: ResultSet, index: Int): Instant =
      val ts = rs.getTimestamp(index)
      if rs.wasNull() then
        throw new java.sql.SQLDataException(
          s"Column \"${columnLabel(rs, index)}\" (index $index) is NULL but was mapped to a non-nullable Instant. Use Option[Instant] for nullable columns."
        )
      end if
      ts.toInstant
    end decode
  end given

  given JdbcDecoder[UUID] with
    def decode(rs: ResultSet, index: Int): UUID =
      rs.getObject(index, classOf[UUID])
  end given

  /** Nullable column: returns `None` when the DB value is SQL NULL. */
  given [T](using inner: JdbcDecoder[T]): JdbcDecoder[Option[T]] with
    def decode(rs: ResultSet, index: Int): Option[T] =
      val v = inner.decode(rs, index)
      if rs.wasNull() then None else Some(v)
      end if
    end decode
  end given

end JdbcDecoder
