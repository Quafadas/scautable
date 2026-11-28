package io.github.quafadas.scautable

import scala.NamedTuple.*
import scala.NamedTuple.withNames
import scala.collection.mutable
import scala.compiletime.*

import io.github.quafadas.scautable.ColumnTyped.*

/** Reducers for `groupBy[...].agg[Spec]`. Phantom types only - a spec is a named tuple *type*, and nothing here is ever instantiated.
  * {{{
  * emps.groupBy["dept"].agg[(n: Agg.Count, total: Agg.Sum["salary"], avg: Agg.Mean["salary"])]
  * }}}
  */
object Agg:
  /** Rows in the group, as pandas `size`. `Int`. */
  final class Count

  /** Sum of a numeric column, skipping `None`. Same type as the column, with any `Option` removed. */
  final class Sum[C <: String]

  /** Arithmetic mean of a numeric column, skipping `None`. `Double`; `NaN` if every value is missing. */
  final class Mean[C <: String]

  /** Smallest value, by the column's `Ordering`. An optional column skips `None` and stays optional. */
  final class Min[C <: String]

  /** Largest value, by the column's `Ordering`. An optional column skips `None` and stays optional. */
  final class Max[C <: String]

  /** The value in the group's first row, exactly as stored. */
  final class First[C <: String]
end Agg

object GroupByExtensions:

  /** A running reduction over one group: seeded from the group's first row, stepped with each later row, finished once the input is exhausted.
    *
    * The accumulator is untyped because reducers of different types share one array per group. The inline code that builds a reducer is the only place that knows its real type.
    */
  final class Reducer(val init: Tuple => Any, val step: (Any, Tuple) => Any, val finish: Any => Any)

  /** Running sum and count for [[Agg.Mean]], mutated in place so a row costs no allocation. */
  final private class MeanAcc(var sum: Double, var n: Long)

  /** Inline code cannot construct a private class at the call site, but it can call a private method. */
  private def freshMean: Any = MeanAcc(0, 0)

  private def meanStep(acc: Any, x: Double): MeanAcc =
    val m = acc.asInstanceOf[MeanAcc]
    m.sum += x
    m.n += 1
    m
  end meanStep

  private def meanFinish(acc: Any): Double =
    val m = acc.asInstanceOf[MeanAcc]
    if m.n == 0 then Double.NaN else m.sum / m.n
    end if
  end meanFinish

  /** Keep the smaller (or, for `max`, the larger) of two present values. */
  private def pick[A](o: Ordering[A], max: Boolean)(a: A, b: A): A = if max then o.max(a, b) else o.min(a, b)

  /** The index of column `C`, failing compilation with `Who`'s name if there is none. */
  private inline def colIdx[K <: Tuple, C, Who <: String]: Int =
    inline erasedValue[NameIn[K, C]] match
      case _: true => constValueTuple[K].toList.indexOf(constValue[C])
      case _       => error(constValue[Who] + ": no column named " + constValue[C & String])

  private inline def numeric[T, R <: String, C]: Numeric[T] =
    summonFrom {
      case n: Numeric[T] => n
      case _             => error("agg: " + constValue[R] + "[\"" + constValue[C & String] + "\"] needs a numeric column")
    }

  private inline def ordering[T, R <: String, C]: Ordering[T] =
    summonFrom {
      case o: Ordering[T] => o
      case _              => error("agg: " + constValue[R] + "[\"" + constValue[C & String] + "\"] needs a column with an Ordering")
    }

  /** Build the runtime half of one reducer. Every arithmetic step goes through the stdlib's `Numeric` or `Ordering`. */
  private inline def reducer[K <: Tuple, V <: Tuple, R]: Reducer =
    inline erasedValue[R] match
      case _: Agg.Count => Reducer(_ => 1, (acc, _) => acc.asInstanceOf[Int] + 1, identity)

      case _: Agg.First[c] =>
        val i = colIdx[K, c, "agg"]
        Reducer(_.productElement(i), (acc, _) => acc, identity)

      case _: Agg.Sum[c] =>
        val i = colIdx[K, c, "agg"]
        inline erasedValue[ColTypeAtName[K, V, c]] match
          case _: Option[a] =>
            val n = numeric[a, "Sum", c]
            def cell(row: Tuple) = row.productElement(i).asInstanceOf[Option[a]]
            Reducer(row => cell(row).getOrElse(n.zero), (acc, row) => cell(row).fold(acc)(n.plus(acc.asInstanceOf[a], _)), identity)
          case _ =>
            type T = ColTypeAtName[K, V, c]
            val n = numeric[T, "Sum", c]
            Reducer(_.productElement(i), (acc, row) => n.plus(acc.asInstanceOf[T], row.productElement(i).asInstanceOf[T]), identity)
        end match

      case _: Agg.Mean[c] =>
        val i = colIdx[K, c, "agg"]
        inline erasedValue[ColTypeAtName[K, V, c]] match
          case _: Option[a] =>
            val n = numeric[a, "Mean", c]
            def step(acc: Any, row: Tuple) = row.productElement(i).asInstanceOf[Option[a]].fold(acc)(x => meanStep(acc, n.toDouble(x)))
            Reducer(row => step(freshMean, row), step, meanFinish)
          case _ =>
            type T = ColTypeAtName[K, V, c]
            val n = numeric[T, "Mean", c]
            def step(acc: Any, row: Tuple) = meanStep(acc, n.toDouble(row.productElement(i).asInstanceOf[T]))
            Reducer(row => step(freshMean, row), step, meanFinish)
        end match

      case _: Agg.Min[c] => extreme[K, V, c, "Min"](max = false)
      case _: Agg.Max[c] => extreme[K, V, c, "Max"](max = true)

  private inline def extreme[K <: Tuple, V <: Tuple, C, R <: String](max: Boolean): Reducer =
    val i = colIdx[K, C, "agg"]
    inline erasedValue[ColTypeAtName[K, V, C]] match
      case _: Option[a] =>
        val keep = pick(ordering[a, R, C], max)
        def cell(row: Tuple) = row.productElement(i).asInstanceOf[Option[a]]
        Reducer(
          cell,
          (acc, row) =>
            (acc.asInstanceOf[Option[a]], cell(row)) match
              case (Some(x), Some(y)) => Some(keep(x, y))
              case (None, y)          => y
              case (x, None)          => x
          ,
          identity
        )
      case _ =>
        type T = ColTypeAtName[K, V, C]
        val keep = pick(ordering[T, R, C], max)
        Reducer(_.productElement(i), (acc, row) => keep(acc.asInstanceOf[T], row.productElement(i).asInstanceOf[T]), identity)
    end match
  end extreme

  private inline def reducers[K <: Tuple, V <: Tuple, Rs <: Tuple]: Array[Reducer] =
    inline erasedValue[Rs] match
      case _: EmptyTuple => Array.empty
      case _: (r *: rs)  => reducer[K, V, r] +: reducers[K, V, rs]

  /** Fail compilation unless every key is a column of `K`. */
  private inline def checkKeys[K <: Tuple, Keys <: Tuple]: Unit =
    inline erasedValue[Keys] match
      case _: EmptyTuple => ()
      case _: (k *: ks)  =>
        inline erasedValue[NameIn[K, k]] match
          case _: true => checkKeys[K, ks]
          case _       => error("groupBy: no column named " + constValue[k & String])

  /** Fail compilation if an output column reuses a key's name. `Tuple.Disjoint` would catch it too, but could not say which name. */
  private inline def checkNoKeyClash[Keys <: Tuple, Out <: Tuple, Who <: String]: Unit =
    inline erasedValue[Out] match
      case _: EmptyTuple => ()
      case _: (n *: ns)  =>
        inline erasedValue[NameIn[Keys, n]] match
          case _: true => error(constValue[Who] + ": output column " + constValue[n & String] + " has the same name as a group key")
          case _       => checkNoKeyClash[Keys, ns, Who]

  /** The rows of a table and the columns to group them by. Does nothing until `agg` or `aggWith` drains it - and, like the iterator inside it, can only be drained once. */
  final class Grouped[K <: Tuple, V <: Tuple, Keys <: Tuple](rows: Iterator[NamedTuple[K, V]], keyIdx: Array[Int]):

    /** The grouping key of a row. A single key is the cell itself, sparing a tuple allocation per row. */
    private def keyOf(row: Tuple): Any =
      if keyIdx.length == 1 then row.productElement(keyIdx(0))
      else Tuple.fromArray(keyIdx.map(row.productElement(_).asInstanceOf[Object]))

    private[scautable] def keyCells(key: Any): Array[Object] =
      if keyIdx.length == 1 then Array(key.asInstanceOf[Object])
      else key.asInstanceOf[Tuple].toArray

    /** One pass, holding one accumulator per reducer per group - memory grows with the number of groups, not of rows.
      *
      * Groups come out in the order their keys were first seen. A `None` key is a group like any other.
      */
    private[scautable] def reduce(fns: Array[Reducer]): Seq[Tuple] =
      // `Map` alone would resolve to `NamedTuple.Map`.
      val groups = mutable.LinkedHashMap.empty[Any, Array[Any]]
      rows.foreach { nt =>
        val row = nt.toTuple
        val key = keyOf(row)
        groups.get(key) match
          case Some(accs) =>
            var j = 0
            while j < accs.length do
              accs(j) = fns(j).step(accs(j), row)
              j += 1
            end while
          case None => groups.update(key, fns.map(_.init(row)))
        end match
      }
      groups.iterator.map { (key, accs) =>
        Tuple.fromArray(keyCells(key) ++ accs.indices.map(j => fns(j).finish(accs(j)).asInstanceOf[Object]))
      }.toSeq
    end reduce

    /** Rows bucketed whole by key, for [[aggWith]]. */
    private[scautable] def buckets: Seq[(Any, IndexedSeq[Tuple])] =
      val groups = mutable.LinkedHashMap.empty[Any, mutable.ArrayBuffer[Tuple]]
      rows.foreach { nt =>
        val row = nt.toTuple
        groups.getOrElseUpdate(keyOf(row), mutable.ArrayBuffer.empty) += row
      }
      groups.iterator.map((k, g) => k -> g.toVector).toSeq
    end buckets

    /** Reduce each group with the typed reducers of `Spec`, whose names become the output columns.
      * {{{
      * emps.groupBy["dept"].agg[(n: Agg.Count, total: Agg.Sum["salary"], top: Agg.Max["salary"])]
      * // Seq[(dept: String, n: Int, total: Int, top: Int)]
      * }}}
      * Streams the input once, keeping only a running total per reducer per group.
      */
    inline def agg[Spec <: AnyNamedTuple]: Seq[Aggregated[K, V, Keys, Spec]] =
      checkNoKeyClash[Keys, Names[Spec], "agg"]
      reduce(reducers[K, V, DropNames[Spec]]).map(_.withNames[Tuple.Concat[Keys, Names[Spec]]].asInstanceOf[Aggregated[K, V, Keys, Spec]])
    end agg

    /** Reduce each group with an arbitrary function of its rows, which keep their column names - for anything the `Agg` reducers do not cover.
      * {{{
      * emps.groupBy["dept"].aggWith(g => (n = g.size, names = g.map(_.name).mkString(",")))
      * }}}
      * Unlike [[agg]], this holds every row in memory, since `f` is handed whole groups.
      */
    inline def aggWith[N <: Tuple, R <: Tuple](
        f: IndexedSeq[NamedTuple[K, V]] => NamedTuple[N, R]
    ): Seq[NamedTuple[Tuple.Concat[Keys, N], Tuple.Concat[ColTypesAtNames[K, V, Keys], R]]] =
      checkNoKeyClash[Keys, N, "aggWith"]
      buckets.map { (key, g) =>
        Tuple
          .fromArray(keyCells(key) ++ f(g.asInstanceOf[IndexedSeq[NamedTuple[K, V]]]).toTuple.toArray)
          .withNames[Tuple.Concat[Keys, N]]
          .asInstanceOf[NamedTuple[Tuple.Concat[Keys, N], Tuple.Concat[ColTypesAtNames[K, V, Keys], R]]]
      }
    end aggWith
  end Grouped

  extension [K <: Tuple, V <: Tuple](itr: Iterator[NamedTuple[K, V]])
    /** Group rows by one or more columns, ready for `agg` or `aggWith`.
      * {{{
      * emps.groupBy["dept"]
      * emps.groupBy[("dept", "region")]
      * }}}
      * Only on `Iterator`: collections have a `groupBy` member of their own, which would always win. Call `.iterator` on them first.
      */
    inline def groupBy[Key]: Grouped[K, V, KeyNames[Key]] =
      checkKeys[K, KeyNames[Key]]
      val headers = constValueTuple[K].toList
      Grouped(itr, constValueTuple[KeyNames[Key]].toList.map(headers.indexOf).toArray)
    end groupBy
  end extension
end GroupByExtensions
