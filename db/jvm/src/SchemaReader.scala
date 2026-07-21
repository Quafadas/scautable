package io.github.quafadas.scautable.db

import java.sql.Connection
import java.sql.ResultSet

import scala.collection.mutable.ArrayBuffer

/** Reads schema information from a live JDBC connection.
  *
  * All methods are pure functions over a live [[java.sql.Connection]] — they perform no writes and
  * leave the connection open.
  */
object SchemaReader:

  /** Returns column metadata for a table or view.
    *
    * Unknown table names produce a compile-time error that lists near-miss candidates.
    *
    * @param conn
    *   An open JDBC connection. Not closed by this method.
    * @param schema
    *   Optional schema / catalog qualifier (e.g. `Some("public")` for Postgres).
    * @param table
    *   The bare table name (without schema prefix).
    * @return
    *   Sequence of [[ColumnMeta]], ordered by column position.
    * @throws IllegalArgumentException
    *   If the table is not found. The message includes a list of near-miss table names.
    */
  def forTable(conn: Connection, schema: Option[String], table: String): Seq[ColumnMeta] =
    val md = conn.getMetaData
    val cols: ResultSet = md.getColumns(null, schema.orNull, table, null)

    val buf = ArrayBuffer.empty[ColumnMeta]
    while cols.next() do
      val name = cols.getString("COLUMN_NAME")
      val jdbcType = cols.getInt("DATA_TYPE")
      val dbTypeName = cols.getString("TYPE_NAME").toLowerCase
      val nullableInt = cols.getInt("NULLABLE")
      val nullable = nullableInt == java.sql.DatabaseMetaData.columnNullable
      val position = cols.getInt("ORDINAL_POSITION")
      buf += ColumnMeta(name, jdbcType, dbTypeName, nullable, position)
    end while
    cols.close()

    if buf.isEmpty then
      // Build a near-miss list for a better error message.
      val candidates = nearMissTables(conn, schema, table)
      val hint = if candidates.nonEmpty then s"\n  Did you mean one of: ${candidates.mkString(", ")}?" else ""
      throw new IllegalArgumentException(
        s"Table '$table'${schema.fold("")(s => s" in schema '$s'")} not found.$hint"
      )
    end if

    buf.toSeq.sortBy(_.position)
  end forTable

  /** Returns column metadata for an arbitrary SQL query via a prepared statement.
    *
    * The statement is **never executed** — only `PreparedStatement.getMetaData` is called. This is
    * the DB path's key advantage over CSV: schema inference for arbitrary SQL text with no data
    * read.
    *
    * @param conn
    *   An open JDBC connection. Not closed by this method.
    * @param sql
    *   Any SELECT (or DML with RETURNING) that the driver supports via `prepareStatement`.
    * @return
    *   Sequence of [[ColumnMeta]], ordered by column position.
    * @throws java.sql.SQLException
    *   If the SQL is syntactically invalid.
    */
  def forQuery(conn: Connection, sql: String): Seq[ColumnMeta] =
    val stmt = conn.prepareStatement(sql)
    try
      val rsmd = stmt.getMetaData
      if rsmd == null then
        throw new IllegalStateException(
          "Driver returned null PreparedStatement.getMetaData — " +
            "the driver may require query execution to determine result set metadata. " +
            "Try using DB.table instead, or upgrade your JDBC driver."
        )
      end if

      val colCount = rsmd.getColumnCount
      val buf = ArrayBuffer.empty[ColumnMeta]
      for i <- 1 to colCount do
        val name = rsmd.getColumnLabel(i)
        val jdbcType = rsmd.getColumnType(i)
        val dbTypeName = rsmd.getColumnTypeName(i).toLowerCase
        val nullableInt = rsmd.isNullable(i)
        val nullable = nullableInt == java.sql.ResultSetMetaData.columnNullable
        buf += ColumnMeta(name, jdbcType, dbTypeName, nullable, i)
      end for
      buf.toSeq
    finally stmt.close()
  end forQuery

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  private def nearMissTables(conn: Connection, schema: Option[String], table: String): Seq[String] =
    try
      val md = conn.getMetaData
      val rs = md.getTables(null, schema.orNull, "%", Array("TABLE", "VIEW"))
      val all = ArrayBuffer.empty[String]
      while rs.next() do all += rs.getString("TABLE_NAME")
      rs.close()
      val lower = table.toLowerCase
      all.filter(t => t.toLowerCase.contains(lower) || levenshtein(t.toLowerCase, lower) <= 3).toSeq
    catch case _: Exception => Seq.empty
  end nearMissTables

  private def levenshtein(a: String, b: String): Int =
    val m = a.length
    val n = b.length
    val dp = Array.ofDim[Int](m + 1, n + 1)
    for i <- 0 to m do dp(i)(0) = i
    for j <- 0 to n do dp(0)(j) = j
    for
      i <- 1 to m
      j <- 1 to n
    do
      dp(i)(j) =
        if a(i - 1) == b(j - 1) then dp(i - 1)(j - 1)
        else 1 + (dp(i - 1)(j) min dp(i)(j - 1) min dp(i - 1)(j - 1))
    dp(m)(n)
  end levenshtein

end SchemaReader
