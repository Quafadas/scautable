package io.github.quafadas.scautable.parquet

import scala.quoted.*

/** Controls how parquet fields declared `optional` are represented in inferred Scala types. */
enum ParquetOptionality:
  /** Follow the parquet schema, representing `optional` fields as `Option[T]`. */
  case FromSchema

  /** Infer every field as its concrete type `T`. A missing value throws [[ParquetDecodeException]] at runtime. */
  case NoOptions
end ParquetOptionality

object ParquetOptionality:
  given FromExpr[ParquetOptionality] with
    def unapply(expr: Expr[ParquetOptionality])(using Quotes): Option[ParquetOptionality] =
      expr match
        case '{ ParquetOptionality.FromSchema } => Some(ParquetOptionality.FromSchema)
        case '{ ParquetOptionality.NoOptions }  => Some(ParquetOptionality.NoOptions)
        case _                                  => None
      end match
    end unapply
  end given
end ParquetOptionality
