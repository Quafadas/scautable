package io.github.quafadas.scautable.db

/** Metadata about a single column returned by the database or a prepared statement.
  *
  * @param name
  *   Column name exactly as reported by the JDBC driver.
  * @param jdbcType
  *   The JDBC type code (see [[java.sql.Types]]).
  * @param dbTypeName
  *   The database-specific type name (e.g. "varchar", "jsonb", "uuid").
  * @param nullable
  *   Whether the column is nullable.
  * @param position
  *   1-based column position in the result set.
  */
case class ColumnMeta(
    name: String,
    jdbcType: Int,
    dbTypeName: String,
    nullable: Boolean,
    position: Int
)
