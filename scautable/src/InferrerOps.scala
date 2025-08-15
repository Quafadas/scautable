package io.github.quafadas.scautable

import io.github.quafadas.scautable.CSVParser.*
import io.github.quafadas.scautable.RowDecoder.*
import io.github.quafadas.table.TypeInferrer

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

  def inferrerFirstN(using Quotes)(rows: Iterator[String], n: Int) =
    import quotes.reflect.*

    if !rows.hasNext then
      throw new IllegalArgumentException("CSV must contain at least one data line for type inference.")

    if n <= 0 then
      throw new IllegalArgumentException("N must be positive for FirstN type inference.")

    // Collect up to n rows for analysis
    val sampleRows = rows.take(n).toList
    
    if sampleRows.isEmpty then
      throw new IllegalArgumentException("CSV must contain at least one data line for type inference.")

    // Parse all sample rows
    val parsedRows = sampleRows.map(CSVParser.parseLine(_))
    
    // Ensure all rows have the same number of columns
    val columnCount = parsedRows.head.length
    if !parsedRows.forall(_.length == columnCount) then
      throw new IllegalArgumentException("All rows must have the same number of columns.")

    // For each column position, collect all values and infer the most appropriate type
    val columnTypes = (0 until columnCount).map { colIdx =>
      val columnValues = parsedRows.map(_(colIdx))
      inferBestTypeFromSamples(columnValues)
    }.toList

    val tupleType: TypeRepr = columnTypes.foldRight(TypeRepr.of[EmptyTuple]) { (tpe, acc) =>
      TypeRepr.of[*:].appliedTo(List(tpe, acc))
    }

    tupleType

  def inferBestTypeFromSamples(using Quotes)(samples: List[String]): quotes.reflect.TypeRepr =
    import quotes.reflect.*

    // Track what types are valid for ALL samples
    var allCanBeInt = true
    var allCanBeLong = true  
    var allCanBeDouble = true
    var allCanBeBoolean = true

    samples.foreach { sample =>
      if sample.toIntOption.isEmpty then allCanBeInt = false
      if sample.toLongOption.isEmpty then allCanBeLong = false
      if sample.toDoubleOption.isEmpty then allCanBeDouble = false
      if sample.toBooleanOption.isEmpty then allCanBeBoolean = false
    }

    // Return the most specific type that works for all samples
    if allCanBeBoolean then TypeRepr.of[Boolean]
    else if allCanBeInt then TypeRepr.of[Int] 
    else if allCanBeLong then TypeRepr.of[Long]
    else if allCanBeDouble then TypeRepr.of[Double]
    else TypeRepr.of[String]
