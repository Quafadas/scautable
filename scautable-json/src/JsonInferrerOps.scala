package io.github.quafadas.scautable.json

import scala.quoted.*
import ujson.*

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

  def inferTypeReprForValue(current: ColumnTypeInfo, value: Value): ColumnTypeInfo =
    value match
      case Null   => current.copy(seenNull = true)
      case Num(n) =>
        val isInt = n.isWhole && n >= Int.MinValue && n <= Int.MaxValue
        val isLong = n.isWhole && n >= Long.MinValue && n <= Long.MaxValue
        current.copy(
          couldBeInt = current.couldBeInt && isInt,
          couldBeLong = current.couldBeLong && isLong,
          couldBeDouble = current.couldBeDouble,
          couldBeBoolean = false
        )
      case Bool(_) =>
        current.copy(
          couldBeInt = false,
          couldBeLong = false,
          couldBeDouble = false,
          couldBeBoolean = current.couldBeBoolean
        )
      case Str(s) =>
        current.copy(
          couldBeInt = current.couldBeInt && s.toIntOption.isDefined,
          couldBeLong = current.couldBeLong && s.toLongOption.isDefined,
          couldBeDouble = current.couldBeDouble && s.toDoubleOption.isDefined,
          couldBeBoolean = current.couldBeBoolean && (s.toBooleanOption.isDefined || s == "0" || s == "1")
        )
      case _ =>
        // For arrays, objects, or other complex types, fall back to String
        current.copy(
          couldBeInt = false,
          couldBeLong = false,
          couldBeDouble = false,
          couldBeBoolean = false
        )
  end inferTypeReprForValue

  def inferMostGeneralType(using Quotes)(values: Seq[Value], preferIntToBoolean: Boolean): quotes.reflect.TypeRepr =
    import quotes.reflect.*

    if values.isEmpty then TypeRepr.of[String]
    else
      val initial = ColumnTypeInfo()
      val resultInfo = values.foldLeft(initial)(inferTypeReprForValue)
      resultInfo.inferMostGeneralType(preferIntToBoolean)
    end if
  end inferMostGeneralType

  def inferrer(using Quotes)(arr: Arr, preferIntToBoolean: Boolean, numRows: Int = Int.MaxValue): quotes.reflect.TypeRepr =
    import quotes.reflect.*

    if arr.value.isEmpty then report.throwError("JSON array must contain at least one object for type inference.")
    end if

    // Take the specified number of rows for inference
    val sampleRows = arr.value.take(numRows)

    // Validate all elements are objects
    sampleRows.foreach {
      case _: Obj => // OK
      case other  =>
        report.throwError(s"JSON array must contain only objects. Found: ${other.getClass.getSimpleName}")
    }

    val objects = sampleRows.collect { case obj: Obj => obj }

    // Extract all unique headers from all objects
    val allHeaders: Set[String] = objects.flatMap(_.value.keys).toSet

    // For each header, collect all values across all objects
    val headerToValues: Map[String, Seq[Value]] = allHeaders.map { header =>
      (header, objects.map(obj => obj.value.getOrElse(header, Null)).toSeq)
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

  def extractHeaders(using Quotes)(arr: Arr): Seq[String] =
    import quotes.reflect.*

    if arr.value.isEmpty then report.throwError("JSON array must contain at least one object to extract headers.")
    end if

    // Collect all keys from all objects and merge them
    val allHeaders: Set[String] = arr.value
      .collect { case obj: Obj =>
        obj.value.keys.toSet
      }
      .reduce(_ ++ _)

    allHeaders.toSeq.sorted
  end extractHeaders

end JsonInferrerOps
