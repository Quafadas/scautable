package io.github.quafadas.scautable

object ColumnTypes:

  type coltttt = CSV.type

  opaque type colt1 = String

  object colt1:
    def apply(value: String): colt1           = value
    def unapply(value: colt1): Option[String] = Some(value)

  extension (value: colt1) def toInt = value.toInt
