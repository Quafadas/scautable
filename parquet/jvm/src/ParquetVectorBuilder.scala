package io.github.quafadas.scautable.parquet

import org.apache.parquet.column.ColumnDescriptor
import org.apache.parquet.column.ColumnReadStore
import org.apache.parquet.column.ColumnReader

import java.time.Instant
import java.time.LocalDate
import java.util.BitSet
import java.util.UUID

import scala.compiletime.erasedValue
import scala.compiletime.summonInline
import scala.reflect.ClassTag

/** Maps a row's element type (`T`, or `Option[T]` for an `optional` column) onto the `T` a [[ParquetVector]] surfaces - nullability moves from the type into the vector's validity
  * bitmap.
  */
type ParquetVectorElem[CT] = CT match
  case Option[t] => t
  case _         => CT

/** The [[ParquetVector]] a row's column type is read into. */
type ParquetVectorOf[CT] = ParquetVector[ParquetVectorElem[CT]]

/** Fills a [[ParquetVector]] straight from parquet's own column chunks - the vector counterpart of [[ParquetColumnBuilder]].
  *
  * Where `ParquetColumnBuilder[Option[T]]` tracks nullability with one `Some`/`None` per row, instances here track it with a single [[java.util.BitSet]] for the whole column, so
  * an `optional` column costs one allocation instead of `n`.
  */
trait ParquetVectorBuilder[CT]:
  // Set to `ParquetVectorElem[CT]` by every instance below - not aliased here, since that match type can't reduce for an abstract `CT`.
  type Elem
  def allocate(n: Int): ParquetVectorBuilder.Buffer[Elem]
  def fill(buffer: ParquetVectorBuilder.Buffer[Elem], offset: Int, count: Int, reader: ColumnReader, maxDefinitionLevel: Int): Unit
  def build(buffer: ParquetVectorBuilder.Buffer[Elem]): ParquetVector[Elem]

  // Type-erased bridge for `ParquetVectorReaders`, which holds a runtime `List[ParquetVectorBuilder[?]]` and can no longer name `Elem`.
  private[parquet] def allocateErased(n: Int): Any = allocate(n)
  private[parquet] def fillErased(buffer: Any, offset: Int, count: Int, reader: ColumnReader, maxDefinitionLevel: Int): Unit =
    fill(buffer.asInstanceOf[ParquetVectorBuilder.Buffer[Elem]], offset, count, reader, maxDefinitionLevel)
  private[parquet] def buildErased(buffer: Any): ParquetVector[?] = build(buffer.asInstanceOf[ParquetVectorBuilder.Buffer[Elem]])
end ParquetVectorBuilder

object ParquetVectorBuilder:

  /** Raw storage midway through being filled: a plain array plus, for `optional` columns, a validity bitmap. */
  final case class Buffer[T](values: Array[T], validity: Option[BitSet])

  private def required[T](alloc: Int => Array[T], readOne: ColumnReader => T, wrap: (Array[T], Option[BitSet]) => ParquetVector[T]): ParquetVectorBuilder[T] =
    new ParquetVectorBuilder[T]:
      type Elem = T
      def allocate(n: Int): Buffer[T] = Buffer(alloc(n), None)
      def fill(buffer: Buffer[T], offset: Int, count: Int, reader: ColumnReader, maxDefinitionLevel: Int): Unit =
        var i = 0
        while i < count do
          if reader.getCurrentDefinitionLevel != maxDefinitionLevel then throw ParquetColumnBuilder.nullInRequiredColumn(reader)
          end if
          buffer.values(offset + i) = readOne(reader)
          reader.consume()
          i += 1
        end while
      end fill
      def build(buffer: Buffer[T]): ParquetVector[T] = wrap(buffer.values, None)

  private def optional[T](alloc: Int => Array[T], readOne: ColumnReader => T, wrap: (Array[T], Option[BitSet]) => ParquetVector[T]): ParquetVectorBuilder[Option[T]] =
    new ParquetVectorBuilder[Option[T]]:
      type Elem = T
      def allocate(n: Int): Buffer[T] = Buffer(alloc(n), Some(new BitSet(n)))
      def fill(buffer: Buffer[T], offset: Int, count: Int, reader: ColumnReader, maxDefinitionLevel: Int): Unit =
        val validity = buffer.validity.get
        var i = 0
        while i < count do
          if reader.getCurrentDefinitionLevel == maxDefinitionLevel then
            buffer.values(offset + i) = readOne(reader)
            validity.set(offset + i)
          end if
          reader.consume()
          i += 1
        end while
      end fill
      def build(buffer: Buffer[T]): ParquetVector[T] = wrap(buffer.values, buffer.validity)

  given ParquetVectorBuilder[Int] = required(new Array[Int](_), _.getInteger, IntVector(_, _))
  given optionIntVectorBuilder: ParquetVectorBuilder[Option[Int]] = optional(new Array[Int](_), _.getInteger, IntVector(_, _))

  given ParquetVectorBuilder[Long] = required(new Array[Long](_), _.getLong, LongVector(_, _))
  given optionLongVectorBuilder: ParquetVectorBuilder[Option[Long]] = optional(new Array[Long](_), _.getLong, LongVector(_, _))

  given ParquetVectorBuilder[Float] = required(new Array[Float](_), _.getFloat, FloatVector(_, _))
  given optionFloatVectorBuilder: ParquetVectorBuilder[Option[Float]] = optional(new Array[Float](_), _.getFloat, FloatVector(_, _))

  given ParquetVectorBuilder[Double] = required(new Array[Double](_), _.getDouble, DoubleVector(_, _))
  given optionDoubleVectorBuilder: ParquetVectorBuilder[Option[Double]] = optional(new Array[Double](_), _.getDouble, DoubleVector(_, _))

  given ParquetVectorBuilder[Boolean] = required(new Array[Boolean](_), _.getBoolean, BooleanVector(_, _))
  given optionBooleanVectorBuilder: ParquetVectorBuilder[Option[Boolean]] = optional(new Array[Boolean](_), _.getBoolean, BooleanVector(_, _))

  /** Reference types already have `null` to mean "absent", so the only difference between the required and optional builder is whether a missing value is an error. */
  private def requiredObject[T <: AnyRef](readOne: ColumnReader => T)(using ct: ClassTag[T]): ParquetVectorBuilder[T] =
    new ParquetVectorBuilder[T]:
      type Elem = T
      def allocate(n: Int): Buffer[T] = Buffer(ct.newArray(n), None)
      def fill(buffer: Buffer[T], offset: Int, count: Int, reader: ColumnReader, maxDefinitionLevel: Int): Unit =
        var i = 0
        while i < count do
          if reader.getCurrentDefinitionLevel != maxDefinitionLevel then throw ParquetColumnBuilder.nullInRequiredColumn(reader)
          end if
          buffer.values(offset + i) = readOne(reader)
          reader.consume()
          i += 1
        end while
      end fill
      def build(buffer: Buffer[T]): ParquetVector[T] = new ObjectVector(buffer.values)

  private def optionalObject[T <: AnyRef](readOne: ColumnReader => T)(using ct: ClassTag[T]): ParquetVectorBuilder[Option[T]] =
    new ParquetVectorBuilder[Option[T]]:
      type Elem = T
      def allocate(n: Int): Buffer[T] = Buffer(ct.newArray(n), None)
      def fill(buffer: Buffer[T], offset: Int, count: Int, reader: ColumnReader, maxDefinitionLevel: Int): Unit =
        var i = 0
        while i < count do
          if reader.getCurrentDefinitionLevel == maxDefinitionLevel then buffer.values(offset + i) = readOne(reader)
          end if
          reader.consume()
          i += 1
        end while
      end fill
      def build(buffer: Buffer[T]): ParquetVector[T] = new ObjectVector(buffer.values)

  given ParquetVectorBuilder[String] = requiredObject(ParquetValues.string)
  given optionStringVectorBuilder: ParquetVectorBuilder[Option[String]] = optionalObject(ParquetValues.string)

  given ParquetVectorBuilder[Array[Byte]] = requiredObject(ParquetValues.binary)
  given optionBinaryVectorBuilder: ParquetVectorBuilder[Option[Array[Byte]]] = optionalObject(ParquetValues.binary)

  given ParquetVectorBuilder[LocalDate] = requiredObject(ParquetValues.date)
  given optionDateVectorBuilder: ParquetVectorBuilder[Option[LocalDate]] = optionalObject(ParquetValues.date)

  given ParquetVectorBuilder[Instant] = requiredObject(ParquetValues.instant)
  given optionInstantVectorBuilder: ParquetVectorBuilder[Option[Instant]] = optionalObject(ParquetValues.instant)

  given ParquetVectorBuilder[BigDecimal] = requiredObject(ParquetValues.decimal)
  given optionDecimalVectorBuilder: ParquetVectorBuilder[Option[BigDecimal]] = optionalObject(ParquetValues.decimal)

  given ParquetVectorBuilder[UUID] = requiredObject(ParquetValues.uuid)
  given optionUuidVectorBuilder: ParquetVectorBuilder[Option[UUID]] = optionalObject(ParquetValues.uuid)

end ParquetVectorBuilder

/** Precomputes, once per concrete row-tuple `V`, the list of [[ParquetVectorBuilder]]s needed to read one row group into vectors.
  *
  * [[ParquetVectorIterator]] pulls a fresh row group on every `next()`, so the per-column builder lookup can't be redone with `inline`/`erasedValue` on every batch - that only
  * reduces where `V` is statically concrete, which an overridden `Iterator` method never is. Deriving this once, at the same call site that constructs the iterator, turns the
  * per-batch work back into plain (non-inline) code over a precomputed `Array`.
  */
trait ParquetVectorReaders[V <: Tuple]:
  def allocateAll(n: Int): Array[Any]
  def fillAll(buffers: Array[Any], offset: Int, count: Int, readStore: ColumnReadStore, descriptors: Vector[ColumnDescriptor]): Unit
  def buildTuple(buffers: Array[Any]): Tuple
end ParquetVectorReaders

object ParquetVectorReaders:

  private inline def summonAll[T <: Tuple]: List[ParquetVectorBuilder[?]] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (h *: t)   => summonInline[ParquetVectorBuilder[h]] :: summonAll[t]

  inline given derived[V <: Tuple]: ParquetVectorReaders[V] = new ParquetVectorReaders[V]:
    private val builders: Array[ParquetVectorBuilder[?]] = summonAll[V].toArray

    def allocateAll(n: Int): Array[Any] =
      Array.tabulate(builders.length)(i => builders(i).allocateErased(n))

    def fillAll(buffers: Array[Any], offset: Int, count: Int, readStore: ColumnReadStore, descriptors: Vector[ColumnDescriptor]): Unit =
      var i = 0
      while i < builders.length do
        val descriptor = descriptors(i)
        builders(i).fillErased(buffers(i), offset, count, readStore.getColumnReader(descriptor), descriptor.getMaxDefinitionLevel)
        i += 1
      end while
    end fillAll

    def buildTuple(buffers: Array[Any]): Tuple =
      var result: Tuple = EmptyTuple
      var i = builders.length - 1
      while i >= 0 do
        result = builders(i).buildErased(buffers(i)) *: result
        i -= 1
      end while
      result
    end buildTuple

end ParquetVectorReaders
