package io.github.quafadas.scautable.parquet

/** Reconstructs logical struct fields from parquet's physical leaf columns. */
private[scautable] object ParquetStructAssembler:

  def assembleColumns(
      fields: Vector[ParquetField],
      leaves: Array[ParquetLeafColumn],
      rowCount: Int,
      optionality: ParquetOptionality
  ): Array[Array[Any]] =
    val columns = Array.ofDim[Any](fields.size, rowCount)

    var fieldIndex = 0
    while fieldIndex < fields.size do
      var rowIndex = 0
      while rowIndex < rowCount do
        columns(fieldIndex)(rowIndex) = assembleTopLevel(fields(fieldIndex), leaves, rowIndex, optionality)
        rowIndex += 1
      end while
      fieldIndex += 1
    end while

    columns
  end assembleColumns

  private def assembleTopLevel(
      field: ParquetField,
      leaves: Array[ParquetLeafColumn],
      rowIndex: Int,
      optionality: ParquetOptionality
  ): Any =
    field match
      case primitive: ParquetField.Primitive => primitiveValue(primitive, leaves, rowIndex, Vector.empty, optionality, topLevel = true)
      case struct: ParquetField.Struct       => structValue(struct, leaves, rowIndex, Vector.empty, optionality)
  end assembleTopLevel

  private def assembleNested(
      field: ParquetField,
      leaves: Array[ParquetLeafColumn],
      rowIndex: Int,
      parentPath: Vector[String],
      optionality: ParquetOptionality
  ): Any =
    field match
      case primitive: ParquetField.Primitive => primitiveValue(primitive, leaves, rowIndex, parentPath, optionality, topLevel = false)
      case struct: ParquetField.Struct       => structValue(struct, leaves, rowIndex, parentPath, optionality)
  end assembleNested

  private def primitiveValue(
      field: ParquetField.Primitive,
      leaves: Array[ParquetLeafColumn],
      rowIndex: Int,
      parentPath: Vector[String],
      optionality: ParquetOptionality,
      topLevel: Boolean
  ): Any =
    val path = parentPath :+ field.name
    val leaf = leaves(field.leafIndex)
    val present = leaf.definitionLevels(rowIndex) >= field.presentDefinitionLevel

    if present then leaf.values(rowIndex)
    else if field.nullable && optionality == ParquetOptionality.FromSchema then null
    else if topLevel then null
    else missing(path)
    end if
  end primitiveValue

  private def structValue(
      field: ParquetField.Struct,
      leaves: Array[ParquetLeafColumn],
      rowIndex: Int,
      parentPath: Vector[String],
      optionality: ParquetOptionality
  ): Any =
    val path = parentPath :+ field.name
    val present = !field.nullable || structPresent(field, leaves, rowIndex)

    if present then
      val values = new Array[Any](field.fields.size)
      var childIndex = 0
      while childIndex < field.fields.size do
        values(childIndex) = assembleNested(field.fields(childIndex), leaves, rowIndex, path, optionality)
        childIndex += 1
      end while
      Tuple.fromArray(values)
    else if optionality == ParquetOptionality.NoOptions then missing(path)
    else null
    end if
  end structValue

  private def structPresent(field: ParquetField.Struct, leaves: Array[ParquetLeafColumn], rowIndex: Int): Boolean =
    firstLeafIndex(field).exists(index => leaves(index).definitionLevels(rowIndex) >= field.presentDefinitionLevel)

  private def firstLeafIndex(field: ParquetField): Option[Int] = field match
    case primitive: ParquetField.Primitive => Some(primitive.leafIndex)
    case struct: ParquetField.Struct       => struct.fields.headOption.flatMap(firstLeafIndex)

  private def missing(path: Vector[String]): Nothing =
    throw ParquetDecodeException(
      s"Field '${path.mkString(".")}' is absent but was inferred as a non-optional type. Read with ParquetOptionality.FromSchema to preserve optional fields."
    )

end ParquetStructAssembler
