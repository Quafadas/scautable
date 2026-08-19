package io.github.quafadas.scautable.parquet

import java.util.BitSet

/** A column decoded straight from parquet's own column chunks: one plain, unboxed array plus - for `optional` columns - a single validity bitmap.
  *
  * This is the "performance use case" counterpart to `ReadAs.Columns`, which surfaces an `optional` column as `Array[Option[T]]`. That costs one `Some` and one boxed primitive per
  * row; a `ParquetVector` costs one `BitSet` for the whole column and nothing per row. [[toOptionArray]] bridges back to the row-oriented world when convenience matters more than
  * throughput.
  */
sealed trait ParquetVector[T]:
  def length: Int
  def isNull(i: Int): Boolean
  def nullCount: Int
  def toOptionArray: Array[Option[T]]
end ParquetVector

final class IntVector(val values: Array[Int], private val validity: Option[BitSet]) extends ParquetVector[Int]:
  def length: Int = values.length
  def isNull(i: Int): Boolean = validity.exists(!_.get(i))
  def nullCount: Int = validity.fold(0)(length - _.cardinality())
  def apply(i: Int): Int = values(i)
  def toOptionArray: Array[Option[Int]] = Array.tabulate(length)(i => if isNull(i) then None else Some(values(i)))
end IntVector

final class LongVector(val values: Array[Long], private val validity: Option[BitSet]) extends ParquetVector[Long]:
  def length: Int = values.length
  def isNull(i: Int): Boolean = validity.exists(!_.get(i))
  def nullCount: Int = validity.fold(0)(length - _.cardinality())
  def apply(i: Int): Long = values(i)
  def toOptionArray: Array[Option[Long]] = Array.tabulate(length)(i => if isNull(i) then None else Some(values(i)))
end LongVector

final class FloatVector(val values: Array[Float], private val validity: Option[BitSet]) extends ParquetVector[Float]:
  def length: Int = values.length
  def isNull(i: Int): Boolean = validity.exists(!_.get(i))
  def nullCount: Int = validity.fold(0)(length - _.cardinality())
  def apply(i: Int): Float = values(i)
  def toOptionArray: Array[Option[Float]] = Array.tabulate(length)(i => if isNull(i) then None else Some(values(i)))
end FloatVector

final class DoubleVector(val values: Array[Double], private val validity: Option[BitSet]) extends ParquetVector[Double]:
  def length: Int = values.length
  def isNull(i: Int): Boolean = validity.exists(!_.get(i))
  def nullCount: Int = validity.fold(0)(length - _.cardinality())
  def apply(i: Int): Double = values(i)
  def toOptionArray: Array[Option[Double]] = Array.tabulate(length)(i => if isNull(i) then None else Some(values(i)))
end DoubleVector

final class BooleanVector(val values: Array[Boolean], private val validity: Option[BitSet]) extends ParquetVector[Boolean]:
  def length: Int = values.length
  def isNull(i: Int): Boolean = validity.exists(!_.get(i))
  def nullCount: Int = validity.fold(0)(length - _.cardinality())
  def apply(i: Int): Boolean = values(i)
  def toOptionArray: Array[Option[Boolean]] = Array.tabulate(length)(i => if isNull(i) then None else Some(values(i)))
end BooleanVector

/** Reference-typed columns (`String`, `LocalDate`, ...) already have a natural "no value" - `null` - so no separate validity bitmap is needed. */
final class ObjectVector[T <: AnyRef](val values: Array[T]) extends ParquetVector[T]:
  def length: Int = values.length
  def isNull(i: Int): Boolean = values(i) == null
  def nullCount: Int = values.count(_ == null)
  def apply(i: Int): T = values(i)
  def toOptionArray: Array[Option[T]] = values.map(Option(_))
end ObjectVector
