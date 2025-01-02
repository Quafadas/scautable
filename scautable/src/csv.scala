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

  type ReplaceOneName[T <: Tuple, Head <: Tuple,  StrConst <: String, A <: String] <: Tuple = T match
    case EmptyTuple => EmptyTuple
    case x *: xs => IsMatch[StrConst, x] match
      case true => Head *: A *: xs
      case false =>
        Head match
          case EmptyTuple => ReplaceOneName[xs, x, StrConst, A]
          case _ => ReplaceOneName[xs, x *: Head, StrConst, A]

  // type ReplaceOneType[T <: Tuple, Head <: Tuple, StrConst <: String, A] <: Tuple = T match
  //   case EmptyTuple => Head
  //   case (name *: typ *: EmptyTuple) *: tail =>
  //     IsMatch[StrConst, name] match
  //       case true => Head *: (name *: A) *: tail
  //       case false =>
  //         ReplaceOneType[
  //           tail,
  //           Head *: (name *: typ),
  //           StrConst,
  //           A
  //         ]

  // type InputTuple = ("col1" *: Int *: EmptyTuple) *: ("col2" *: String) *: EmptyTuple

  // type Result = ReplaceOneType[InputTuple, EmptyTuple, "col1", Boolean]

  type ReplaceOneTypeAtName[N <: Tuple, StrConst <: String, T <: Tuple, Head <: Tuple, A] <: Tuple = (T, N) match
    case (EmptyTuple, _) => EmptyTuple
    case (_, EmptyTuple) => EmptyTuple
    case (nameHead *: nameTail, typeHead *: typeTail) =>
      IsMatch[nameHead, StrConst] match
          case true => A *: typeTail
          case false =>
            typeHead *: ReplaceOneTypeAtName[nameTail, StrConst, typeTail, Head, A]

  // match
  //   case EmptyTuple => T *: A *: StrConst *:  EmptyTuple
  //   case x *: xs => T *: A *: xs *: EmptyTuple
  //   case _ => EmptyTuple
    // DropAfterName[T, StrConst] *: A *: Tail[T, StrConst] match
    //   case EmptyTuple => EmptyTuple
    //   case x *: xs => x *: xs

  type DropAfterName[T , StrConst <: String] = T match
    case EmptyTuple => EmptyTuple
    case (head *: tail) => IsMatch[StrConst, head] match
      case true => EmptyTuple
      case false => head *: DropAfterName[tail, StrConst]

  type DropOneName[T, StrConst <: String]= T match
    case EmptyTuple => EmptyTuple
      case (head *: tail) => IsMatch[StrConst, head] match
        case true => DropOneName[tail, StrConst]
        case false => head *: DropOneName[tail , StrConst]

  type IsMatch[A <: String, B <: String] = B match
    case A => true
    case _ => false



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

  // extension [K <: Tuple, V <: Tuple](itr: Iterator[NamedTuple[K, V]])
  //   inline def addColumn[S <: String, A](fct: (tup: NamedTuple.NamedTuple[K, V]) => A) =
  //     itr.map{
  //       (tup: NamedTuple[K, V]) =>
  //         (fct(tup) *: tup.toTuple).withNames[Concat[S, K]]
  //     }

  // end extension
  extension [K, V, K1 <: Tuple & K, V1 <: Tuple & K](itr: Iterator[NamedTuple[K1, V1]])

    inline def renameColumn[From <: String, To <: String](using ev: IsColumn[From, K1] =:= true, FROM: ValueOf[From], TO: ValueOf[To])= {
        val headers = constValueTuple[K1].toList.map(_.toString())
        val idx = headers.indexOf(FROM.value)
        if(idx == -1) ???
        itr.map{_.withNames[ReplaceOneName[K1, EmptyTuple, From, To]]}
    }

    inline def addColumn[S <: String, A](fct: (tup: NamedTuple.NamedTuple[K1, V1]) => A) =
      itr.map{
        (tup: NamedTuple[K1, V1]) =>
          (fct(tup) *: tup.toTuple).withNames[Concat[S, K1]]
      }



    inline def mapColumn[S <: String, B, A](fct: B => A)(using ev: IsColumn[S, K1] =:= true, s: ValueOf[S])= {
      import scala.compiletime.ops.string.*
      val headers = constValueTuple[K1].toList.map(_.toString())
      type temp = "TEMP_COLUMN"
      val tmp = summonInline[ValueOf[temp]]
      /**
        * Aaahhhh... apparently, TupleXXL is in reverse order!
        */
      val headers2 = if headers.size > 22 then headers.reverse else headers
      val idx = headers.indexOf(s.value)
      if(idx == -1) ???
      itr.map{
        (x: NamedTuple[K1, V1]) =>
          val tup = x.toTuple
          val mapped = fct(tup(idx).asInstanceOf[B])
          val (head, tail) = x.toTuple.splitAt(idx)
          (head ++ mapped *: tail.tail).withNames[K1].asInstanceOf[NamedTuple[K1,ReplaceOneTypeAtName[K1,  S, V1, EmptyTuple, A]]]
      }
    }

    inline def column[S <: String, A](fct: String => A = identity)(using ev: IsColumn[S, K1] =:= true, s: ValueOf[S]): Iterator[A] = {
      column[S](using ev, s).map(fct)
    }

    inline def column[S <: String](using ev: IsColumn[S, K1] =:= true, s: ValueOf[S]): Iterator[String]= {
      val headers = constValueTuple[K1].toList.map(_.toString())
      /**
        * Aaahhhh... apparently, TupleXXL is in reverse order!
        */
      val headers2 = if headers.size > 22 then headers.reverse else headers

      val idx = headers2.indexOf(s.value)
      itr.map(x =>
        x.toTuple(idx).asInstanceOf[String]
      )
    }



    inline def dropColumn[S <: String](using ev: IsColumn[S, K1] =:= true, s: ValueOf[S]): Iterator[DropOneName[V1, S]] =
      val headers = constValueTuple[K1].toList.map(_.toString())
      /**
        * Aaahhhh... apparently, TupleXXL is in reverse order!
        */
      val headers2 = if headers.size > 22 then headers.reverse else headers
      val idx = headers2.indexOf(s.value)

      itr.map{
        (x: NamedTuple[K1, V1]) =>

        // val hmmm = x.toTuple.productIterator
        val (head, tail) = x.toTuple.splitAt(idx)
        head.init match
          case x: EmptyTuple => tail.withNames[DropOneName[K, S] & Tuple].asInstanceOf[DropOneName[V1, S] & Tuple]
          case _ => (head.init *: tail).withNames[DropOneName[K, S] & Tuple].asInstanceOf[DropOneName[V1, S] & Tuple]
        // head.init ++ tail
      }
  end extension


  extension [K <: Tuple](csvItr: CsvIterator[K])
    def mapRows[A](fct: (tup: NamedTuple.NamedTuple[K, K]) => A) =
      csvItr.drop(1).map(fct)

    // def addColumn[S <: String, A](fct: (tup: NamedTuple.NamedTuple[K, K]) => A) =

    //   val itr: Iterator[NamedTuple[K & Tuple, K & Tuple]] = csvItr.copy()
    //   val csvItr2 = csvItr.copy().map((x: NamedTuple[K & Tuple, K & Tuple]) => (fct(x) *: x.toTuple ).withNames[Concat[S, K & Tuple] & Tuple])
    //   csvItr2

  end extension


  extension [K <: Tuple, V <: Tuple](nt: Seq[NamedTuple[K, V]])
    inline def consolePrint(headers: Option[List[String]] = None, fansi: Boolean = true) =
      val foundHeaders = constValueTuple[K].toList.map(_.toString())
      val values = nt.map(_.toTuple)
      scautable.consoleFormat(values, fansi, headers.getOrElse(foundHeaders))

    end consolePrint
  end extension

  class CsvIterator[K](filePath: String) extends Iterator[NamedTuple[K & Tuple, K & Tuple]]:

    type COLUMNS = K

    // type ZipWithIndex[T <: Tuple] <: Tuple = T match
    //   case EmptyTuple => EmptyTuple
    //   case x *: xs => (x, ValueOf[Size[xs]]) *: ZipWithIndex[xs]

    // type IndexOf[A, T <: Tuple, N <: 0] <: Int = T match
    //   case EmptyTuple => 0
    //   case head *: tail => head match
    //     case A => N
    //     case _ => IndexOf[tail, A, N+1]


    // type Size[T] <: Int = T match
    //   case EmptyTuple => 0
    //   case x *: xs => 1 + Size[xs]

    // type AreAllColumns[Strs <: Tuple, T] = Strs match
    //   case EmptyTuple => true
    //   case (head *: tail) => IsColumn[head, T] match
    //     case true => AreAllColumns[tail, T]
    //     case false => false
    //   case _ => false



    // TODO:  I wish I could get this to work.

    // inline def mapColumn[S <: String, A](fct: String => A)(using ev: IsColumn[S, K] =:= true, s: ValueOf[S]):Iterator[NamedTuple[K & Tuple, Tuple]] = {

    //   val idx = headerIndex(s.value)
    //   val size = headers.size
    //   val parent = this
    //   ???
    //   // attempt2
    //   // new CsvIterator[K & Tuple](filePath) {
    //   //   override def next(): NamedTuple[K & Tuple, K & Tuple] = {
    //   //     val tup = parent.next()
    //   //     val (headTup, tailTup) =  tup.splitAt(idx)
    //   //     val mapped = fct(tup(idx).asInstanceOf[String])
    //   //     val newTup = headTup.init *: tailTup.tail
    //   //     newTup
    //   //   }
    //   // }

    //   //. attempt 1
    //   //   row =>
    //   //     val tup = row.toTuple

    //   //     val mapped = fct(tup(idx).asInstanceOf[String])

    //   //       val (headTup, tailTup) =  tup.splitAt(idx)
    //   //       val newTup = headTup.init *: mapped *: tailTup.tail
    //   //       newTup.withNames[K & Tuple]
    //   // ).asInstanceOf[Iterator[NamedTuple[K & Tuple, K & Tuple]]]

    // }

    // inline def column[S <: String, A](fct: String => A = identity)(using ev: IsColumn[S, K] =:= true, s: ValueOf[S]): Iterator[A] = {
    //   column[S](using ev, s).map(fct)
    // }

    // inline def column[S <: String](using ev: IsColumn[S, K] =:= true, s: ValueOf[S]): Iterator[String]= {
    //   val idx = headerIndex(s.value)
    //   val itr: Iterator[NamedTuple[K & Tuple, K & Tuple]] =
    //     this.copy()
    //   itr.map(x => x.toTuple.productElement(idx).asInstanceOf[String])
    // }

    def getFilePath: String = filePath
    lazy private val source = Source.fromFile(filePath)
    lazy private val lineIterator = source.getLines()
    lazy val headers = CSVParser.parseLine((Source.fromFile(filePath).getLines().next()))
    lazy val headersTuple =
      listToTuple(headers)

    // println("headers in CsvIterator")
    // println(headers.mkString(","))
    // println("tuple")
    // println(headersTuple.toString)

    inline def headerIndex(s: String) =
      headers.zipWithIndex.find(_._1 == s).get._2

    /**
      * Here be dragons, in Tuple Land, Tuple XXL is reversed, creating a discontinuity. Small tuples start at 1, big tuples start the other end.
      *
      * @return
      */
    inline def headerIndex[S <: String & Singleton] =
      val headers2 = if headers.size > 22 then headers.reverse else headers
      headers.indexOf(constValue[S].toString)

    inline override def hasNext: Boolean =
      val hasMore = lineIterator.hasNext
      if !hasMore then source.close() // Close the file when done
      hasMore
    end hasNext

    private def listToTuple(list: List[String]): Tuple = list match
      case Nil    => EmptyTuple
      // case h :: t => listToTuple(t) :* h
      case h :: t => h *: listToTuple(t)

    inline override def next(): NamedTuple[K & Tuple, K & Tuple] =
      if !hasNext then throw new NoSuchElementException("No more lines")
      end if
      val str = lineIterator.next()
      val splitted = CSVParser.parseLine(str)
      val tuple = listToTuple(splitted).asInstanceOf[K & Tuple]
      NamedTuple.build[K & Tuple]()(tuple)
    end next
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

  transparent inline def absolutePath[T](inline path: String) = ${ readCsvAbolsutePath('path) }

  // TODO : I can't figure out how to refactor the common code inside the contraints of metaprogamming... 4 laterz.

  def readCsvFromUrl(pathExpr: Expr[String])(using Quotes) =
    import quotes.reflect.*

    report.warning("This method saves the CSV to a local file and opens it. This is a security risk, a performance risk and a lots of things risk. Use at your own risk and no where near something you care about.")
    val source = Source.fromURL(pathExpr.valueOrAbort)
    val tmpPath = os.temp(dir = os.pwd, prefix = "temp_csv_", suffix = ".csv")
    os.write.over(tmpPath, source.toArray.mkString)

    println(tmpPath.toString())

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

  def readCsvFromCurrentDir(pathExpr: Expr[String])(using Quotes) =
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


    // println(s"Read headers in macro : size ${headers.size}")
    // println(headers.mkString(","))
    // println(tupleExpr2.show)

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
end CSV
