package io.github.quafadas.scautable

import io.github.quafadas.scautable.CSVParser.*
import io.github.quafadas.scautable.RowDecoder.*

import scala.quoted.*

object InferrerOps:

  def inferTypeRepr(using Quotes)(str: String): quotes.reflect.TypeRepr =
    import quotes.reflect.*

    if str.toIntOption.isDefined then TypeRepr.of[Int]
    else if str.toLongOption.isDefined then TypeRepr.of[Long]
    else if str.toDoubleOption.isDefined then TypeRepr.of[Double]
    else if str.toBooleanOption.isDefined then TypeRepr.of[Boolean]
    else TypeRepr.of[String]

  def inferTypeAsType(using Quotes)(str: String): Type[?] =
    inferTypeRepr(str).asType

  def inferrer(using Quotes)(rows: Iterator[String]) =
    import quotes.reflect.*

    if !rows.hasNext then
      throw new IllegalArgumentException("CSV must contain at least one data line for type inference.")

    val line = rows.next()
    val rowParsed = CSVParser.parseLine(line)

    val elementTypesRepr: List[TypeRepr] = rowParsed.map(inferTypeRepr).toList

    val tupleType: TypeRepr = elementTypesRepr.foldRight(TypeRepr.of[EmptyTuple]) { (tpe, acc) =>
      TypeRepr.of[*:].appliedTo(List(tpe, acc))
    }

    tupleType
