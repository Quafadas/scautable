package io.github.quafadas.scautable.json

import scala.quoted.*
import StreamingJsonParser.*

private[json] object JsonInferrerOps:

  case class ColumnTypeInfo(
      couldBeInt: Boolean = true,
      couldBeLong: Boolean = true,
      couldBeDouble: Boolean = true,
      couldBeBoolean: Boolean = true,
      seenNull: Boolean = false
  ):
    def inferMostGeneralType(preferIntToBoolean: Boolean)(using Quotes): quotes.reflect.TypeRepr =
      import quotes.reflect.*
      val base =
        if preferIntToBoolean then
          if couldBeInt then TypeRepr.of[Int]
          else if couldBeBoolean then TypeRepr.of[Boolean]
          else if couldBeLong then TypeRepr.of[Long]
          else if couldBeDouble then TypeRepr.of[Double]
          else TypeRepr.of[String]
        else if couldBeBoolean then TypeRepr.of[Boolean]
        else if couldBeInt then TypeRepr.of[Int]
        else if couldBeLong then TypeRepr.of[Long]
        else if couldBeDouble then TypeRepr.of[Double]
        else TypeRepr.of[String]

      if seenNull then TypeRepr.of[Option].appliedTo(base) else base
      end if
    end inferMostGeneralType
  end ColumnTypeInfo

  inline def inferTypeReprForValue(current: ColumnTypeInfo, value: JsonValue): ColumnTypeInfo =
    value match
      case JsonNull      => current.copy(seenNull = true)
      case JsonNumber(n) =>
        val isInt = n.isWhole && n >= Int.MinValue && n <= Int.MaxValue
        val isLong = n.isWhole && n >= Long.MinValue && n <= Long.MaxValue
        current.copy(
          couldBeInt = current.couldBeInt && isInt,
          couldBeLong = current.couldBeLong && isLong,
          couldBeDouble = current.couldBeDouble,
          couldBeBoolean = false
        )
      case JsonBool(_) =>
        current.copy(
          couldBeInt = false,
          couldBeLong = false,
          couldBeDouble = false,
          couldBeBoolean = current.couldBeBoolean
        )
      case JsonString(s) =>
        current.copy(
          couldBeInt = current.couldBeInt && s.toIntOption.isDefined,
          couldBeLong = current.couldBeLong && s.toLongOption.isDefined,
          couldBeDouble = current.couldBeDouble && s.toDoubleOption.isDefined,
          couldBeBoolean = current.couldBeBoolean && (s.toBooleanOption.isDefined || s == "0" || s == "1")
        )
      case _ =>
        // For complex types, fall back to String
        current.copy(
          couldBeInt = false,
          couldBeLong = false,
          couldBeDouble = false,
          couldBeBoolean = false
        )
  end inferTypeReprForValue

  def inferMostGeneralType(using Quotes)(values: Seq[JsonValue], preferIntToBoolean: Boolean): quotes.reflect.TypeRepr =
    import quotes.reflect.*

    if values.isEmpty then TypeRepr.of[String]
    else
      val initial = ColumnTypeInfo()
      val resultInfo = values.foldLeft(initial)(inferTypeReprForValue)
      resultInfo.inferMostGeneralType(preferIntToBoolean)
    end if
  end inferMostGeneralType

  def inferrer(using Quotes)(objects: Iterator[JsonObject], preferIntToBoolean: Boolean, numRows: Int = Int.MaxValue): quotes.reflect.TypeRepr =
    import quotes.reflect.*

    val sampleObjects = objects.take(numRows).toList

    if sampleObjects.isEmpty then report.throwError("JSON array must contain at least one object for type inference.")
    end if

    // Extract all unique headers from all objects
    val allHeaders: Set[String] = sampleObjects.flatMap(_.fields.keys).toSet

    // For each header, collect all values across all objects
    val headerToValues: Map[String, Seq[JsonValue]] = allHeaders.map { header =>
      (header, sampleObjects.map(obj => obj.fields.getOrElse(header, JsonNull)))
    }.toMap

    // Infer type for each header
    val headerTypes: Seq[(String, TypeRepr)] = allHeaders.toSeq.sorted.map { header =>
      val values = headerToValues(header)
      val inferredType = inferMostGeneralType(values, preferIntToBoolean)
      (header, inferredType)
    }

    // Build tuple type from inferred types
    val tupleType: TypeRepr = headerTypes.map(_._2).foldRight(TypeRepr.of[EmptyTuple]) { (tpe, acc) =>
      TypeRepr.of[*:].appliedTo(List(tpe, acc))
    }

    tupleType
  end inferrer

  def extractHeaders(using Quotes)(objects: Iterator[JsonObject]): Seq[String] =
    import quotes.reflect.*

    val firstObjects = objects.take(100).toList // Sample first 100 to get headers
    if firstObjects.isEmpty then report.throwError("JSON array must contain at least one object to extract headers.")
    end if

    // Collect all keys from all sampled objects and merge them
    val allHeaders: Set[String] = firstObjects.flatMap(_.fields.keys).toSet
    allHeaders.toSeq.sorted
  end extractHeaders

end JsonInferrerOps
