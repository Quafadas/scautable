package io.github.quafadas.scautable

import scala.NamedTuple.{AnyNamedTuple, DropNames, Names}
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

  /** The element type of a column, i.e. `T` with any `Option` wrapper removed. */
  type Unwrapped[T] = T match
    case Option[a] => a
    case _         => T

  /** Replace a column's value type with a display tag (see `ColumnFormat`), preserving any `Option` wrapper so that `IsNumeric` and friends still reduce. */
  type Tagged[T, Tag] = T match
    case Option[a] => Option[Tag]
    case _         => Tag

  /** `Option[T]`, idempotently - a column that is already optional does not become `Option[Option[_]]`.
    *
    * Treats `Option` the same way [[Unwrapped]] and [[Tagged]] do. `optionalise` in `NamedTupleIteratorExtensions` is the runtime counterpart and must agree with it: a left join
    * would otherwise hand back values whose shape does not match their static type.
    */
  type Optional[T] = T match
    case Option[a] => Option[a]
    case _         => Option[T]

  /** [[Optional]] applied to every element - the value types of the right hand table of a left join. */
  type Optionalize[T <: Tuple] <: Tuple = T match
    case EmptyTuple   => EmptyTuple
    case head *: tail => Optional[head] *: Optionalize[tail]

  /** Marker returned by [[ColTypeAtName]] when a name is not a column at all. */
  sealed trait NoSuchColumn

  /** The type of column `Name`, or [[NoSuchColumn]].
    *
    * Same job as [[GetTypeAtName]], but `Name` is unbounded and appears as the *pattern* rather than as an argument to `IsMatch`. That is what lets it be driven by a type captured
    * in a match type or an inline match, where bounds are lost - `Name & String` would be an intersection in pattern position and would never reduce.
    */
  type ColTypeAtName[K <: Tuple, V <: Tuple, Name] = (K, V) match
    case (Name *: ?, v *: ?) => v
    case (? *: ks, ? *: vs)  => ColTypeAtName[ks, vs, Name]
    case (EmptyTuple, ?)     => NoSuchColumn
    case (?, EmptyTuple)     => NoSuchColumn

  /** Whether `Name` is one of `Names`.
    *
    * Same job as [[IsColumn]], but `Name` is unbounded and appears as the *pattern*, for the reason given on [[ColTypeAtName]] - which is what lets it be driven by a name captured
    * by an inline match, where the `<: String` bound is lost.
    */
  type NameIn[Names <: Tuple, Name] <: Boolean = Names match
    case Name *: ?  => true
    case ? *: rest  => NameIn[rest, Name]
    case EmptyTuple => false

  /** [[ReplaceOneTypeAtName]] with an unbounded `Name`, for the same reason as [[ColTypeAtName]]. A name that is not a column leaves `V` alone. */
  type ReplaceTypeAtName[K <: Tuple, V <: Tuple, Name, A] <: Tuple = (K, V) match
    case (Name *: ?, ? *: vs) => A *: vs
    case (? *: ks, v *: vs)   => v *: ReplaceTypeAtName[ks, vs, Name, A]
    case (?, ?)               => V

  /** Whether a display tag may decorate a column of type `ColT`, i.e. whether `Tag <: ColT`.
    *
    * Expressed as a match type rather than a `<:<`: the tag reaches this check as a type captured by an inline match, and implicit search would happily unify an unsubstituted
    * capture with anything, silently accepting every spec.
    */
  type TagOk[Tag, ColT] <: Boolean = Tag match
    case ColT => true
    case _    => false

  /** Fold a named-tuple spec over the column types of a table.
    *
    * A *spec* is a named tuple type whose names pick out columns and whose values say what to do with them; `Step` turns a column's current type and its spec value into its new
    * type. `Styled` is this fold with `Step = Tagged`; retyping or rewrapping a set of columns is the same fold with a different `Step`, which is the whole point of naming it
    * separately.
    *
    * A spec name that is not a column of `K` leaves `V` untouched here - callers reject it themselves, where they can name it in the error.
    */
  type FoldSpec[K <: Tuple, V <: Tuple, SpecNames <: Tuple, SpecVals <: Tuple, Step[_, _]] <: Tuple = (SpecNames, SpecVals) match
    case (EmptyTuple, ?)    => V
    case (?, EmptyTuple)    => V
    case (n *: ns, s *: ss) =>
      FoldSpec[K, ReplaceTypeAtName[K, V, n, Step[ColTypeAtName[K, V, n], s]], ns, ss, Step]

  /** The column types of a table with a whole style spec applied.
    *
    * `Spec` is a named tuple *type* whose names are column names and whose values are display tags, e.g. `(bid: Decimals[2], el: Percent[1])`. Each binding lands exactly where the
    * equivalent `formatColumn` would, so a spec is indistinguishable from the corresponding chain - including at runtime, where both are casts.
    */
  type Styled[K <: Tuple, V <: Tuple, Spec <: AnyNamedTuple] =
    FoldSpec[K, V, Names[Spec], DropNames[Spec], Tagged]

  /** The column types of a table with a set of columns forcibly retyped.
    *
    * The fold's `Step` simply discards the old type, since a `retype` spec says what each column should become outright.
    */
  type Retyped[K <: Tuple, V <: Tuple, Spec <: AnyNamedTuple] =
    FoldSpec[K, V, Names[Spec], DropNames[Spec], [Old, New] =>> New]

  /** The argument type of the function a `mapColumns` spec gives for a column, used to check it against the column's own type. */
  type FnArg[Fn] = Fn match
    case Function1[a, ?] => a

  /** The result type of that function - the column's type after the map. `Old` is unused; the fold's `Step` is arity two. */
  type FnResult[Old, Fn] = Fn match
    case Function1[?, r] => r

  /** The column types of a table with a set of columns mapped by a spec of functions. */
  type MappedCols[K <: Tuple, V <: Tuple, Spec <: AnyNamedTuple] =
    FoldSpec[K, V, Names[Spec], DropNames[Spec], FnResult]

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
