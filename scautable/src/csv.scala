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



@experimental
object CSV:
  type Concat[X <: String, Y <: Tuple] = X *: Y

  type ConcatSingle[X, A] = X *: A *: EmptyTuple

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
    case Int => true
    case Long => true
    case Float => true
    case Double => true
    case _ => false

  type NumericCols[T <: Tuple] <: Tuple = 
    T match
      case EmptyTuple => EmptyTuple
      case (head *: tail) => IsNumeric[head] match
        case true => head *: NumericCols[tail]
        case false => false *: NumericCols[tail]

  type SelectFromTuple[T <: Tuple, Bools <: Tuple] <: Tuple = T match
    case EmptyTuple => EmptyTuple
    case (head *: tail) => Bools match
      case (true *: boolTail) => head *: SelectFromTuple[tail, boolTail]
      case (false *: boolTail) => SelectFromTuple[tail, boolTail]

  type AllAreColumns[T >: Tuple, K <: Tuple] <: Boolean = T match
    case EmptyTuple => true
    case head *: tail => IsColumn[head, K] match
      case true => AllAreColumns[tail, K]
      case false => false


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

    
    def dropColumnsImpl[T <: Tuple](using Quotes): Expr[Any] = {
      import quotes.reflect.*

      // Extract the types of the tuple `T`
      T match 
        case '[t *: ts] => extractTupleTypes(Type.of[T])
        case '[EmptyTuple] => Nil
        case _ => report.throwError("Expected T to be a Tuple")
      
        

      // Generate a chain of `.dropColumn[Ti]` calls
      val dropColumnCalls = tTypes.foldLeft('{ itr }: Expr[Any]) { (acc, tpe) =>
        '{ $acc.dropColumn[tpe] }
      }

      dropColumnCalls
    }

    private def extractTupleTypes[T](using Quotes)(using Type[T]): List[Type[_]] = {
      import quotes.reflect.*
      Type.of[T] match {
        case '[t *: ts] => Type.of[t] :: extractTupleTypes(Type.of[ts])
        case '[EmptyTuple] => Nil
        case _ => report.throwError("Expected a tuple type")
      }
    }
    

    
    inline def dropColumns[T <: Tuple](using ev: AllAreColumns[T, K1] =:= true) = {

      dropColumnsImpl[T, K1]

      // val allCols = constValueTuple[K1].toList.map(_.toString())
      // val selectedCols = constValueTuple[T].toList.map(_.toString())

      // val setdiff = allCols.diff(selectedCols)

      // itr
      //   .dropColumn[T1]
      //   .dropColumn[T2]
    }

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

  class CsvIterator[K](filePath: String) extends Iterator[NamedTuple[K & Tuple, StringyTuple[K & Tuple] ]]:
    type COLUMNS = K

    def getFilePath: String = filePath
    lazy private val source = Source.fromFile(filePath)
    lazy private val lineIterator = source.getLines()
    lazy val headers = CSVParser.parseLine((Source.fromFile(filePath).getLines().next()))
    lazy val headersTuple =
      listToTuple(headers)

    inline def headerIndex(s: String) =
      headers.zipWithIndex.find(_._1 == s).get._2

    /**
      * Here be dragons, in Tuple Land, Tuple XXL is reversed, creating a discontinuity. Small tuples start at 1, big tuples start the other end.
      *
      * Apparently fixed in 3.6.3
      *
      * @return
      */
    inline def headerIndex[S <: String & Singleton] =
      val headers2 = if headers.size > 22 then headers.reverse else headers
      headers.indexOf(constValue[S].toString)

    inline override def hasNext: Boolean =
      val hasMore = lineIterator.hasNext
      if !hasMore then source.close()
      hasMore
    end hasNext

    private def listToTuple[A](list: List[A]): Tuple = list match
      case Nil    => EmptyTuple
      case h :: t => h *: listToTuple(t)

    def numericTypeTest(sample: Option[Int] = None) =
      val sampled = sample match
        case Some(n) =>
          this.take(n)
        case None =>
          this
      val asList = headers.map(_ => ConversionAcc(0, 0, 0))

      sampled.foldLeft((asList, 0L))( (acc: (List[ConversionAcc], Long), elem: NamedTuple[K & Tuple, StringyTuple[K & Tuple]] ) =>

          val list = elem.toList.asInstanceOf[List[String]].zip(acc._1).map{
            case (str, acc) =>

              (
                ConversionAcc(
                  acc.validInts + str.toIntOption.fold(0)(_ => 1),
                  acc.validDoubles + str.toDoubleOption.fold(0)(_ => 1),
                  acc.validLongs + str.toLongOption.fold(0)(_ => 1)
                )
              )
          }
          (list, acc._2 + 1)
        )

    inline def formatTypeTest(sample: Option[Int] = None): String =
      val (asList, n) = numericTypeTest(sample)
      val intReport = (
        "int" *: listToTuple({
          for(acc <- asList ) yield
            (acc.validInts / n.toDouble).formatAsPercentage
          }
        )
      )
      val doubleReported =   "doubles" *: listToTuple({
        for(acc <- asList ) yield
          (acc.validDoubles / n.toDouble).formatAsPercentage
      })
      val longReported = "long" *: listToTuple({
        for(acc <- asList ) yield
          (acc.validLongs / n.toDouble).formatAsPercentage
      })
      val recommendation = "recommendation" *: listToTuple({
        for(acc <- asList ) yield
          recommendConversion(List(acc), n)
      })

      val ntList = Seq(
        intReport,
        doubleReported,
        longReported,
        recommendation
      )

      ConsoleFormat.consoleFormat_(headers = "conversion % to" +: headers, fancy = true, table = ntList )


    inline def showTypeTest(sample: Option[Int] = None): Unit  =
      println(formatTypeTest(sample))

    inline override def next() =
      if !hasNext then throw new NoSuchElementException("No more lines")
      end if
      val str = lineIterator.next()
      val splitted = CSVParser.parseLine(str)
      val tuple = listToTuple(splitted).asInstanceOf[StringyTuple[K & Tuple]]
      NamedTuple.build[K & Tuple]()(tuple)
    end next

    next() // drop the headers
  end CsvIterator

  /**
    * According to chatGPT will parse RFC 4180 compliant CSV.
    */
  object CSVParser {
    def parseLine(line: String, delimiter: Char = ',', quote: Char = '"'): List[String] = {
      var inQuotes = false
      val cellBuffer = new StringBuilder
      val result = scala.collection.mutable.ListBuffer.empty[String]

      for (char <- line) {
        char match {
          case `quote` if !inQuotes =>
            // Start of quoted section
            inQuotes = true

          case `quote` if inQuotes =>
            // End of quoted section (peek ahead to handle escaped quotes)
            if (cellBuffer.nonEmpty && cellBuffer.last == quote) {
              cellBuffer.deleteCharAt(cellBuffer.length - 1) // Handle escaped quote
              cellBuffer.append(char)
            } else {
              inQuotes = false
            }

          case `delimiter` if !inQuotes =>
            // Delimiter outside quotes ends the current cell
            result.append(cellBuffer.toString)
            cellBuffer.clear()

          case _ =>
            // Add character to the current cell
            cellBuffer.append(char)
        }
      }

      // Append the last cell, if any
      result.append(cellBuffer.toString)

      result.toList
    }
  }

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
