package io.github.quafadas.scautable.db

import java.sql.Connection

import scala.quoted.*

/** Entry point for compile-time database schema inference.
  *
  * == Usage ==
  *
  * {{{
  * // Read an entire table — schema inferred from live DB at compile time.
  * val countries = DB.table[H2]("country")
  * // countries: DbIterator[("iso3", "name", "population"), (String, String, Option[Long])]
  *
  * // Read with schema-qualified name (schema.table)
  * val c2 = DB.table[Postgres]("public.country")
  *
  * // Arbitrary SQL — no data is executed; schema comes from PreparedStatement.getMetaData.
  * val pop = DB.query[H2]("SELECT name, population FROM country WHERE population > 1000000")
  * }}}
  *
  * == Connection resolution (macro expansion time) ==
  *
  * The macro reads `SCAUTABLE_DB_URL`, `SCAUTABLE_DB_USER`, `SCAUTABLE_DB_PASSWORD` from the
  * compiler's JVM system properties (`-DSCAUTABLE_DB_URL=...`) or, failing that, its environment
  * variables.  If the live DB is unavailable it falls back to a JSON snapshot file (path
  * controlled by `SCAUTABLE_DB_SNAPSHOT` or defaulting to `scautable-db-schema.json`).
  *
  * == Runtime connection ==
  *
  * Generated code consults the same system properties / env vars at runtime — credentials are
  * never baked into source.
  */
object DB:

  /** Infer the schema of `tableName` from the live database at compile time.
    *
    * `tableName` may include a schema qualifier: `"schema.table"` or just `"table"`.
    */
  transparent inline def table[F <: DbFlavour](inline tableName: String): Any =
    ${ tableImpl[F]('tableName) }

  /** Infer the schema of an arbitrary SQL query at compile time.
    *
    * The query is **never executed** — only `PreparedStatement.getMetaData` is consulted.
    */
  transparent inline def query[F <: DbFlavour](inline sql: String): Any =
    ${ queryImpl[F]('sql) }

  // ---------------------------------------------------------------------------
  // Macro implementations
  // ---------------------------------------------------------------------------

  def tableImpl[F <: DbFlavour: Type](tableNameExpr: Expr[String])(using q: Quotes): Expr[Any] =
    import q.reflect.*

    val tableName = tableNameExpr.valueOrAbort

    // Parse optional "schema.table" notation (split on literal dot using char overload)
    val (schema, table) = tableName.split('.') match
      case Array(s, t) => (Some(s), t)
      case Array(t)    => (None, t)
      case other       => report.throwError(s"Invalid table name: '$tableName'. Use 'table' or 'schema.table'.")

    val cols = readSchema(s"table:$tableName", () => withConnection { conn =>
      SchemaReader.forTable(conn, schema, table)
    })

    buildIteratorExpr(cols, buildTableSql(schema, table, cols))
  end tableImpl

  def queryImpl[F <: DbFlavour: Type](sqlExpr: Expr[String])(using q: Quotes): Expr[Any] =
    val sql = sqlExpr.valueOrAbort

    val cols = readSchema(s"query:$sql", () => withConnection { conn =>
      SchemaReader.forQuery(conn, sql)
    })

    buildIteratorExpr(cols, sql)
  end queryImpl

  // ---------------------------------------------------------------------------
  // Private helpers (run inside the macro expansion, i.e. at compile time)
  // ---------------------------------------------------------------------------

  /** Try to read schema from live DB; fall back to snapshot file (filesystem then classpath). */
  private def readSchema(key: String, live: () => Seq[ColumnMeta])(using q: Quotes): Seq[ColumnMeta] =
    import q.reflect.*

    // 1. Try live connection
    ConnectionResolver.resolveAtCompileTime() match
      case Some(_) =>
        try live()
        catch
          case ex: Exception =>
            // Connection succeeded (env var set) but query failed — propagate as error
            report.throwError(s"Schema read failed: ${ex.getMessage}")
      case None =>
        // 2. Try filesystem snapshot (env-var path or default)
        val snapshotPath = ConnectionResolver.snapshotPath()
        SchemaSnapshot.load(key, snapshotPath) match
          case Some(cols) => cols
          case None       =>
            // 3. Try classpath snapshot (for test resources on the compile classpath)
            loadFromClasspath(key, SchemaSnapshot.defaultSnapshotPath) match
              case Some(cols) => cols
              case None       =>
                report.throwError(
                  s"""Cannot infer DB schema for '$key': no live connection and no snapshot found.
                     |
                     |To fix, choose one of:
                     |  A) Set system property or environment variable ${ConnectionResolver.urlEnvVar} to a JDBC URL
                     |     (e.g. -D${ConnectionResolver.urlEnvVar}=... on the compiler's JVM).
                     |     Optionally set ${ConnectionResolver.userEnvVar} and ${ConnectionResolver.passwordEnvVar}.
                     |  B) Generate a snapshot file:
                     |       SchemaSnapshot.write(conn, Map("$key" -> SchemaSnapshot.fromTable(conn, None, "...")))
                     |     then commit '$snapshotPath' (or set ${ConnectionResolver.snapshotEnvVar}).
                     |""".stripMargin
                )
  end readSchema

  /** Load snapshot from the compiler classpath (works for test compile resources). */
  private def loadFromClasspath(key: String, resourceName: String): Option[Seq[ColumnMeta]] =
    try
      val url = getClass.getClassLoader.getResource(resourceName)
      if url == null then None
      else
        val content = scala.io.Source.fromURL(url).mkString
        SchemaSnapshot.parseSnapshotJson(content, key)
    catch case _: Exception => None
  end loadFromClasspath

  /** Open a compile-time JDBC connection using env vars. */
  private def withConnection[A](f: Connection => A): A =
    val (url, user, pass) = ConnectionResolver.resolveAtCompileTime().get
    // Use openConnectionWith rather than DriverManager directly: during macro
    // expansion the compiler JVM uses a child classloader for project deps, which
    // DriverManager cannot see. openConnectionWith consults the thread context
    // classloader (set by Mill/sbt to the project classpath) instead.
    val conn = ConnectionResolver.openConnectionWith(url, user, pass)
    try f(conn)
    finally conn.close()
  end withConnection

  /** Map a [[ColumnMeta]] to a [[scala.quoted.TypeRepr]] inside the macro. */
  private def jdbcTypeToTypeRepr(col: ColumnMeta)(using q: Quotes): q.reflect.TypeRepr =
    import q.reflect.*
    import java.sql.Types.*

    val base: TypeRepr = col.jdbcType match
      case TINYINT | SMALLINT | INTEGER => TypeRepr.of[Int]
      case BIGINT                        => TypeRepr.of[Long]
      case REAL | FLOAT                  => TypeRepr.of[Float]
      case DOUBLE                        => TypeRepr.of[Double]
      case NUMERIC | DECIMAL             => TypeRepr.of[BigDecimal]
      case BOOLEAN | BIT                 => TypeRepr.of[Boolean]
      case CHAR | VARCHAR | LONGVARCHAR | NCHAR | NVARCHAR | LONGNVARCHAR | CLOB | NCLOB => TypeRepr.of[String]
      case DATE                          => TypeRepr.of[java.time.LocalDate]
      case TIMESTAMP                     => TypeRepr.of[java.time.LocalDateTime]
      case TIMESTAMP_WITH_TIMEZONE       => TypeRepr.of[java.time.Instant]
      case BINARY | VARBINARY | LONGVARBINARY | BLOB                                    => TypeRepr.of[Array[Byte]]
      // UUID: Java UUID via OTHER or when dbTypeName = "uuid"
      case OTHER if col.dbTypeName == "uuid"  => TypeRepr.of[java.util.UUID]
      case OTHER if col.dbTypeName == "json"  => TypeRepr.of[String]
      case OTHER if col.dbTypeName == "jsonb" => TypeRepr.of[String]
      case other =>
        report.throwError(
          s"""Cannot map column '${col.name}' (JDBC type $other / DB type '${col.dbTypeName}') to a Scala type.
             |
             |Escape hatches:
             |  • Use DB.query with an explicit CAST in your SQL to convert to a supported type.
             |    Example: SELECT CAST(my_col AS VARCHAR) AS my_col FROM ...
             |  • Override the column type via a SQL alias to a supported JDBC type.
             |  • Provide a custom given JdbcDecoder[YourType] for the unsupported column type.
             |""".stripMargin
        )

    if col.nullable then TypeRepr.of[Option].appliedTo(base) else base
  end jdbcTypeToTypeRepr

  /** Build the SQL `SELECT col1, col2 FROM [schema.]table` used at runtime. */
  private def buildTableSql(schema: Option[String], table: String, cols: Seq[ColumnMeta]): String =
    val quotedCols = cols.map(c => quoteIdentifier(c.name)).mkString(", ")
    val qualifiedTable = schema.fold(quoteIdentifier(table))(s => s"${quoteIdentifier(s)}.${quoteIdentifier(table)}")
    s"SELECT $quotedCols FROM $qualifiedTable"
  end buildTableSql

  /** Wrap an identifier in double-quotes, escaping embedded double-quotes as per SQL standard. */
  private def quoteIdentifier(name: String): String =
    "\"" + name.replace("\"", "\"\"") + "\""
  end quoteIdentifier

  /** Splice the `Expr[DbIterator[K, V]]` from the inferred column metadata and SQL. */
  private def buildIteratorExpr(cols: Seq[ColumnMeta], sql: String)(using q: Quotes): Expr[Any] =
    import q.reflect.*

    val headers: List[String] = cols.map(_.name).toList

    // Build the K type (tuple of string literal types)
    val tupleExpr = Expr.ofTupleFromSeq(headers.map(Expr(_)))

    // Build the V type (tuple of Scala value types)
    val valueTypesRepr: List[TypeRepr] = cols.map(col => jdbcTypeToTypeRepr(col)).toList
    val vTypeRepr: TypeRepr = valueTypesRepr.foldRight(TypeRepr.of[EmptyTuple]) { (tpe, acc) =>
      TypeRepr.of[*:].appliedTo(List(tpe, acc))
    }

    tupleExpr match
      case '{ $tup: hdrs } =>
        vTypeRepr.asType match
          case '[v] =>
            '{
              new DbIterator[hdrs & Tuple, v & Tuple](() => {
                val conn = ConnectionResolver.openConnection()
                val stmt = conn.createStatement()
                val rs   = stmt.executeQuery(${ Expr(sql) })
                (conn, stmt, rs)
              })
            }
      case _ =>
        report.throwError("Internal error: could not construct K type from column headers.")
  end buildIteratorExpr

end DB
