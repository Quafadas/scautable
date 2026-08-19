package io.github.quafadas.scautable.parquet

import scala.NamedTuple.*

/** A lazily-evaluated `Iterator[NamedTuple[K, V]]` over every `.parquet` file directly inside one directory, assumed - and, at compile time, verified - to share one schema.
  *
  * Files are visited in file-name order, one at a time, by delegating to a fresh [[ParquetIterator]] per file: only one file's current row group is ever resident in memory, the
  * same bound a single-file read gets.
  *
  * ===Single-use semantics===
  * Like [[ParquetIterator]] this iterator can only be traversed once; the currently-open file's handle is closed when a file is exhausted, when the whole iterator is exhausted, or
  * when [[close]] is called.
  *
  * @tparam K
  *   Tuple of column-name string literals, shared by every file in the directory.
  * @tparam V
  *   Tuple of Scala value types, shared by every file in the directory.
  */
class ParquetDirIterator[K <: Tuple, V <: Tuple](
    val headers: Seq[String],
    private val openDir: () => ParquetSource
)(using decoder: ParquetRowDecoder[V])
    extends Iterator[NamedTuple[K, V]]
    with AutoCloseable:

  private lazy val files: Iterator[ParquetSource] = openDir().listParquetFiles.iterator
  private var current: Option[ParquetIterator[K, V]] = None
  private var closed = false

  private def advance(): Boolean =
    if closed then false
    else
      current match
        case Some(it) if it.hasNext => true
        case Some(it)               =>
          it.close()
          current = None
          advance()
        case None =>
          if files.hasNext then
            current = Some(new ParquetIterator[K, V](headers, () => new ParquetColumnSource(files.next())))
            advance()
          else
            closed = true
            false
      end match
    end if
  end advance

  override def hasNext: Boolean = advance()

  override def next(): NamedTuple[K, V] =
    if !hasNext then throw new NoSuchElementException("ParquetDirIterator exhausted")
    end if
    current.get.next()
  end next

  /** Close the currently-open file's handle, if any. Safe to call more than once. */
  override def close(): Unit =
    if !closed then
      closed = true
      current.foreach(_.close())
      current = None
    end if
  end close

end ParquetDirIterator
