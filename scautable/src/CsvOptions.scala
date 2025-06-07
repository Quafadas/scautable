package io.github.quafadas.scautable

import scala.quoted.*

given FromExpr[TypeInferenceStrategy] with
  def unapply(expr: Expr[TypeInferenceStrategy])(using
      Quotes
  ): Option[TypeInferenceStrategy] =
    expr match
      case '{ TypeInferenceStrategy.FirstRow } =>
        Some(TypeInferenceStrategy.FirstRow)
      case '{ TypeInferenceStrategy.AutoType } =>
        Some(TypeInferenceStrategy.AutoType)
      case '{ TypeInferenceStrategy.StringsOnly } =>
        Some(TypeInferenceStrategy.StringsOnly)
      case _ => None
end given

given ToExpr[TypeInferenceStrategy] with
  def apply(ts: TypeInferenceStrategy)(using
      Quotes
  ): Expr[TypeInferenceStrategy] = ts match
    case TypeInferenceStrategy.FirstRow => '{ TypeInferenceStrategy.FirstRow }
    case TypeInferenceStrategy.AutoType => '{ TypeInferenceStrategy.AutoType }
    case TypeInferenceStrategy.StringsOnly =>
      '{ TypeInferenceStrategy.StringsOnly }
end given

enum TypeInferenceStrategy:
  case FirstRow, AutoType, StringsOnly
end TypeInferenceStrategy

given ToExpr[CsvReadOptions] with
  def apply(opt: CsvReadOptions)(using Quotes): Expr[CsvReadOptions] =
    val delimiterExpr = Expr(opt.delimiter)
    val typeInferenceStrategyExpr = Expr(opt.typeInferenceStrategy)
    '{ CsvReadOptions($delimiterExpr, $typeInferenceStrategyExpr) }
  end apply
end given

given FromExpr[CsvReadOptions] with
  def unapply(expr: Expr[CsvReadOptions])(using
      Quotes
  ): Option[CsvReadOptions] =
    import quotes.reflect.*
    println("FromExpr unapply for CsvReadOptions called")
    expr match
      case '{ CsvReadOptions(${ Expr(d) }, ${ Expr(t) }) } =>
        Some(CsvReadOptions(d, t))
      case _ =>
        println("match Failed for CsvReadOptions")
        None
    end match
  end unapply
end given

case class CsvReadOptions(
    delimiter: Char,
    typeInferenceStrategy: TypeInferenceStrategy
)
