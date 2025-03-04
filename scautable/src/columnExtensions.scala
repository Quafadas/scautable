package io.github.quafadas.scautable

import scala.quoted.*
import scala.io.Source
import scala.annotation.experimental
import NamedTuple.withNames
import scala.NamedTuple.*
import scala.collection.immutable.Stream.Empty
import scala.deriving.Mirror
import scala.io.BufferedSource
import scala.util.Using.Manager.Resource
import scala.compiletime.*
import scala.compiletime.ops.int.+
import fansi.Str
import scala.collection.View.FlatMap
import io.github.quafadas.scautable.ConsoleFormat.*
import ColumnTyped.*
import scala.math.Fractional.Implicits.*
import scala.annotation.implicitNotFound

object NamedTupleIteratorExtensions:
  val rand = new scala.util.Random

  extension [K, V, K1 <: Tuple & K, V1 <: Tuple & K](itr: Iterator[NamedTuple[K1, V1]])

    inline def numericTypeTest: (List[ConversionAcc], Long) =
      val headers = constValueTuple[K1].toList.map(_.toString())
      val headerAcc = headers.map(_ => ConversionAcc(0, 0, 0))

      itr.foldLeft[List[ConversionAcc] *: Long *: EmptyTuple]((headerAcc, 0L)) { case (acc: (List[ConversionAcc], Long), elem: NamedTuple[K1, V1]) =>
        val list = elem.toList
          .asInstanceOf[List[String]]
          .zip(acc._1)
          .map { case (str, acc) =>
            (
              ConversionAcc(
                acc.validInts + str.toIntOption.fold(0)(_ => 1),
                acc.validDoubles + str.toDoubleOption.fold(0)(_ => 1),
                acc.validLongs + str.toLongOption.fold(0)(_ => 1)
              )
            )
          }

        (list, acc._2 + 1L)

      }
    end numericTypeTest

    inline def formatTypeTest: String =
      val headers = constValueTuple[K1].toList.map(_.toString())
      val (asList, n) = numericTypeTest
      val intReport = (
        "int" *: listToTuple(
          for (acc <- asList) yield (acc.validInts / n.toDouble).formatAsPercentage
        )
      )
      val doubleReported = "doubles" *: listToTuple(
        for (acc <- asList) yield (acc.validDoubles / n.toDouble).formatAsPercentage
      )
      val longReported = "long" *: listToTuple(
        for (acc <- asList) yield (acc.validLongs / n.toDouble).formatAsPercentage
      )
      val recommendation = "recommendation" *: listToTuple(
        for (acc <- asList) yield recommendConversion(List(acc), n)
      )

      val ntList = Seq(
        intReport,
        doubleReported,
        longReported,
        recommendation
      )

      ConsoleFormat.consoleFormat_(headers = "conversion % to" +: headers, fancy = true, table = ntList)
    end formatTypeTest

    inline def showTypeTest: Unit =
      println(formatTypeTest)

    inline def sample(frac: Double, inline deterministic: Boolean = false): Iterator[NamedTuple[K1, V1]] =
      if deterministic then itr.zipWithIndex.filter { case (_, idx) => idx % (1 / frac) == 0 }.map(_._1)
      else itr.filter(_ => rand.nextDouble() < frac)

    inline def renameColumn[From <: String, To <: String](using
        @implicitNotFound("Column ${From} not found")
        ev: IsColumn[From, K1] =:= true,
        FROM: ValueOf[From],
        TO: ValueOf[To]
    ): Iterator[NamedTuple[ReplaceOneName[K1, From, To], V1]] =
      itr.map(_.withNames[ReplaceOneName[K1, From, To]].asInstanceOf[NamedTuple[ReplaceOneName[K1, From, To], V1]])

    inline def addColumn[S <: String, A](fct: (tup: NamedTuple.NamedTuple[K1, V1]) => A): Iterator[NamedTuple[S *: K1, A *: V1]] =
      itr.map { (tup: NamedTuple[K1, V1]) =>
        (fct(tup) *: tup.toTuple).withNames[ConcatString[S, K1]]
      }

    inline def forceColumnType[S <: String, A]: Iterator[NamedTuple[K1, ReplaceOneTypeAtName[K1, S, V1, A]]] =
      itr.map(_.asInstanceOf[NamedTuple[K1, ReplaceOneTypeAtName[K1, S, V1, A]]])

    inline def mapColumn[S <: String, A](using
        @implicitNotFound("Column ${S} not found")
        ev: IsColumn[S, K1] =:= true,
        s: ValueOf[S]
    )(
        fct: GetTypeAtName[K1, S, V1] => A
    ): Iterator[NamedTuple[K1, ReplaceOneTypeAtName[K1, S, V1, A]]] =
      import scala.compiletime.ops.string.*
      val headers = constValueTuple[K1].toList.map(_.toString())

      /** Aaahhhh... apparently, TupleXXL is in reverse order!
        */
      val headers2 = if headers.size > 22 then headers.reverse else headers
      val idx = headers.indexOf(s.value)
      if idx == -1 then ???
      end if
      itr.map { (x: NamedTuple[K1, V1]) =>
        val tup = x.toTuple
        val typ = tup(idx).asInstanceOf[GetTypeAtName[K1, S, V1]]
        val mapped = fct(typ)
        val (head, tail) = x.toTuple.splitAt(idx)
        (head ++ mapped *: tail.tail).withNames[K1].asInstanceOf[NamedTuple[K1, ReplaceOneTypeAtName[K1, S, V1, A]]]
      }
    end mapColumn

    inline def numericCols: Iterator[
      NamedTuple[
        SelectFromTuple[K1, TupleContainsIdx[SelectFromTuple[K1, NumericColsIdx[V1]], K1]],
        GetTypesAtNames[K1, SelectFromTuple[K1, TupleContainsIdx[SelectFromTuple[K1, NumericColsIdx[V1]], K1]], V1]
      ]
    ] =
      val ev1 = summonInline[AllAreColumns[SelectFromTuple[K1, NumericColsIdx[V1]], K1] =:= true]
      columns[SelectFromTuple[K1, NumericColsIdx[V1]]](using ev1)
    end numericCols

    inline def nonNumericCols: Iterator[
      NamedTuple[
        SelectFromTuple[K1, TupleContainsIdx[SelectFromTuple[K1, Negate[NumericColsIdx[V1]]], K1]],
        GetTypesAtNames[K1, SelectFromTuple[K1, TupleContainsIdx[SelectFromTuple[K1, Negate[NumericColsIdx[V1]]], K1]], V1]
      ]
    ] =
      val ev1 = summonInline[
        AllAreColumns[SelectFromTuple[K1, Negate[NumericColsIdx[V1]]], K1] =:= true
      ]
      columns[SelectFromTuple[K1, Negate[NumericColsIdx[V1]]]](using ev1)
    end nonNumericCols

    // inline def resolve[ST <: Tuple]: SelectFromTuple[K1, TupleContainsIdx[ST, K1]] =
    //   ("Pclass", "Age", "SibSp", "Parch", "Fare").asInstanceOf[SelectFromTuple[K1, TupleContainsIdx[ST, K1]]]
    // inline def resolveT[ST <: Tuple]: GetTypesAtNames[K1, SelectFromTuple[K1, TupleContainsIdx[ST, K1]], V1] =
    //   (1, Some(2.0), 1, 1, 2.0).asInstanceOf[GetTypesAtNames[K1, SelectFromTuple[K1, TupleContainsIdx[ST, K1]], V1]]

    // inline def resolveNT[ST <: Tuple]: NamedTuple[
    //   SelectFromTuple[K1, TupleContainsIdx[ST, K1]],
    //   GetTypesAtNames[K1, SelectFromTuple[K1, TupleContainsIdx[ST, K1]], V1]
    // ] =
    //   (1, Some(2.0), 1, 1, 2.0)
    //     .withNames[("Pclass", "Age", "SibSp", "Parch", "Fare")]
    //     .asInstanceOf[
    //       NamedTuple[
    //         SelectFromTuple[K1, TupleContainsIdx[ST, K1]],
    //         GetTypesAtNames[K1, SelectFromTuple[K1, TupleContainsIdx[ST, K1]], V1]
    //       ]
    //     ]

    inline def columns[ST <: Tuple](using
        @implicitNotFound("Not all columns in ${ST} were found")
        ev: AllAreColumns[ST, K1] =:= true
    ): Iterator[
      NamedTuple[
        SelectFromTuple[K1, TupleContainsIdx[ST, K1]],
        GetTypesAtNames[K1, SelectFromTuple[K1, TupleContainsIdx[ST, K1]], V1]
      ]
    ] =
      val headers = constValueTuple[K1].toList.map(_.toString())
      // val types  = constValueTuple[SelectFromTuple[V1, TupleContainsIdx[ST, K1]]].toList.map(_.toString())
      val selectedHeaders = constValueTuple[SelectFromTuple[K1, TupleContainsIdx[ST, K1]]].toList.map(_.toString())

      // Preserve the existing column order
      val idxes = selectedHeaders.map(headers.indexOf(_)).filterNot(_ == -1)

      // println(s"headers $headers")
      // println(s"selectedHeaders $selectedHeaders")
      // println(s"idxes $idxes")

      itr.map[NamedTuple[SelectFromTuple[K1, TupleContainsIdx[ST, K1]], GetTypesAtNames[K1, SelectFromTuple[K1, TupleContainsIdx[ST, K1]], V1]]] { (x: NamedTuple[K1, V1]) =>
        val tuple = x.toTuple

        // println("in tuple")
        // println(tuple.toList.mkString(","))
        val selected: Tuple = idxes.foldRight(EmptyTuple: Tuple) { (idx, acc) =>
          // println(tuple(idx))
          tuple(idx) *: acc
        }

        selected
          .withNames[SelectFromTuple[K1, TupleContainsIdx[ST, K1]]]
          .asInstanceOf[
            NamedTuple[
              SelectFromTuple[K1, TupleContainsIdx[ST, K1]],
              GetTypesAtNames[K1, SelectFromTuple[K1, TupleContainsIdx[ST, K1]], V1]
            ]
          ]
      }
    end columns

    inline def numericColSummary[S <: String](using
        ev: IsColumn[S, K1] =:= true,
        isNum: IsNumeric[GetTypeAtName[K1, S, V1]] =:= true,
        s: ValueOf[S],
        a: Fractional[GetTypeAtName[K1, S, V1]]
    ) =
      val numericValues = itr.column[S].toList.asInstanceOf[List[GetTypeAtName[K1, S, V1]]]

      val sortedValues = numericValues.sorted
      val size = sortedValues.size

      def percentile(p: Double): Double =
        val rank = p * (size - 1)
        val lower = sortedValues(rank.toInt)
        val upper = sortedValues(math.ceil(rank).toInt)
        lower.toDouble + a.minus(upper, lower).toDouble * (rank - rank.toInt)
      end percentile

      val mean = numericValues.sum / a.fromInt(size)
      val min = sortedValues.head
      val max = sortedValues.last
      val variance = numericValues.map(x => a.minus(x, mean)).map(x => a.times(x, x)).sum / a.fromInt(size)

      val percentiles = List(0.25, 0.5, 0.75).map(percentile)

      val std = math.sqrt(variance.toDouble)

      (mean, std, min, percentiles(0), percentiles(1), percentiles(2), max).withNames[("mean", "std", "min", "25%", "50%", "75%", "max")]
    end numericColSummary

    inline def column[S <: String](using
        @implicitNotFound("Column ${S} not found")
        ev: IsColumn[S, K1] =:= true,
        s: ValueOf[S]
    ): Iterator[GetTypeAtName[K1, S, V1]] =
      val headers = constValueTuple[K1].toList.map(_.toString())
      val headers2 = if headers.size > 22 then headers.reverse else headers

      val idx = headers2.indexOf(s.value)
      itr.map(x => x.toTuple(idx).asInstanceOf[GetTypeAtName[K1, S, V1]])
    end column

    inline def dropColumn[S <: String](using
        @implicitNotFound("Column ${S} not found")
        ev: IsColumn[S, K1] =:= true,
        s: ValueOf[S]
    ): Iterator[NamedTuple[DropOneName[K1, S], DropOneTypeAtName[K1, S, V1]]] =
      val headers = constValueTuple[K1].toList.map(_.toString())

      /** Aaahhhh... apparently, TupleXXL is in reverse order!
        */
      val headers2 = if headers.size > 22 then headers.reverse else headers
      val idx = headers2.indexOf(s.value)

      type RemoveMe = GetTypeAtName[K1, S, V1]

      itr.map { (x: NamedTuple[K1, V1]) =>
        val (head, tail) = x.toTuple.splitAt(idx)
        head match
          case x: EmptyTuple => tail.tail.withNames[DropOneName[K, S]].asInstanceOf[NamedTuple[DropOneName[K1, S], DropOneTypeAtName[K1, S, V1]]]
          case _             => (head ++ tail.tail).withNames[DropOneName[K, S]].asInstanceOf[NamedTuple[DropOneName[K1, S], DropOneTypeAtName[K1, S, V1]]]
        end match
      }
    end dropColumn
  end extension

  extension [K <: Tuple, V <: Tuple](nt: Seq[NamedTuple[K, V]])

    inline def column[S <: String](using
        @implicitNotFound("Column ${S} not found")
        ev: IsColumn[S, K] =:= true,
        s: ValueOf[S]
    ): Seq[GetTypeAtName[K, S, V]] =
      nt.toIterator.column[S](using ev).toSeq
    end column

    inline def addColumn[S <: String, A](fct: (tup: NamedTuple.NamedTuple[K, V]) => A): Seq[NamedTuple[S *: K, A *: V]] =
      nt.toIterator.addColumn[S, A](fct).toSeq

    inline def numericCols: Seq[
      NamedTuple[
        SelectFromTuple[K, TupleContainsIdx[SelectFromTuple[K, NumericColsIdx[V]], K]],
        GetTypesAtNames[K, SelectFromTuple[K, TupleContainsIdx[SelectFromTuple[K, NumericColsIdx[V]], K]], V]
      ]
    ] =
      val ev1 = summonInline[AllAreColumns[SelectFromTuple[K, NumericColsIdx[V]], K] =:= true]
      columns[SelectFromTuple[K, NumericColsIdx[V]]](using ev1)
    end numericCols

    inline def nonNumericCols: Seq[
      NamedTuple[
        SelectFromTuple[K, TupleContainsIdx[SelectFromTuple[K, Negate[NumericColsIdx[V]]], K]],
        GetTypesAtNames[K, SelectFromTuple[K, TupleContainsIdx[SelectFromTuple[K, Negate[NumericColsIdx[V]]], K]], V]
      ]
    ] =
      val ev1 = summonInline[
        AllAreColumns[SelectFromTuple[K, Negate[NumericColsIdx[V]]], K] =:= true
      ]
      columns[SelectFromTuple[K, Negate[NumericColsIdx[V]]]](using ev1)
    end nonNumericCols

    inline def columns[ST <: Tuple](using
        @implicitNotFound("Not all columns in ${ST} are present in ${K}")
        ev: AllAreColumns[ST, K] =:= true
    ): Seq[
      NamedTuple[
        SelectFromTuple[K, TupleContainsIdx[ST, K]],
        GetTypesAtNames[K, SelectFromTuple[K, TupleContainsIdx[ST, K]], V]
      ]
    ] =
      nt.toIterator.columns[ST](using ev).toSeq

    inline def dropColumn[S <: String](using
        @implicitNotFound("Column ${S} not found")
        ev: IsColumn[S, K] =:= true,
        s: ValueOf[S]
    ): Seq[NamedTuple[DropOneName[K, S], DropOneTypeAtName[K, S, V]]] =
      nt.toIterator.dropColumn[S].toSeq

    inline def mapColumn[S <: String, A](fct: GetTypeAtName[K, S, V] => A)(using
        @implicitNotFound("Column ${S} not found")
        ev: IsColumn[S, K] =:= true,
        s: ValueOf[S]
    ): Seq[NamedTuple[K, ReplaceOneTypeAtName[K, S, V, A]]] =
      nt.toIterator.mapColumn[S, A](fct).toSeq
    inline def forceColumnType[S <: String, A]: Any =
      nt.toIterator.forceColumnType[S, A].toSeq
    inline def renameColumn[From <: String, To <: String](using
        ev: IsColumn[From, K] =:= true,
        FROM: ValueOf[From],
        TO: ValueOf[To]
    ): Seq[NamedTuple[ReplaceOneName[K, From, To], V]] =
      nt.toIterator.renameColumn[From, To].toSeq

  end extension
end NamedTupleIteratorExtensions
