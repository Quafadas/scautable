package io.github.quafadas.scautable

import com.tdunning.math.stats.TDigest
import scala.NamedTuple.*
import scala.compiletime.constValueTuple
import io.github.quafadas.scautable.ColumnTyped.*
import io.github.quafadas.scautable.NamedTupleIteratorExtensions.*
import scala.collection.BuildFrom
import scala.Tuple.Map
import scala.Tuple.Fold


object Stats:

  type StatsContext[T] = (typ: String, sum: Double, count: Int, mean: Double, digest: TDigest)

  // Alternative approach: Use a helper function with context bound and call it from the context function
  private inline def processNumericValue[T: Numeric](value: T): Double =
    summon[Numeric[T]].toDouble(value)

  extension [K <: Tuple, V <: Tuple](nt: Iterator[NamedTuple[K, V]])
    inline def summary = 
      if !nt.hasNext then
        // Empty iterator case
        List.empty
      else
        // Use the first row to initialize the stats structure
        val firstRow = nt.next()
        val zeroValue = firstRow.map[StatsContext] {
          [T] => (value: T) =>
            val digest = TDigest.createDigest(100)
            
            // Enhanced runtime type detection that handles Option types properly
            val typ = value match {
              case v: Double => (typ = "Double")
              case i: Int    => (typ = "Int")
              case l: Long   => (typ = "Long")
              case Some(v: Double) => (typ = "Double")
              case Some(i: Int) => (typ = "Int")
              case Some(l: Long) => (typ = "Long")
              case None => (typ = "Unknown")
              case _    => (typ = "Unknown")
            }
            
            // Process the first value
            val (initialSum, initialCount, shouldInclude) = value match {
              case v: Double => (v, 1, true)
              case v: Int => (v.toDouble, 1, true)
              case v: Long => (v.toDouble, 1, true)
              case Some(v: Double) => (v, 1, true)
              case Some(i: Int) => (i.toDouble, 1, true)
              case Some(l: Long) => (l.toDouble, 1, true)
              case None => (0.0, 0, false)
              case _ => (0.0, 0, false)
            }
            
            if shouldInclude then digest.add(initialSum)
            
            typ ++ (sum = initialSum, count = initialCount, mean = if initialCount == 0 then 0.0 else initialSum, digest = digest)
        }
        
        // Process the remaining rows
        val res = nt.foldLeft(zeroValue) { (acc, row) =>
          val result = acc.zip(row).map[StatsContext] {
            [T] => (t: T) =>
              t match {
                case ((typ: String, sum: Double, count: Int, mean: Double, digestA: TDigest), incA) =>
                  val incADouble = incA match {
                    case v: Double => v
                    case v: Int => v.toDouble
                    case v: Long => v.toDouble
                    case Some(v: Double) => v
                    case Some(i: Int) => i.toDouble
                    case Some(l: Long) => l.toDouble
                    case _ => 0.0
                  }

                  val shouldIncludeValue = incA match {
                    case v: Double => true
                    case v: Int => true
                    case v: Long => true
                    case Some(_) => true
                    case None => false
                    case _ => false
                  }

                  if shouldIncludeValue then
                    digestA.add(incADouble)
                    val newSum = sum + incADouble
                    val newCount = count + 1
                    val newMean = if newCount == 0 then 0.0 else newSum / newCount

                    // Update type if it was Unknown and we now have a real value
                    val updatedTyp = if typ == "Unknown" then
                      incA match {
                        case v: Double => "Double"
                        case i: Int => "Int"
                        case l: Long => "Long"
                        case Some(v: Double) => "Double"
                        case Some(i: Int) => "Int"
                        case Some(l: Long) => "Long"
                        case _ => "Unknown"
                      }
                    else typ

                    (typ = updatedTyp, sum = newSum, count = newCount, mean = newMean, digest = digestA)
                  else
                    // Don't include None values in statistics
                    (typ = typ, sum = sum, count = count, mean = mean, digest = digestA)
              }
          }
          result.asInstanceOf[NamedTuple[K, Tuple.Map[V, StatsContext]]]
        }
        
        val headers: List[String] = constValueTuple[K].toList.map(_.toString())
        val asList: List[(typ: String, sum: Double, count: Int, mean: Double, digest: TDigest)] = res.toList.asInstanceOf[List[(typ: String, sum: Double, count: Int, mean: Double, digest: TDigest)]]
        headers.zip(asList)
          .map { case (name, res) => (name = name) ++ res }
          .map { p =>
            (name = p.name, typ = p.typ, mean = p.mean, min = p.digest.getMin, `0.25` = p.digest.quantile(0.25), median = p.digest.quantile(0.5), `0.75` = p.digest.quantile(0.75), max = p.digest.getMax)
          }

  extension [K <: Tuple, V <: Tuple](nt: Iterable[NamedTuple[K, V]])

    inline def zeroStatsValue = nt.head.map[StatsContext] {
      [T] => (value: T) =>
        val digest = TDigest.createDigest(100)

        // We'll start with empty stats
        val base = (sum = 0.0, count = 0, mean = 0.0, digest = digest)

        // Enhanced runtime type detection that handles Option types properly
        val typ = value match {
          case v: Double => (typ = "Double")
          case i: Int    => (typ = "Int")
          case l: Long   => (typ = "Long")
          case Some(v: Double) => (typ = "Double")
          case Some(i: Int) => (typ = "Int")
          case Some(l: Long) => (typ = "Long")
          case None =>
            // For None values, we need to look ahead in the data to infer type
            // This is a limitation - we'll mark as Unknown and fix during processing
            (typ = "Unknown")
          case _         => (typ = "Unknown")
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
                  case Some(v: Double) => v
                  case Some(i: Int) => processNumericValue(i)
                  case Some(l: Long) => processNumericValue(l)
                  case _ => 0.0 // fallback instead of ???
                }

                val shouldIncludeValue = incA match {
                  case v: Double => true
                  case v: Int => true
                  case v: Long => true
                  case Some(_) => true
                  case None => false
                  case _ => false
                }

                if shouldIncludeValue then
                  digestA.add(incADouble)
                  val newSum = sum + incADouble
                  val newCount = count + 1
                  val newMean = if newCount == 0 then 0.0 else newSum / newCount

                  // Update type if it was Unknown and we now have a real value
                  val updatedTyp = if typ == "Unknown" then
                    incA match {
                      case v: Double => "Double"
                      case i: Int => "Int"
                      case l: Long => "Long"
                      case Some(v: Double) => "Double"
                      case Some(i: Int) => "Int"
                      case Some(l: Long) => "Long"
                      case _ => "Unknown"
                    }
                  else typ

                  (typ = updatedTyp, sum = newSum, count = newCount, mean = newMean, digest = digestA)
                else
                  // Don't include None values in statistics
                  (typ = typ, sum = sum, count = count, mean = mean, digest = digestA)
            }
        }
        result.asInstanceOf[NamedTuple[K, Tuple.Map[V, StatsContext]]]
      }
      val headers : List[String] = constValueTuple[K].toList.map(_.toString())
      val asList: List[(typ : String, sum : Double, count : Int, mean : Double, digest : TDigest)] = res.toList.asInstanceOf[List[(typ : String, sum : Double, count : Int, mean : Double, digest : TDigest)]]
      headers.zip(asList)
        .map { case (name, res) => (name = name) ++ res }
        .map{
          p =>
            (name = p.name, typ = p.typ, mean = p.mean, min = p.digest.getMin, `0.25` = p.digest.quantile(0.25), median = p.digest.quantile(0.5), `0.75` = p.digest.quantile(0.75), max = p.digest.getMax)
        }


  end extension


