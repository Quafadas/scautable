package io.github.quafadas.scautable

import scala.quoted.*
import scala.io.Source
import scala.annotation.experimental
import NamedTuple.withNames
import scala.NamedTuple

@experimental
object CSV:
  transparent inline def readCsvAsNamedTupleType[T](inline path: String) = ${ readCsvAsNamedTupleTypeImpl2('path) }

  def readCsvAsNamedTupleTypeImpl2(pathExpr: Expr[String])(using Quotes) = {

    import quotes.reflect._

    def listToTuple(list: List[String]): Tuple = list match {
      case Nil    => EmptyTuple
      case h :: t => h *: listToTuple(t)
    }

    import quotes.reflect.*

    val path = pathExpr.valueOrAbort

    val source = Source.fromFile(path)
    val headerLine =
      try source.getLines().next()
      finally source.close()

    val headers     = headerLine.split(",").toList
    val headerTuple = listToTuple(headers)
    val headersExpr = Expr.ofList(headers.map(Expr(_)))
    println(s"headers: ${headers.mkString(", ")}")

    def toTypeTuple(as: List[String]): TypeRepr = as match
      case Nil    => TypeRepr.of[EmptyTuple]
      case h :: t => TypeRepr.of[Tuple].appliedTo(List(TypeRepr.of[String], toTypeTuple(t)))

    // val typeTuple = toTypeTuple(headers)
    val tupleExpr = Expr.ofTupleFromSeq(headers.map(Expr(_)))

    // val headerTup = headersToTuple(headers)

    // val nameTypes: List[TypeRepr] = headers.map(header => ConstantType(StringConstant(header)))
    val nameTypes: TypeRepr = headers.foldRight(TypeRepr.of[EmptyTuple]) { (header, acc) =>
      AppliedType(TypeRepr.of[*:], List(ConstantType(StringConstant(header)), acc))
    }
    println(s"headers: ${nameTypes.show}")
    val typeTuple = toTypeTuple(headers)

    val typ = TypeRepr.of[Tuple].appliedTo(List(nameTypes))
    // val typ2       = Type.valueOfTuple[nameTypes]
    // TypeRepr.of[Tuple]
    // Generate the NamedTuple expression
    val tupleExpr2 = Expr.ofTupleFromSeq(headers.map(Expr(_)))
    // val headersExpr2 = Expr.ofTuple(headerTuple)

    // // println(s"headers: ${typ}")

    // '{
    //   val t = $tupleExpr2
    //   NamedTuple.build[("col1", "col2")]()(headersExpr2)
    // }

    // tupleExpr2.asType match
    //   case '[t] =>
    //     val tupleExpr = Expr.ofTupleFromSeq(headers.map(Expr(_)))
    //     // val typ2      = Type.valueOfTuple[t]
    //     // val typ2       = Type.of[t & Tuple]
    //     // val typ3       = Type.of[t]
    //     // val tupleExpr2 = Type.valueOfTuple[t & Tuple]
    //     // println(tupleExpr.show)
    //     // val typ1 = TypeRepr.(one)

    //     // println("----")
    //     // println(typly)
    //     // println(tuple)

    //     '{
    //       val tuple = $tupleExpr
    //       val typly = ${ headersExpr }.map(x => s"\"$x\"").mkString("(", ", ", ")")
    //       val bah   = ${ tupleExpr }
    //       NamedTuple.build[("col1", "col2")]()(bah)
    //       // NamedTuple.build[("col1", "col2")]()(bah)
    //       bah
    //     }
    //   case _ => report.throwError(s"Could not summon Type for type: ${typeTuple.show}")
    // nameTypes.asType match {
    //   case '[t] =>
    //     if TypeRepr.of[t] <:< TypeRepr.of[Tuple] then
    //       val tupleExpr = Expr.ofTupleFromSeq(headers.map(Expr(_))).asInstanceOf[Expr[t & Tuple]]
    //       '{
    //         val tuple = $tupleExpr
    //         NamedTuple.build[t1]()(tuple)
    //       }
    //     else report.throwError(s"Type $nameTypes is not a subtype of Tuple")
    // }

    // headerTuple.withNames[nameTypes]
    // given OptionToExpr[T: Type: ToExpr]: ToExpr[NamedTuple] with
    //   def apply(opt: Option[T])(using Quotes): Expr[NamedTuple] =
    //     opt match
    //       case Some(x) => '{ Some[T](${ Expr(x) }) }
    //       case None    => '{ None }

    // val n1 = ("col1", "col2").withNames[("col1", "col2")]
    // Expr(
    //   n1
    // )
    tupleExpr2 match
      case '{ $tup: t } =>
        // val tupleExpr  = Expr.ofTupleFromSeq(headers.map(Expr(_)))
        // val tuple2Exrp = Expr(Tuple2("1", "2"))

        '{ NamedTuple.build[t & Tuple]()($tup) }
      case _ => report.throwError(s"Could not summon Type for type: ${typeTuple.show}")
  }
