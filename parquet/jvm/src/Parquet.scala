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
  * A field declared `optional` is surfaced as `Option[T]`; a `required` field is surfaced as `T`.
  */

object Parquet:

  /** Read a parquet file from the java resources, inferring its schema at compile time.
    *
    * {{{
    * val titanic = Parquet.resource("titanic.parquet")
    * }}}
    */
  transparent inline def resource(inline name: String): Any = ${ resourceImpl('name, '{ ReadAs.Rows }) }

  /** Read a parquet file from the java resources as rows, columns, or vectors.
    *
    * {{{
    * val cols = Parquet.resource("titanic.parquet", ReadAs.Columns)
    * // cols: NamedTuple[("PassengerId", ...), (Array[Option[Long]], ...)]
    * cols.Age.flatten.sum
    *
    * val vecs = Parquet.resource("titanic.parquet", ReadAs.Vectors)
    * // vecs: Iterator[NamedTuple[("PassengerId", ...), (ParquetVector[Long], ...)]]
    * // one ParquetVector-tuple per row group - lazy across groups, vectorized within one
    * for batch <- vecs do batch.Age.values.sum
    * }}}
    */
  transparent inline def resource(inline name: String, inline readAs: ReadAs): Any = ${ resourceImpl('name, 'readAs) }

  /** Read a parquet file from an absolute filesystem path, inferring its schema at compile time. */
  transparent inline def absolutePath(inline path: String): Any = ${ absolutePathImpl('path, '{ ReadAs.Rows }) }

  /** Read a parquet file from an absolute filesystem path, as rows or as columns. */
  transparent inline def absolutePath(inline path: String, inline readAs: ReadAs): Any = ${ absolutePathImpl('path, 'readAs) }

  /** Read a parquet file relative to the working directory, inferring its schema at compile time.
    *
    * Note that the *compiler's* working directory is used to find the schema and the *runtime* working directory is used to find the data — these are frequently not the same
    * directory. Prefer [[resource]] or [[absolutePath]] unless you know they are.
    */
  transparent inline def pwd(inline path: String): Any = ${ pwdImpl('path, '{ ReadAs.Rows }) }

  /** Read a parquet file relative to the working directory, as rows or as columns. */
  transparent inline def pwd(inline path: String, inline readAs: ReadAs): Any = ${ pwdImpl('path, 'readAs) }

  /** Read every `.parquet` file directly inside a resource directory, in file-name order, as one `Iterator` of rows.
    *
    * Every file is assumed to share one schema - the macro reads the first file's footer for the static type, then checks every other file agrees, failing to compile if any
    * disagree.
    *
    * {{{
    * val sales = Parquet.resourceDir("sales")
    * // sales: Iterator[NamedTuple[("order_id", "region", "amount", "quantity"), (Long, String, Double, Option[Int])]]
    * // one file after another, lazily - only one file's current row group is ever resident
    * }}}
    */
  transparent inline def resourceDir(inline dirName: String): Any = ${ resourceDirImpl('dirName) }

  /** Read every `.parquet` file directly inside a directory on the local filesystem, in file-name order, as one `Iterator` of rows. See [[resourceDir]]. */
  transparent inline def absolutePathDir(inline path: String): Any = ${ absolutePathDirImpl('path) }

  /** Read every `.parquet` file directly inside a directory relative to the working directory, in file-name order, as one `Iterator` of rows. See [[resourceDir]] and the caveat on
    * [[pwd]] about compiler vs runtime working directories.
    */
  transparent inline def pwdDir(inline path: String): Any = ${ pwdDirImpl('path) }

  /** The parquet footer schema, as a string. Handy when a schema is rejected and you want to see why. */
  def schemaOf(source: ParquetSource): String = ParquetSchema.read(source).toString

  // ---------------------------------------------------------------------------
  // Macro implementations — these run at compile time.
  // ---------------------------------------------------------------------------

  private def resourceImpl(nameExpr: Expr[String], readAsExpr: Expr[ReadAs])(using Quotes): Expr[Any] =
    build(ParquetSource.Resource(nameExpr.valueOrAbort), readAsExpr)

  private def absolutePathImpl(pathExpr: Expr[String], readAsExpr: Expr[ReadAs])(using Quotes): Expr[Any] =
    build(ParquetSource.AbsolutePath(pathExpr.valueOrAbort), readAsExpr)

  private def pwdImpl(pathExpr: Expr[String], readAsExpr: Expr[ReadAs])(using Quotes): Expr[Any] =
    build(ParquetSource.RelativePath(pathExpr.valueOrAbort), readAsExpr)

  private def resourceDirImpl(nameExpr: Expr[String])(using Quotes): Expr[Any] =
    buildDir(ParquetSource.Resource(nameExpr.valueOrAbort))

  private def absolutePathDirImpl(pathExpr: Expr[String])(using Quotes): Expr[Any] =
    buildDir(ParquetSource.AbsolutePath(pathExpr.valueOrAbort))

  private def pwdDirImpl(pathExpr: Expr[String])(using Quotes): Expr[Any] =
    buildDir(ParquetSource.RelativePath(pathExpr.valueOrAbort))

  private def buildDir(dirSource: ParquetSource)(using q: Quotes): Expr[Any] =
    import q.reflect.*

    val files =
      try dirSource.listParquetFiles
      catch case ex: Exception => report.throwError(s"Could not list parquet files in $dirSource: ${ex.getMessage}")

    if files.isEmpty then report.throwError(s"No .parquet files found in $dirSource.")
    end if

    val colsByFile = files.map { file =>
      val cols =
        try ParquetSchema.columns(ParquetSchema.read(file))
        catch
          case ex: UnsupportedParquetSchemaException => report.throwError(s"${ex.getMessage} (in $file)")
          case ex: Exception                         => report.throwError(s"Could not read the parquet schema of $file: ${ex.getMessage}")
      file -> cols
    }

    val (firstFile, cols) = colsByFile.head
    for (file, otherCols) <- colsByFile.tail do
      if otherCols != cols then
        report.throwError(
          s"Parquet.resourceDir assumes every file in a directory shares one schema, but '$file' does not match '$firstFile':\n  $firstFile: $cols\n  $file: $otherCols"
        )
      end if
    end for

    val headers = cols.map(_.name).toList

    if headers.distinct.size != headers.size then report.warning(s"Duplicate column names in parquet schema: ${headers.diff(headers.distinct).distinct.mkString(", ")}")
    end if

    val headerTupleExpr = Expr.ofTupleFromSeq(headers.map(Expr(_)))
    val dirSourceExpr = sourceToExpr(dirSource)

    headerTupleExpr match
      case '{ $tup: hdrs } =>
        val valueTypeRepr = cols.foldRight(TypeRepr.of[EmptyTuple]) { (col, acc) =>
          TypeRepr.of[*:].appliedTo(List(typeReprOf(col), acc))
        }
        val headersExpr = Expr(headers)
        valueTypeRepr.asType match
          case '[v] =>
            '{ new ParquetDirIterator[hdrs & Tuple, v & Tuple]($headersExpr, () => $dirSourceExpr) }
        end match

      case _ =>
        report.throwError("Internal error: could not build the column-name tuple type from the parquet schema.")
    end match
  end buildDir

  private def build(source: ParquetSource, readAsExpr: Expr[ReadAs])(using q: Quotes): Expr[Any] =
    import q.reflect.*

    val cols =
      try ParquetSchema.columns(ParquetSchema.read(source))
      catch
        case ex: UnsupportedParquetSchemaException => report.throwError(ex.getMessage)
        case ex: Exception                         => report.throwError(s"Could not read the parquet schema of $source: ${ex.getMessage}")

    val headers = cols.map(_.name).toList

    if headers.distinct.size != headers.size then report.warning(s"Duplicate column names in parquet schema: ${headers.diff(headers.distinct).distinct.mkString(", ")}")
    end if

    val headerTupleExpr = Expr.ofTupleFromSeq(headers.map(Expr(_)))
    val sourceExpr = sourceToExpr(source)

    val readAs = readAsExpr.value.getOrElse {
      report.throwError("`readAs` must be a compile-time constant. Parquet supports ReadAs.Rows, ReadAs.Columns and ReadAs.Vectors.")
    }

    headerTupleExpr match
      case '{ $tup: hdrs } =>
        readAs match
          case ReadAs.Rows =>
            val valueTypeRepr = cols.foldRight(TypeRepr.of[EmptyTuple]) { (col, acc) =>
              TypeRepr.of[*:].appliedTo(List(typeReprOf(col), acc))
            }
            val headersExpr = Expr(headers)
            valueTypeRepr.asType match
              case '[v] =>
                '{ new ParquetIterator[hdrs & Tuple, v & Tuple]($headersExpr, () => new ParquetColumnSource($sourceExpr)) }
            end match

          case ReadAs.Columns =>
            val arrayTypeRepr = cols.foldRight(TypeRepr.of[EmptyTuple]) { (col, acc) =>
              TypeRepr.of[*:].appliedTo(List(TypeRepr.of[Array].appliedTo(typeReprOf(col)), acc))
            }
            arrayTypeRepr.asType match
              case '[arrs] =>
                '{ NamedTuple.build[hdrs & Tuple]()(ParquetColumns.readAll[arrs & Tuple]($sourceExpr)) }
            end match

          case ReadAs.Vectors =>
            val rowTypeRepr = cols.foldRight(TypeRepr.of[EmptyTuple]) { (col, acc) =>
              TypeRepr.of[*:].appliedTo(List(typeReprOf(col), acc))
            }
            val vectorTypeRepr = cols.foldRight(TypeRepr.of[EmptyTuple]) { (col, acc) =>
              TypeRepr.of[*:].appliedTo(List(TypeRepr.of[ParquetVector].appliedTo(baseTypeReprOf(col)), acc))
            }
            val headersExpr = Expr(headers)
            (rowTypeRepr.asType, vectorTypeRepr.asType) match
              case ('[v], '[ve]) =>
                '{ new ParquetVectorIterator[hdrs & Tuple, v & Tuple, ve & Tuple]($headersExpr, () => $sourceExpr) }
              case _ =>
                report.throwError("Internal error: could not build the vector tuple type from the parquet schema.")
            end match

          case other =>
            report.throwError(s"Parquet supports ReadAs.Rows, ReadAs.Columns and ReadAs.Vectors; $other is not available.")
        end match

      case _ =>
        report.throwError("Internal error: could not build the column-name tuple type from the parquet schema.")
    end match
  end build

  private def baseTypeReprOf(col: ParquetColumnMeta)(using q: Quotes): q.reflect.TypeRepr =
    import q.reflect.*
    import ParquetScalaType.*

    col.scalaType match
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
  end baseTypeReprOf

  private def typeReprOf(col: ParquetColumnMeta)(using q: Quotes): q.reflect.TypeRepr =
    import q.reflect.*
    val base = baseTypeReprOf(col)
    if col.nullable then TypeRepr.of[Option].appliedTo(base) else base
    end if
  end typeReprOf

  private def sourceToExpr(source: ParquetSource)(using Quotes): Expr[ParquetSource] = source match
    case ParquetSource.Resource(name)     => '{ ParquetSource.Resource(${ Expr(name) }) }
    case ParquetSource.AbsolutePath(path) => '{ ParquetSource.AbsolutePath(${ Expr(path) }) }
    case ParquetSource.RelativePath(path) => '{ ParquetSource.RelativePath(${ Expr(path) }) }

end Parquet
