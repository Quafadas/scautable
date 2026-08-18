package io.github.quafadas.scautable.parquet

/** Print the footer schema of a parquet file — useful when exploring a file before wiring it into a macro. */
@main def printParquetSchema(path: String): Unit =
  println(Parquet.schemaOf(ParquetSource.AbsolutePath(path)))
