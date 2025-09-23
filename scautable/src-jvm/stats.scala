package io.github.quafadas.scautable

import scala.NamedTuple.*
import scala.collection.BuildFrom
import scala.compiletime.constValueTuple
import scala.util.Random

import com.tdunning.math.stats.TDigest

import io.github.quafadas.scautable.ConsoleFormat.ptbln
import io.github.quafadas.scautable.NamedTupleIteratorExtensions.*

object Stats:

  type StatsContext[T] = (typ: String, sum: Double, count: Int, mean: Double, digest: TDigest)

  type NonNumericStatsContext[T] = (uniqueValues: Set[String], counts: scala.collection.mutable.Map[String, Int])

  // Alternative approach: Use a helper function with context bound and call it from the context function
  private inline def processNumericValue[T: Numeric](value: T): Double =
    summon[Numeric[T]].toDouble(value)

  extension [K <: Tuple, V <: Tuple](nt: Iterator[NamedTuple[K, V]])
    /** Computes comprehensive statistical summaries for all numeric columns in the dataset.
      *
      * This method processes an `Iterator` of `NamedTuple`s and calculates descriptive statistics for each column that contains numeric data (Int, Long, Double, or Option-wrapped
      * versions). Non-numeric columns are ignored. The computation uses T-Digest for efficient quantile estimation, making it suitable for large datasets.
      *
      * The method handles missing values gracefully:
      *   - `None` values in `Option` types are excluded from calculations
      *   - Columns with all missing values will show appropriate default values
      *   - Type inference works even when the first few values are missing
      *
      * @note
      *   This method consumes the iterator. If you need to preserve the data, consider converting to a collection first using the `Iterable` version.
      *
      * @return
      *   A list of named tuples, one per numeric column, containing:
      *   - `name`: Column name (String)
      *   - `typ`: Detected data type ("Int", "Long", "Double", or "Unknown")
      *   - `mean`: Arithmetic mean of non-missing values
      *   - `min`: Minimum value
      *   - `0.25`: 25th percentile (first quartile)
      *   - `median`: 50th percentile (median)
      *   - `0.75`: 75th percentile (third quartile)
      *   - `max`: Maximum value
      *
      * @example
      *   {{{ val data = Iterator( (name = "Alice", age = 25, salary = 50000.0), (name = "Bob", age = 30, salary = 60000.0), (name = "Charlie", age = 35, salary = None) ) val stats =
      *   data.numericSummary // Returns statistics for 'age' and 'salary' columns only // 'name' column is ignored as it's non-numeric }}}
      *
      * @see
      *   [[numericSummary]] for the `Iterable` version that doesn't consume the collection
      */
    inline def numericSummary =
      if !nt.hasNext then
        // Empty iterator case
        List.empty
      else
        // Use the first row to initialize the stats structure
        val firstRow = nt.next()
        val zeroValue = firstRow.map[StatsContext] { [T] => (value: T) =>
          val digest = TDigest.createDigest(100)

          // Enhanced runtime type detection that handles Option types properly
          val typ = value match
            case v: Double       => (typ = "Double")
            case i: Int          => (typ = "Int")
            case l: Long         => (typ = "Long")
            case Some(v: Double) => (typ = "Double")
            case Some(i: Int)    => (typ = "Int")
            case Some(l: Long)   => (typ = "Long")
            case None            => (typ = "Unknown")
            case _               => (typ = "Unknown")

          // Process the first value
          val (initialSum, initialCount, shouldInclude) = value match
            case v: Double       => if v.isNaN then (0.0, 0, false) else (v, 1, true)
            case v: Int          => (v.toDouble, 1, true)
            case v: Long         => (v.toDouble, 1, true)
            case Some(v: Double) => if v.isNaN then (0.0, 0, false) else (v, 1, true)
            case Some(i: Int)    => (i.toDouble, 1, true)
            case Some(l: Long)   => (l.toDouble, 1, true)
            case None            => (0.0, 0, false)
            case _               => (0.0, 0, false)

          if shouldInclude then digest.add(initialSum)
          end if

          typ ++ (sum = initialSum, count = initialCount, mean = if initialCount == 0 then 0.0 else initialSum, digest = digest)
        }

        // Process the remaining rows
        val res = nt.foldLeft(zeroValue) { (acc, row) =>
          val result = acc.zip(row).map[StatsContext] { [T] => (t: T) =>
            t match
              case ((typ: String, sum: Double, count: Int, mean: Double, digestA: TDigest), incA) =>
                val incADouble = incA match
                  case v: Double       => v
                  case v: Int          => v.toDouble
                  case v: Long         => v.toDouble
                  case Some(v: Double) => v
                  case Some(i: Int)    => i.toDouble
                  case Some(l: Long)   => l.toDouble
                  case _               => 0.0

                val shouldIncludeValue = incA match
                  case v: Double       => !v.isNaN
                  case v: Int          => true
                  case v: Long         => true
                  case Some(v: Double) => !v.isNaN
                  case Some(_)         => true
                  case None            => false
                  case _               => false

                if shouldIncludeValue then
                  digestA.add(incADouble)
                  val newSum = sum + incADouble
                  val newCount = count + 1
                  val newMean = if newCount == 0 then 0.0 else newSum / newCount

                  // Update type if it was Unknown and we now have a real value
                  val updatedTyp =
                    if typ == "Unknown" then
                      incA match
                        case v: Double       => "Double"
                        case i: Int          => "Int"
                        case l: Long         => "Long"
                        case Some(v: Double) => "Double"
                        case Some(i: Int)    => "Int"
                        case Some(l: Long)   => "Long"
                        case _               => "Unknown"
                    else typ

                  (typ = updatedTyp, sum = newSum, count = newCount, mean = newMean, digest = digestA)
                else
                  // Don't include None values in statistics
                  (typ = typ, sum = sum, count = count, mean = mean, digest = digestA)
                end if
          }
          result.asInstanceOf[NamedTuple[K, Tuple.Map[V, StatsContext]]]
        }

        val headers: List[String] = constValueTuple[K].toList.map(_.toString())
        val asList: List[(typ: String, sum: Double, count: Int, mean: Double, digest: TDigest)] =
          res.toList.asInstanceOf[List[(typ: String, sum: Double, count: Int, mean: Double, digest: TDigest)]]
        headers
          .zip(asList)
          .map { case (name, res) => (name = name) ++ res }
          .map { p =>
            (
              name = p.name,
              typ = p.typ,
              mean = p.mean,
              min = p.digest.getMin,
              `0.25` = p.digest.quantile(0.25),
              median = p.digest.quantile(0.5),
              `0.75` = p.digest.quantile(0.75),
              max = p.digest.getMax
            )
          }

    /** Computes comprehensive categorical summaries for all columns in the dataset.
      *
      * This method processes an `Iterator` of `NamedTuple`s and calculates descriptive statistics for each column focusing on categorical/text data patterns. All column types are
      * supported, with values converted to their string representation for analysis. The method is particularly useful for understanding the distribution and variety of
      * non-numeric data.
      *
      * The method handles missing values gracefully:
      *   - `None` values in `Option` types are excluded from calculations
      *   - Empty strings are ignored
      *   - Columns with all missing values will show appropriate default values
      *
      * @note
      *   This method consumes the iterator. If you need to preserve the data, consider converting to a collection first using the `Iterable` version.
      *
      * @return
      *   A list of named tuples, one per column, containing:
      *   - `name`: Column name (String)
      *   - `uniqueEntries`: Count of unique non-missing values in the column
      *   - `mostFrequent`: The most frequently occurring value (as Option[String]), or None if all values are missing
      *   - `frequency`: Number of times the most frequent value appears
      *   - `sample`: A comma-separated sample of up to 5 unique values, randomly selected and truncated to 75 characters with "..." if needed
      *
      * @example
      *   {{{ val data = Iterator( (name = "Alice", city = "New York", department = "Engineering"), (name = "Bob", city = "Boston", department = "Sales"), (name = "Charlie", city =
      *   "New York", department = "Engineering") ) val summary = data.nonNumericSummary // Returns categorical statistics for all columns }}}
      *
      * @see
      *   [[nonNumericSummary]] for the `Iterable` version that doesn't consume the collection
      */
    inline def nonNumericSummary =
      if !nt.hasNext then
        // Empty iterator case
        List.empty
      else
        // Use the first row to initialize the stats structure
        val firstRow = nt.next()
        val zeroValue = firstRow.map[NonNumericStatsContext] { [T] => (value: T) =>
          val counts = scala.collection.mutable.Map[String, Int]()

          // Process the first value
          val stringValue = value match
            case None    => "" // Skip None values
            case Some(v) => v.toString
            case v       => v.toString

          val initialValues = if stringValue.nonEmpty then
            counts.update(stringValue, 1)
            Set(stringValue)
          else Set.empty[String]

          (uniqueValues = initialValues, counts = counts)
        }

        // Process the remaining rows
        val res = nt.foldLeft(zeroValue) { (acc, row) =>
          val result = acc.zip(row).map[NonNumericStatsContext] { [T] => (t: T) =>
            t match
              case ((uniqueValues: Set[String] @unchecked, counts: scala.collection.mutable.Map[String, Int] @unchecked), newValue) =>
                val stringValue = newValue match
                  case None    => "" // Skip None values
                  case Some(v) => v.toString
                  case v       => v.toString

                if stringValue.nonEmpty then
                  counts.updateWith(stringValue)(existing => Some(existing.getOrElse(0) + 1))
                  val updatedValues = uniqueValues + stringValue
                  (uniqueValues = updatedValues, counts = counts)
                else
                  // Skip None/empty values
                  (uniqueValues = uniqueValues, counts = counts)
                end if
          }
          result.asInstanceOf[NamedTuple[K, Tuple.Map[V, NonNumericStatsContext]]]
        }

        val headers: List[String] = constValueTuple[K].toList.map(_.toString())
        val asList = res.toList.asInstanceOf[List[(uniqueValues: Set[String], counts: scala.collection.mutable.Map[String, Int])]]

        headers
          .zip(asList)
          .map { case (name, stats) =>
            val uniqueEntries = stats.uniqueValues.size
            val (mostFrequentStr, frequency) =
              if stats.counts.nonEmpty then stats.counts.maxBy(_._2)
              else ("", 0)
            val mostFrequent = if mostFrequentStr.nonEmpty then Some(mostFrequentStr) else None

            // Create sample from random selection of unique values
            val sampleValues = Random.shuffle(stats.uniqueValues.toList).take(5)
            val sampleString = sampleValues.mkString(", ")
            val truncatedSample = if sampleString.length > 75 then sampleString.take(72) + "..." else sampleString

            (name = name, uniqueEntries = uniqueEntries, mostFrequent = mostFrequent.getOrElse(""), frequency = frequency, sample = truncatedSample)
          }
  end extension

  extension [K <: Tuple, V <: Tuple](nt: Iterable[NamedTuple[K, V]])

    private inline def zeroStatsValue = nt.head.map[StatsContext] { [T] => (value: T) =>
      val digest = TDigest.createDigest(100)

      // We'll start with empty stats
      val base = (sum = 0.0, count = 0, mean = 0.0, digest = digest)

      // Enhanced runtime type detection that handles Option types properly
      val typ = value match
        case v: Double       => (typ = "Double")
        case i: Int          => (typ = "Int")
        case l: Long         => (typ = "Long")
        case Some(v: Double) => (typ = "Double")
        case Some(i: Int)    => (typ = "Int")
        case Some(l: Long)   => (typ = "Long")
        case None            =>
          // For None values, we need to look ahead in the data to infer type
          // This is a limitation - we'll mark as Unknown and fix during processing
          (typ = "Unknown")
        case _ => (typ = "Unknown")
      typ ++ base
    }

    /** Computes comprehensive categorical summaries for all columns in the dataset.
      *
      * This method processes an `Iterable` of `NamedTuple`s and calculates descriptive statistics for each column focusing on categorical/text data patterns. All column types are
      * supported, with values converted to their string representation for analysis. The method is particularly useful for understanding the distribution and variety of
      * non-numeric data.
      *
      * The method handles missing values gracefully:
      *   - `None` values in `Option` types are excluded from calculations
      *   - Empty strings are ignored
      *   - Columns with all missing values will show appropriate default values
      *
      * @note
      *   Unlike the `Iterator` version, this method does not consume the collection, allowing for multiple analyses on the same dataset.
      *
      * @return
      *   A list of named tuples, one per column, containing:
      *   - `name`: Column name (String)
      *   - `uniqueEntries`: Count of unique non-missing values in the column
      *   - `mostFrequent`: The most frequently occurring value (as Option[String]), or None if all values are missing
      *   - `frequency`: Number of times the most frequent value appears
      *   - `sample`: A comma-separated sample of up to 5 unique values, randomly selected and truncated to 75 characters with "..." if needed
      *
      * @example
      *   {{{ val data = List( (name = "Alice", city = "New York", department = "Engineering"), (name = "Bob", city = "Boston", department = "Sales"), (name = "Charlie", city =
      *   "New York", department = "Engineering"), (name = "Diana", city = "Chicago", department = "Marketing") ) val summary = data.nonNumericSummary
      *
      * // Results might look like: // name: uniqueEntries=4, mostFrequent=None, frequency=1, sample="Alice, Bob, Charlie, Diana" // city: uniqueEntries=3, mostFrequent=Some("New
      * York"), frequency=2, sample="New York, Boston, Chicago" // department: uniqueEntries=3, mostFrequent=Some("Engineering"), frequency=2, sample="Engineering, Sales,
      * Marketing" }}}
      *
      * @example
      *   {{{ val dataWithOptions = List( (id = 1, category = Some("A"), notes = None), (id = 2, category = Some("B"), notes = Some("Important")), (id = 3, category = Some("A"),
      *   notes = None), (id = 4, category = None, notes = Some("Review")) ) val summary = dataWithOptions.nonNumericSummary
      *
      * // The category column will show uniqueEntries=2 (A, B), excluding None values // The notes column will show uniqueEntries=2 (Important, Review), excluding None values }}}
      *
      * @see
      *   [[numericSummary]] for numeric column analysis
      */
    inline def nonNumericSummary =
      val zeroValue = nt.head.map[NonNumericStatsContext] { [T] => (value: T) =>
        (uniqueValues = Set.empty[String], counts = scala.collection.mutable.Map[String, Int]())
      }

      val res = nt.foldLeft(zeroValue) { (acc, row) =>
        val result = acc.zip(row).map[NonNumericStatsContext] { [T] => (t: T) =>
          t match
            case ((uniqueValues: Set[String] @unchecked, counts: scala.collection.mutable.Map[String, Int] @unchecked), newValue) =>
              val stringValue = newValue match
                case None    => "" // Skip None values
                case Some(v) => v.toString
                case v       => v.toString

              if stringValue.nonEmpty then
                counts.updateWith(stringValue)(existing => Some(existing.getOrElse(0) + 1))
                val updatedValues = uniqueValues + stringValue
                (uniqueValues = updatedValues, counts = counts)
              else
                // Skip None/empty values
                (uniqueValues = uniqueValues, counts = counts)
              end if
        }
        result.asInstanceOf[NamedTuple[K, Tuple.Map[V, NonNumericStatsContext]]]
      }

      val headers: List[String] = constValueTuple[K].toList.map(_.toString())
      val asList = res.toList.asInstanceOf[List[(uniqueValues: Set[String], counts: scala.collection.mutable.Map[String, Int])]]

      headers
        .zip(asList)
        .map { case (name, stats) =>
          val uniqueEntries = stats.uniqueValues.size
          val (mostFrequentStr, frequency) =
            if stats.counts.nonEmpty then stats.counts.maxBy(_._2)
            else ("", 0)
          val mostFrequent = if mostFrequentStr.nonEmpty then Some(mostFrequentStr) else None

          // Create sample from random selection of unique values
          val sampleValues = Random.shuffle(stats.uniqueValues.toList).take(5)
          val sampleString = sampleValues.mkString(", ")
          val truncatedSample = if sampleString.length > 75 then sampleString.take(72) + "..." else sampleString

          (name = name, uniqueEntries = uniqueEntries, mostFrequent = mostFrequent.getOrElse(""), frequency = frequency, sample = truncatedSample)
        }
    end nonNumericSummary

    /** Computes comprehensive statistical summaries for all numeric columns in the dataset.
      *
      * This method processes an `Iterable` of `NamedTuple`s and calculates descriptive statistics for each column that contains numeric data (Int, Long, Double, or Option-wrapped
      * versions). Non-numeric columns are ignored. The computation uses T-Digest for efficient quantile estimation, making it suitable for large datasets.
      *
      * The method handles missing values gracefully:
      *   - `None` values in `Option` types are excluded from calculations
      *   - Columns with all missing values will show appropriate default values
      *   - Type inference works even when the first few values are missing
      *
      * @note
      *   Unlike the `Iterator` version, this method does not consume the collection, allowing for multiple statistical computations on the same dataset.
      *
      * @return
      *   A list of named tuples, one per numeric column, containing:
      *   - `name`: Column name (String)
      *   - `typ`: Detected data type ("Int", "Long", "Double", or "Unknown")
      *   - `mean`: Arithmetic mean of non-missing values
      *   - `min`: Minimum value
      *   - `0.25`: 25th percentile (first quartile)
      *   - `median`: 50th percentile (median)
      *   - `0.75`: 75th percentile (third quartile)
      *   - `max`: Maximum value
      *
      * @example
      *   {{{ val data = List( (name = "Alice", age = 25, salary = 50000.0), (name = "Bob", age = 30, salary = 60000.0), (name = "Charlie", age = 35, salary = None) ) val stats =
      *   data.numericSummary // Returns statistics for 'age' and 'salary' columns only // 'name' column is ignored as it's non-numeric
      *
      * // Can be called multiple times since data is not consumed val moreStats = data.numericSummary }}}
      *
      * @see
      *   [[numericSummary]] for the `Iterator` version
      */
    inline def numericSummary =
      val zeroValue = nt.zeroStatsValue
      val res = nt.foldLeft(zeroValue) { (acc, row) =>
        val result = acc.zip(row).map[StatsContext] { [T] => (t: T) =>
          t match
            case ((typ: String, sum: Double, count: Int, mean: Double, digestA: TDigest), incA) =>
              // Use helper function for type-safe numeric conversion

              val incADouble = incA match
                case v: Double       => processNumericValue(v)
                case v: Int          => processNumericValue(v)
                case v: Long         => processNumericValue(v)
                case Some(v: Double) => processNumericValue(v)
                case Some(i: Int)    => processNumericValue(i)
                case Some(l: Long)   => processNumericValue(l)
                case _               => 0.0 // fallback instead of ???

              val shouldIncludeValue = incA match
                case v: Double       => !v.isNaN
                case v: Int          => true
                case v: Long         => true
                case Some(v: Double) => !v.isNaN
                case Some(_)         => true
                case None            => false
                case _               => false

              if shouldIncludeValue then
                digestA.add(incADouble)
                val newSum = sum + incADouble
                val newCount = count + 1
                val newMean = if newCount == 0 then 0.0 else newSum / newCount

                // Update type if it was Unknown and we now have a real value
                val updatedTyp =
                  if typ == "Unknown" then
                    incA match
                      case v: Double       => "Double"
                      case i: Int          => "Int"
                      case l: Long         => "Long"
                      case Some(v: Double) => "Double"
                      case Some(i: Int)    => "Int"
                      case Some(l: Long)   => "Long"
                      case _               => "Unknown"
                  else typ

                (typ = updatedTyp, sum = newSum, count = newCount, mean = newMean, digest = digestA)
              else
                // Don't include None values in statistics
                (typ = typ, sum = sum, count = count, mean = mean, digest = digestA)
              end if
        }
        result.asInstanceOf[NamedTuple[K, Tuple.Map[V, StatsContext]]]
      }
      val headers: List[String] = constValueTuple[K].toList.map(_.toString())
      val asList: List[(typ: String, sum: Double, count: Int, mean: Double, digest: TDigest)] =
        res.toList.asInstanceOf[List[(typ: String, sum: Double, count: Int, mean: Double, digest: TDigest)]]
      headers
        .zip(asList)
        .map { case (name, res) => (name = name) ++ res }
        .map { p =>
          (
            name = p.name,
            typ = p.typ,
            mean = p.mean,
            min = p.digest.getMin,
            `0.25` = p.digest.quantile(0.25),
            median = p.digest.quantile(0.5),
            `0.75` = p.digest.quantile(0.75),
            max = p.digest.getMax
          )
        }
    end numericSummary

    /** Computes comprehensive statistical summaries for both numeric and non-numeric columns.
      *
      * This method processes an `Iterable` of `NamedTuple`s and calculates both numeric and categorical statistics for all columns in the dataset. It automatically separates
      * columns into numeric (Int, Long, Double, or Option-wrapped versions) and non-numeric types, providing comprehensive analysis in a single call.
      *
      * @note
      *   Unlike the `Iterator` version, this method does not consume the collection, allowing for multiple analyses on the same dataset.
      *
      * @return
      *   A named tuple containing:
      *   - `numeric`: List of numeric column statistics (same as `numericSummary`)
      *   - `nonNumeric`: List of categorical column statistics (same as `nonNumericSummary`)
      *
      * @example
      *   {{{ val data = List( (name = "Alice", age = 25, city = "New York", salary = 50000.0), (name = "Bob", age = 30, city = "Boston", salary = 60000.0), (name = "Charlie", age =
      *   35, city = "New York", salary = None) ) val summary = data.summary // summary.numeric contains statistics for 'age' and 'salary' columns // summary.nonNumeric contains
      *   statistics for 'name' and 'city' columns
      *
      * // Can be called multiple times since data is not consumed val moreSummary = data.summary }}}
      *
      * @see
      *   [[numericSummary]] for numeric-only analysis
      * @see
      *   [[nonNumericSummary]] for categorical-only analysis
      * @see
      *   [[summary]] for the `Iterator` version
      */
    inline def summary =
      val numeric = nt.numericCols.numericSummary
      val nonNumeric = nt.nonNumericCols.nonNumericSummary
      (numeric = numeric, nonNumeric = nonNumeric)
    end summary

    inline def describe =
      val (numeric, nonNumeric) = nt.summary
      println("===== Numeric Columns: =======")
      numeric.ptbln
      println("\n====== Non-Numeric Columns: ======")
      nonNumeric.ptbln
    end describe

  end extension
end Stats
