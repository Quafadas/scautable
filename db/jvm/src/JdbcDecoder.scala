package io.github.quafadas.scautable.db

import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/** Typeclass for reading a single column from a [[java.sql.ResultSet]] by 1-based index.
  *
  * Instances are provided for all types supported by the [[Flavour]] type mapping. Custom types can be supported by providing a given instance.
  *
  * ===Nullability===
  * A column is read from the `ResultSet` exactly once, because JDBC only guarantees that each column of a row is read once, in left-to-right order. That is why nullability is a
  * second method on this typeclass rather than a wrapper around `decode`: the wrapper would have to either read the column twice, or let `decode` throw before it could ask whether
  * the value was NULL.
  *
  * [[decode]] is strict - a SQL NULL is a schema disagreement and is reported as one. [[decodeOption]] is the same read with the opposite answer for NULL, and is what the
  * `JdbcDecoder[Option[T]]` instance calls.
  */
trait JdbcDecoder[T]:
  /** Read the column, failing if it holds SQL NULL. */
  def decode(rs: ResultSet, index: Int): T

  /** Read the column, returning `None` if it holds SQL NULL.
    *
    * The default is correct for any `decode` that tolerates a NULL read without throwing, which is the case for a decoder built on a JDBC getter that returns a sentinel (`0`,
    * `false`, `null`). Override it whenever `decode` inspects `rs.wasNull()` itself, or dereferences the value it read - otherwise `Option[T]` inherits the very failure the
    * `Option` was meant to express.
    */
  def decodeOption(rs: ResultSet, index: Int): Option[T] =
    val v = decode(rs, index)
    if rs.wasNull() then None else Some(v)
    end if
  end decodeOption
end JdbcDecoder

object JdbcDecoder:

  /** Returns the column name for diagnostic messages, falling back to the 1-based index string on error. */
  private def columnLabel(rs: ResultSet, index: Int): String =
    try rs.getMetaData.getColumnLabel(index)
    catch case _: Exception => index.toString

  private def nullError(rs: ResultSet, index: Int, typeName: String): java.sql.SQLDataException =
    new java.sql.SQLDataException(
      s"Column \"${columnLabel(rs, index)}\" (index $index) is NULL but was mapped to a non-nullable $typeName. Use Option[$typeName] for nullable columns."
    )

  /** Builds a decoder that reads the column exactly once and then decides what NULL means.
    *
    * `read` is the raw JDBC getter; `convert` runs only after the NULL check, so a converter may dereference its argument freely - `BigDecimal(_)` and `Timestamp#toInstant` would
    * both NPE if they ran on the sentinel a NULL read returns.
    *
    * @param typeName
    *   How the type is spelled in the error message, so it reads as the Scala type the user actually wrote
    */
  private def strict[R, T](typeName: String)(read: (ResultSet, Int) => R)(convert: R => T): JdbcDecoder[T] =
    new JdbcDecoder[T]:
      def decode(rs: ResultSet, index: Int): T =
        val raw = read(rs, index)
        if rs.wasNull() then throw nullError(rs, index, typeName)
        end if
        convert(raw)
      end decode

      override def decodeOption(rs: ResultSet, index: Int): Option[T] =
        val raw = read(rs, index)
        if rs.wasNull() then None else Some(convert(raw))
        end if
      end decodeOption

  private def strict[T](typeName: String)(read: (ResultSet, Int) => T): JdbcDecoder[T] =
    strict[T, T](typeName)(read)(identity)

  given JdbcDecoder[Int] = strict("Int")(_.getInt(_))

  given JdbcDecoder[Long] = strict("Long")(_.getLong(_))

  given JdbcDecoder[Double] = strict("Double")(_.getDouble(_))

  given JdbcDecoder[Float] = strict("Float")(_.getFloat(_))

  given JdbcDecoder[Boolean] = strict("Boolean")(_.getBoolean(_))

  given JdbcDecoder[String] = strict("String")(_.getString(_))

  given JdbcDecoder[BigDecimal] = strict("BigDecimal")(_.getBigDecimal(_))(BigDecimal(_))

  given JdbcDecoder[Array[Byte]] = strict("Array[Byte]")(_.getBytes(_))

  given JdbcDecoder[LocalDate] = strict("LocalDate")(_.getObject(_, classOf[LocalDate]))

  given JdbcDecoder[LocalDateTime] = strict("LocalDateTime")(_.getObject(_, classOf[LocalDateTime]))

  given JdbcDecoder[Instant] = strict("Instant")(_.getTimestamp(_))(_.toInstant)

  given JdbcDecoder[UUID] = strict("UUID")(_.getObject(_, classOf[UUID]))

  /** Nullable column: returns `None` when the DB value is SQL NULL.
    *
    * Delegates to [[JdbcDecoder.decodeOption]] rather than calling `decode` and testing `rs.wasNull()` afterwards. The latter cannot work: a strict decoder throws on NULL, so the
    * test would never be reached, and every reference-typed `Option` column would fail on its first NULL row.
    */
  given [T](using inner: JdbcDecoder[T]): JdbcDecoder[Option[T]] with
    def decode(rs: ResultSet, index: Int): Option[T] = inner.decodeOption(rs, index)

    /** `Option[Option[T]]` is not a column shape, but the default would read the column a second time. */
    override def decodeOption(rs: ResultSet, index: Int): Option[Option[T]] =
      Some(inner.decodeOption(rs, index))
  end given

end JdbcDecoder
