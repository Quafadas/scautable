package io.github.quafadas.scautable.db

import java.sql.{Connection, ResultSet, Statement}
import scala.NamedTuple.*
import scala.annotation.publicInBinary

/** A lazily-evaluated iterator over a JDBC [[java.sql.ResultSet]] that surfaces rows as
  * `NamedTuple[K, V]`.
  *
  * === Single-use semantics ===
  * Like [[io.github.quafadas.scautable.CsvIterator]], this iterator can only be traversed once. The
  * underlying [[java.sql.ResultSet]], [[java.sql.Statement]], and [[java.sql.Connection]] are
  * **closed** when the iterator is exhausted or when [[close]] is called explicitly.
  *
  * The JDBC connection is opened **lazily** — only when the iterator is first used (first call to
  * `hasNext` or `next()`). This means that constructing a `DbIterator` has no side effects;
  * errors from the connection or query appear on first use.
  *
  * The simplest REPL-friendly usage:
  * {{{
  *   val rows = DB.table[H2]("country").toSeq  // exhausts + closes
  * }}}
  *
  * @tparam K
  *   Tuple of column name string-literals (e.g. `("iso3", "name", "population")`).
  * @tparam V
  *   Tuple of Scala value types corresponding to each column (e.g. `(String, String, Option[Long])`).
  */
class DbIterator[K <: Tuple, V <: Tuple] @publicInBinary private[db] (
    private val factory: () => (Connection, Statement, ResultSet)
)(using decoder: JdbcRowDecoder[V])
    extends Iterator[NamedTuple[K, V]]
    with AutoCloseable:

  private var _closed       = false
  private var _initialised  = false
  private var _conn: Option[Connection] = None
  private var _stmt: Option[Statement]  = None
  private var _rs: Option[ResultSet]    = None
  private var _hasNextCached: Boolean = false
  private var _nextCached: Boolean    = false

  private def ensureOpen(): Unit =
    if !_initialised && !_closed then
      val (c, s, r) = factory()
      _conn        = Some(c)
      _stmt        = Some(s)
      _rs          = Some(r)
      _initialised = true
  end ensureOpen

  override def hasNext: Boolean =
    if _closed then return false
    ensureOpen()
    if !_nextCached then
      _hasNextCached = _rs.get.next()
      _nextCached    = true
      if !_hasNextCached then closeResources()
    end if
    _hasNextCached
  end hasNext

  override def next(): NamedTuple[K, V] =
    if !hasNext then throw new NoSuchElementException("DbIterator exhausted")
    _nextCached = false // consume the cached next()
    val tuple = decoder.decodeRow(_rs.get)
    NamedTuple.build[K & Tuple]()(tuple)
  end next

  /** Explicitly close the underlying JDBC resources.
    *
    * Safe to call multiple times; subsequent calls are no-ops. If the iterator was never used,
    * this is a no-op (no connection was opened).
    */
  def close(): Unit = if !_closed then closeResources()

  private def closeResources(): Unit =
    _closed = true
    _hasNextCached = false
    _rs.foreach(r => try r.close() catch case _: Exception => ())
    _stmt.foreach(s => try s.close() catch case _: Exception => ())
    _conn.foreach(c => try c.close() catch case _: Exception => ())
  end closeResources

end DbIterator
