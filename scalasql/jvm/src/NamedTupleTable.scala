package io.github.quafadas.scautable.scalasql

import scalasql.query.{Table, TableRef, Column}
import scalasql.core.{Queryable, DialectTypeMappers, Expr, Sc, TypeMapper}
import scala.NamedTuple.*
import scala.compiletime.*
import scala.annotation.nowarn

// ---------------------------------------------------------------------------
// Row-type alias
// ---------------------------------------------------------------------------

/** The higher-kinded row-type for a NamedTuple table.
  *
  * When `T = scalasql.Expr` the row is `NamedTuple[N, Tuple.Map[Vals, Expr]]` — an expression tuple usable inside scalasql queries. When `T = scalasql.Sc` (identity) the row is
  * `NamedTuple[N, Tuple.Map[Vals, Sc]]` — effectively `NamedTuple[N, Vals]` returned from `db.run`.
  */
type NTRow[N <: Tuple, Vals <: Tuple] = [T[_]] =>> NamedTuple[N, Tuple.Map[Vals, T]]

// ---------------------------------------------------------------------------
// NamedTupleTable
// ---------------------------------------------------------------------------

/** A scalasql [[scalasql.query.Table]] whose row type is an anonymous `NamedTuple`.
  *
  * Enables the full scalasql query DSL (select / filter / map / join / aggregate / insert / update / delete) on a table whose schema was inferred from a live JDBC database at
  * compile time by `DB.sqlTable`. No case class, no codegen step.
  *
  * ==Usage==
  * {{{
  * import io.github.quafadas.scautable.db.*
  * import io.github.quafadas.scautable.scalasql.*
  *
  * // Macro infers schema at compile time; `db` is a live DbApi wired from
  * // SCAUTABLE_DB_URL / _USER / _PASSWORD, connected lazily on first use.
  * val (db, countries) = DB.sqlTable[H2]("country")
  *
  * // Full scalasql DSL:
  * val big = db.run(countries.select.filter(_.population > 1_000_000))
  * }}}
  *
  * ==Identifier handling==
  * Table names are always quoted by the dialect's `escape` mechanism. Column names are passed through without transformation: configure your `scalasql.Config` with
  * `columnNameMapper = identity` to prevent any camelCase-to-snake_case conversion.
  *
  * @param ntName
  *   The exact database table name.
  * @param ntSchema
  *   Optional schema qualifier (e.g. `"public"` for PostgreSQL).
  * @tparam N
  *   Tuple of column-name string literals.
  * @tparam Vals
  *   Tuple of Scala value types for each column.
  */
class NamedTupleTable[N <: Tuple, Vals <: Tuple](
    val ntName: String,
    val ntSchema: String = ""
)(using
    _src: sourcecode.Name,
    _meta: Table.Metadata[NTRow[N, Vals]]
) extends Table[NTRow[N, Vals]]()(using _src, _meta):

  /** Use the exact DB table name; bypass `tableNameMapper`. */
  override def tableName: String = ntName

  /** Optional schema qualifier. */
  override def schemaName: String = ntSchema

  /** Always quote the table identifier to preserve case and reserved words. */
  override def escape: Boolean = true

  /** Bypass per-column name transformation — DB-exact names are preserved. */
  override def tableColumnNameOverride(s: String): String = s
end NamedTupleTable

// ---------------------------------------------------------------------------
// Custom Queryable.Row (avoids TableQueryable's <: Product bound)
// ---------------------------------------------------------------------------

/** A `Queryable.Row` for `NamedTuple` rows that does not require the result type to extend `Product`. This is necessary because `Table.Internal.TableQueryable[Q, R <: Product]`
  * cannot prove `R = NTRow[N, Vals][Sc] <: Product` when `Vals` is abstract.
  */
private final class NTQueryableRow[N <: Tuple, Vals <: Tuple](
    private val walkLabels0: () => Seq[String],
    private val rowFns: IArray[DialectTypeMappers => Queryable.Row[Expr[?], ?]],
    private val mappers: DialectTypeMappers
) extends Queryable.Row[NTRow[N, Vals][Expr], NTRow[N, Vals][Sc]]:

  private val rows: IArray[Queryable.Row[Expr[?], ?]] = rowFns.map(_(mappers))

  def walkLabels(): Seq[List[String]] = walkLabels0().map(List(_))

  def walkExprs(q: NTRow[N, Vals][Expr]): Seq[Expr[?]] =
    q.toTuple.productIterator.toIndexedSeq.zipWithIndex.flatMap { case (e, i) =>
      rows(i).walkExprs(e.asInstanceOf[Expr[?]])
    }

  def construct(args: Queryable.ResultSetIterator): NTRow[N, Vals][Sc] =
    val data = (0 until rows.length).map(i => rows(i).construct(args).asInstanceOf[AnyRef])
    Tuple.fromIArray(IArray.unsafeFromArray(data.toArray)).asInstanceOf[NTRow[N, Vals][Sc]]
  end construct

  def deconstruct(r: NTRow[N, Vals][Sc]): NTRow[N, Vals][Expr] =
    val data = r.toTuple.productIterator.toIndexedSeq.zipWithIndex.map { case (v, i) =>
      type R
      rows(i).asInstanceOf[Queryable.Row[Expr[?], R]].deconstruct(v.asInstanceOf[R]).asInstanceOf[AnyRef]
    }
    Tuple.fromIArray(IArray.unsafeFromArray(data.toArray)).asInstanceOf[NTRow[N, Vals][Expr]]
  end deconstruct
end NTQueryableRow

// ---------------------------------------------------------------------------
// Metadata derivation helpers
// ---------------------------------------------------------------------------

object NamedTupleTable:

  /** Opaque deferred row queryable; companion searched by `compiletime.summonAll`. */
  opaque type ColRowFn[T] = DialectTypeMappers => Queryable.Row[Expr[T], T]

  object ColRowFn:
    @nowarn("msg=inline given alias")
    inline given [T]: ColRowFn[T] = mappers =>
      import mappers.given
      compiletime.summonInline[Queryable.Row[Expr[T], T]]
  end ColRowFn

  /** Opaque deferred column maker. */
  opaque type ColMaker[T] = (DialectTypeMappers, TableRef, String) => Column[T]

  object ColMaker:
    @nowarn("msg=inline given alias")
    inline given [T]: ColMaker[T] = (mappers, ref, name) =>
      import mappers.given
      new Column[T](ref, name)(using compiletime.summonInline[TypeMapper[T]])
  end ColMaker

  /** Build `Table.Metadata[NTRow[N, Vals]]` from compile-time column type information.
    *
    * Used by the `inline given initNTMetadata` for hand-coded tables. Not suitable for macros because it calls `constValueTuple[N]` and `compiletime.summonAll`, which fail when
    * `N` or `Vals` contain intersection types (e.g. `n & Tuple` generated by a macro splice).
    */
  inline def buildMetadata[N <: Tuple, Vals <: Tuple]: Table.Metadata[NTRow[N, Vals]] =
    import NamedTupleTable.ColRowFn.given
    import NamedTupleTable.ColMaker.given
    type RowFns = Tuple.Map[Vals, ColRowFn]
    type ColMakers = Tuple.Map[Vals, ColMaker]

    val rowFns: IArray[DialectTypeMappers => Queryable.Row[Expr[?], ?]] =
      IArray.from(
        compiletime
          .summonAll[RowFns]
          .productIterator
          .asInstanceOf[Iterator[DialectTypeMappers => Queryable.Row[Expr[?], ?]]]
      )
    val colMakers: IArray[(DialectTypeMappers, TableRef, String) => Column[?]] =
      IArray.from(
        compiletime
          .summonAll[ColMakers]
          .productIterator
          .asInstanceOf[Iterator[(DialectTypeMappers, TableRef, String) => Column[?]]]
      )
    val colNames: IArray[String] =
      IArray.from(constValueTuple[N].productIterator.asInstanceOf[Iterator[String]])

    buildMetadataFrom[N, Vals](colNames, rowFns, colMakers)
  end buildMetadata

  /** Build `Table.Metadata` from explicit runtime arrays.
    *
    * Used by the `DB.sqlTable` macro which cannot call `constValueTuple` or `compiletime.summonAll` from inside a quote splice. The macro constructs per-column `rowFn` and
    * `colMaker` closures inside the quoted expression and passes them here at runtime.
    */
  def buildMetadataFrom[N <: Tuple, Vals <: Tuple](
      colNames: IArray[String],
      rowFns: IArray[DialectTypeMappers => Queryable.Row[Expr[?], ?]],
      colMakers: IArray[(DialectTypeMappers, TableRef, String) => Column[?]]
  ): Table.Metadata[NTRow[N, Vals]] =

    def queryables(mappers: DialectTypeMappers, i: Int): Queryable.Row[?, ?] =
      rowFns(i)(mappers)

    def walkLabels0(): Seq[String] = colNames.toSeq

    def buildQueryable(
        wl0: () => Seq[String],
        mappers: DialectTypeMappers,
        proxy: Table.Metadata.QueryableProxy
    ): Queryable[NTRow[N, Vals][Expr], NTRow[N, Vals][Sc]] =
      new NTQueryableRow[N, Vals](wl0, rowFns, mappers)

    def vExpr0(
        ref: TableRef,
        mappers: DialectTypeMappers,
        proxy: Table.Metadata.QueryableProxy
    ): NTRow[N, Vals][Column] =
      val data = colNames.zipWithIndex.map { case (name, i) =>
        colMakers(i)(mappers, ref, name).asInstanceOf[AnyRef]
      }
      Tuple.fromIArray(data).asInstanceOf[NTRow[N, Vals][Column]]
    end vExpr0

    new Table.Metadata[NTRow[N, Vals]](queryables, walkLabels0, buildQueryable, vExpr0)
  end buildMetadataFrom

end NamedTupleTable

// ---------------------------------------------------------------------------
// Top-level given — in scope with `import io.github.quafadas.scautable.scalasql.given`
// ---------------------------------------------------------------------------

/** Derives `Table.Metadata[NTRow[N, Vals]]` for any `NamedTupleTable[N, Vals]`.
  *
  * Brought into scope automatically by:
  * {{{
  * import io.github.quafadas.scautable.scalasql.given
  * // or
  * import io.github.quafadas.scautable.scalasql.*
  * }}}
  */
inline given initNTMetadata[N <: Tuple, Vals <: Tuple]: Table.Metadata[NTRow[N, Vals]] =
  NamedTupleTable.buildMetadata[N, Vals]

/** Derives `Queryable.Row[NTRow[N, Vals][Expr], NTRow[N, Vals][Sc]]` for any `NamedTupleTable[N, Vals]` row.
  *
  * scalasql's aggregate operations (`sumBy`, `avgBy`, `minBy`, `maxBy`, `countBy`, ...) are reached via an implicit conversion from `Aggregatable[Q]` to `AggOps[Q]` (e.g.
  * `PostgresDialect.AggOpsConv`), which needs a *fresh*, ambient `Queryable.Row[Q, ?]` for the row type `Q` — separate from the one already embedded in the table's
  * `Table.Metadata`, which is only used internally to build the `Select`. Without this given, implicit search falls back to scalasql's single-column `Expr.ExprQueryable`, which
  * cannot unify with a multi-column `NamedTuple` row and fails with "extension method could not be fully constructed".
  *
  * Reuses the already-resolved `Table.Metadata` (rather than re-deriving from scratch) so it works equally for hand-coded and macro-generated (`DB.sqlTable`) tables.
  *
  * Brought into scope automatically by:
  * {{{
  * import io.github.quafadas.scautable.scalasql.given
  * // or
  * import io.github.quafadas.scautable.scalasql.*
  * }}}
  */
given initNTQueryableRow[N <: Tuple, Vals <: Tuple](using
    meta: Table.Metadata[NTRow[N, Vals]],
    dialect: DialectTypeMappers
): Queryable.Row[NTRow[N, Vals][Expr], NTRow[N, Vals][Sc]] =
  val proxy = new Table.Metadata.QueryableProxy(i => meta.queryables(dialect, i))
  meta
    .queryable(meta.walkLabels0, dialect, proxy)
    .asInstanceOf[Queryable.Row[NTRow[N, Vals][Expr], NTRow[N, Vals][Sc]]]
end initNTQueryableRow
