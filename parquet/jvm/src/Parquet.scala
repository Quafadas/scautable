package io.github.quafadas.scautable.parquet

import io.github.quafadas.table.ReadAs

import scala.NamedTuple.NamedTuple
import scala.quoted.*

/** Compile-time schema inference for parquet files.
  *
  * Parquet files carry their schema in the footer, so — unlike CSV — nothing has to be guessed. The macro reads the footer at compile time and hands back a [[ParquetIterator]]
  * whose `NamedTuple` shape is exactly the file's schema.
  *
  * {{{
  * val titanic = Parquet.resource("titanic.parquet")
  * // titanic: ParquetIterator[
  * //   ("PassengerId", "Survived", ..., "Embarked"),
  * //   (Option[Long], Option[Long], ..., Option[String])
  * // ]
  *
  * titanic.toSeq.take(5).ptbln
  * }}}
  *
  * ===Type mapping===
  *
  * {{{
  * BOOLEAN                              -> Boolean
  * INT32                                -> Int
  * INT32  (DATE)                        -> java.time.LocalDate
  * INT64                                -> Long
  * INT64  (TIMESTAMP) / INT96           -> java.time.Instant
  * FLOAT                                -> Float
  * DOUBLE                               -> Double
  * BINARY (STRING | ENUM | JSON)        -> String
  * BINARY (UUID)                        -> java.util.UUID
  * BINARY                               -> Array[Byte]
  * any    (DECIMAL)                     -> BigDecimal
  * }}}
  *
  * By default, a field declared `optional` is surfaced as `Option[T]`; a `required` field is surfaced as `T`. Pass [[ParquetOptionality.NoOptions]] to surface every field as `T`
  * and fail at runtime if a value is absent.
  *
  * ===Nested structs===
  *
  * `ReadAs.Rows` represents parquet groups as nested `NamedTuple` values and supports required and optional structs at arbitrary depth. `ReadAs.Columns` currently supports flat
  * schemas only; requesting columns for a nested schema is rejected at compile time. Parquet `LIST`, `MAP`, and other repeated fields are not yet supported.
  */

object Parquet:

  /** Read a parquet file from the java resources, inferring its schema at compile time.
    *
    * {{{
    * val titanic = Parquet.resource("titanic.parquet")
    * }}}
    */
  transparent inline def resource(inline name: String): Any = ${ resourceImpl('name, '{ ReadAs.Rows }, '{ ParquetOptionality.FromSchema }) }

  /** Read a parquet file from the java resources as rows or as columns. Nested structs require [[ReadAs.Rows]].
    *
    * {{{
    * val cols = Parquet.resource("titanic.parquet", ReadAs.Columns)
    * // cols: NamedTuple[("PassengerId", ...), (Array[Option[Long]], ...)]
    * cols.Age.flatten.sum
    * }}}
    */
  transparent inline def resource(inline name: String, inline readAs: ReadAs): Any = ${ resourceImpl('name, 'readAs, '{ ParquetOptionality.FromSchema }) }

  /** Read a parquet resource with explicit handling for fields declared `optional`. */
  transparent inline def resource(inline name: String, inline optionality: ParquetOptionality): Any =
    ${ resourceImpl('name, '{ ReadAs.Rows }, 'optionality) }

  /** Read a parquet resource as rows or columns with explicit handling for fields declared `optional`. Nested structs require [[ReadAs.Rows]]. */
  transparent inline def resource(inline name: String, inline readAs: ReadAs, inline optionality: ParquetOptionality): Any =
    ${ resourceImpl('name, 'readAs, 'optionality) }

  /** Read a parquet file from an absolute filesystem path, inferring its schema at compile time. */
  transparent inline def absolutePath(inline path: String): Any = ${ absolutePathImpl('path, '{ ReadAs.Rows }, '{ ParquetOptionality.FromSchema }) }

  /** Read a parquet file from an absolute filesystem path, as rows or as columns. Nested structs require [[ReadAs.Rows]]. */
  transparent inline def absolutePath(inline path: String, inline readAs: ReadAs): Any = ${ absolutePathImpl('path, 'readAs, '{ ParquetOptionality.FromSchema }) }

  /** Read an absolute parquet path with explicit handling for fields declared `optional`. */
  transparent inline def absolutePath(inline path: String, inline optionality: ParquetOptionality): Any =
    ${ absolutePathImpl('path, '{ ReadAs.Rows }, 'optionality) }

  /** Read an absolute parquet path as rows or columns with explicit handling for fields declared `optional`. Nested structs require [[ReadAs.Rows]]. */
  transparent inline def absolutePath(inline path: String, inline readAs: ReadAs, inline optionality: ParquetOptionality): Any =
    ${ absolutePathImpl('path, 'readAs, 'optionality) }

  /** Read a parquet file relative to the working directory, inferring its schema at compile time.
    *
    * Note that the *compiler's* working directory is used to find the schema and the *runtime* working directory is used to find the data — these are frequently not the same
    * directory. Prefer [[resource]] or [[absolutePath]] unless you know they are.
    */
  transparent inline def pwd(inline path: String): Any = ${ pwdImpl('path, '{ ReadAs.Rows }, '{ ParquetOptionality.FromSchema }) }

  /** Read a parquet file relative to the working directory, as rows or as columns. Nested structs require [[ReadAs.Rows]]. */
  transparent inline def pwd(inline path: String, inline readAs: ReadAs): Any = ${ pwdImpl('path, 'readAs, '{ ParquetOptionality.FromSchema }) }

  /** Read a working-directory-relative parquet path with explicit handling for fields declared `optional`. */
  transparent inline def pwd(inline path: String, inline optionality: ParquetOptionality): Any =
    ${ pwdImpl('path, '{ ReadAs.Rows }, 'optionality) }

  /** Read a working-directory-relative parquet path as rows or columns with explicit handling for fields declared `optional`. Nested structs require [[ReadAs.Rows]]. */
  transparent inline def pwd(inline path: String, inline readAs: ReadAs, inline optionality: ParquetOptionality): Any =
    ${ pwdImpl('path, 'readAs, 'optionality) }

  /** The parquet footer schema, as a string. Handy when a schema is rejected and you want to see why. */
  def schemaOf(source: ParquetSource): String = ParquetSchema.read(source).toString

  // ---------------------------------------------------------------------------
  // Macro implementations — these run at compile time.
  // ---------------------------------------------------------------------------

  private def resourceImpl(nameExpr: Expr[String], readAsExpr: Expr[ReadAs], optionalityExpr: Expr[ParquetOptionality])(using Quotes): Expr[Any] =
    build(ParquetSource.Resource(nameExpr.valueOrAbort), readAsExpr, optionalityExpr)

  private def absolutePathImpl(pathExpr: Expr[String], readAsExpr: Expr[ReadAs], optionalityExpr: Expr[ParquetOptionality])(using Quotes): Expr[Any] =
    build(ParquetSource.AbsolutePath(pathExpr.valueOrAbort), readAsExpr, optionalityExpr)

  private def pwdImpl(pathExpr: Expr[String], readAsExpr: Expr[ReadAs], optionalityExpr: Expr[ParquetOptionality])(using Quotes): Expr[Any] =
    build(ParquetSource.RelativePath(pathExpr.valueOrAbort), readAsExpr, optionalityExpr)

  private def build(source: ParquetSource, readAsExpr: Expr[ReadAs], optionalityExpr: Expr[ParquetOptionality])(using q: Quotes): Expr[Any] =
    import q.reflect.*

    val schema =
      try ParquetSchema.read(source)
      catch
        case ex: UnsupportedParquetSchemaException => report.throwError(ex.getMessage)
        case ex: Exception                         => report.throwError(s"Could not read the parquet schema of $source: ${ex.getMessage}")
    val fields =
      try ParquetSchema.fields(schema)
      catch case ex: UnsupportedParquetSchemaException => report.throwError(ex.getMessage)

    val headers = fields.map(fieldName).toList

    if headers.distinct.size != headers.size then report.warning(s"Duplicate column names in parquet schema: ${headers.diff(headers.distinct).distinct.mkString(", ")}")
    end if

    val headerTupleExpr = Expr.ofTupleFromSeq(headers.map(Expr(_)))
    val sourceExpr = sourceToExpr(source)

    val readAs = readAsExpr.value.getOrElse {
      report.throwError("`readAs` must be a compile-time constant. Parquet supports ReadAs.Rows and ReadAs.Columns.")
    }
    val optionality = optionalityExpr.value.getOrElse {
      report.throwError("`optionality` must be a compile-time constant.")
    }

    headerTupleExpr match
      case '{ $tup: hdrs } =>
        readAs match
          case ReadAs.Rows =>
            val valueTypeRepr = tupleTypeRepr(fields, optionality)
            val headersExpr = Expr(headers)
            valueTypeRepr.asType match
              case '[v] =>
                '{ new ParquetIterator[hdrs & Tuple, v & Tuple]($headersExpr, () => new ParquetColumnSource($sourceExpr, $optionalityExpr)) }
            end match

          case ReadAs.Columns =>
            if ParquetSchema.isNested(fields) then report.throwError("ReadAs.Columns does not yet support nested parquet fields. Use ReadAs.Rows for nested schemas.")
            end if
            val cols =
              ParquetSchema.columns(schema)
            val arrayTypeRepr = cols.foldRight(TypeRepr.of[EmptyTuple]) { (col, acc) =>
              val field = ParquetField.Primitive(col.name, col.scalaType, col.nullable, 0, 0)
              TypeRepr.of[*:].appliedTo(List(TypeRepr.of[Array].appliedTo(typeReprOf(field, optionality)), acc))
            }
            arrayTypeRepr.asType match
              case '[arrs] =>
                '{ NamedTuple.build[hdrs & Tuple]()(ParquetColumns.readAll[arrs & Tuple]($sourceExpr)) }
            end match

          case other =>
            report.throwError(s"Parquet supports ReadAs.Rows and ReadAs.Columns; $other is not available.")
        end match

      case _ =>
        report.throwError("Internal error: could not build the column-name tuple type from the parquet schema.")
    end match
  end build

  private def tupleTypeRepr(fields: Vector[ParquetField], optionality: ParquetOptionality)(using q: Quotes): q.reflect.TypeRepr =
    import q.reflect.*

    fields.foldRight(TypeRepr.of[EmptyTuple]) { (field, acc) =>
      TypeRepr.of[*:].appliedTo(List(typeReprOf(field, optionality), acc))
    }
  end tupleTypeRepr

  private def namesTypeRepr(fields: Vector[ParquetField])(using q: Quotes): q.reflect.TypeRepr =
    import q.reflect.*

    fields.foldRight(TypeRepr.of[EmptyTuple]) { (field, acc) =>
      TypeRepr.of[*:].appliedTo(List(ConstantType(StringConstant(fieldName(field))), acc))
    }
  end namesTypeRepr

  private def typeReprOf(field: ParquetField, optionality: ParquetOptionality)(using q: Quotes): q.reflect.TypeRepr =
    import q.reflect.*
    import ParquetScalaType.*

    val (base, nullable): (TypeRepr, Boolean) = field match
      case ParquetField.Primitive(_, scalaType, nullable, _, _) =>
        val primitive = scalaType match
          case IntT     => TypeRepr.of[Int]
          case LongT    => TypeRepr.of[Long]
          case FloatT   => TypeRepr.of[Float]
          case DoubleT  => TypeRepr.of[Double]
          case BooleanT => TypeRepr.of[Boolean]
          case StringT  => TypeRepr.of[String]
          case BinaryT  => TypeRepr.of[Array[Byte]]
          case DateT    => TypeRepr.of[java.time.LocalDate]
          case InstantT => TypeRepr.of[java.time.Instant]
          case DecimalT => TypeRepr.of[BigDecimal]
          case UuidT    => TypeRepr.of[java.util.UUID]
        (primitive, nullable)
      case ParquetField.Struct(_, children, nullable, _) =>
        val namedTuple = namedTupleTypeRepr(namesTypeRepr(children), tupleTypeRepr(children, optionality))
        (namedTuple, nullable)

    if nullable && optionality == ParquetOptionality.FromSchema then TypeRepr.of[Option].appliedTo(base) else base
    end if
  end typeReprOf

  private def namedTupleTypeRepr(using q: Quotes)(names: q.reflect.TypeRepr, values: q.reflect.TypeRepr): q.reflect.TypeRepr =
    import q.reflect.*

    names.asType match
      case '[n] =>
        values.asType match
          case '[v] => TypeRepr.of[NamedTuple[n & Tuple, v & Tuple]]
    end match
  end namedTupleTypeRepr

  private def fieldName(field: ParquetField): String = field match
    case ParquetField.Primitive(name, _, _, _, _) => name
    case ParquetField.Struct(name, _, _, _)       => name

  private def sourceToExpr(source: ParquetSource)(using Quotes): Expr[ParquetSource] = source match
    case ParquetSource.Resource(name)     => '{ ParquetSource.Resource(${ Expr(name) }) }
    case ParquetSource.AbsolutePath(path) => '{ ParquetSource.AbsolutePath(${ Expr(path) }) }
    case ParquetSource.RelativePath(path) => '{ ParquetSource.RelativePath(${ Expr(path) }) }

end Parquet
