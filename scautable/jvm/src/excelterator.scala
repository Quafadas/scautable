package io.github.quafadas.scautable

import scala.io.Source
import scala.util.Try
import scala.util.chaining.*
import scala.util.matching.Regex
import scala.NamedTuple.*
import scala.compiletime.*
import CSV.*
import ConsoleFormat.*
import org.apache.poi.ss.usermodel.{ DataFormatter, WorkbookFactory, Row }
import java.io.File
import scala.collection.JavaConverters.*
import scala.quoted.*


object Excel:


  given IteratorToExpr2[K](using ToExpr[String], Type[K]): ToExpr[ExcelIterator[K]] with
    def apply(opt: ExcelIterator[K])(using Quotes): Expr[ExcelIterator[K]] =
      val str = Expr(opt.getFilePath)
      val sheet = Expr(opt.getSheet)
      '{
        new ExcelIterator[K]($str, $sheet)
      }
    end apply
  end IteratorToExpr2

  transparent inline def absolutePath[K](filePath: String, sheetName: String)= ${ readExcelAbolsutePath('filePath, 'sheetName) }

  def readExcelAbolsutePath(pathExpr: Expr[String], sheetName: Expr[String])(using Quotes) =
    import quotes.reflect.*

    val fPath = pathExpr.valueOrAbort
    val headers = ExcelIterator(pathExpr.valueOrAbort, sheetName.valueOrAbort).headers
    val tupleExpr2 = Expr.ofTupleFromSeq(headers.map(Expr(_)))
    tupleExpr2 match
      case '{ $tup: t } =>


        val itr = new ExcelIterator[t](fPath, sheetName.valueOrAbort)
        // println("tup")
        // println(tup)
        // '{ NamedTuple.build[t & Tuple]()($tup) }
        Expr(itr)
      case _ => report.throwError(s"Could not summon Type for type: ${tupleExpr2.show}")
    end match


class ExcelIterator[K](filePath: String, sheetName: String) extends Iterator[NamedTuple[K & Tuple, StringyTuple[K & Tuple] ]]:
  type COLUMNS = K

  def getFilePath: String = filePath
  def getSheet : String = sheetName
  lazy val sheetIterator = {
    val workbook = WorkbookFactory.create(new File(filePath))
    val sheet = workbook.getSheet(sheetName)
    sheet.iterator().asScala
  }
  val headers = sheetIterator.next().cellIterator().asScala.toList.map(_.toString)
  lazy val headersTuple =
    listToTuple(headers)

  override def next(): NamedTuple[K & Tuple, StringyTuple[K & Tuple]] =
    if !hasNext then throw new NoSuchElementException("No more rows")
    val row = sheetIterator.next()
    val cells = row.cellIterator().asScala.toList.map(_.toString)
    val tuple = listToTuple(cells)
    NamedTuple.build[K & Tuple]()(tuple).asInstanceOf[StringyTuple[K & Tuple]]


  override def hasNext: Boolean = sheetIterator.hasNext

end ExcelIterator