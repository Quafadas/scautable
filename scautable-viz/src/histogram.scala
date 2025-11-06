package io.github.quafadas.scautable

import io.github.quafadas.table.*
import scala.annotation.implicitNotFound
import scala.Tuple.Elem
import io.github.quafadas.scautable.ColumnTyped.IsColumn
import io.github.quafadas.scautable.ColumnTyped.IdxAtName
import scala.NamedTuple.NamedTuple
import viz.Plottable.plot


object Histogram:

  val histogramResource = "histogram.vg.json"

  extension [CC[X] <: Iterable[X], K <: Tuple, V <: Tuple](data: Iterable[NamedTuple[K, V]])

    inline def plotHistogram[S <: String](resourceSpec: Option[String] = None, mods: Seq[ujson.Value => Unit] = Seq.empty, maxBins: Option[Int])(using
        @implicitNotFound("Column ${S} not found")
        ev: IsColumn[S, K] =:= true,
        s: ValueOf[S],
        classTag: scala.reflect.ClassTag[Elem[V, IdxAtName[S, K]]]
    )(using ctx: viz.LowPriorityPlotTarget): Unit =
      import viz.vegaFlavour
      import viz.NamedTupleReadWriter.given_ReadWriter_T
      val oneCol = data.column[S]
      val specR = os.resource / resourceSpec.fold(histogramResource)(_.trim)
      val colName: String = s.value


      def summonNumeric[T](cls: Class[?], colName: String): Option[Numeric[T]] = cls match {
        case c if c == classOf[Int] => Some(Numeric.IntIsIntegral.asInstanceOf[Numeric[T]])
        case c if c == classOf[Long] => Some(Numeric.LongIsIntegral.asInstanceOf[Numeric[T]])
        case c if c == classOf[Float] => Some(Numeric.FloatIsFractional.asInstanceOf[Numeric[T]])
        case c if c == classOf[Double] => Some(Numeric.DoubleIsFractional.asInstanceOf[Numeric[T]])
        case c if c == classOf[Short] => Some(Numeric.ShortIsIntegral.asInstanceOf[Numeric[T]])
        case c if c == classOf[Byte] => Some(Numeric.ByteIsIntegral.asInstanceOf[Numeric[T]])
        case c if c == classOf[BigInt] => Some(Numeric.BigIntIsIntegral.asInstanceOf[Numeric[T]])
        case c if c == classOf[BigDecimal] => Some(Numeric.BigDecimalIsFractional.asInstanceOf[Numeric[T]])
        case _ => None
      }

      // Get numeric instance for the column type
      val numeric: Option[Numeric[Elem[V, IdxAtName[S, K]]]] = summonNumeric[Elem[V, IdxAtName[S, K]]](classTag.runtimeClass, s.value)
      val titel = upickle.default.writeJs(
        (
          text = colName,
          fontSize = (
            expr = "width / 20"
          )
        )
      )

      numeric match
        case None =>
          specR.plot(
            List(
              spec => spec("data")("values") = oneCol.map:
                d => ujson.Obj("Col" -> d.toString()),
              spec =>
                spec("title") = titel
            )
          )

        case Some(numerical) =>
          val colName: String = s.value
          specR.plot(
            List(
              spec =>
                spec("data") = upickle.default.writeJs(
                  (values = oneCol.map: d =>
                    ujson.Obj(
                      colName -> numerical.toDouble(d)
                    ))
                ),
              spec => spec("encoding")("x") = ujson.Obj(
                "field" -> colName,
                "bin" -> maxBins.fold(ujson.Bool(true))(min => ujson.Obj("maxbins" -> min, "nice" -> true, "bin" -> true))
              ),
              spec => spec("description") = colName,
              spec => spec("title") = titel
            )
          )
    end plotHistogram

    inline def plotHistogram[S <: String](using
        @implicitNotFound("Column ${S} not found")
        ev: IsColumn[S, K] =:= true,
        s: ValueOf[S],

        classTag: scala.reflect.ClassTag[Elem[V, IdxAtName[S, K]]]
    )(using ctx: viz.LowPriorityPlotTarget): Unit =
      plotHistogram[S](None, Seq.empty, None)