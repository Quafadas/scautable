package io.github.quafadas.scautable
import scala.NamedTuple.*
import scala.NamedTuple.withNames
import scala.annotation.implicitNotFound
import scala.collection.BuildFrom
import scala.collection.Factory
import scala.compiletime.*

import io.github.quafadas.scautable.ColumnTyped.*

object NamedTupleIteratorExtensions:
  private val rand = new scala.util.Random

  /** Fail compilation unless every name in a spec is a column of `K`. `Who` is the calling method, so the message reads in its terms.
    *
    * Shared by every spec-driven operation - only what each then does with the spec's *values* differs.
    */
  private inline def checkSpecNames[K <: Tuple, V <: Tuple, SpecNames <: Tuple, Who <: String]: Unit =
    inline erasedValue[SpecNames] match
      case _: EmptyTuple   => ()
      case _: (n *: names) =>
        inline erasedValue[ColTypeAtName[K, V, n]] match
          case _: NoSuchColumn => error(constValue[Who] + ": no column named " + constValue[n & String])
          case _               => checkSpecNames[K, V, names, Who]

  /** Fail compilation unless each tag in a style spec refines the element type of its column. Assumes [[checkSpecNames]] has already passed. */
  private inline def checkStyleTags[K <: Tuple, V <: Tuple, SpecNames <: Tuple, SpecTags <: Tuple]: Unit =
    inline erasedValue[SpecNames] match
      case _: EmptyTuple   => ()
      case _: (n *: names) =>
        inline erasedValue[SpecTags] match
          case _: EmptyTuple  => ()
          case _: (t *: tags) =>
            inline erasedValue[TagOk[t, Unwrapped[ColTypeAtName[K, V, n]]]] match
              case _: true => checkStyleTags[K, V, names, tags]
              case _       => error("style: the tag given for column " + constValue[n & String] + " is not compatible with its type")

  /** Fail compilation unless each function in a `mapColumns` spec accepts its column's type. Assumes [[checkSpecNames]] has already passed. */
  private inline def checkMapFns[K <: Tuple, V <: Tuple, SpecNames <: Tuple, SpecFns <: Tuple]: Unit =
    inline erasedValue[SpecNames] match
      case _: EmptyTuple   => ()
      case _: (n *: names) =>
        inline erasedValue[SpecFns] match
          case _: EmptyTuple => ()
          case _: (f *: fns) =>
            inline erasedValue[TagOk[ColTypeAtName[K, V, n], FnArg[f]]] match
              case _: true => checkMapFns[K, V, names, fns]
              case _       => error("mapColumns: the function given for column " + constValue[n & String] + " does not accept its type")

  /** The column indexes a spec touches, paired with the spec's values, in spec order. */
  private inline def specPlan[K <: Tuple, Spec <: AnyNamedTuple](spec: Spec): List[(Int, Any)] =
    val headers = constValueTuple[K].toList.map(_.toString())
    val names = constValueTuple[Names[Spec]].toList.map(_.toString())
    val values = spec.asInstanceOf[DropNames[Spec]].productIterator.toList
    names.map(headers.indexOf).zip(values)
  end specPlan

  /** Apply a plan from [[specPlan]] to one row, leaving untouched columns alone. */
  private def applyPlan[K <: Tuple, V <: Tuple](plan: List[(Int, Any)], row: NamedTuple[K, V]): Tuple =
    val cells = row.toTuple.toArray
    plan.foreach { case (idx, fn) => cells(idx) = fn.asInstanceOf[Any => Any](cells(idx)).asInstanceOf[Object] }
    Tuple.fromArray(cells)
  end applyPlan

  /** Fail compilation if any name appears on both sides of a join. `Who` is the calling method, as in [[checkSpecNames]].
    *
    * `Tuple.Disjoint[Left, Right] =:= true` would also reject these, but it cannot say *which* column is at fault, and that is the whole of the message's value.
    */
  private inline def checkNoSharedNames[Left <: Tuple, Right <: Tuple, Who <: String]: Unit =
    inline erasedValue[Right] match
      case _: EmptyTuple  => ()
      case _: (n *: rest) =>
        inline erasedValue[NameIn[Left, n]] match
          case _: true => error(constValue[Who] + ": column " + constValue[n & String] + " exists on both sides - rename or drop it first")
          case _       => checkNoSharedNames[Left, rest, Who]

  /** Drop the cell at `idx`. The `EmptyTuple` case is not redundant - it is what a right hand table consisting of nothing but the join key hits. */
  private def removeAt(t: Tuple, idx: Int): Tuple =
    val (head, tail) = t.splitAt(idx)
    head match
      case _: EmptyTuple => tail.tail
      case _             => head ++ tail.tail
    end match
  end removeAt

  /** Runtime counterpart of [[ColumnTyped.Optional]]: wrap each cell in `Some` unless it already is an `Option`.
    *
    * The two must agree. Wrapping unconditionally would hand back `Some(None)` where the static type says `None`.
    */
  private def optionalise(t: Tuple): Tuple =
    Tuple.fromArray(t.toArray.map {
      case o: Option[?] => o
      case x            => Some(x)
    })

  /** Whether the join key is optional, so that the runtime unwrap happens exactly where [[ColumnTyped.NarrowKey]] drops the `Option` and nowhere else. */
  private inline def isOptionKey[T]: Boolean =
    inline erasedValue[T] match
      case _: Option[?] => true
      case _            => false

  /** Strip the `Option` off the cell at `idx`, the runtime half of [[ColumnTyped.NarrowKey]]. Only ever called on an inner join's key, which the `None` filter has already made a
    * `Some`.
    */
  private def unwrapAt(t: Tuple, idx: Int): Tuple =
    val cells = t.toArray
    cells(idx) = cells(idx) match
      case Some(v) => v.asInstanceOf[Object]
      case other   => other
    Tuple.fromArray(cells)
  end unwrapAt

  /** A `None` key means "unknown", and two unknowns are not a match.
    *
    * This is what SQL does - `NULL = NULL` is never true - and what pandas does, dropping missing keys from a merge. Treating `None` as an ordinary value would also quietly square
    * the missing rows: three `None` keys on each side is nine output rows carrying no information. A left join still keeps such a row, with its right hand columns all `None`.
    *
    * To match missing to missing deliberately, map the key to a sentinel first: `mapColumn["k", Int](_.getOrElse(-1))`.
    */
  private def isMissingKey(key: Any): Boolean = key == None

  /** Hash join. The left side streams; the right is materialised, but not until the result is first pulled - so building a join does not drain its argument.
    *
    * `rightArity` is the number of right hand columns *after* the key has been dropped, and is only used to shape the all-`None` row a left join emits for a left row that matched
    * nothing.
    */
  private def hashJoin(
      left: Iterator[Tuple],
      lIdx: Int,
      right: => IterableOnce[Tuple],
      rIdx: Int,
      leftOuter: Boolean,
      rightArity: Int,
      unwrapKey: Boolean
  ): Iterator[Tuple] =
    // groupMap keeps right hand rows in encounter order within a key, so the output order is deterministic.
    // `Map` alone would resolve to `NamedTuple.Map`, courtesy of the wildcard import at the top of this file.
    lazy val index: scala.collection.immutable.Map[Any, Seq[Tuple]] =
      right.iterator
        .filterNot(t => isMissingKey(t.productElement(rIdx)))
        .map { t =>
          val rest = removeAt(t, rIdx)
          t.productElement(rIdx) -> (if leftOuter then optionalise(rest) else rest)
        }
        .toSeq
        .groupMap(_._1)(_._2)

    lazy val nones: Tuple = Tuple.fromArray(Array.fill[Object](rightArity)(None))

    left.flatMap { lt =>
      val key = lt.productElement(lIdx)
      val matches = if isMissingKey(key) then Nil else index.getOrElse(key, Nil)
      lazy val outLeft = if unwrapKey then unwrapAt(lt, lIdx) else lt
      if matches.nonEmpty then matches.iterator.map(rt => outLeft ++ rt)
      else if leftOuter then Iterator.single(lt ++ nones)
      else Iterator.empty
      end if
    }
  end hashJoin

  /** The shared body of every join: resolve both key positions, join, and re-attach the output names. Callers own the compile time checks, so that errors name *their* method. */
  private inline def joinCore[K <: Tuple, V <: Tuple, K2 <: Tuple, V2 <: Tuple, OutK <: Tuple, OutV <: Tuple](
      itr: Iterator[NamedTuple[K, V]],
      that: IterableOnce[NamedTuple[K2, V2]],
      leftKey: String,
      rightKey: String,
      leftOuter: Boolean,
      unwrapKey: Boolean
  ): Iterator[NamedTuple[OutK, OutV]] =
    val rightHeaders = constValueTuple[K2].toList.map(_.toString())
    hashJoin(
      itr.map(_.toTuple),
      constValueTuple[K].toList.map(_.toString()).indexOf(leftKey),
      that.iterator.map(_.toTuple),
      rightHeaders.indexOf(rightKey),
      leftOuter,
      rightHeaders.size - 1,
      unwrapKey
    ).map(_.withNames[OutK].asInstanceOf[NamedTuple[OutK, OutV]])
  end joinCore

  extension [K <: Tuple, V <: Tuple](itr: Iterator[NamedTuple[K, V]])

    def sample(frac: Double, deterministic: Boolean = false): Iterator[NamedTuple[K, V]] =
      if deterministic then itr.zipWithIndex.filter { case (_, idx) => idx % (1 / frac) == 0 }.map(_._1)
      else itr.filter(_ => rand.nextDouble() < frac)

    def renameColumn[From <: String, To <: String](using
        @implicitNotFound("Column ${From} not found")
        ev: IsColumn[From, K] =:= true,
        FROM: ValueOf[From],
        TO: ValueOf[To]
    ): Iterator[NamedTuple[ReplaceOneName[K, From, To], V]] =
      itr.map(_.withNames[ReplaceOneName[K, From, To]].asInstanceOf[NamedTuple[ReplaceOneName[K, From, To], V]])

    def addColumn[S <: String, A](fct: (tup: NamedTuple.NamedTuple[K, V]) => A): Iterator[NamedTuple[Tuple.Append[K, S], Tuple.Append[V, A]]] =
      itr.map { (tup: NamedTuple[K, V]) =>
        (tup.toTuple :* fct(tup)).withNames[Tuple.Append[K, S]]
      }

    def forceColumnType[S <: String, A]: Iterator[NamedTuple[K, ReplaceOneTypeAtName[K, S, V, A]]] =
      itr.map(_.asInstanceOf[NamedTuple[K, ReplaceOneTypeAtName[K, S, V, A]]])

    /** Attach a display tag (see `ColumnFormat`) to a column, changing how it renders without touching the data.
      *
      * Zero cost at runtime - like `forceColumnType` this is a cast. `Tag` must be a subtype of the column's element type, so `Decimals[2]` on a `String` column will not compile.
      *
      * {{{
      * csv.formatColumn["price", Currency["$", 2]].formatColumn["rate", Percent[2]].ptbln
      * }}}
      */
    def formatColumn[S <: String, Tag](using
        @implicitNotFound("Column ${S} not found")
        ev: IsColumn[S, K] =:= true,
        @implicitNotFound("Format tag ${Tag} is not compatible with the type of column ${S}")
        compat: Tag <:< Unwrapped[GetTypeAtName[K, S, V]]
    ): Iterator[NamedTuple[K, ReplaceOneTypeAtName[K, S, V, Tagged[GetTypeAtName[K, S, V], Tag]]]] =
      itr.map(_.asInstanceOf[NamedTuple[K, ReplaceOneTypeAtName[K, S, V, Tagged[GetTypeAtName[K, S, V], Tag]]]])

    /** Attach a whole spec of display tags in one call, so that the look of a table can be named, reused, and applied at the point of display.
      *
      * The spec is a named tuple *type* - names are columns, values are tags from `ColumnFormat`. Equivalent to the corresponding chain of `formatColumn` calls, and like them a
      * cast:
      * {{{
      * type PriceSheet = (`Bid Spread`: Decimals[2], `Offer Spread`: Decimals[2], EL: Percent[1])
      * prices.style[PriceSheet].ptbln
      * }}}
      * A column the spec does not mention is left alone, so one spec can serve a family of tables.
      */
    inline def style[Spec <: AnyNamedTuple]: Iterator[NamedTuple[K, Styled[K, V, Spec]]] =
      checkSpecNames[K, V, Names[Spec], "style"]
      checkStyleTags[K, V, Names[Spec], DropNames[Spec]]
      itr.asInstanceOf[Iterator[NamedTuple[K, Styled[K, V, Spec]]]]
    end style

    /** Force the types of several columns at once, as [[forceColumnType]] does for one.
      *
      * The spec is a named tuple *type* - names are columns, values are the types they should take. A cast, and just as unchecked: nothing verifies that the data really holds the
      * new type.
      * {{{
      * raw.retype[(price: Double, qty: Int)]
      * }}}
      */
    inline def retype[Spec <: AnyNamedTuple]: Iterator[NamedTuple[K, Retyped[K, V, Spec]]] =
      checkSpecNames[K, V, Names[Spec], "retype"]
      itr.asInstanceOf[Iterator[NamedTuple[K, Retyped[K, V, Spec]]]]
    end retype

    /** Map several columns at once, as [[mapColumn]] does for one.
      *
      * The spec is a named tuple *value* - names are columns, values are the functions to apply. Each column's new type is the function's result type; columns the spec omits are
      * untouched.
      * {{{
      * raw.mapColumns((price = (s: String) => s.toDouble, qty = (s: String) => s.toInt))
      * }}}
      * The lambdas need their parameter types written out: the spec's type is being inferred from the lambdas themselves, so there is no expected type to infer them from.
      */
    inline def mapColumns[Spec <: AnyNamedTuple](spec: Spec): Iterator[NamedTuple[K, MappedCols[K, V, Spec]]] =
      checkSpecNames[K, V, Names[Spec], "mapColumns"]
      checkMapFns[K, V, Names[Spec], DropNames[Spec]]
      val plan = specPlan[K, Spec](spec)
      itr.map(row => applyPlan(plan, row).withNames[K].asInstanceOf[NamedTuple[K, MappedCols[K, V, Spec]]])
    end mapColumns

    inline def mapColumn[S <: String, A](using
        @implicitNotFound("Column ${S} not found")
        ev: IsColumn[S, K] =:= true,
        s: ValueOf[S]
    )(
        fct: GetTypeAtName[K, S, V] => A
    ): Iterator[NamedTuple[K, ReplaceOneTypeAtName[K, S, V, A]]] =
      import scala.compiletime.ops.string.*
      val headers = constValueTuple[K].toList.map(_.toString())
      val idx = headers.indexOf(s.value)
      if idx == -1 then ???
      end if
      itr.map { (x: NamedTuple[K, V]) =>
        val tup = x.toTuple
        val typ = tup(idx).asInstanceOf[GetTypeAtName[K, S, V]]
        val mapped = fct(typ)
        val (head, tail) = x.toTuple.splitAt(idx)
        (head ++ mapped *: tail.tail).withNames[K].asInstanceOf[NamedTuple[K, ReplaceOneTypeAtName[K, S, V, A]]]
      }
    end mapColumn

    inline def numericCols: Iterator[
      NamedTuple[
        SelectFromTuple[K, NumericColsIdx[V]],
        GetTypesAtNames[K, SelectFromTuple[K, NumericColsIdx[V]], V]
      ]
    ] =
      val ev1 = summonInline[AllAreColumns[SelectFromTuple[K, NumericColsIdx[V]], K] =:= true]
      columns[SelectFromTuple[K, NumericColsIdx[V]]](using ev1)
    end numericCols

    inline def nonNumericCols: Iterator[
      NamedTuple[
        SelectFromTuple[K, Negate[NumericColsIdx[V]]],
        GetTypesAtNames[K, SelectFromTuple[K, Negate[NumericColsIdx[V]]], V]
      ]
    ] =
      val ev1 = summonInline[
        AllAreColumns[SelectFromTuple[K, Negate[NumericColsIdx[V]]], K] =:= true
      ]
      columns[SelectFromTuple[K, Negate[NumericColsIdx[V]]]](using ev1)
    end nonNumericCols

    inline def columns[ST <: Tuple](using
        @implicitNotFound("Not all columns in ${ST} were found")
        ev: AllAreColumns[ST, K] =:= true
    ): Iterator[
      NamedTuple[
        ST,
        GetTypesAtNames[K, ST, V]
      ]
    ] =
      val headers = constValueTuple[K].toList.map(_.toString())
      // val types  = constValueTuple[SelectFromTuple[V, TupleContainsIdx[ST, K]]].toList.map(_.toString())
      val selectedHeaders = constValueTuple[ST].toList.map(_.toString())

      // Preserve the existing column order
      val idxes = selectedHeaders.map(headers.indexOf(_)).filterNot(_ == -1)

      // println(s"headers $headers")
      // println(s"selectedHeaders $selectedHeaders")
      // println(s"idxes $idxes")

      itr.map { (x: NamedTuple[K, V]) =>
        val tuple = x.toTuple

        // println("in tuple")
        // println(tuple.toList.mkString(","))
        val selected: Tuple = idxes.foldRight(EmptyTuple: Tuple) { (idx, acc) =>
          // println(tuple(idx))
          tuple(idx) *: acc
        }

        selected
          .withNames[ST]
          .asInstanceOf[
            NamedTuple[
              ST,
              GetTypesAtNames[K, ST, V]
            ]
          ]
      }
    end columns

    inline def column[S <: String](using
        @implicitNotFound("Column ${S} not found")
        ev: IsColumn[S, K] =:= true
    ): Iterator[NamedTuple.Elem[NamedTuple.NamedTuple[K, V], IdxAtName[S, K]]] =
      itr.map(x => x.apply(constValue[IdxAtName[S, K]]))
    end column

    inline def dropColumn[S <: String](using
        @implicitNotFound("Column ${S} not found")
        ev: IsColumn[S, K] =:= true,
        s: ValueOf[S]
    ): Iterator[NamedTuple[DropOneName[K, S], DropOneTypeAtName[K, S, V]]] =
      val headers = constValueTuple[K].toList.map(_.toString())
      val idx = headers.indexOf(s.value)

      itr.map { (x: NamedTuple[K, V]) =>
        val (head, tail) = x.toTuple.splitAt(idx)
        head match
          case x: EmptyTuple => tail.tail.withNames[DropOneName[K, S]].asInstanceOf[NamedTuple[DropOneName[K, S], DropOneTypeAtName[K, S, V]]]
          case _             => (head ++ tail.tail).withNames[DropOneName[K, S]].asInstanceOf[NamedTuple[DropOneName[K, S], DropOneTypeAtName[K, S, V]]]
        end match
      }
    end dropColumn

    /** Inner join on a column of the same name in both tables.
      *
      * The key is given *after* the right hand table, because the compiler infers that table's shape from the argument and only the key needs writing out:
      * {{{
      * orders.join(customers)["custId"]
      * }}}
      * The output is every column of the left table, then every column of the right except the key. The left side streams; the right is read into a hash index the first time the
      * result is pulled, so the right table is the one that has to fit in memory.
      *
      * A column name on both sides is a compile error - rename or drop it first. Keys are matched with `==`, so an `Option` key column matches `None` to `None`.
      */
    inline def join[K2 <: Tuple, V2 <: Tuple](that: IterableOnce[NamedTuple[K2, V2]])[Key <: String](using
        @implicitNotFound("join: no column named ${Key} on the left")
        evL: IsColumn[Key, K] =:= true,
        @implicitNotFound("join: no column named ${Key} on the right")
        evR: IsColumn[Key, K2] =:= true,
        @implicitNotFound("join: column ${Key} has a different type on each side")
        evT: GetTypeAtName[K, Key, V] =:= GetTypeAtName[K2, Key, V2],
        key: ValueOf[Key]
    ): Iterator[NamedTuple[Tuple.Concat[K, DropOneName[K2, Key]], Tuple.Concat[NarrowKey[K, V, Key], DropOneTypeAtName[K2, Key, V2]]]] =
      checkNoSharedNames[K, DropOneName[K2, Key], "join"]
      joinCore[K, V, K2, V2, Tuple.Concat[K, DropOneName[K2, Key]], Tuple.Concat[NarrowKey[K, V, Key], DropOneTypeAtName[K2, Key, V2]]](
        itr,
        that,
        key.value,
        key.value,
        false,
        isOptionKey[GetTypeAtName[K, Key, V]]
      )
    end join

    /** Inner join where the key is named differently on each side. See [[join]] for everything else.
      * {{{
      * orders.joinOn(customers)["customer_id", "id"]
      * }}}
      * The output keeps the *left* key's name; the right key column is dropped, since the left table's columns are carried over whole.
      */
    inline def joinOn[K2 <: Tuple, V2 <: Tuple](that: IterableOnce[NamedTuple[K2, V2]])[LeftKey <: String, RightKey <: String](using
        @implicitNotFound("joinOn: no column named ${LeftKey} on the left")
        evL: IsColumn[LeftKey, K] =:= true,
        @implicitNotFound("joinOn: no column named ${RightKey} on the right")
        evR: IsColumn[RightKey, K2] =:= true,
        @implicitNotFound("joinOn: key ${LeftKey} and key ${RightKey} have different types")
        evT: GetTypeAtName[K, LeftKey, V] =:= GetTypeAtName[K2, RightKey, V2],
        lk: ValueOf[LeftKey],
        rk: ValueOf[RightKey]
    ): Iterator[NamedTuple[Tuple.Concat[K, DropOneName[K2, RightKey]], Tuple.Concat[NarrowKey[K, V, LeftKey], DropOneTypeAtName[K2, RightKey, V2]]]] =
      checkNoSharedNames[K, DropOneName[K2, RightKey], "joinOn"]
      joinCore[K, V, K2, V2, Tuple.Concat[K, DropOneName[K2, RightKey]], Tuple.Concat[NarrowKey[K, V, LeftKey], DropOneTypeAtName[K2, RightKey, V2]]](
        itr,
        that,
        lk.value,
        rk.value,
        false,
        isOptionKey[GetTypeAtName[K, LeftKey, V]]
      )
    end joinOn

    /** Left outer join on a column of the same name in both tables - every left row survives, and the right hand columns become `Option`.
      * {{{
      * orders.leftJoin(customers)["custId"]  // (custId: Int, qty: Int, name: Option[String])
      * }}}
      * A right hand column that is *already* an `Option` stays as it is rather than nesting, which does mean an unmatched row and a matched row holding `None` look the same.
      */
    inline def leftJoin[K2 <: Tuple, V2 <: Tuple](that: IterableOnce[NamedTuple[K2, V2]])[Key <: String](using
        @implicitNotFound("leftJoin: no column named ${Key} on the left")
        evL: IsColumn[Key, K] =:= true,
        @implicitNotFound("leftJoin: no column named ${Key} on the right")
        evR: IsColumn[Key, K2] =:= true,
        @implicitNotFound("leftJoin: column ${Key} has a different type on each side")
        evT: GetTypeAtName[K, Key, V] =:= GetTypeAtName[K2, Key, V2],
        key: ValueOf[Key]
    ): Iterator[NamedTuple[Tuple.Concat[K, DropOneName[K2, Key]], Tuple.Concat[V, Optionalize[DropOneTypeAtName[K2, Key, V2]]]]] =
      checkNoSharedNames[K, DropOneName[K2, Key], "leftJoin"]
      joinCore[K, V, K2, V2, Tuple.Concat[K, DropOneName[K2, Key]], Tuple.Concat[V, Optionalize[DropOneTypeAtName[K2, Key, V2]]]](itr, that, key.value, key.value, true, false)
    end leftJoin

    /** Left outer join where the key is named differently on each side. See [[leftJoin]] and [[joinOn]]. */
    inline def leftJoinOn[K2 <: Tuple, V2 <: Tuple](that: IterableOnce[NamedTuple[K2, V2]])[LeftKey <: String, RightKey <: String](using
        @implicitNotFound("leftJoinOn: no column named ${LeftKey} on the left")
        evL: IsColumn[LeftKey, K] =:= true,
        @implicitNotFound("leftJoinOn: no column named ${RightKey} on the right")
        evR: IsColumn[RightKey, K2] =:= true,
        @implicitNotFound("leftJoinOn: key ${LeftKey} and key ${RightKey} have different types")
        evT: GetTypeAtName[K, LeftKey, V] =:= GetTypeAtName[K2, RightKey, V2],
        lk: ValueOf[LeftKey],
        rk: ValueOf[RightKey]
    ): Iterator[NamedTuple[Tuple.Concat[K, DropOneName[K2, RightKey]], Tuple.Concat[V, Optionalize[DropOneTypeAtName[K2, RightKey, V2]]]]] =
      checkNoSharedNames[K, DropOneName[K2, RightKey], "leftJoinOn"]
      joinCore[K, V, K2, V2, Tuple.Concat[K, DropOneName[K2, RightKey]], Tuple.Concat[V, Optionalize[DropOneTypeAtName[K2, RightKey, V2]]]](
        itr,
        that,
        lk.value,
        rk.value,
        true,
        false
      )
    end leftJoinOn
  end extension

  extension [CC[X] <: Iterable[X], K <: Tuple, V <: Tuple](nt: CC[NamedTuple[K, V]])
    inline def toColumnOriented: NamedTuple[K, Tuple.Map[V, CC]] =
      import scala.compiletime.ops.int.*
      import scala.compiletime.constValue

      // Convert to List of tuples for easier processing
      val rowList = nt.toList
      val size = rowList.size

      if size == 0 then
        // Handle empty case - create empty collections for each column type
        val emptyTuple = createEmptyTranspose[V]
        emptyTuple.withNames[K]
      else
        // Get all values for each column position
        val transposedTuple = transposeValues[V](rowList, 0)
        transposedTuple.withNames[K]
      end if
    end toColumnOriented

    // Helper to create empty collections for each type in the tuple
    private inline def createEmptyTranspose[Vs <: Tuple]: Tuple.Map[Vs, CC] =
      inline erasedValue[Vs] match
        case _: EmptyTuple => EmptyTuple
        case _: (h *: t)   =>
          val bf = summonInline[BuildFrom[CC[NamedTuple[K, V]], h, CC[h]]]
          bf.fromSpecific(nt)(Iterator.empty) *: createEmptyTranspose[t]

    // Helper to transpose values recursively
    private inline def transposeValues[Vs <: Tuple](rows: List[NamedTuple[K, V]], colIndex: Int): Tuple.Map[Vs, CC] =
      inline erasedValue[Vs] match
        case _: EmptyTuple => EmptyTuple
        case _: (h *: t)   =>
          val bf = summonInline[BuildFrom[CC[NamedTuple[K, V]], h, CC[h]]]
          val columnValues = rows.map(_.toTuple.productElement(colIndex).asInstanceOf[h])
          bf.fromSpecific(nt)(columnValues) *: transposeValues[t](rows, colIndex + 1)

    inline def toColumnOrientedAs[Target[_]]: NamedTuple[K, Tuple.Map[V, Target]] =
      import scala.compiletime.ops.int.*
      import scala.compiletime.constValue

      // Convert to List of tuples for easier processing
      val rowList = nt.toList
      val size = rowList.size

      if size == 0 then
        // Handle empty case - create empty collections for each column type
        val emptyTuple = createEmptyTransposeTarget[V, Target]
        emptyTuple.withNames[K]
      else
        // Get all values for each column position
        val transposedTuple = transposeValuesTarget[V, Target](rowList, 0)
        transposedTuple.withNames[K]
      end if
    end toColumnOrientedAs

    // Helper to create empty collections for each type in the tuple with target collection type
    private inline def createEmptyTransposeTarget[Vs <: Tuple, Target[_]]: Tuple.Map[Vs, Target] =
      inline erasedValue[Vs] match
        case _: EmptyTuple => EmptyTuple
        case _: (h *: t)   =>
          val factory = summonInline[Factory[h, Target[h]]]
          factory.fromSpecific(Iterator.empty) *: createEmptyTransposeTarget[t, Target]

    // Helper to transpose values recursively with target collection type
    private inline def transposeValuesTarget[Vs <: Tuple, Target[_]](rows: List[NamedTuple[K, V]], colIndex: Int): Tuple.Map[Vs, Target] =
      inline erasedValue[Vs] match
        case _: EmptyTuple => EmptyTuple
        case _: (h *: t)   =>
          val factory = summonInline[Factory[h, Target[h]]]
          val columnValues = rows.map(_.toTuple.productElement(colIndex).asInstanceOf[h])
          factory.fromSpecific(columnValues) *: transposeValuesTarget[t, Target](rows, colIndex + 1)

    inline def column[S <: String](using
        @implicitNotFound("Column ${S} not found")
        ev: IsColumn[S, K] =:= true,
        bf: BuildFrom[
          CC[NamedTuple[K, V]],
          NamedTuple.Elem[NamedTuple.NamedTuple[K, V], IdxAtName[S, K]],
          CC[NamedTuple.Elem[NamedTuple.NamedTuple[K, V], IdxAtName[S, K]]]
        ]
    ): CC[NamedTuple.Elem[NamedTuple.NamedTuple[K, V], IdxAtName[S, K]]] =
      bf.fromSpecific(nt)(nt.view.map(x => x.toTuple(constValue[IdxAtName[S, K]])))
    end column

    def addColumn[S <: String, A](fct: (tup: NamedTuple.NamedTuple[K, V]) => A)(using
        bf: BuildFrom[CC[NamedTuple[K, V]], NamedTuple[Tuple.Append[K, S], Tuple.Append[V, A]], CC[NamedTuple[Tuple.Append[K, S], Tuple.Append[V, A]]]]
    ): CC[NamedTuple[Tuple.Append[K, S], Tuple.Append[V, A]]] =
      bf.fromSpecific(nt)(nt.view.map { (tup: NamedTuple[K, V]) =>
        (tup.toTuple :* fct(tup)).withNames[Tuple.Append[K, S]]
      })

    inline def numericCols(using
        bf: BuildFrom[
          CC[NamedTuple[K, V]],
          NamedTuple[SelectFromTuple[K, NumericColsIdx[V]], GetTypesAtNames[K, SelectFromTuple[K, NumericColsIdx[V]], V]],
          CC[NamedTuple[SelectFromTuple[K, NumericColsIdx[V]], GetTypesAtNames[K, SelectFromTuple[K, NumericColsIdx[V]], V]]]
        ]
    ): CC[
      NamedTuple[
        SelectFromTuple[K, NumericColsIdx[V]],
        GetTypesAtNames[K, SelectFromTuple[K, NumericColsIdx[V]], V]
      ]
    ] =
      val ev1 = summonInline[AllAreColumns[SelectFromTuple[K, NumericColsIdx[V]], K] =:= true]
      columns[SelectFromTuple[K, NumericColsIdx[V]]](using ev1)
    end numericCols

    inline def nonNumericCols(using
        bf: BuildFrom[
          CC[NamedTuple[K, V]],
          NamedTuple[SelectFromTuple[K, Negate[NumericColsIdx[V]]], GetTypesAtNames[K, SelectFromTuple[K, Negate[NumericColsIdx[V]]], V]],
          CC[NamedTuple[SelectFromTuple[K, Negate[NumericColsIdx[V]]], GetTypesAtNames[K, SelectFromTuple[K, Negate[NumericColsIdx[V]]], V]]]
        ]
    ): CC[
      NamedTuple[
        SelectFromTuple[K, Negate[NumericColsIdx[V]]],
        GetTypesAtNames[K, SelectFromTuple[K, Negate[NumericColsIdx[V]]], V]
      ]
    ] =
      val ev1 = summonInline[
        AllAreColumns[SelectFromTuple[K, Negate[NumericColsIdx[V]]], K] =:= true
      ]
      columns[SelectFromTuple[K, Negate[NumericColsIdx[V]]]](using ev1)
    end nonNumericCols

    inline def columns[ST <: Tuple](using
        @implicitNotFound("Not all columns in ${ST} are present in ${K}")
        ev: AllAreColumns[ST, K] =:= true,
        bf: BuildFrom[CC[NamedTuple[K, V]], NamedTuple[ST, GetTypesAtNames[K, ST, V]], CC[NamedTuple[ST, GetTypesAtNames[K, ST, V]]]]
    ): CC[
      NamedTuple[
        ST,
        GetTypesAtNames[K, ST, V]
      ]
    ] =
      val headers = constValueTuple[K].toList.map(_.toString())
      val selectedHeaders = constValueTuple[ST].toList.map(_.toString())
      val idxes = selectedHeaders.map(headers.indexOf(_)).filterNot(_ == -1)

      bf.fromSpecific(nt)(nt.view.map { (x: NamedTuple[K, V]) =>
        val tuple = x.toTuple
        val selected: Tuple = idxes.foldRight(EmptyTuple: Tuple) { (idx, acc) =>
          tuple(idx) *: acc
        }
        selected
          .withNames[ST]
          .asInstanceOf[NamedTuple[ST, GetTypesAtNames[K, ST, V]]]
      })
    end columns

    inline def dropColumn[S <: String](using
        @implicitNotFound("Column ${S} not found")
        ev: IsColumn[S, K] =:= true,
        s: ValueOf[S],
        bf: BuildFrom[CC[NamedTuple[K, V]], NamedTuple[DropOneName[K, S], DropOneTypeAtName[K, S, V]], CC[NamedTuple[DropOneName[K, S], DropOneTypeAtName[K, S, V]]]]
    ): CC[NamedTuple[DropOneName[K, S], DropOneTypeAtName[K, S, V]]] =
      val headers = constValueTuple[K].toList.map(_.toString())
      val idx = headers.indexOf(s.value)

      bf.fromSpecific(nt)(nt.view.map { (x: NamedTuple[K, V]) =>
        val (head, tail) = x.toTuple.splitAt(idx)
        head match
          case x: EmptyTuple => tail.tail.withNames[DropOneName[K, S]].asInstanceOf[NamedTuple[DropOneName[K, S], DropOneTypeAtName[K, S, V]]]
          case _             => (head ++ tail.tail).withNames[DropOneName[K, S]].asInstanceOf[NamedTuple[DropOneName[K, S], DropOneTypeAtName[K, S, V]]]
        end match
      })
    end dropColumn

    inline def mapColumn[S <: String, A](fct: GetTypeAtName[K, S, V] => A)(using
        @implicitNotFound("Column ${S} not found")
        ev: IsColumn[S, K] =:= true,
        s: ValueOf[S],
        bf: BuildFrom[CC[NamedTuple[K, V]], NamedTuple[K, ReplaceOneTypeAtName[K, S, V, A]], CC[NamedTuple[K, ReplaceOneTypeAtName[K, S, V, A]]]]
    ): CC[NamedTuple[K, ReplaceOneTypeAtName[K, S, V, A]]] =
      val headers = constValueTuple[K].toList.map(_.toString())
      val idx = headers.indexOf(s.value)
      if idx == -1 then ???
      end if

      bf.fromSpecific(nt)(nt.view.map { (x: NamedTuple[K, V]) =>
        val tup = x.toTuple
        val typ = tup(idx).asInstanceOf[GetTypeAtName[K, S, V]]
        val mapped = fct(typ)
        val (head, tail) = x.toTuple.splitAt(idx)
        (head ++ mapped *: tail.tail).withNames[K].asInstanceOf[NamedTuple[K, ReplaceOneTypeAtName[K, S, V, A]]]
      })
    end mapColumn

    def forceColumnType[S <: String, A](using
        bf: BuildFrom[CC[NamedTuple[K, V]], NamedTuple[K, ReplaceOneTypeAtName[K, S, V, A]], CC[NamedTuple[K, ReplaceOneTypeAtName[K, S, V, A]]]]
    ): CC[NamedTuple[K, ReplaceOneTypeAtName[K, S, V, A]]] =
      bf.fromSpecific(nt)(nt.view.map(_.asInstanceOf[NamedTuple[K, ReplaceOneTypeAtName[K, S, V, A]]]))

    /** Attach a display tag (see `ColumnFormat`) to a column, changing how it renders without touching the data. */
    def formatColumn[S <: String, Tag](using
        @implicitNotFound("Column ${S} not found")
        ev: IsColumn[S, K] =:= true,
        @implicitNotFound("Format tag ${Tag} is not compatible with the type of column ${S}")
        compat: Tag <:< Unwrapped[GetTypeAtName[K, S, V]],
        bf: BuildFrom[
          CC[NamedTuple[K, V]],
          NamedTuple[K, ReplaceOneTypeAtName[K, S, V, Tagged[GetTypeAtName[K, S, V], Tag]]],
          CC[
            NamedTuple[K, ReplaceOneTypeAtName[K, S, V, Tagged[GetTypeAtName[K, S, V], Tag]]]
          ]
        ]
    ): CC[NamedTuple[K, ReplaceOneTypeAtName[K, S, V, Tagged[GetTypeAtName[K, S, V], Tag]]]] =
      bf.fromSpecific(nt)(nt.view.map(_.asInstanceOf[NamedTuple[K, ReplaceOneTypeAtName[K, S, V, Tagged[GetTypeAtName[K, S, V], Tag]]]]))

    /** Attach a whole spec of display tags in one call, so that the look of a table can be named, reused, and applied at the point of display.
      *
      * The spec is a named tuple *type* - names are columns, values are tags from `ColumnFormat`. Equivalent to the corresponding chain of `formatColumn` calls, and like them a
      * cast:
      * {{{
      * type PriceSheet = (`Bid Spread`: Decimals[2], `Offer Spread`: Decimals[2], EL: Percent[1])
      * prices.style[PriceSheet].ptbln
      * }}}
      * A column the spec does not mention is left alone, so one spec can serve a family of tables.
      */
    inline def style[Spec <: AnyNamedTuple]: CC[NamedTuple[K, Styled[K, V, Spec]]] =
      checkSpecNames[K, V, Names[Spec], "style"]
      checkStyleTags[K, V, Names[Spec], DropNames[Spec]]
      nt.asInstanceOf[CC[NamedTuple[K, Styled[K, V, Spec]]]]
    end style

    /** Force the types of several columns at once, as [[forceColumnType]] does for one. See the `Iterator` overload for the spec's shape. */
    inline def retype[Spec <: AnyNamedTuple]: CC[NamedTuple[K, Retyped[K, V, Spec]]] =
      checkSpecNames[K, V, Names[Spec], "retype"]
      nt.asInstanceOf[CC[NamedTuple[K, Retyped[K, V, Spec]]]]
    end retype

    /** Map several columns at once, as [[mapColumn]] does for one. See the `Iterator` overload for the spec's shape. */
    inline def mapColumns[Spec <: AnyNamedTuple](spec: Spec)(using
        bf: BuildFrom[CC[NamedTuple[K, V]], NamedTuple[K, MappedCols[K, V, Spec]], CC[NamedTuple[K, MappedCols[K, V, Spec]]]]
    ): CC[NamedTuple[K, MappedCols[K, V, Spec]]] =
      checkSpecNames[K, V, Names[Spec], "mapColumns"]
      checkMapFns[K, V, Names[Spec], DropNames[Spec]]
      val plan = specPlan[K, Spec](spec)
      bf.fromSpecific(nt)(nt.view.map(row => applyPlan(plan, row).withNames[K].asInstanceOf[NamedTuple[K, MappedCols[K, V, Spec]]]))
    end mapColumns

    def renameColumn[From <: String, To <: String](using
        ev: IsColumn[From, K] =:= true,
        bf: BuildFrom[CC[NamedTuple[K, V]], NamedTuple[ReplaceOneName[K, From, To], V], CC[NamedTuple[ReplaceOneName[K, From, To], V]]]
    ): CC[NamedTuple[ReplaceOneName[K, From, To], V]] =
      bf.fromSpecific(nt)(nt.view.map(_.withNames[ReplaceOneName[K, From, To]].asInstanceOf[NamedTuple[ReplaceOneName[K, From, To], V]]))

    /** Inner join on a column of the same name in both tables, preserving the collection type. See the `Iterator` overload for the semantics.
      * {{{
      * orders.join(customers)["custId"]
      * }}}
      */
    inline def join[K2 <: Tuple, V2 <: Tuple](that: IterableOnce[NamedTuple[K2, V2]])[Key <: String](using
        @implicitNotFound("join: no column named ${Key} on the left")
        evL: IsColumn[Key, K] =:= true,
        @implicitNotFound("join: no column named ${Key} on the right")
        evR: IsColumn[Key, K2] =:= true,
        @implicitNotFound("join: column ${Key} has a different type on each side")
        evT: GetTypeAtName[K, Key, V] =:= GetTypeAtName[K2, Key, V2],
        key: ValueOf[Key],
        bf: BuildFrom[
          CC[NamedTuple[K, V]],
          NamedTuple[Tuple.Concat[K, DropOneName[K2, Key]], Tuple.Concat[NarrowKey[K, V, Key], DropOneTypeAtName[K2, Key, V2]]],
          CC[NamedTuple[Tuple.Concat[K, DropOneName[K2, Key]], Tuple.Concat[NarrowKey[K, V, Key], DropOneTypeAtName[K2, Key, V2]]]]
        ]
    ): CC[NamedTuple[Tuple.Concat[K, DropOneName[K2, Key]], Tuple.Concat[NarrowKey[K, V, Key], DropOneTypeAtName[K2, Key, V2]]]] =
      checkNoSharedNames[K, DropOneName[K2, Key], "join"]
      bf.fromSpecific(nt)(
        joinCore[K, V, K2, V2, Tuple.Concat[K, DropOneName[K2, Key]], Tuple.Concat[NarrowKey[K, V, Key], DropOneTypeAtName[K2, Key, V2]]](
          nt.iterator,
          that,
          key.value,
          key.value,
          false,
          isOptionKey[GetTypeAtName[K, Key, V]]
        )
      )
    end join

    /** Inner join where the key is named differently on each side, preserving the collection type. See the `Iterator` overload. */
    inline def joinOn[K2 <: Tuple, V2 <: Tuple](that: IterableOnce[NamedTuple[K2, V2]])[LeftKey <: String, RightKey <: String](using
        @implicitNotFound("joinOn: no column named ${LeftKey} on the left")
        evL: IsColumn[LeftKey, K] =:= true,
        @implicitNotFound("joinOn: no column named ${RightKey} on the right")
        evR: IsColumn[RightKey, K2] =:= true,
        @implicitNotFound("joinOn: key ${LeftKey} and key ${RightKey} have different types")
        evT: GetTypeAtName[K, LeftKey, V] =:= GetTypeAtName[K2, RightKey, V2],
        lk: ValueOf[LeftKey],
        rk: ValueOf[RightKey],
        bf: BuildFrom[
          CC[NamedTuple[K, V]],
          NamedTuple[Tuple.Concat[K, DropOneName[K2, RightKey]], Tuple.Concat[NarrowKey[K, V, LeftKey], DropOneTypeAtName[K2, RightKey, V2]]],
          CC[NamedTuple[Tuple.Concat[K, DropOneName[K2, RightKey]], Tuple.Concat[NarrowKey[K, V, LeftKey], DropOneTypeAtName[K2, RightKey, V2]]]]
        ]
    ): CC[NamedTuple[Tuple.Concat[K, DropOneName[K2, RightKey]], Tuple.Concat[NarrowKey[K, V, LeftKey], DropOneTypeAtName[K2, RightKey, V2]]]] =
      checkNoSharedNames[K, DropOneName[K2, RightKey], "joinOn"]
      bf.fromSpecific(nt)(
        joinCore[K, V, K2, V2, Tuple.Concat[K, DropOneName[K2, RightKey]], Tuple.Concat[NarrowKey[K, V, LeftKey], DropOneTypeAtName[K2, RightKey, V2]]](
          nt.iterator,
          that,
          lk.value,
          rk.value,
          false,
          isOptionKey[GetTypeAtName[K, LeftKey, V]]
        )
      )
    end joinOn

    /** Left outer join on a column of the same name in both tables, preserving the collection type. See the `Iterator` overload. */
    inline def leftJoin[K2 <: Tuple, V2 <: Tuple](that: IterableOnce[NamedTuple[K2, V2]])[Key <: String](using
        @implicitNotFound("leftJoin: no column named ${Key} on the left")
        evL: IsColumn[Key, K] =:= true,
        @implicitNotFound("leftJoin: no column named ${Key} on the right")
        evR: IsColumn[Key, K2] =:= true,
        @implicitNotFound("leftJoin: column ${Key} has a different type on each side")
        evT: GetTypeAtName[K, Key, V] =:= GetTypeAtName[K2, Key, V2],
        key: ValueOf[Key],
        bf: BuildFrom[
          CC[NamedTuple[K, V]],
          NamedTuple[Tuple.Concat[K, DropOneName[K2, Key]], Tuple.Concat[V, Optionalize[DropOneTypeAtName[K2, Key, V2]]]],
          CC[NamedTuple[Tuple.Concat[K, DropOneName[K2, Key]], Tuple.Concat[V, Optionalize[DropOneTypeAtName[K2, Key, V2]]]]]
        ]
    ): CC[NamedTuple[Tuple.Concat[K, DropOneName[K2, Key]], Tuple.Concat[V, Optionalize[DropOneTypeAtName[K2, Key, V2]]]]] =
      checkNoSharedNames[K, DropOneName[K2, Key], "leftJoin"]
      bf.fromSpecific(nt)(
        joinCore[K, V, K2, V2, Tuple.Concat[K, DropOneName[K2, Key]], Tuple.Concat[V, Optionalize[DropOneTypeAtName[K2, Key, V2]]]](
          nt.iterator,
          that,
          key.value,
          key.value,
          true,
          false
        )
      )
    end leftJoin

    /** Left outer join where the key is named differently on each side, preserving the collection type. See the `Iterator` overload. */
    inline def leftJoinOn[K2 <: Tuple, V2 <: Tuple](that: IterableOnce[NamedTuple[K2, V2]])[LeftKey <: String, RightKey <: String](using
        @implicitNotFound("leftJoinOn: no column named ${LeftKey} on the left")
        evL: IsColumn[LeftKey, K] =:= true,
        @implicitNotFound("leftJoinOn: no column named ${RightKey} on the right")
        evR: IsColumn[RightKey, K2] =:= true,
        @implicitNotFound("leftJoinOn: key ${LeftKey} and key ${RightKey} have different types")
        evT: GetTypeAtName[K, LeftKey, V] =:= GetTypeAtName[K2, RightKey, V2],
        lk: ValueOf[LeftKey],
        rk: ValueOf[RightKey],
        bf: BuildFrom[
          CC[NamedTuple[K, V]],
          NamedTuple[Tuple.Concat[K, DropOneName[K2, RightKey]], Tuple.Concat[V, Optionalize[DropOneTypeAtName[K2, RightKey, V2]]]],
          CC[NamedTuple[Tuple.Concat[K, DropOneName[K2, RightKey]], Tuple.Concat[V, Optionalize[DropOneTypeAtName[K2, RightKey, V2]]]]]
        ]
    ): CC[NamedTuple[Tuple.Concat[K, DropOneName[K2, RightKey]], Tuple.Concat[V, Optionalize[DropOneTypeAtName[K2, RightKey, V2]]]]] =
      checkNoSharedNames[K, DropOneName[K2, RightKey], "leftJoinOn"]
      bf.fromSpecific(nt)(
        joinCore[K, V, K2, V2, Tuple.Concat[K, DropOneName[K2, RightKey]], Tuple.Concat[V, Optionalize[DropOneTypeAtName[K2, RightKey, V2]]]](
          nt.iterator,
          that,
          lk.value,
          rk.value,
          true,
          false
        )
      )
    end leftJoinOn

  end extension
end NamedTupleIteratorExtensions
