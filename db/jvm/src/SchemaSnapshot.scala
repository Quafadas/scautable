package io.github.quafadas.scautable.db

/** A serialisable snapshot of the schema for a single table or query.
  *
  * Snapshots are stored as JSON (one file per project, keyed by `key`) so that compiled code and CI can build without a live database connection. The snapshot path is controlled
  * by the `SCAUTABLE_DB_SNAPSHOT` system property or environment variable (defaults to `scautable-db-schema.json` in the working directory).
  *
  * Produce a snapshot with:
  * {{{
  *   SchemaSnapshot.write(conn, Map("country" -> SchemaSnapshot.fromTable(conn, None, "country")))
  * }}}
  */
case class SchemaSnapshot(
    key: String,
    columns: Seq[ColumnMeta]
)

object SchemaSnapshot:

  import scala.util.Try
  import java.nio.file.{Files, Paths}

  /** Default snapshot file name / path (relative to working directory unless overridden by env). */
  val defaultSnapshotPath: String = "scautable-db-schema.json"

  /** Read a snapshot for `key` from the snapshot file located at `path`. */
  def load(key: String, path: String = defaultSnapshotPath): Option[Seq[ColumnMeta]] =
    Try:
      val content = new String(Files.readAllBytes(Paths.get(path)))
      parseSnapshotJson(content, key)
    .toOption.flatten
  end load

  /** Serialise a map of snapshots to the snapshot file at `path`. */
  def write(snapshots: Map[String, Seq[ColumnMeta]], path: String = defaultSnapshotPath): Unit =
    val json = renderJson(snapshots)
    Files.write(Paths.get(path), json.getBytes("UTF-8"))
  end write

  /** Build a snapshot from a live connection for a table. */
  def fromTable(conn: java.sql.Connection, schema: Option[String], table: String): Seq[ColumnMeta] =
    SchemaReader.forTable(conn, schema, table)

  /** Build a snapshot from a live connection for a query. */
  def fromQuery(conn: java.sql.Connection, sql: String): Seq[ColumnMeta] =
    SchemaReader.forQuery(conn, sql)

  // ---------------------------------------------------------------------------
  // Minimal hand-rolled JSON serialisation (no external library dependency)
  // ---------------------------------------------------------------------------

  private[db] def renderJson(snapshots: Map[String, Seq[ColumnMeta]]): String =
    val entries = snapshots
      .map { case (key, cols) =>
        val colsJson = cols.map(colJson).mkString(",\n      ")
        s"""  ${jsonStr(key)}: [\n      $colsJson\n    ]"""
      }
      .mkString(",\n")
    s"{\n$entries\n}"
  end renderJson

  private def colJson(c: ColumnMeta): String =
    s"""{\"name\":${jsonStr(c.name)},\"jdbcType\":${c.jdbcType},\"dbTypeName\":${jsonStr(c.dbTypeName)},\"nullable\":${c.nullable},\"position\":${c.position}}"""

  /** Wrap a string in JSON double-quotes, escaping backslashes and double-quotes. */
  private def jsonStr(s: String): String = "\"" + escapeJsonString(s) + "\""

  /** Escape special characters for use inside a JSON string value. */
  private def escapeJsonString(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")

  def parseSnapshotJson(content: String, key: String): Option[Seq[ColumnMeta]] =
    // Very simple hand-rolled parser: look for the key and extract the column array.
    // This avoids any JSON library dependency in the core db module.
    // The key is JSON-escaped using the same escapeJsonString helper.
    val keyPat = s""""${escapeJsonString(key)}\"\\s*:\\s*\\[""".r
    keyPat.findFirstMatchIn(content).map { m =>
      // Start scanning from the '[' (at m.end - 1) to properly track bracket depth,
      // but extract content starting at m.end (first character after '[').
      var depth = 0
      var i = m.end - 1 // index of the opening '[' in content
      val start = m.end // first character inside the array (after '[')
      var end = start
      var found = false
      while i < content.length && !found do
        content(i) match
          case '[' => depth += 1
          case ']' =>
            depth -= 1
            if depth == 0 then
              end = i
              found = true
            end if
          case _ => ()
        end match
        i += 1
      end while
      val arrayContent = content.substring(start, end)
      parseColumnMetas(arrayContent)
    }
  end parseSnapshotJson

  private def parseColumnMetas(arrayContent: String): Seq[ColumnMeta] =
    // Split on },{  to get individual objects
    val objPattern = """\{[^}]+\}""".r
    objPattern.findAllIn(arrayContent).flatMap(parseColumnMeta).toSeq
  end parseColumnMetas

  private def parseColumnMeta(obj: String): Option[ColumnMeta] =
    def extract(field: String): Option[String] =
      val pat = s""""$field"\\s*:\\s*"([^"]*)"""".r
      pat.findFirstMatchIn(obj).map(_.group(1))
    end extract
    def extractNum(field: String): Option[Int] =
      val pat = s""""$field"\\s*:\\s*(-?\\d+)""".r
      pat.findFirstMatchIn(obj).map(_.group(1).toInt)
    end extractNum
    def extractBool(field: String): Option[Boolean] =
      val pat = s""""$field"\\s*:\\s*(true|false)""".r
      pat.findFirstMatchIn(obj).map(_.group(1).toBoolean)
    end extractBool

    for
      name <- extract("name")
      jdbcType <- extractNum("jdbcType")
      dbTypeName <- extract("dbTypeName")
      nullable <- extractBool("nullable")
      position <- extractNum("position")
    yield ColumnMeta(name, jdbcType, dbTypeName, nullable, position)
    end for
  end parseColumnMeta

end SchemaSnapshot
