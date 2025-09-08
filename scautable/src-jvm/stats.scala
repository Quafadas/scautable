package io.github.quafadas.scautable

import com.tdunning.math.stats.TDigest
import scala.NamedTuple.*
import scala.compiletime.constValueTuple
import io.github.quafadas.scautable.ColumnTyped.*
import io.github.quafadas.scautable.NamedTupleIteratorExtensions.*
import scala.collection.BuildFrom
import scala.Tuple.Map


object Stats:

  type StatsContext[T] = (typ: String, sum: Double, count: Int, mean: Double, digest: TDigest)

  // Alternative approach: Use a helper function with context bound and call it from the context function
  private inline def processNumericValue[T: Numeric](value: T): Double =
    summon[Numeric[T]].toDouble(value)

  extension [K <: Tuple, V <: Tuple](nt: NamedTuple[K, V])

    inline def numericColNames =  constValueTuple[SelectFromTuple[K, NumericColsIdx[V]]].toList.asInstanceOf[List[String]]

  end extension

  extension [K <: Tuple, V <: Tuple](nt: Iterable[NamedTuple[K, V]])

    inline def zeroStatsValue = nt.head.map[StatsContext] {
      [T] => (value: T) =>
        val digest = TDigest.createDigest(100)

        // We'll start with empty stats
        val base = value match {
          case _ => (sum = 0.0, count = 0, mean = 0.0, digest = digest)
        }

        val typ = value match {
          case v: Double => (typ = "Double")
          case i: Int    => (typ = "Int")
          case l: Long   => (typ = "Long")
          case _         => ???
        }
        typ ++ base
    }

    inline def summary =
      val zeroValue = nt.zeroStatsValue
      val res = nt.foldLeft(zeroValue) { (acc, row) =>
        val result = acc.zip(row).map[StatsContext] {
          [T] => (t: T) =>
            t match {
              case ((typ: String, sum: Double, count: Int, mean: Double, digestA: TDigest), incA) =>
                // Use helper function for type-safe numeric conversion
                val incADouble = incA match {
                  case v: Double => processNumericValue(v)
                  case v: Int => processNumericValue(v)
                  case v: Long => processNumericValue(v)
                  case _ => 0.0 // fallback instead of ???
                }

                digestA.add(incADouble)
                val newSum = sum + incADouble
                val newCount = count + 1
                val newMean = if newCount == 0 then 0.0 else newSum / newCount
                (typ = typ, sum = newSum, count = newCount, mean = newMean, digest = digestA)
            }
        }
        result.asInstanceOf[NamedTuple[K, Tuple.Map[V, StatsContext]]]
      }
      val headers : List[String] = constValueTuple[SelectFromTuple[K, NumericColsIdx[V]]].toList.asInstanceOf[List[String]]

  end extension


