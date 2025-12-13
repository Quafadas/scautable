package io.github.quafadas.scautable

import scala.compiletime.*
import scala.reflect.ClassTag

/** Decoder for converting a column of strings to a typed array. */
private[scautable] trait ColumnDecoder[T]:
  def decodeColumn(values: scala.collection.mutable.ArrayBuffer[String]): Array[T]
end ColumnDecoder

private[scautable] object ColumnDecoder:
  import scala.collection.mutable.ArrayBuffer

  inline given intDecoder: ColumnDecoder[Int] with
    def decodeColumn(values: ArrayBuffer[String]): Array[Int] =
      val arr = new Array[Int](values.length)
      var i = 0
      while i < values.length do
        arr(i) = values(i).toInt
        i += 1
      end while
      arr
    end decodeColumn
  end intDecoder

  inline given longDecoder: ColumnDecoder[Long] with
    def decodeColumn(values: ArrayBuffer[String]): Array[Long] =
      val arr = new Array[Long](values.length)
      var i = 0
      while i < values.length do
        arr(i) = values(i).toLong
        i += 1
      end while
      arr
    end decodeColumn
  end longDecoder

  inline given doubleDecoder: ColumnDecoder[Double] with
    def decodeColumn(values: ArrayBuffer[String]): Array[Double] =
      val arr = new Array[Double](values.length)
      var i = 0
      while i < values.length do
        arr(i) = values(i).toDouble
        i += 1
      end while
      arr
    end decodeColumn
  end doubleDecoder

  inline given booleanDecoder: ColumnDecoder[Boolean] with
    def decodeColumn(values: ArrayBuffer[String]): Array[Boolean] =
      val arr = new Array[Boolean](values.length)
      var i = 0
      while i < values.length do
        val s = values(i).toLowerCase
        arr(i) = s == "true" || s == "1"
        i += 1
      end while
      arr
    end decodeColumn
  end booleanDecoder

  inline given stringDecoder: ColumnDecoder[String] with
    def decodeColumn(values: ArrayBuffer[String]): Array[String] =
      values.toArray
  end stringDecoder

  inline given optionDecoder[T](using d: ColumnDecoder[T], ct: ClassTag[Option[T]]): ColumnDecoder[Option[T]] with
    def decodeColumn(values: ArrayBuffer[String]): Array[Option[T]] =
      // For Option types, we need to handle empty strings specially
      val nonEmpty = ArrayBuffer[String]()
      val emptyIndices = ArrayBuffer[Int]()
      var i = 0
      while i < values.length do
        if values(i).isEmpty then
          emptyIndices += i
          nonEmpty += "0" // placeholder for decoding (will be replaced with None)
        else nonEmpty += values(i)
        end if
        i += 1
      end while

      // Decode non-empty values
      val decoded = d.decodeColumn(nonEmpty)
      val result = new Array[Option[T]](values.length)

      i = 0
      var emptyIdx = 0
      while i < values.length do
        if emptyIdx < emptyIndices.length && emptyIndices(emptyIdx) == i then
          result(i) = None
          emptyIdx += 1
        else result(i) = Some(decoded(i))
        end if
        i += 1
      end while
      result
    end decodeColumn
  end optionDecoder
end ColumnDecoder

/** Helper object for decoding columns at runtime using compile-time derived decoders */
private[scautable] object ColumnsDecoder:
  import scala.collection.mutable.ArrayBuffer

  /** Decode columns recursively. V is the tuple of Array types (e.g., (Array[Int], Array[String])). */
  inline def decodeAllColumns[V <: Tuple](buffers: Array[ArrayBuffer[String]], idx: Int = 0): V =
    inline erasedValue[V] match
      case _: EmptyTuple =>
        EmptyTuple.asInstanceOf[V]
      case _: (Array[h] *: t) =>
        val decoder = summonInline[ColumnDecoder[h]]
        val head: Array[h] = decoder.decodeColumn(buffers(idx))
        val tail = decodeAllColumns[t](buffers, idx + 1)
        (head *: tail).asInstanceOf[V]

end ColumnsDecoder
