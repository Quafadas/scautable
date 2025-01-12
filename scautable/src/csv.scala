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
import scala.compiletime.ops.int.*
import fansi.Str
import scala.collection.View.FlatMap
import io.github.quafadas.scautable.ConsoleFormat.*

import scala.math.Fractional.Implicits.*


@experimental
object CSV:

  inline def constValueAll[A]: A =
    inline erasedValue[A] match
      case _: *:[h, t] => (constValueAll[h] *: constValueAll[t]).asInstanceOf[A]
      case _: EmptyTuple => EmptyTuple.asInstanceOf[A]
      case _ => constValue[A]


  def listToTuple[A](list: List[A]): Tuple = list match
    case Nil    => EmptyTuple
    case h :: t => h *: listToTuple(t)

  type Concat[X <: String, Y <: Tuple] = X *: Y

  type ConcatSingle[X, A] = X *: A *: EmptyTuple

  type Negate[T <: Tuple] <: Tuple = T match
    case EmptyTuple => EmptyTuple
    case (head *: tail) => head match
      case false => true *: Negate[tail]
      case true => false *: Negate[tail]

  type IsColumn[StrConst <: String, T <: Tuple] = T match
    case EmptyTuple => false
    case (head *: tail) => IsMatch[StrConst, head] match
      case true => true
      case false => IsColumn[StrConst, tail]
    case _ => false

  type Tail[T <: Tuple, S <: String] <: Tuple = T match
    case EmptyTuple => EmptyTuple
    case head *: tail =>
      IsMatch[S, head] match
        case true => EmptyTuple
        case false => Tail[tail, S]

  type ReplaceOneName[T <: Tuple, StrConst <: String, A <: String] <: Tuple = T match
    case EmptyTuple => EmptyTuple
    case nameHead *: nameTail =>
      IsMatch[nameHead, StrConst] match
        case true => A *: nameTail
        case false => nameHead *: ReplaceOneName[nameTail, StrConst, A]

  type ReplaceOneTypeAtName[N <: Tuple, StrConst <: String, T <: Tuple, A] <: Tuple = (N, T) match
    case (EmptyTuple, _) => EmptyTuple
    case (_, EmptyTuple) => EmptyTuple
    case (nameHead *: nameTail, typeHead *: typeTail) =>
      IsMatch[nameHead, StrConst] match
          case true => A *: typeTail
          case false =>
            typeHead *: ReplaceOneTypeAtName[nameTail, StrConst, typeTail, A]

  type DropOneTypeAtName[N <: Tuple, StrConst <: String, T <: Tuple] <: Tuple = (N, T) match
    case (EmptyTuple, _) => EmptyTuple
    case (_, EmptyTuple) => EmptyTuple
    case (nameHead *: nameTail, typeHead *: typeTail) =>
      IsMatch[nameHead, StrConst] match
          case true => typeTail
          case false =>
            typeHead *: DropOneTypeAtName[nameTail, StrConst, typeTail]

  type GetTypesAtNames[N <: Tuple, ForNames <: Tuple, T <: Tuple] <: Tuple = ForNames match
    case EmptyTuple => EmptyTuple
    case nameHead *: nameTail => GetTypeAtName[N, nameHead, T] *: GetTypesAtNames[N, nameTail, T]

  type GetTypeAtName[N <: Tuple, StrConst <: String, T <: Tuple] = (N, T) match
    case (EmptyTuple, _) => EmptyTuple
    case (_, EmptyTuple) => EmptyTuple
    case (nameHead *: nameTail, typeHead *: typeTail) =>
      IsMatch[nameHead, StrConst] match
          case true => typeHead
          case false =>
            GetTypeAtName[nameTail, StrConst, typeTail]

  type DropAfterName[T , StrConst <: String] = T match
    case EmptyTuple => EmptyTuple
    case (head *: tail) => IsMatch[StrConst, head] match
      case true => EmptyTuple
      case false => head *: DropAfterName[tail, StrConst]

  type DropOneName[T, StrConst <: String] <: Tuple = T match
    case EmptyTuple => EmptyTuple
      case (head *: tail) => IsMatch[StrConst, head] match
        case true => DropOneName[tail, StrConst]
        case false => head *: DropOneName[tail , StrConst]

  type IsMatch[A <: String, B <: String] = B match
    case A => true
    case _ => false

  type IsNumeric[T] <: Boolean = T match
    case Option[a] => IsNumeric[a]
    case Int => true
    case Long => true
    case Float => true
    case Double => true
    case _ => false

  type NumericColsIdx[T <: Tuple] <: Tuple =
    T match
      case EmptyTuple => EmptyTuple
      case (head *: tail) => IsNumeric[head] match
        case true => true *: NumericColsIdx[tail]
        case false => false *: NumericColsIdx[tail]

  type SelectFromTuple[T <: Tuple, Bools <: Tuple] <: Tuple = T match
    case EmptyTuple => EmptyTuple
    case (head *: tail) => Bools match
      case (true *: boolTail) => head *: SelectFromTuple[tail, boolTail]
      case (false *: boolTail) => SelectFromTuple[tail, boolTail]

  type AllAreColumns[T <: Tuple, K <: Tuple] <: Boolean = T match
    case EmptyTuple => true
    case head *: tail => IsColumn[head, K] match
      case true => AllAreColumns[tail, K]
      case false => false

  type TupleContainsIdx[Search <: Tuple, In <: Tuple ] <: Tuple = In match
    case EmptyTuple => EmptyTuple
    case head *: tail => Search match
      case EmptyTuple => false *: EmptyTuple
      case searchHead *: searchTail => IsColumn[head, Search] match
        case true => true *: TupleContainsIdx[Search, tail]
        case false => false *: TupleContainsIdx[Search, tail]



  type StringifyTuple[T >: Tuple] <: Tuple = T match
    case EmptyTuple => EmptyTuple
    case head *: tail => (head : String) *: StringifyTuple[tail]

  type StringyTuple[T <: Tuple] <: Tuple = T match
    case EmptyTuple => EmptyTuple
    case head *: tail =>  String *: StringyTuple[tail]


  type ReReverseXLL[t] = Size[t] match
    case 0 => EmptyTuple
    case 1 => t
    case 2 => t
    case 3 => t
    case 4 => t
    case 5 => t
    case 6 => t
    case 7 => t
    case 8 => t
    case 9 => t
    case 10 => t
    case 11 => t
    case 12 => t
    case 13 => t
    case 14 => t
    case 15 => t
    case 16 => t
    case 17 => t
    case 18 => t
    case 19 => t
    case 20 => t
    case 21 => t
    case 22 => t
    case _ => ReverseTuple[t]

  type ReverseTuple[T <: Tuple] <: Tuple = T match
    case EmptyTuple => EmptyTuple
    case x *: xs => ReverseTuple[xs] *: x

  type Size[T] <: Int = T match
    case EmptyTuple => 0
    case x *: xs => 1 + Size[xs]

  extension [K, V, K1 <: Tuple & K, V1 <: Tuple & K](itr: Iterator[NamedTuple[K1, V1]])

    inline def renameColumn[From <: String, To <: String](using ev: IsColumn[From, K1] =:= true, FROM: ValueOf[From], TO: ValueOf[To]): Iterator[NamedTuple[ReplaceOneName[K1, From, To], V1]]= {
        itr.map{_.withNames[ReplaceOneName[K1, From, To]].asInstanceOf[NamedTuple[ReplaceOneName[K1, From, To], V1]]}
    }

    inline def addColumn[S <: String, A](fct: (tup: NamedTuple.NamedTuple[K1, V1]) => A): Iterator[NamedTuple[S *: K1, A *: V1]] =
      itr.map{
        (tup: NamedTuple[K1, V1]) =>
          (fct(tup) *: tup.toTuple).withNames[Concat[S, K1]]
      }

    inline def forceColumnType[S <: String, A]: Iterator[NamedTuple[K1, ReplaceOneTypeAtName[K1, S, V1, A]]] = {
      itr.map(_.asInstanceOf[NamedTuple[K1, ReplaceOneTypeAtName[K1, S, V1, A]]])
    }

    inline def mapColumn[S <: String, A](fct: GetTypeAtName[K1, S, V1] => A)(using ev: IsColumn[S, K1] =:= true, s: ValueOf[S]): Iterator[NamedTuple[K1, ReplaceOneTypeAtName[K1, S, V1, A]]]= {
      import scala.compiletime.ops.string.*
      val headers = constValueTuple[K1].toList.map(_.toString())

      /**
        * Aaahhhh... apparently, TupleXXL is in reverse order!
        */
      val headers2 = if headers.size > 22 then headers.reverse else headers
      val idx = headers.indexOf(s.value)
      if(idx == -1) ???
      itr.map{
        (x
        : NamedTuple[K1, V1]) =>
          val tup = x.toTuple
          val typ = tup(idx).asInstanceOf[GetTypeAtName[K1, S, V1]]
          val mapped = fct(typ)
          val (head, tail) = x.toTuple.splitAt(idx)
          (head ++ mapped *: tail.tail).withNames[K1].asInstanceOf[NamedTuple[K1,ReplaceOneTypeAtName[K1,  S, V1, A]]]
      }
    }

    // inline def numericCols: Iterator[
    //     NamedTuple.NamedTuple[
    //       io.github.quafadas.scautable.CSV.SelectFromTuple[K1,
    //         io.github.quafadas.scautable.CSV.TupleContainsIdx[
    //           io.github.quafadas.scautable.CSV.SelectFromTuple[K1,
    //             io.github.quafadas.scautable.CSV.NumericColsIdx[V1]],
    //         K1]
    //       ],
    //       io.github.quafadas.scautable.CSV.SelectFromTuple[V1,
    //         io.github.quafadas.scautable.CSV.TupleContainsIdx[
    //           io.github.quafadas.scautable.CSV.SelectFromTuple[K1,
    //             io.github.quafadas.scautable.CSV.NumericColsIdx[V1]],
    //         K1]
    //       ]
    //     ]
    //   ] =
    //     val ev1 = summonInline[AllAreColumns[SelectFromTuple[K1, NumericColsIdx[V1]], K1] =:= true]
    //     columns[SelectFromTuple[K1, NumericColsIdx[V1]]](using ev1)

    // inline def nonNumericCols: Iterator[
    //   NamedTuple.NamedTuple[
    //     io.github.quafadas.scautable.CSV.SelectFromTuple[K1,
    //       io.github.quafadas.scautable.CSV.TupleContainsIdx[
    //         io.github.quafadas.scautable.CSV.SelectFromTuple[K1,
    //           io.github.quafadas.scautable.CSV.Negate[
    //             io.github.quafadas.scautable.CSV.NumericColsIdx[V1]]
    //         ],
    //       K1]
    //     ],
    //     io.github.quafadas.scautable.CSV.SelectFromTuple[V1,
    //       io.github.quafadas.scautable.CSV.TupleContainsIdx[
    //         io.github.quafadas.scautable.CSV.SelectFromTuple[K1,
    //           io.github.quafadas.scautable.CSV.Negate[
    //             io.github.quafadas.scautable.CSV.NumericColsIdx[V1]]
    //         ],
    //       K1]
    //     ]
    //   ]
    // ] =
    //     val ev1 = summonInline[
    //       AllAreColumns[SelectFromTuple[K1, Negate[NumericColsIdx[V1]]], K1] =:= true
    //     ]
    //     columns[SelectFromTuple[K1, Negate[NumericColsIdx[V1]]]](using ev1)

    inline def resolve[ST <: Tuple]:SelectFromTuple[K1, TupleContainsIdx[ST, K1]]  = ("Pclass", "Age", "SibSp", "Parch", "Fare").asInstanceOf[SelectFromTuple[K1, TupleContainsIdx[ST, K1]]]
    inline def resolveT[ST <: Tuple]:GetTypesAtNames[K1, SelectFromTuple[K1, TupleContainsIdx[ST, K1]] ,V1]  = (1, Some(2.0), 1, 1, 2.0).asInstanceOf[GetTypesAtNames[K1, SelectFromTuple[K1, TupleContainsIdx[ST, K1]] ,V1]]

    inline def resolveNT[ST <: Tuple]:NamedTuple[
      SelectFromTuple[K1, TupleContainsIdx[ST, K1]],
      GetTypesAtNames[K1, SelectFromTuple[K1, TupleContainsIdx[ST, K1]] ,V1]
     ] =
        (1, Some(2.0), 1, 1, 2.0)
          .withNames[("Pclass", "Age", "SibSp", "Parch", "Fare")]
          .asInstanceOf[
            NamedTuple[
              SelectFromTuple[K1, TupleContainsIdx[ST, K1]],
              GetTypesAtNames[K1, SelectFromTuple[K1, TupleContainsIdx[ST, K1]] ,V1]
          ]
    ]



    inline def columns[ST <: Tuple](using ev: AllAreColumns[ST, K1] =:= true):
      Iterator[
        NamedTuple[
                SelectFromTuple[K1, TupleContainsIdx[ST, K1]],
                GetTypesAtNames[K1, SelectFromTuple[K1, TupleContainsIdx[ST, K1]] ,V1]
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

      itr.map[NamedTuple[SelectFromTuple[K1, TupleContainsIdx[ST, K1]], GetTypesAtNames[K1, SelectFromTuple[K1, TupleContainsIdx[ST, K1]], V1]]]{
        (x: NamedTuple[K1, V1]) =>
          val tuple = x.toTuple

          // println("in tuple")
          // println(tuple.toList.mkString(","))
          val selected: Tuple = idxes.foldRight(EmptyTuple: Tuple){
            (idx, acc) =>
              // println(tuple(idx))
              tuple(idx) *: acc
          }

          val out = selected
            .withNames[SelectFromTuple[K1, TupleContainsIdx[ST, K1]]]
            .asInstanceOf[
              NamedTuple[
                SelectFromTuple[K1, TupleContainsIdx[ST, K1]],
                GetTypesAtNames[K1, SelectFromTuple[K1, TupleContainsIdx[ST, K1]] ,V1]
              ]
            ]

          out
      }

    inline def numericColSummary[S <: String](using ev: IsColumn[S, K1] =:= true, isNum: IsNumeric[GetTypeAtName[K1, S, V1]] =:= true,  s: ValueOf[S], a: Fractional[GetTypeAtName[K1, S, V1]]) =
      val numericValues = itr.column[S].toList.asInstanceOf[List[GetTypeAtName[K1, S, V1]]]

      val sortedValues = numericValues.sorted
      val size = sortedValues.size

      def percentile(p: Double) : Double = {
        val rank = p * (size - 1)
        val lower = sortedValues(rank.toInt)
        val upper = sortedValues(math.ceil(rank).toInt)
        lower.toDouble + a.minus(upper, lower).toDouble * (rank - rank.toInt)
      }

      val mean = numericValues.sum / a.fromInt(size)
      val min = sortedValues.head
      val max = sortedValues.last
      val variance = numericValues.map(x => a.minus(x, mean)).map(x => a.times(x, x)).sum / a.fromInt(size)

      val percentiles = List(0.25, 0.5, 0.75).map(percentile)

      val std = math.sqrt(variance.toDouble)

      (mean, std, min, percentiles(0), percentiles(1), percentiles(2), max).withNames[("mean", "std", "min", "25%", "50%", "75%", "max")]


    inline def column[S <: String](using ev: IsColumn[S, K1] =:= true, s: ValueOf[S]): Iterator[GetTypeAtName[K1, S, V1]] = {
      val headers = constValueTuple[K1].toList.map(_.toString())
      /**
        * Aaahhhh... apparently, TupleXXL is in reverse order!
        */
      val headers2 = if headers.size > 22 then headers.reverse else headers

      val idx = headers2.indexOf(s.value)
      itr.map(x =>
        x.toTuple(idx).asInstanceOf[GetTypeAtName[K1, S, V1]]
      )
    }

    inline def dropColumn[S <: String](using ev: IsColumn[S, K1] =:= true, s: ValueOf[S]): Iterator[NamedTuple[DropOneName[K1, S], DropOneTypeAtName[K1, S, V1]]] =
      val headers = constValueTuple[K1].toList.map(_.toString())
      /**
        * Aaahhhh... apparently, TupleXXL is in reverse order!
        */
      val headers2 = if headers.size > 22 then headers.reverse else headers
      val idx = headers2.indexOf(s.value)

      type RemoveMe = GetTypeAtName[K1, S, V1]

      itr.map{
        (x: NamedTuple[K1, V1]) =>
          val (head, tail) = x.toTuple.splitAt(idx)
          head match
            case x: EmptyTuple => tail.tail.withNames[DropOneName[K, S]].asInstanceOf[NamedTuple[DropOneName[K1, S], DropOneTypeAtName[K1, S, V1]]]
            case _ => (head ++ tail.tail).withNames[DropOneName[K, S]].asInstanceOf[NamedTuple[DropOneName[K1, S], DropOneTypeAtName[K1, S, V1]]]
      }
  end extension

  extension [K <: Tuple, V <: Tuple](nt: Seq[NamedTuple[K, V]])

    inline def addColumn[S <: String, A](fct: (tup: NamedTuple.NamedTuple[K, V]) => A): Seq[NamedTuple[S *: K, A *: V]] =
      nt.toIterator.addColumn[S, A](fct).toSeq

    inline def columns[ST <: Tuple](using ev: AllAreColumns[ST, K] =:= true):
      Seq[
        NamedTuple[
          SelectFromTuple[K, TupleContainsIdx[ST, K]],
          GetTypesAtNames[K, SelectFromTuple[K, TupleContainsIdx[ST, K]] ,V]
        ]
      ] =
      nt.toIterator.columns[ST](using ev).toSeq

    inline def dropColumn[S <: String](using ev: IsColumn[S, K] =:= true, s: ValueOf[S]): Seq[NamedTuple[DropOneName[K, S], DropOneTypeAtName[K, S, V]]] =
      nt.toIterator.dropColumn[S].toSeq

    inline def mapColumn[S <: String, A](fct: GetTypeAtName[K, S, V] => A)(using ev: IsColumn[S, K] =:= true, s: ValueOf[S]): Seq[NamedTuple[K, ReplaceOneTypeAtName[K, S, V, A]]]= {
      nt.toIterator.mapColumn[S, A](fct).toSeq
    }
    inline def forceColumnType[S <: String, A]: Any = {
      nt.toIterator.forceColumnType[S, A].toSeq
    }
    inline def renameColumn[From <: String, To <: String](using ev: IsColumn[From, K] =:= true, FROM: ValueOf[From], TO: ValueOf[To]): Seq[NamedTuple[ReplaceOneName[K, From, To], V]]= {
      nt.toIterator.renameColumn[From, To].toSeq
    }


  end extension

  given IteratorToExpr2[K](using ToExpr[String], Type[K]): ToExpr[CsvIterator[K]] with
    def apply(opt: CsvIterator[K])(using Quotes): Expr[CsvIterator[K]] =
      val str = Expr(opt.getFilePath)
      '{
        new CsvIterator[K]($str)
      }
    end apply
  end IteratorToExpr2

  transparent inline def url[T](inline path: String) = ${ readCsvFromUrl('path) }

  transparent inline def pwd[T](inline path: String) = ${ readCsvFromCurrentDir('path) }

  transparent inline def resource[T](inline path: String) = ${ readCsvResource('path) }

  transparent inline def absolutePath[T](inline path: String) = ${ readCsvAbolsutePath('path) }

  // TODO : I can't figure out how to refactor the common code inside the contraints of metaprogamming... 4 laterz.

  private def readCsvFromUrl(pathExpr: Expr[String])(using Quotes) =
    import quotes.reflect.*

    report.warning("This method saves the CSV to a local temp file and opens it. There may be performance implications - it is recommended to use one of the other methods where possible.")
    val source = Source.fromURL(pathExpr.valueOrAbort)
    val tmpPath = os.temp(dir = os.pwd, prefix = "temp_csv_", suffix = ".csv")
    os.write.over(tmpPath, source.toArray.mkString)

    val headerLine =
      try Source.fromFile(tmpPath.toString()).getLines().next()
      finally source.close()

    val headers = headerLine.split(",").toList
    val tupleExpr2 = Expr.ofTupleFromSeq(headers.map(Expr(_)))


    tupleExpr2 match
      case '{ $tup: t } =>
        val itr = new CsvIterator[t](tmpPath.toString())
        // '{ NamedTuple.build[t & Tuple]()($tup) }
        Expr(itr)
      case _ => report.throwError(s"Could not summon Type for type: ${tupleExpr2.show}")
    end match

  end readCsvFromUrl

  private def readCsvFromCurrentDir(pathExpr: Expr[String])(using Quotes) =
    import quotes.reflect.*

    val path = os.pwd / pathExpr.valueOrAbort

    val source = Source.fromFile(path.toString)
    val headerLine =
      try source.getLines().next()
      finally source.close()

    val headers = headerLine.split(",").toList
    val tupleExpr2 = Expr.ofTupleFromSeq(headers.map(Expr(_)))

    tupleExpr2 match
      case '{ $tup: t } =>
        val itr = new CsvIterator[t](path.toString)
        // '{ NamedTuple.build[t & Tuple]()($tup) }
        Expr(itr)
      case _ => report.throwError(s"Could not summon Type for type: ${tupleExpr2.show}")
    end match

  end readCsvFromCurrentDir

  def readCsvAbolsutePath(pathExpr: Expr[String])(using Quotes) =
    import quotes.reflect.*

    val path = pathExpr.valueOrAbort

    val source = Source.fromFile(path)
    val headerLine =
      try source.getLines().next()
      finally source.close()

    val headers = headerLine.split(",").toList
    val tupleExpr2 = Expr.ofTupleFromSeq(headers.map(Expr(_)))
    tupleExpr2 match
      case '{ $tup: t } =>


        val itr = new CsvIterator[t](path)
        // println("tup")
        // println(tup)
        // '{ NamedTuple.build[t & Tuple]()($tup) }
        Expr(itr)
      case _ => report.throwError(s"Could not summon Type for type: ${tupleExpr2.show}")
    end match
  end readCsvAbolsutePath

  private def readCsvResource(pathExpr: Expr[String])(using Quotes) =
    import quotes.reflect.*

    val path = pathExpr.valueOrAbort
    val resourcePath = this.getClass.getClassLoader.getResource(path)
    if (resourcePath == null) {
      report.throwError(s"Resource not found: $path")
    }
    val source = Source.fromResource(path)
    val headerLine =
      try source.getLines().next()
      finally source.close()

    val headers = headerLine.split(",").toList
    val tupleExpr2 = Expr.ofTupleFromSeq(headers.map(Expr(_)))
    tupleExpr2 match
      case '{ $tup: t } =>


        val itr = new CsvIterator[t](resourcePath.getPath.toString())
        // println("tup")
        // println(tup)
        // '{ NamedTuple.build[t & Tuple]()($tup) }
        Expr(itr)
      case _ => report.throwError(s"Could not summon Type for type: ${tupleExpr2.show}")
    end match

end CSV
