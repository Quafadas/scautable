package io.github.quafadas.scautable
import java.io.File

import scala.NamedTuple.*
import scala.collection.JavaConverters.*
import scala.quoted.*

import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.util.CellRangeAddress

import io.github.quafadas.scautable.ColumnTyped.*
import io.github.quafadas.table.TypeInferrer
/** */
object Excel:

  class BadTableException(message: String) extends Exception(message)

//   given IteratorToExpr2[K](using ToExpr[String], Type[K]): ToExpr[ExcelIterator[K]] with
//     def apply(opt: ExcelIterator[K])(using Quotes): Expr[ExcelIterator[K]] =
//       val str = Expr(opt.getFilePath)
//       val sheet = Expr(opt.getSheet)
//       val colRange = Expr(opt.getColRange)
//       '{
//         new ExcelIterator[K]($str, $sheet, $colRange)
//       }
//     end apply
//   end IteratorToExpr2

//   transparent inline def absolutePath[K](filePath: String, sheetName: String, range: String = "") = ${ readExcelAbolsutePath('filePath, 'sheetName, 'range) }
  transparent inline def resource[K](filePath: String, sheetName: String, range: String = "", inferrer: TypeInferrer) = ${ readExcelResource('filePath, 'sheetName, 'range, 'inferrer) }

  def readExcelResource(pathExpr: Expr[String], sheetName: Expr[String], colRangeExpr: Expr[String], dataType: Expr[TypeInferrer])(using Quotes) =
    import quotes.reflect.*

    val path = pathExpr.valueOrAbort
    val resourcePath = this.getClass.getClassLoader.getResource(path)
    if resourcePath == null then report.throwError(s"Resource not found: $path")
    end if
    val validatedPath = resourcePath.toURI.getPath
    val colRange = colRangeExpr.value
    val iterator = ExcelIterator(validatedPath, sheetName.valueOrAbort, colRange)
    val tupleExpr2 = Expr.ofTupleFromSeq(iterator.headers.map(Expr(_)))
    tupleExpr2 match
      case '{ $tup: t } =>
        '{
            iterator
        }
        // val iterator = 
        // Expr(iterator.asInstanceOf[ExcelIterator[t]])
      case _ => report.throwError(s"Could not summon Type for type: ${tupleExpr2.show}")
    end match
  end readExcelResource

//   def readExcelAbolsutePath(pathExpr: Expr[String], sheetName: Expr[String], colRangeExpr: Expr[String], dataType: Expr[TypeInferrer])(using Quotes) =
//     import quotes.reflect.*

//     val fPath = pathExpr.valueOrAbort
//     val colRange = colRangeExpr.value
//     val iterator = ExcelIterator(fPath, sheetName.valueOrAbort, colRange)
//     val tupleExpr2 = Expr.ofTupleFromSeq(iterator.headers.map(Expr(_)))

//     tupleExpr2 match
//       case '{ $tup: t } =>
//         // val itr = new ExcelIterator[t](fPath, sheetName.valueOrAbort, colRange)
//         Expr(iterator.asInstanceOf[ExcelIterator[t]])
//       case _ => report.throwError(s"Could not summon Type for type: ${tupleExpr2.show}")
//     end match
//   end readExcelAbolsutePath
end Excel
