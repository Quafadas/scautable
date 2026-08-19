package io.github.quafadas.scautable.parquet

import org.apache.parquet.column.impl.ColumnReadStoreImpl
import org.apache.parquet.hadoop.ParquetFileReader

import scala.NamedTuple.*
import scala.jdk.CollectionConverters.*

/** A lazily-evaluated `Iterator[NamedTuple[K, VE]]` over a parquet file's row groups, where each element is one row group decoded straight into a tuple of [[ParquetVector]]s.
  *
  * This is the vectorized, boxing-free counterpart to [[ParquetIterator]]: where that decodes one row at a time out of a row group, this decodes a whole row group at once into
  * plain arrays (plus one validity bitmap per `optional` column) - vectorized like the file itself, and bounded by row-group size rather than file size.
  *
  * ===Single-use semantics===
  * Like [[ParquetIterator]] this iterator can only be traversed once; the underlying file handle is closed when the iterator is exhausted or when [[close]] is called.
  *
  * @tparam K
  *   Tuple of column-name string literals.
  * @tparam V
  *   Tuple of *row* Scala value types the macro inferred, e.g. `(Option[Long], String)` - used only to summon the right [[ParquetVectorBuilder]] per column.
  * @tparam VE
  *   Tuple of `ParquetVector`s the caller sees, e.g. `(ParquetVector[Long], ParquetVector[String])`.
  */
class ParquetVectorIterator[K <: Tuple, V <: Tuple, VE <: Tuple](
    val headers: Seq[String],
    private val open: () => ParquetSource
)(using readers: ParquetVectorReaders[V])
    extends Iterator[NamedTuple[K, VE]]
    with AutoCloseable:

  private var reader: Option[ParquetFileReader] = None
  private var pending: Option[NamedTuple[K, VE]] = None
  private var closed = false

  private def openedReader(): ParquetFileReader =
    reader.getOrElse {
      val opened = open().openReader()
      reader = Some(opened)
      opened
    }

  private def advance(): Boolean =
    if closed then false
    else if pending.isDefined then true
    else
      val r = openedReader()
      val pages = r.readNextRowGroup()
      if pages == null then
        close()
        false
      else
        val rowCount = pages.getRowCount.toInt
        val fileMetaData = r.getFooter.getFileMetaData
        val schema = fileMetaData.getSchema
        val descriptors = schema.getColumns.asScala.toVector
        val readStore = new ColumnReadStoreImpl(pages, ParquetColumnSource.noOpConverter, schema, fileMetaData.getCreatedBy)

        val buffers = readers.allocateAll(rowCount)
        readers.fillAll(buffers, 0, rowCount, readStore, descriptors)
        val tuple = readers.buildTuple(buffers).asInstanceOf[VE]

        pending = Some(NamedTuple.build[K & Tuple]()(tuple))
        // A row group with no rows is legal but carries no data - keep looking.
        if rowCount == 0 then advance() else true
      end if
    end if
  end advance

  override def hasNext: Boolean = advance()

  override def next(): NamedTuple[K, VE] =
    if !hasNext then throw new NoSuchElementException("ParquetVectorIterator exhausted")
    end if
    val result = pending.get
    pending = None
    result
  end next

  /** Close the underlying file handle. Safe to call more than once. */
  override def close(): Unit =
    if !closed then
      closed = true
      pending = None
      reader.foreach(_.close())
      reader = None
    end if
  end close

end ParquetVectorIterator

