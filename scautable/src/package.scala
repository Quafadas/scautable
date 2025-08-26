package io.github.quafadas

object table:
  export io.github.quafadas.scautable.CSV
  export io.github.quafadas.scautable.CSV.*
  export io.github.quafadas.scautable.scautable
  export io.github.quafadas.scautable.CsvIterator
  export io.github.quafadas.scautable.Excel
  export io.github.quafadas.scautable.Excel.*
  export io.github.quafadas.scautable.scautable.*
  export io.github.quafadas.scautable.ConsoleFormat.*
  export io.github.quafadas.scautable.NamedTupleIteratorExtensions.*
  export io.github.quafadas.scautable.HeaderOptions.*


  enum TypeInferrer {
    case FirstRow
    case StringType
    case FromTuple[T]()
  }


end table
