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


@experimental
object CSV:
  type Concat[X <: String, Y <: Tuple] = X *: Y

  type IsColumn[StrConst <: String, T] = T match
    case EmptyTuple => false
    case (head *: tail) => IsMatch[StrConst, head] match
      case true => true
      case false => IsColumn[StrConst, tail]
    case _ => false

  type IsMatch[A <: String, B <: String] = B match
    case A => true
    case _ => false

  extension [K <: Tuple, V <: Tuple](itr: Iterator[NamedTuple[K, V]])
    inline def addColumn[S <: String, A](fct: (tup: NamedTuple.NamedTuple[K, V]) => A) =
      itr.map{
        (tup: NamedTuple[K, V]) =>
          (fct(tup) *: tup.toTuple).withNames[Concat[S, K]]
      }

    inline def mapColumn[S <: String, A, HEADERS <: Tuple](fct: String => A)(using ev: IsColumn[S, HEADERS] =:= true, s: ValueOf[S]) =
      val headers = constValueTuple[HEADERS].toList.map(_.toString())
      val idx = headers.indexOf(s.value)
      if(idx == -1) ???

      // def reduce(t : Tuple): Tuple = t.toList.zipWithIndex match
      //   case EmptyTuple => EmptyTuple
      //   case h *: t => h match
      //     case _: String => fct(t.productElement(idx).asInstanceOf[String]) *: reduce(t)
      //     case _ => h *: reduce(t)


      // type Typ =
      throw new Exception("Not implemented")
      itr.map{
        tup =>
          tup.splitAt(idx) match
            case (headTup, tailTup) =>
              val mapped = fct(tup(idx).asInstanceOf[String])

              val interm = headTup.toTuple.init :* mapped :* tailTup.toTuple
              interm.withNames[K]
      }




  end extension


  extension [K <: Tuple](csvItr: CsvIterator[K])
    def mapRows[A](fct: (tup: NamedTuple.NamedTuple[K, K]) => A) =
      val itr: Iterator[NamedTuple[K & Tuple, K & Tuple]] =
        csvItr.copy()
      itr.drop(1).map(fct)

    // def addColumn[S <: String, A](fct: (tup: NamedTuple.NamedTuple[K, K]) => A) =

    //   val itr: Iterator[NamedTuple[K & Tuple, K & Tuple]] = csvItr.copy()
    //   val csvItr2 = csvItr.copy().map((x: NamedTuple[K & Tuple, K & Tuple]) => (fct(x) *: x.toTuple ).withNames[Concat[S, K & Tuple] & Tuple])
    //   csvItr2

  end extension


  extension [K <: Tuple, V <: Tuple](nt: Seq[NamedTuple[K, V]])
    inline def consolePrint(headers: List[String], fansi: Boolean = true) =
      // val headers = nt.names
      val values = nt.map(_.toTuple)
      scautable.consoleFormat(values, fansi, headers)

    end consolePrint
  end extension

  case class CsvIterator[K](filePath: String) extends Iterator[NamedTuple[K & Tuple, K & Tuple]]:

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

    inline def mapColumn[S <: String, A](fct: String => A)(using ev: IsColumn[S, K] =:= true, s: ValueOf[S]):Iterator[NamedTuple[K & Tuple, Tuple]] = {

      val idx = headerIndex(s.value)
      val size = headers.size
      val parent = this
      ???
      // attempt2
      // new CsvIterator[K & Tuple](filePath) {
      //   override def next(): NamedTuple[K & Tuple, K & Tuple] = {
      //     val tup = parent.next()
      //     val (headTup, tailTup) =  tup.splitAt(idx)
      //     val mapped = fct(tup(idx).asInstanceOf[String])
      //     val newTup = headTup.init *: tailTup.tail
      //     newTup
      //   }
      // }

      //. attempt 1
      //   row =>
      //     val tup = row.toTuple

      //     val mapped = fct(tup(idx).asInstanceOf[String])

      //       val (headTup, tailTup) =  tup.splitAt(idx)
      //       val newTup = headTup.init *: mapped *: tailTup.tail
      //       newTup.withNames[K & Tuple]
      // ).asInstanceOf[Iterator[NamedTuple[K & Tuple, K & Tuple]]]

    }

    inline def column[S <: String, A](fct: String => A = identity)(using ev: IsColumn[S, K] =:= true, s: ValueOf[S]): Iterator[A] = {
      column[S](using ev, s).map(fct)
    }

    inline def column[S <: String](using ev: IsColumn[S, K] =:= true, s: ValueOf[S]): Iterator[String]= {
      val idx = headerIndex(s.value)
      val itr: Iterator[NamedTuple[K & Tuple, K & Tuple]] =
        this.copy()
      itr.drop(1).map(x => x.toTuple.productElement(idx).asInstanceOf[String])
    }

    def getFilePath: String = filePath
    lazy private val source = Source.fromFile(filePath)
    lazy private val lineIterator = source.getLines()
    lazy val headers = Source.fromFile(filePath).getLines().next().split(",").toList // done on instatiation
    lazy val headersTuple =
      listToTuple(headers)

    inline def headerIndex(s: String) = headers.zipWithIndex.find(_._1 == s).get._2
    inline override def hasNext: Boolean =
      val hasMore = lineIterator.hasNext
      if !hasMore then source.close() // Close the file when done
      hasMore
    end hasNext

    private def listToTuple(list: List[String]): Tuple = list match
      case Nil    => EmptyTuple
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

    tupleExpr2 match
      case '{ $tup: t } =>
        val itr = new CsvIterator[t](path)
        // '{ NamedTuple.build[t & Tuple]()($tup) }
        Expr(itr)
      case _ => report.throwError(s"Could not summon Type for type: ${tupleExpr2.show}")
    end match
  end readCsvAbolsutePath
end CSV
