package io.github.quafadas.scautable.db

import java.sql.ResultSet
import java.time.{Instant, LocalDate, LocalDateTime}
import java.util.UUID

/** Typeclass for reading a single column from a [[java.sql.ResultSet]] by 1-based index.
  *
  * Instances are provided for all types supported by the [[Flavour]] type mapping. Custom types can
  * be supported by providing a given instance.
  */
trait JdbcDecoder[T]:
  def decode(rs: ResultSet, index: Int): T
end JdbcDecoder

object JdbcDecoder:

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
      if v == null then "" else v
  end given

  given JdbcDecoder[BigDecimal] with
    def decode(rs: ResultSet, index: Int): BigDecimal =
      val v = rs.getBigDecimal(index)
      if v == null then BigDecimal(0) else BigDecimal(v)
  end given

  given JdbcDecoder[Array[Byte]] with
    def decode(rs: ResultSet, index: Int): Array[Byte] =
      val v = rs.getBytes(index)
      if v == null then Array.emptyByteArray else v
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
      if ts == null then Instant.EPOCH else ts.toInstant
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
  end given

end JdbcDecoder
