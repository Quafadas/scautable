package io.github.quafadas.scautable.parquet

import org.apache.parquet.column.impl.ColumnReadStoreImpl
import org.apache.parquet.hadoop.ParquetFileReader
import org.apache.parquet.io.api.Converter
import org.apache.parquet.io.api.GroupConverter
import org.apache.parquet.io.api.PrimitiveConverter
import org.apache.parquet.schema.MessageType

import scala.jdk.CollectionConverters.*

/** A single row group, materialised column by column.
  *
  * `columns(c)(r)` is the value of column `c` for row `r` within the group, or `null` where the parquet definition level says the value is absent.
  *
  * Internal API — public only because the `Parquet` macros splice references to it into user code.
  */
final case class RowGroupColumns(columns: Array[Array[Any]], rowCount: Int)

/** Reads a parquet file the way parquet is laid out on disk: one row group at a time, and within a row group one column chunk at a time.
  *
  * This keeps the sequential-scan behaviour that makes parquet fast, while still letting scautable hand back rows as `NamedTuple`s. Only one row group is resident in memory at a
  * time, so the memory cost is bounded by the row group size rather than the file size.
  *
  * Internal API — public only because the `Parquet` macros splice references to it into user code.
  */
final class ParquetColumnSource(source: ParquetSource) extends AutoCloseable:

  private val reader: ParquetFileReader = source.openReader()
  private val fileMetaData = reader.getFooter.getFileMetaData

  val schema: MessageType = fileMetaData.getSchema
  private val createdBy: String = fileMetaData.getCreatedBy

  private val descriptors = schema.getColumns.asScala.toVector
  private val scalaTypes = ParquetSchema.columns(schema).map(_.scalaType)

  /** Materialise the next row group, or `None` when the file is exhausted. */
  def nextRowGroup(): Option[RowGroupColumns] =
    val pages = reader.readNextRowGroup()
    if pages == null then None
    else
      val rowCount = pages.getRowCount.toInt
      val readStore = new ColumnReadStoreImpl(pages, ParquetColumnSource.noOpConverter, schema, createdBy)
      val columns = Array.ofDim[Array[Any]](descriptors.size)

      var c = 0
      while c < descriptors.size do
        val descriptor = descriptors(c)
        val maxDefinitionLevel = descriptor.getMaxDefinitionLevel
        val columnReader = readStore.getColumnReader(descriptor)
        val scalaType = scalaTypes(c)
        val values = new Array[Any](rowCount)

        var r = 0
        while r < rowCount do
          values(r) =
            if columnReader.getCurrentDefinitionLevel == maxDefinitionLevel then ParquetValues.boxed(columnReader, scalaType)
            else null
          columnReader.consume()
          r += 1
        end while

        columns(c) = values
        c += 1
      end while

      Some(RowGroupColumns(columns, rowCount))
    end if
  end nextRowGroup

  override def close(): Unit = reader.close()

end ParquetColumnSource

/** Internal API — public only because the `Parquet` macros splice references to it into user code. */
object ParquetColumnSource:

  /** `ColumnReadStoreImpl` insists on a converter tree, but we read values directly off the `ColumnReader` rather than pushing them into converters. A converter that never claims
    * dictionary support keeps the reader on the plain-value path.
    */
  val noOpConverter: GroupConverter = new GroupConverter:
    private val primitive = new PrimitiveConverter {}
    override def getConverter(fieldIndex: Int): Converter = primitive
    override def start(): Unit = ()
    override def end(): Unit = ()

end ParquetColumnSource
