package io.github.quafadas.scautable.parquet

import scala.NamedTuple.*

/** A lazily-evaluated `Iterator[NamedTuple[K, V]]` over a parquet file.
  *
  * The file is opened on first use, then read one row group at a time. Each row group is materialised column by column (parquet's native layout) and rows are decoded out of those
  * column arrays on demand — so only one row group is ever resident in memory.
  *
  * ===Single-use semantics===
  * Like [[io.github.quafadas.scautable.CsvIterator]] this iterator can only be traversed once; the underlying file handle is closed when the iterator is exhausted or when
  * [[close]] is called.
  *
  * @tparam K
  *   Tuple of column-name string literals, e.g. `("PassengerId", "Name")`.
  * @tparam V
  *   Tuple of Scala value types, e.g. `(Option[Long], Option[String])`.
  */
class ParquetIterator[K <: Tuple, V <: Tuple](
    val headers: Seq[String],
    private val open: () => ParquetColumnSource
)(using decoder: ParquetRowDecoder[V])
    extends Iterator[NamedTuple[K, V]]
    with AutoCloseable:

  type COLUMNS = K

  type Col[N <: Int] = Tuple.Elem[K, N]

  private var source: Option[ParquetColumnSource] = None
  private var current: Option[RowGroupColumns] = None
  private var rowIdx = 0
  private var closed = false

  private def advance(): Boolean =
    if closed then false
    else
      val src = source.getOrElse {
        val opened = open()
        source = Some(opened)
        opened
      }

      current match
        case Some(group) if rowIdx < group.rowCount => true
        case _                                      =>
          src.nextRowGroup() match
            case Some(next) =>
              current = Some(next)
              rowIdx = 0
              // A row group with no rows is legal but carries no data — keep looking.
              if next.rowCount == 0 then advance() else true
              end if
            case None =>
              close()
              false
      end match
    end if
  end advance

  override def hasNext: Boolean = advance()

  override def next(): NamedTuple[K, V] =
    if !hasNext then throw new NoSuchElementException("ParquetIterator exhausted")
    end if
    val group = current.get
    val tuple = decoder.decodeRow(group.columns, rowIdx)
    rowIdx += 1
    NamedTuple.build[K & Tuple]()(tuple)
  end next

  /** Close the underlying file handle. Safe to call more than once. */
  override def close(): Unit =
    if !closed then
      closed = true
      current = None
      source.foreach(_.close())
      source = None
    end if
  end close

end ParquetIterator
