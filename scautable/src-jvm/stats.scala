package io.github.quafadas.scautable

import scala.NamedTuple.*
import scala.collection.BuildFrom
import scala.compiletime.constValueTuple
import scala.util.Random

import com.tdunning.math.stats.TDigest

import io.github.quafadas.scautable.ColumnFormat.*
import io.github.quafadas.scautable.ConsoleFormat.ptbln
import io.github.quafadas.scautable.NamedTupleIteratorExtensions.*

object Stats:

  /** Accumulates the non-missing values of a numeric column for quantiles, with bounded memory.
    *
    * Up to `exactLimit` values are buffered in a primitive `Array[Double]` and quantiles are exact, using linear interpolation between closest ranks (Hyndman & Fan type 7), the
    * default of `numpy.quantile`, `pandas.Series.quantile` and R's `quantile`. t-digest is badly wrong at small n (median of `[1, 100]` comes out as 100), hence the exact path.
    *
    * Once more than `exactLimit` values arrive, the buffer is flushed into a t-digest and dropped, and every later value streams into the digest. Memory is then bounded (roughly
    * `exactLimit` doubles plus the digest's centroids) however long the input iterator is, and quantiles are approximate. `min` and `max` are always exact.
    */
  final class QuantileSketch(exactLimit: Int = QuantileSketch.DefaultExactLimit):
    private var values: Array[Double] = new Array[Double](16)
    private var digest: TDigest | Null = null
    private var n: Long = 0
    private var sorted: Boolean = true
    private var minV: Double = Double.PositiveInfinity
    private var maxV: Double = Double.NegativeInfinity

    def add(v: Double): Unit =
      if v < minV then minV = v
      end if
      if v > maxV then maxV = v
      end if
      val d = digest
      if d != null then d.add(v)
      else if n < exactLimit then
        if n == values.length then values = java.util.Arrays.copyOf(values, math.min(values.length * 2, exactLimit))
        end if
        values(n.toInt) = v
        sorted = false
      else
        val fresh = TDigest.createDigest(QuantileSketch.Compression)
        var i = 0
        while i < n do
          fresh.add(values(i))
          i += 1
        end while
        fresh.add(v)
        digest = fresh
        values = Array.emptyDoubleArray
      end if
      n += 1
    end add

    def size: Long = n

    /** True while every value is still held and quantiles are exact. */
    def isExact: Boolean = digest == null

    /** Minimum of the added values, or `NaN` if none were added. */
    def min: Double = if n == 0 then Double.NaN else minV

    /** Maximum of the added values, or `NaN` if none were added. */
    def max: Double = if n == 0 then Double.NaN else maxV

    /** The `p`-quantile (0 <= p <= 1), or `NaN` if no values were added. Exact (type 7) up to `exactLimit` values, t-digest estimate beyond. */
    def quantile(p: Double): Double =
      require(p >= 0.0 && p <= 1.0, s"quantile must be in [0, 1], got $p")
      val d = digest
      if n == 0 then Double.NaN
      else if d != null then d.quantile(p)
      else
        if !sorted then
          java.util.Arrays.sort(values, 0, n.toInt)
          sorted = true
        end if
        val h = (n - 1) * p
        val lo = math.floor(h).toInt
        val hi = math.min(lo + 1, n.toInt - 1)
        values(lo) + (h - lo) * (values(hi) - values(lo))
      end if
    end quantile
  end QuantileSketch

  object QuantileSketch:
    /** Columns with at most this many values get exact quantiles. */
    val DefaultExactLimit: Int = 1000

    /** t-digest compression once a column outgrows the exact buffer. 200 keeps median error well under 1% of the range for typical data. */
    val Compression: Double = 200
  end QuantileSketch

  type StatsContext[T] = (typ: String, sum: Double, count: Int, mean: Double, digest: QuantileSketch)

  type NonNumericStatsContext[T] = (uniqueValues: Set[String], counts: scala.collection.mutable.Map[String, Int])

  private def scalarToDoubleOpt(a: Any): Option[Double] = a match
    case v: Double => if v.isNaN then None else Some(v)
    case v: Float  => if v.isNaN then None else Some(v.toDouble)
    case v: Int    => Some(v.toDouble)
    case v: Long   => Some(v.toDouble)
    case v: Short  => Some(v.toDouble)
    case v: Byte   => Some(v.toDouble)
    case _         => None

  /** The numeric value of a cell (plain or `Some`-wrapped), or `None` if it is missing, NaN or not numeric. Single source of truth for both the value and whether to include it. */
  private[scautable] def toDoubleOpt(a: Any): Option[Double] = a match
    case Some(v) => scalarToDoubleOpt(v)
    case v       => scalarToDoubleOpt(v)

  private def scalarTypeName(a: Any): String = a match
    case _: Double => "Double"
    case _: Float  => "Float"
    case _: Int    => "Int"
    case _: Long   => "Long"
    case _: Short  => "Short"
    case _: Byte   => "Byte"
    case _         => "Unknown"

  /** Runtime numeric type name of a cell (plain or `Some`-wrapped); "Unknown" for `None` or non-numeric values. */
  private[scautable] def numericTypeName(a: Any): String = a match
    case Some(v) => scalarTypeName(v)
    case v       => scalarTypeName(v)

  extension [K <: Tuple, V <: Tuple](nt: Iterator[NamedTuple[K, V]])
    /** Computes comprehensive statistical summaries for all numeric columns in the dataset.
      *
      * This method processes an `Iterator` of `NamedTuple`s and calculates descriptive statistics for each column that contains numeric data (Int, Long, Float, Double, or
      * Option-wrapped versions). Non-numeric columns are ignored. Quantiles are exact, using linear interpolation (the numpy / pandas default), for columns of up to
      * `QuantileSketch.DefaultExactLimit` non-missing values; longer columns fall back to a T-Digest estimate to bound memory.
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
      *   - `typ`: Detected data type ("Int", "Long", "Float", "Double", "Short", "Byte", or "Unknown")
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
          val digest = new QuantileSketch()

          val typ = (typ = numericTypeName(value))

          // Process the first value
          val firstValue = toDoubleOpt(value)
          val initialSum = firstValue.getOrElse(0.0)
          val initialCount = if firstValue.isDefined then 1 else 0

          firstValue.foreach(digest.add)

          typ ++ (sum = initialSum, count = initialCount, mean = if initialCount == 0 then 0.0 else initialSum, digest = digest)
        }

        // Process the remaining rows
        val res = nt.foldLeft(zeroValue) { (acc, row) =>
          val result = acc.zip(row).map[StatsContext] { [T] => (t: T) =>
            t match
              case ((typ: String, sum: Double, count: Int, mean: Double, digestA: QuantileSketch), incA) =>
                val incAOpt = toDoubleOpt(incA)

                if incAOpt.isDefined then
                  val incADouble = incAOpt.get
                  digestA.add(incADouble)
                  val newSum = sum + incADouble
                  val newCount = count + 1
                  val newMean = if newCount == 0 then 0.0 else newSum / newCount

                  // Update type if it was Unknown and we now have a real value
                  val updatedTyp =
                    if typ == "Unknown" then numericTypeName(incA)
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
        val asList: List[(typ: String, sum: Double, count: Int, mean: Double, digest: QuantileSketch)] =
          res.toList.asInstanceOf[List[(typ: String, sum: Double, count: Int, mean: Double, digest: QuantileSketch)]]
        headers
          .zip(asList)
          .map { case (name, res) => (name = name) ++ res }
          .map { p =>
            (
              name = p.name,
              typ = p.typ,
              mean = p.mean,
              min = p.digest.min,
              `0.25` = p.digest.quantile(0.25),
              median = p.digest.quantile(0.5),
              `0.75` = p.digest.quantile(0.75),
              max = p.digest.max
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
      val digest = new QuantileSketch()

      // We'll start with empty stats
      val base = (sum = 0.0, count = 0, mean = 0.0, digest = digest)

      // "Unknown" for None / non-numeric; fixed up during processing once a real value appears
      val typ = (typ = numericTypeName(value))
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
      * This method processes an `Iterable` of `NamedTuple`s and calculates descriptive statistics for each column that contains numeric data (Int, Long, Float, Double, or
      * Option-wrapped versions). Non-numeric columns are ignored. Quantiles are exact, using linear interpolation (the numpy / pandas default), for columns of up to
      * `QuantileSketch.DefaultExactLimit` non-missing values; longer columns fall back to a T-Digest estimate to bound memory.
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
      *   - `typ`: Detected data type ("Int", "Long", "Float", "Double", "Short", "Byte", or "Unknown")
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
            case ((typ: String, sum: Double, count: Int, mean: Double, digestA: QuantileSketch), incA) =>
              val incAOpt = toDoubleOpt(incA)

              if incAOpt.isDefined then
                val incADouble = incAOpt.get
                digestA.add(incADouble)
                val newSum = sum + incADouble
                val newCount = count + 1
                val newMean = if newCount == 0 then 0.0 else newSum / newCount

                // Update type if it was Unknown and we now have a real value
                val updatedTyp =
                  if typ == "Unknown" then numericTypeName(incA)
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
      val asList: List[(typ: String, sum: Double, count: Int, mean: Double, digest: QuantileSketch)] =
        res.toList.asInstanceOf[List[(typ: String, sum: Double, count: Int, mean: Double, digest: QuantileSketch)]]
      headers
        .zip(asList)
        .map { case (name, res) => (name = name) ++ res }
        .map { p =>
          (
            name = p.name,
            typ = p.typ,
            mean = p.mean,
            min = p.digest.min,
            `0.25` = p.digest.quantile(0.25),
            median = p.digest.quantile(0.5),
            `0.75` = p.digest.quantile(0.75),
            max = p.digest.max
          )
        }
    end numericSummary

    /** Computes comprehensive statistical summaries for both numeric and non-numeric columns.
      *
      * This method processes an `Iterable` of `NamedTuple`s and calculates both numeric and categorical statistics for all columns in the dataset. It automatically separates
      * columns into numeric (Int, Long, Float, Double, or Option-wrapped versions) and non-numeric types, providing comprehensive analysis in a single call.
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

    /** Print [[summary]] to the console.
      *
      * The numeric columns are tagged with `SigFigs[4]` for display only - `summary` still returns raw `Double`s, so nothing downstream of it is affected.
      */
    inline def describe =
      val (numeric, nonNumeric) = nt.summary
      println("===== Numeric Columns: =======")
      numeric
        .formatColumn["mean", SigFigs[4]]
        .formatColumn["min", SigFigs[4]]
        .formatColumn["0.25", SigFigs[4]]
        .formatColumn["median", SigFigs[4]]
        .formatColumn["0.75", SigFigs[4]]
        .formatColumn["max", SigFigs[4]]
        .ptbln
      println("\n====== Non-Numeric Columns: ======")
      nonNumeric.ptbln
    end describe

  end extension
end Stats
