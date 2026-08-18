package io.github.quafadas.scautable.parquet

import org.apache.parquet.column.ColumnDescriptor
import org.apache.parquet.column.ColumnReadStore
import org.apache.parquet.column.ColumnReader
import org.apache.parquet.column.impl.ColumnReadStoreImpl

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

import scala.compiletime.erasedValue
import scala.compiletime.summonInline
import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag

/** Fills a typed column array straight from parquet's own column chunks.
  *
  * This is the column-oriented counterpart to [[ParquetDecoder]]. Because the target array type is known statically, the primitive instances write into `Array[Long]`,
  * `Array[Double]` and friends with no boxing at all — the value goes from the parquet page to its final slot in one step.
  */
private[scautable] trait ParquetColumnBuilder[T]:

  /** Allocate the backing array for a whole column. The parquet footer tells us the exact row count up front, so this is allocated once and never resized. */
  def allocate(n: Int): Array[T]

  /** Read a single value from a positioned reader. */
  def read(reader: ColumnReader): T

  /** Fill `target(offset until offset + count)` from `reader`, consuming exactly `count` values. */
  def fill(target: Array[T], offset: Int, count: Int, reader: ColumnReader, maxDefinitionLevel: Int): Unit =
    var i = 0
    while i < count do
      if reader.getCurrentDefinitionLevel == maxDefinitionLevel then target(offset + i) = read(reader)
      else throw ParquetColumnBuilder.nullInRequiredColumn(reader)
      end if
      reader.consume()
      i += 1
    end while
  end fill

end ParquetColumnBuilder

private[scautable] object ParquetColumnBuilder:

  def nullInRequiredColumn(reader: ColumnReader): ParquetDecodeException =
    ParquetDecodeException(
      s"Column '${reader.getDescriptor.getPath.mkString(".")}' contains a null, but was inferred as a non-optional type. This means the parquet footer and its data disagree."
    )

  /** Builder for a reference type — values are read one at a time, which costs nothing extra since the array holds references anyway. */
  private def objectBuilder[T](readOne: ColumnReader => T)(using ct: ClassTag[T]): ParquetColumnBuilder[T] =
    new ParquetColumnBuilder[T]:
      def allocate(n: Int): Array[T] = ct.newArray(n)
      def read(reader: ColumnReader): T = readOne(reader)

  given ParquetColumnBuilder[Int] with
    def allocate(n: Int): Array[Int] = new Array[Int](n)
    def read(reader: ColumnReader): Int = reader.getInteger
    override def fill(target: Array[Int], offset: Int, count: Int, reader: ColumnReader, maxDefinitionLevel: Int): Unit =
      var i = 0
      while i < count do
        if reader.getCurrentDefinitionLevel == maxDefinitionLevel then target(offset + i) = reader.getInteger
        else throw nullInRequiredColumn(reader)
        end if
        reader.consume()
        i += 1
      end while
  end given

  given ParquetColumnBuilder[Long] with
    def allocate(n: Int): Array[Long] = new Array[Long](n)
    def read(reader: ColumnReader): Long = reader.getLong
    override def fill(target: Array[Long], offset: Int, count: Int, reader: ColumnReader, maxDefinitionLevel: Int): Unit =
      var i = 0
      while i < count do
        if reader.getCurrentDefinitionLevel == maxDefinitionLevel then target(offset + i) = reader.getLong
        else throw nullInRequiredColumn(reader)
        end if
        reader.consume()
        i += 1
      end while
  end given

  given ParquetColumnBuilder[Float] with
    def allocate(n: Int): Array[Float] = new Array[Float](n)
    def read(reader: ColumnReader): Float = reader.getFloat
    override def fill(target: Array[Float], offset: Int, count: Int, reader: ColumnReader, maxDefinitionLevel: Int): Unit =
      var i = 0
      while i < count do
        if reader.getCurrentDefinitionLevel == maxDefinitionLevel then target(offset + i) = reader.getFloat
        else throw nullInRequiredColumn(reader)
        end if
        reader.consume()
        i += 1
      end while
  end given

  given ParquetColumnBuilder[Double] with
    def allocate(n: Int): Array[Double] = new Array[Double](n)
    def read(reader: ColumnReader): Double = reader.getDouble
    override def fill(target: Array[Double], offset: Int, count: Int, reader: ColumnReader, maxDefinitionLevel: Int): Unit =
      var i = 0
      while i < count do
        if reader.getCurrentDefinitionLevel == maxDefinitionLevel then target(offset + i) = reader.getDouble
        else throw nullInRequiredColumn(reader)
        end if
        reader.consume()
        i += 1
      end while
  end given

  given ParquetColumnBuilder[Boolean] with
    def allocate(n: Int): Array[Boolean] = new Array[Boolean](n)
    def read(reader: ColumnReader): Boolean = reader.getBoolean
    override def fill(target: Array[Boolean], offset: Int, count: Int, reader: ColumnReader, maxDefinitionLevel: Int): Unit =
      var i = 0
      while i < count do
        if reader.getCurrentDefinitionLevel == maxDefinitionLevel then target(offset + i) = reader.getBoolean
        else throw nullInRequiredColumn(reader)
        end if
        reader.consume()
        i += 1
      end while
  end given

  given ParquetColumnBuilder[String] = objectBuilder(ParquetValues.string)
  given ParquetColumnBuilder[Array[Byte]] = objectBuilder(ParquetValues.binary)
  given ParquetColumnBuilder[LocalDate] = objectBuilder(ParquetValues.date)
  given ParquetColumnBuilder[Instant] = objectBuilder(ParquetValues.instant)
  given ParquetColumnBuilder[BigDecimal] = objectBuilder(ParquetValues.decimal)
  given ParquetColumnBuilder[UUID] = objectBuilder(ParquetValues.uuid)

  /** An `optional` parquet column. Absent values become `None` rather than throwing. */
  given [T](using inner: ParquetColumnBuilder[T], ct: ClassTag[Option[T]]): ParquetColumnBuilder[Option[T]] with
    def allocate(n: Int): Array[Option[T]] = ct.newArray(n)
    def read(reader: ColumnReader): Option[T] = Some(inner.read(reader))
    override def fill(target: Array[Option[T]], offset: Int, count: Int, reader: ColumnReader, maxDefinitionLevel: Int): Unit =
      var i = 0
      while i < count do
        target(offset + i) =
          if reader.getCurrentDefinitionLevel == maxDefinitionLevel then Some(inner.read(reader))
          else None
        reader.consume()
        i += 1
      end while
  end given

end ParquetColumnBuilder

/** Reads a whole parquet file into one typed array per column.
  *
  * This is the shape parquet is stored in, so it is also the cheapest thing to ask for: each column chunk is decoded once, straight into its final array, with no per-row
  * `NamedTuple` allocation and no row-major transpose in between.
  *
  * Unlike CSV — where the row count is unknown until the file has been read — the parquet footer gives us the exact record count, so each column is allocated once at its final
  * size.
  */
private[scautable] object ParquetColumns:

  /** Read every column of `source` into arrays. `V` is the tuple of array types, e.g. `(Array[Long], Array[Option[String]])`. */
  inline def readAll[V <: Tuple](source: ParquetSource): V =
    val reader = source.openReader()
    try
      val fileMetaData = reader.getFooter.getFileMetaData
      val schema = fileMetaData.getSchema
      val descriptors = schema.getColumns.asScala.toVector
      val totalRows = reader.getRecordCount

      if totalRows > Int.MaxValue then
        throw ParquetDecodeException(
          s"This parquet file has $totalRows rows, which will not fit in a single JVM array. Read it as rows instead - `ReadAs.Rows` streams a row group at a time."
        )
      end if

      val arrays = new Array[Any](descriptors.size)
      allocateAll[V](totalRows.toInt, arrays, 0)

      var offset = 0
      var pages = reader.readNextRowGroup()
      while pages != null do
        val rowCount = pages.getRowCount.toInt
        val readStore = new ColumnReadStoreImpl(pages, ParquetColumnSource.noOpConverter, schema, fileMetaData.getCreatedBy)
        fillAll[V](arrays, offset, rowCount, readStore, descriptors, 0)
        offset += rowCount
        pages = reader.readNextRowGroup()
      end while

      toTuple[V](arrays, 0)
    finally reader.close()
    end try
  end readAll

  private inline def allocateAll[V <: Tuple](n: Int, into: Array[Any], idx: Int): Unit =
    inline erasedValue[V] match
      case _: EmptyTuple      => ()
      case _: (Array[h] *: t) =>
        into(idx) = summonInline[ParquetColumnBuilder[h]].allocate(n)
        allocateAll[t](n, into, idx + 1)

  private inline def fillAll[V <: Tuple](
      arrays: Array[Any],
      offset: Int,
      count: Int,
      readStore: ColumnReadStore,
      descriptors: Vector[ColumnDescriptor],
      idx: Int
  ): Unit =
    inline erasedValue[V] match
      case _: EmptyTuple      => ()
      case _: (Array[h] *: t) =>
        val descriptor = descriptors(idx)
        summonInline[ParquetColumnBuilder[h]].fill(
          arrays(idx).asInstanceOf[Array[h]],
          offset,
          count,
          readStore.getColumnReader(descriptor),
          descriptor.getMaxDefinitionLevel
        )
        fillAll[t](arrays, offset, count, readStore, descriptors, idx + 1)

  private inline def toTuple[V <: Tuple](arrays: Array[Any], idx: Int): V =
    inline erasedValue[V] match
      case _: EmptyTuple      => EmptyTuple.asInstanceOf[V]
      case _: (Array[h] *: t) =>
        (arrays(idx).asInstanceOf[Array[h]] *: toTuple[t](arrays, idx + 1)).asInstanceOf[V]

end ParquetColumns
