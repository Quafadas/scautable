package io.github.quafadas.scautable

import scala.compiletime.*
import scala.compiletime.ops.int.*

object ColumnTyped:

  inline def constValueAll[A]: A =
    inline erasedValue[A] match
      case _: *:[h, t]   => (constValueAll[h] *: constValueAll[t]).asInstanceOf[A]
      case _: EmptyTuple => EmptyTuple.asInstanceOf[A]
      case _             => constValue[A]

  def listToTuple[A](list: List[A]): Tuple = list match
    case Nil    => EmptyTuple
    case h :: t => h *: listToTuple(t)

  // Error type for compile-time failures
  type ColumnNotFoundError[ColumnName <: String, AvailableColumns <: Tuple]

  // The index of a column name in a tuple of column names
  type IdxAtName[STR <: String, T <: Tuple] <: Int = T match
    case EmptyTuple   => -1
    case head *: tail =>
      IsMatch[STR, head] match
        case true  => 0
        case false => S[IdxAtName[STR, tail]]

  // Get the indexes of multiple column names
  type IndexesAtNames[Names <: Tuple, Columns <: Tuple] <: Tuple = Names match
    case EmptyTuple           => EmptyTuple
    case nameHead *: nameTail => IdxAtName[nameHead, Columns] *: IndexesAtNames[nameTail, Columns]

  type Negate[T <: Tuple] <: Tuple = T match
    case EmptyTuple     => EmptyTuple
    case (head *: tail) =>
      head match
        case false => true *: Negate[tail]
        case true  => false *: Negate[tail]

  type IsColumn[StrConst <: String, T <: Tuple] <: Boolean = T match
    case EmptyTuple     => false
    case (head *: tail) =>
      IsMatch[StrConst, head] match
        case true  => true
        case false => IsColumn[StrConst, tail]
    case _ => false

  type ReplaceOneName[T <: Tuple, StrConst <: String, A <: String] <: Tuple = T match
    case EmptyTuple           => EmptyTuple
    case nameHead *: nameTail =>
      IsMatch[nameHead, StrConst] match
        case true  => A *: nameTail
        case false => nameHead *: ReplaceOneName[nameTail, StrConst, A]

  type ReplaceOneTypeAtName[N <: Tuple, StrConst <: String, T <: Tuple, A] <: Tuple = (N, T) match
    case (EmptyTuple, _)                              => EmptyTuple
    case (_, EmptyTuple)                              => EmptyTuple
    case (nameHead *: nameTail, typeHead *: typeTail) =>
      IsMatch[nameHead, StrConst] match
        case true  => A *: typeTail
        case false =>
          typeHead *: ReplaceOneTypeAtName[nameTail, StrConst, typeTail, A]

  type DropOneTypeAtName[N <: Tuple, StrConst <: String, T <: Tuple] <: Tuple = (N, T) match
    case (EmptyTuple, _)                              => EmptyTuple
    case (_, EmptyTuple)                              => EmptyTuple
    case (nameHead *: nameTail, typeHead *: typeTail) =>
      IsMatch[nameHead, StrConst] match
        case true  => typeTail
        case false =>
          typeHead *: DropOneTypeAtName[nameTail, StrConst, typeTail]

  type GetTypesAtNames[N <: Tuple, ForNames <: Tuple, T <: Tuple] <: Tuple = ForNames match
    case EmptyTuple           => EmptyTuple
    case nameHead *: nameTail => GetTypeAtName[N, nameHead, T] *: GetTypesAtNames[N, nameTail, T]

  type GetTypeAtName[N <: Tuple, StrConst <: String, T <: Tuple] = (N, T) match
    case (EmptyTuple, _)                              => EmptyTuple
    case (_, EmptyTuple)                              => EmptyTuple
    case (nameHead *: nameTail, typeHead *: typeTail) =>
      IsMatch[nameHead, StrConst] match
        case true  => typeHead
        case false =>
          GetTypeAtName[nameTail, StrConst, typeTail]

  type DropAfterName[T, StrConst <: String] = T match
    case EmptyTuple     => EmptyTuple
    case (head *: tail) =>
      IsMatch[StrConst, head] match
        case true  => EmptyTuple
        case false => head *: DropAfterName[tail, StrConst]

  type DropOneName[T, StrConst <: String] <: Tuple = T match
    case EmptyTuple     => EmptyTuple
    case (head *: tail) =>
      IsMatch[StrConst, head] match
        case true  => DropOneName[tail, StrConst]
        case false => head *: DropOneName[tail, StrConst]

  type IsMatch[A <: String, B <: String] = B match
    case A => true
    case _ => false

  type IsNumeric[T] <: Boolean = T match
    case Option[a] => IsNumeric[a]
    case Int       => true
    case Long      => true
    case Float     => true
    case Double    => true
    case _         => false

  type NumericColsIdx[T <: Tuple] <: Tuple =
    T match
      case EmptyTuple     => EmptyTuple
      case (head *: tail) =>
        IsNumeric[head] match
          case true  => true *: NumericColsIdx[tail]
          case false => false *: NumericColsIdx[tail]

  type SelectFromTuple[T <: Tuple, Bools <: Tuple] <: Tuple = T match
    case EmptyTuple     => EmptyTuple
    case (head *: tail) =>
      Bools match
        case (true *: boolTail)  => head *: SelectFromTuple[tail, boolTail]
        case (false *: boolTail) => SelectFromTuple[tail, boolTail]

  type AllAreColumns[T <: Tuple, K <: Tuple] <: Boolean = T match
    case EmptyTuple   => true
    case head *: tail =>
      IsColumn[head, K] match
        case true  => AllAreColumns[tail, K]
        case false => false

  type TupleContainsIdx[Search <: Tuple, In <: Tuple] <: Tuple = In match
    case EmptyTuple   => EmptyTuple
    case head *: tail =>
      Search match
        case EmptyTuple               => false *: EmptyTuple
        case searchHead *: searchTail =>
          IsColumn[head, Search] match
            case true  => true *: TupleContainsIdx[Search, tail]
            case false => false *: TupleContainsIdx[Search, tail]

  type StringifyTuple[T >: Tuple] <: Tuple = T match
    case EmptyTuple   => EmptyTuple
    case head *: tail => (head: String) *: StringifyTuple[tail]

  type StringyTuple[T <: Tuple] <: Tuple = T match
    case EmptyTuple   => EmptyTuple
    case head *: tail => String *: StringyTuple[tail]

end ColumnTyped
