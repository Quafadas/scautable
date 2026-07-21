package io.github.quafadas.scautable.scalasql

import scala.quoted.*
import io.github.quafadas.scautable.db.{DbFlavour, ColumnMeta, SchemaReader, SchemaSnapshot, ConnectionResolver}
import java.sql.DriverManager
import scalasql.query.{Table, TableRef, Column}
import scalasql.core.{Queryable, DialectTypeMappers, Expr as SExpr}

/** Extension point on the `DB` object that adds the `sqlTable` / `connection` macros.
  *
  * Because this macro needs to reference `NamedTupleTable` (from `scautable-scalasql`) at splice time, it lives in the `scalasql` module rather than `db`. Import both modules and
  * then call `DB.sqlTable[F]("tableName")` and, separately, `DB.connection[F]("tableName")`.
  *
  * ==Usage==
  * {{{
  * import io.github.quafadas.scautable.db.*
  * import io.github.quafadas.scautable.scalasql.*
  *
  * // Schema inferred at compile time; `connection` returns a live DbApi built from the same
  * // SCAUTABLE_DB_URL / _USER / _PASSWORD env vars, connected lazily on first use.
  * val countries = DB.sqlTable[H2]("country")
  * // countries: NamedTupleTable[("iso3","name","population","area_km2","is_island"),
  * //                             (String,String,Option[Long],Double,Boolean)]
  * val db = DB.connection[H2]("country")
  *
  * db.run(countries.select.filter(_.population > 1_000_000))
  * }}}
  */
extension (db: io.github.quafadas.scautable.db.DB.type)
  /** Infer the schema of `tableName` from the live database at compile time and return just the `NamedTupleTable` — no connection is opened or wired up. Use `DB.connection` to
    * obtain a matching `DbApi`.
    */
  transparent inline def sqlTable[F <: DbFlavour](inline tableName: String)(using
        fd: FlavourDialect[F]
    ): Any =
      ${ SqlTableMacro.tableOnlyImpl[F]('tableName, 'fd) }

  /** A lazily-connecting `DbApi` wired from `SCAUTABLE_DB_URL` / `_USER` / `_PASSWORD`, matching the schema-inference connection used by `DB.sqlTable`. Constructing it never
    * touches the network.
    */
  transparent inline def connection[F <: DbFlavour](inline tableName: String)(using
      fd: FlavourDialect[F]
  ): Any =
    ${ SqlTableMacro.connectionOnlyImpl[F]('tableName, 'fd) }

  /** Infer the schema of `tableName` from the live database at compile time and return a `(DbApi, NamedTupleTable)` pair: a lazily-connecting `DbApi` wired from the same
    * connection env vars as the schema inference, and a `NamedTupleTable` that enables the full scalasql push-down query DSL.
    *
    * `tableName` may include a schema qualifier: `"schema.table"` or just `"table"`.
    *
    * ==Connection resolution==
    * Schema (compile time): live env vars → filesystem snapshot → classpath snapshot, same priority order as `DB.table`. Runtime `DbApi`: opened lazily from `SCAUTABLE_DB_URL` /
    * `_USER` / `_PASSWORD` on first use — constructing the pair never touches the network.
    */
  transparent inline def sqlConnectionAndTable[F <: DbFlavour](inline tableName: String)(using
      fd: FlavourDialect[F]
  ): Any =
    ${ SqlTableMacro.sqlTableImpl[F]('tableName, 'fd) }
end extension

/** Internal macro implementation for `DB.sqlTable`. */
private object SqlTableMacro:

  def sqlTableImpl[F <: DbFlavour: Type](
      tableNameExpr: Expr[String],
      fdExpr: Expr[FlavourDialect[F]]
  )(using q: Quotes): Expr[Any] =
    import q.reflect.*

    val tableName = tableNameExpr.valueOrAbort

    val (schema, table) = tableName.split('.') match
      case Array(s, t) => (Some(s), t)
      case Array(t)    => (None, t)
      case other       =>
        report.throwError(s"Invalid table name: '$tableName'. Use 'table' or 'schema.table'.")

    val cols = readSchema(s"table:$tableName", (url, user, pass) =>
      val conn = ConnectionResolver.openConnectionWith(url, user, pass)
      SchemaReader.forTable(conn, schema, table)
    )

    buildSqlTableExpr(cols, table, schema, fdExpr)
  end sqlTableImpl

  def tableOnlyImpl[F <: DbFlavour: Type](
      tableNameExpr: Expr[String],
      fdExpr: Expr[FlavourDialect[F]]
  )(using q: Quotes): Expr[Any] =
    sqlTableImpl[F](tableNameExpr, fdExpr) match
      case '{ $t: (_, r) } => '{ $t._2 }
  end tableOnlyImpl

  def connectionOnlyImpl[F <: DbFlavour: Type](
      tableNameExpr: Expr[String],
      fdExpr: Expr[FlavourDialect[F]]
  )(using q: Quotes): Expr[Any] =
    sqlTableImpl[F](tableNameExpr, fdExpr) match
      case '{ $t: (l, _) } => '{ $t._1 }
  end connectionOnlyImpl

  // ---------------------------------------------------------------------------
  // Helpers (mirror the private helpers in DB)
  // ---------------------------------------------------------------------------

  private def readSchema(key: String, live: (String, Option[String], Option[String]) => Seq[ColumnMeta])(using q: Quotes): Seq[ColumnMeta] =
    import q.reflect.*
    ConnectionResolver.resolveAtCompileTime() match
      case Some((url, user, pass)) =>
        try live(url, user, pass)
        catch
          case ex: Exception =>
            report.throwError(s"Schema read failed: ${ex.getMessage}")
      case None =>
        val snapshotPath = ConnectionResolver.snapshotPath()
        SchemaSnapshot.load(key, snapshotPath) match
          case Some(cols) => cols
          case None       =>
            loadFromClasspath(key, SchemaSnapshot.defaultSnapshotPath) match
              case Some(cols) => cols
              case None       =>
                report.throwError(
                  s"""Cannot infer DB schema for '$key': no live connection and no snapshot found.
                     |
                     |To fix, choose one of:
                     |  A) Set environment variable ${ConnectionResolver.urlEnvVar} to a JDBC URL.
                     |     Optionally set ${ConnectionResolver.userEnvVar} and ${ConnectionResolver.passwordEnvVar}.
                     |  B) Commit a snapshot file produced by SchemaSnapshot.write().
                     |""".stripMargin
                )
        end match
    end match
  end readSchema

  private def loadFromClasspath(key: String, resourceName: String): Option[Seq[ColumnMeta]] =
    try
      val url = getClass.getClassLoader.getResource(resourceName)
      if url == null then None
      else
        val content = scala.io.Source.fromURL(url).mkString
        SchemaSnapshot.parseSnapshotJson(content, key)
      end if
    catch case _: Exception => None
  end loadFromClasspath

  private def withConnection[A](f: java.sql.Connection => A): A =
    val (url, user, pass) = ConnectionResolver.resolveAtCompileTime().get
    val conn =
      if user.isDefined then DriverManager.getConnection(url, user.get, pass.getOrElse(""))
      else DriverManager.getConnection(url)
    try f(conn)
    finally conn.close()
    end try
  end withConnection

  private def jdbcTypeToTypeRepr(col: ColumnMeta)(using q: Quotes): q.reflect.TypeRepr =
    import q.reflect.*
    import java.sql.Types.*

    val base: TypeRepr = col.jdbcType match
      case TINYINT | SMALLINT | INTEGER                                                  => TypeRepr.of[Int]
      case BIGINT                                                                        => TypeRepr.of[Long]
      case REAL | FLOAT                                                                  => TypeRepr.of[Float]
      case DOUBLE                                                                        => TypeRepr.of[Double]
      case NUMERIC | DECIMAL                                                             => TypeRepr.of[BigDecimal]
      case BOOLEAN | BIT                                                                 => TypeRepr.of[Boolean]
      case CHAR | VARCHAR | LONGVARCHAR | NCHAR | NVARCHAR | LONGNVARCHAR | CLOB | NCLOB =>
        TypeRepr.of[String]
      case DATE                                      => TypeRepr.of[java.time.LocalDate]
      case TIMESTAMP                                 => TypeRepr.of[java.time.LocalDateTime]
      case TIMESTAMP_WITH_TIMEZONE                   => TypeRepr.of[java.time.Instant]
      case BINARY | VARBINARY | LONGVARBINARY | BLOB => TypeRepr.of[Array[Byte]]
      case OTHER if col.dbTypeName == "uuid"         => TypeRepr.of[java.util.UUID]
      case OTHER if col.dbTypeName == "json"         => TypeRepr.of[String]
      case OTHER if col.dbTypeName == "jsonb"        => TypeRepr.of[String]
      case other                                     =>
        report.throwError(
          s"""Cannot map column '${col.name}' (JDBC type $other / DB type '${col.dbTypeName}') to a Scala type.
             |
             |Escape hatches:
             |  • Use an explicit CAST in your SQL: SELECT CAST(my_col AS VARCHAR) AS my_col FROM ...
             |  • Override via SQL alias to a supported JDBC type.
             |""".stripMargin
        )

    if col.nullable then TypeRepr.of[Option].appliedTo(base) else base
    end if
  end jdbcTypeToTypeRepr

  private def buildSqlTableExpr[F <: DbFlavour: Type](
      cols: Seq[ColumnMeta],
      table: String,
      schema: Option[String],
      fdExpr: Expr[FlavourDialect[F]]
  )(using q: Quotes): Expr[Any] =
    import q.reflect.*

    val headers: List[String] = cols.map(_.name).toList

    // Build N type as a tuple of singleton literal string types ("col1" *: "col2" *: EmptyTuple).
    // We must NOT use Expr.ofTupleFromSeq here — that produces String *: ... widened types and
    // prevents constValueTuple from working inside buildMetadata.
    val nTypeRepr: TypeRepr = headers.foldRight(TypeRepr.of[EmptyTuple]) { (name, acc) =>
      TypeRepr.of[*:].appliedTo(List(ConstantType(StringConstant(name)), acc))
    }

    // Build V type as the corresponding Scala value types.
    val valueTypesRepr: List[TypeRepr] = cols.map(col => jdbcTypeToTypeRepr(col)).toList
    val vTypeRepr: TypeRepr = valueTypesRepr.foldRight(TypeRepr.of[EmptyTuple]) { (tpe, acc) =>
      TypeRepr.of[*:].appliedTo(List(tpe, acc))
    }

    // Per-column row-function expressions.  Use Expr.summon[TypeMapper[t]] at the expansion
    // site to find the dialect's TypeMapper for each concrete column type `t`.
    val rowFnExprs: List[scala.quoted.Expr[DialectTypeMappers => Queryable.Row[SExpr[?], ?]]] =
      valueTypesRepr.map { tpeRepr =>
        tpeRepr.asType match
          case '[t] =>
            val tmExpr = scala.quoted.Expr
              .summon[scalasql.core.TypeMapper[t]]
              .getOrElse(
                report.throwError(
                  s"No TypeMapper[${tpeRepr.show}] found. " +
                    "Import your scalasql dialect (e.g. `import scalasql.H2Dialect.given`) at the call site."
                )
              )
            '{ (_: DialectTypeMappers) =>
              (scalasql.core.Expr
                .ExprQueryable[scalasql.core.Expr, t](using $tmExpr)
                .asInstanceOf[Queryable.Row[SExpr[?], ?]])
            }
      }

    // Per-column column-maker expressions (for vExpr0 / Column construction).
    val colMakerExprs: List[scala.quoted.Expr[(DialectTypeMappers, TableRef, String) => Column[?]]] =
      valueTypesRepr.map { tpeRepr =>
        tpeRepr.asType match
          case '[t] =>
            val tmExpr = scala.quoted.Expr
              .summon[scalasql.core.TypeMapper[t]]
              .getOrElse(
                report.throwError(s"No TypeMapper[${tpeRepr.show}] found.")
              )
            '{ (_: DialectTypeMappers, ref: TableRef, name: String) =>
              (new Column[t](ref, name)(using $tmExpr): Column[?])
            }
      }

    val colNamesExpr = scala.quoted.Expr(headers)
    val rowFnsExpr = scala.quoted.Expr.ofList(rowFnExprs)
    val colMakersExpr = scala.quoted.Expr.ofList(colMakerExprs)
    val tableNameExpr = scala.quoted.Expr(table)
    val schemaNameExpr = scala.quoted.Expr(schema.getOrElse(""))

    nTypeRepr.asType match
      case '[n] =>
        vTypeRepr.asType match
          case '[v] =>
            '{
              // buildMetadataFrom avoids constValueTuple / summonAll, so it compiles
              // even when N and Vals are intersection types (n & Tuple, v & Tuple).
              val _meta: Table.Metadata[NTRow[n & Tuple, v & Tuple]] =
                NamedTupleTable.buildMetadataFrom[n & Tuple, v & Tuple](
                  IArray.from($colNamesExpr),
                  IArray.from($rowFnsExpr),
                  IArray.from($colMakersExpr)
                )
              val table = new NamedTupleTable[n & Tuple, v & Tuple]($tableNameExpr, $schemaNameExpr)(using
                summon[sourcecode.Name],
                _meta
              )
              val liveDb: scalasql.core.DbApi = new LazyDbApi(() =>
                scalasql.DbClient
                  .Connection(ConnectionResolver.openConnection(), new scalasql.Config {})(using
                    $fdExpr.dialect
                  )
                  .getAutoCommitClientConnection
              )
              (liveDb, table)
            }
    end match
  end buildSqlTableExpr

end SqlTableMacro
