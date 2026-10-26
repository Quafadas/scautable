package io.github.quafadas.scautable
import scala.quoted.*

private[scautable] object InferrerOps:

  case class ColumnTypeInfo(
      couldBeInt: Boolean = true,
      couldBeLong: Boolean = true,
      couldBeDouble: Boolean = true,
      couldBeBoolean: Boolean = true,
      seenEmpty: Boolean = false
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

      if seenEmpty then TypeRepr.of[Option].appliedTo(base) else base
      end if
    end inferMostGeneralType
  end ColumnTypeInfo

  def inferTypeReprForValue(current: ColumnTypeInfo, str: String): ColumnTypeInfo =
    if str.isEmpty then current.copy(seenEmpty = true)
    else
      current.copy(
        couldBeInt = current.couldBeInt && str.toIntOption.isDefined,
        couldBeLong = current.couldBeLong && str.toLongOption.isDefined,
        couldBeDouble = current.couldBeDouble && str.toDoubleOption.isDefined,
        couldBeBoolean = current.couldBeBoolean && (str.toBooleanOption.isDefined || str == "0" || str == "1")
      )

  def inferMostGeneralType(using Quotes)(values: Seq[String], preferIntToBoolean: Boolean): quotes.reflect.TypeRepr =
    import quotes.reflect.*

    if values.isEmpty then TypeRepr.of[String]
    else
      val initial = ColumnTypeInfo()
      val resultInfo = values.foldLeft(initial)(inferTypeReprForValue)
      resultInfo.inferMostGeneralType(preferIntToBoolean)
    end if
  end inferMostGeneralType

  def inferrer(using Quotes)(allRows: Iterator[String], preferIntToBoolean: Boolean, numRows: Int = 1, delimiter: Char = ',') =
    import quotes.reflect.*

    // Blank lines are skipped at runtime by every read mode, so they must not take part in inference either.
    val rows = allRows.filterNot(CsvRows.isBlank(_, delimiter))
    validateInput(rows, numRows)

    val sampleRows = rows.take(numRows).toList
    validateSampleRows(sampleRows)

    val parsedRows = sampleRows.map(line => CSVParser.parseLine(line, delimiter))
    validateColumnConsistency(parsedRows)

    val columns = parsedRows.transpose

    val elementTypesRepr: List[TypeRepr] = columns.map { columnValues =>
      inferMostGeneralType(columnValues, preferIntToBoolean)
    }

    val tupleType: TypeRepr = elementTypesRepr.foldRight(TypeRepr.of[EmptyTuple]) { (tpe, acc) =>
      TypeRepr.of[*:].appliedTo(List(tpe, acc))
    }

    tupleType
  end inferrer

  private def validateInput(rows: Iterator[String], numRows: Int)(using Quotes): Unit =
    if !rows.hasNext then
      throw new IllegalArgumentException(
        "CSV must contain at least one data line for type inference."
      )
    end if

    if numRows <= 0 then
      throw new IllegalArgumentException(
        "N must be positive for FirstN type inference."
      )
    end if
  end validateInput

  private def validateSampleRows(sampleRows: List[String])(using Quotes): Unit =
    if sampleRows.isEmpty then
      throw new IllegalArgumentException(
        "No rows available for type inference."
      )

  /** Aborts compilation when the header and the data rows disagree on the number of columns. Every compile-time CSV reader runs this before choosing a type, whichever
    * `TypeInferrer` is in play - including those that never read the data themselves. Returns the rows, unconsumed.
    */
  def validateHeaderWidth(using Quotes)(headers: Seq[String], rows: Iterator[String], delimiter: Char): Iterator[String] =
    HeaderOptionsProcessing.validateDataWidth(headers, rows, delimiter) match
      case Left(msg)      => quotes.reflect.report.errorAndAbort(msg)
      case Right(checked) => checked
  end validateHeaderWidth

  /** Aborts compilation when `TypeInferrer.FromTuple` declares a different number of column types than the header has names. */
  def validateTupleArity(using Quotes)(headers: Seq[String], tupleType: quotes.reflect.TypeRepr): Unit =
    import quotes.reflect.*
    tupleArity(tupleType).foreach { arity =>
      if arity != headers.length then
        val names = headers.map(h => if h.isEmpty then "<empty>" else h).mkString(", ")
        report.errorAndAbort(
          s"TypeInferrer.FromTuple declares $arity column types but the header has ${headers.length} columns ($names)."
        )
    }
  end validateTupleArity

  private def tupleArity(using Quotes)(tpe: quotes.reflect.TypeRepr): Option[Int] =
    import quotes.reflect.*
    val consSym = TypeRepr.of[*:].typeSymbol
    tpe.dealias match
      case t if t =:= TypeRepr.of[EmptyTuple]                               => Some(0)
      case AppliedType(tycon, List(_, tail)) if tycon.typeSymbol == consSym => tupleArity(tail).map(_ + 1)
      case AppliedType(tycon, args) if defn.isTupleClass(tycon.typeSymbol)  => Some(args.length)
      case _                                                                => None
    end match
  end tupleArity

  private def validateColumnConsistency(parsedRows: List[List[String]])(using Quotes): Unit =
    val columnCount = parsedRows.head.length
    if !parsedRows.forall(_.length == columnCount) then
      throw new IllegalArgumentException(
        "All rows must have the same number of columns."
      )
    end if
  end validateColumnConsistency
end InferrerOps
