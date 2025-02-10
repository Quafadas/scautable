package io.github.quafadas.scautable

import scala.compiletime.*
import scala.compiletime.ops.int.*
import scala.annotation.experimental

@experimental
object ColumnTyped:

  inline def constValueAll[A]: A =
    inline erasedValue[A] match
      case _: *:[h, t]   => (constValueAll[h] *: constValueAll[t]).asInstanceOf[A]
      case _: EmptyTuple => EmptyTuple.asInstanceOf[A]
      case _             => constValue[A]

  def listToTuple[A](list: List[A]): Tuple = list match
    case Nil    => EmptyTuple
    case h :: t => h *: listToTuple(t)

  type Negate[T <: Tuple] <: Tuple = T match
    case EmptyTuple => EmptyTuple
    case (head *: tail) =>
      head match
        case false => true *: Negate[tail]
        case true  => false *: Negate[tail]

  type IsColumn[StrConst <: String, T <: Tuple] = T match
    case EmptyTuple => false
    case (head *: tail) =>
      IsMatch[StrConst, head] match
        case true  => true
        case false => IsColumn[StrConst, tail]
    case _ => false

  type Tail[T <: Tuple, S <: String] <: Tuple = T match
    case EmptyTuple => EmptyTuple
    case head *: tail =>
      IsMatch[S, head] match
        case true  => EmptyTuple
        case false => Tail[tail, S]

  type ReplaceOneName[T <: Tuple, StrConst <: String, A <: String] <: Tuple = T match
    case EmptyTuple => EmptyTuple
    case nameHead *: nameTail =>
      IsMatch[nameHead, StrConst] match
        case true  => A *: nameTail
        case false => nameHead *: ReplaceOneName[nameTail, StrConst, A]

  type ReplaceOneTypeAtName[N <: Tuple, StrConst <: String, T <: Tuple, A] <: Tuple = (N, T) match
    case (EmptyTuple, _) => EmptyTuple
    case (_, EmptyTuple) => EmptyTuple
    case (nameHead *: nameTail, typeHead *: typeTail) =>
      IsMatch[nameHead, StrConst] match
        case true => A *: typeTail
        case false =>
          typeHead *: ReplaceOneTypeAtName[nameTail, StrConst, typeTail, A]

  type DropOneTypeAtName[N <: Tuple, StrConst <: String, T <: Tuple] <: Tuple = (N, T) match
    case (EmptyTuple, _) => EmptyTuple
    case (_, EmptyTuple) => EmptyTuple
    case (nameHead *: nameTail, typeHead *: typeTail) =>
      IsMatch[nameHead, StrConst] match
        case true => typeTail
        case false =>
          typeHead *: DropOneTypeAtName[nameTail, StrConst, typeTail]

  type GetTypesAtNames[N <: Tuple, ForNames <: Tuple, T <: Tuple] <: Tuple = ForNames match
    case EmptyTuple           => EmptyTuple
    case nameHead *: nameTail => GetTypeAtName[N, nameHead, T] *: GetTypesAtNames[N, nameTail, T]

  type GetTypeAtName[N <: Tuple, StrConst <: String, T <: Tuple] = (N, T) match
    case (EmptyTuple, _) => EmptyTuple
    case (_, EmptyTuple) => EmptyTuple
    case (nameHead *: nameTail, typeHead *: typeTail) =>
      IsMatch[nameHead, StrConst] match
        case true => typeHead
        case false =>
          GetTypeAtName[nameTail, StrConst, typeTail]

  type GetNames[N <: Tuple] = N match
    case (EmptyTuple, _) => EmptyTuple
    case (nameHead *: typ, tail) =>
      nameHead *: GetNames[tail]

  type DropAfterName[T, StrConst <: String] = T match
    case EmptyTuple => EmptyTuple
    case (head *: tail) =>
      IsMatch[StrConst, head] match
        case true  => EmptyTuple
        case false => head *: DropAfterName[tail, StrConst]

  type DropOneName[T, StrConst <: String] <: Tuple = T match
    case EmptyTuple => EmptyTuple
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
      case EmptyTuple => EmptyTuple
      case (head *: tail) =>
        IsNumeric[head] match
          case true  => true *: NumericColsIdx[tail]
          case false => false *: NumericColsIdx[tail]

  type SelectFromTuple[T <: Tuple, Bools <: Tuple] <: Tuple = T match
    case EmptyTuple => EmptyTuple
    case (head *: tail) =>
      Bools match
        case (true *: boolTail)  => head *: SelectFromTuple[tail, boolTail]
        case (false *: boolTail) => SelectFromTuple[tail, boolTail]

  type AllAreColumns[T <: Tuple, K <: Tuple] <: Boolean = T match
    case EmptyTuple => true
    case head *: tail =>
      IsColumn[head, K] match
        case true  => AllAreColumns[tail, K]
        case false => false

  type TupleContainsIdx[Search <: Tuple, In <: Tuple] <: Tuple = In match
    case EmptyTuple => EmptyTuple
    case head *: tail =>
      Search match
        case EmptyTuple => false *: EmptyTuple
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

  type ReReverseXLL[t] = Size[t] match
    case 0  => EmptyTuple
    case 1  => t
    case 2  => t
    case 3  => t
    case 4  => t
    case 5  => t
    case 6  => t
    case 7  => t
    case 8  => t
    case 9  => t
    case 10 => t
    case 11 => t
    case 12 => t
    case 13 => t
    case 14 => t
    case 15 => t
    case 16 => t
    case 17 => t
    case 18 => t
    case 19 => t
    case 20 => t
    case 21 => t
    case 22 => t
    case _  => ReverseTuple[t]

  type ReverseTuple[T <: Tuple] <: Tuple = T match
    case EmptyTuple => EmptyTuple
    case x *: xs    => ReverseTuple[xs] *: x

  type Size[T] <: Int = T match
    case EmptyTuple => 0
    case x *: xs    => 1 + Size[xs]
end ColumnTyped
