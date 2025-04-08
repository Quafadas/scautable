   package io.github.quafadas.scautable

   /** Base trait for all CSV parsing errors */
    trait CsvError {
     def message: String
     def lineNumber: Int
     def rowContent: String
     def severity: ErrorSeverity
   }

   enum ErrorSeverity {
     case Fatal, Warning, Info
   }

   /** Row has incorrect number of fields */
   case class MalformedRowError(
     lineNumber: Int, 
     expectedFields: Int, 
     actualFields: Int, 
     rowContent: String,
     severity: ErrorSeverity = ErrorSeverity.Warning
   ) extends CsvError {
     def message = s"Row at line $lineNumber has incorrect number of fields (expected $expectedFields, got $actualFields)"
   }

   /** Column value cannot be converted to the expected type */
   case class TypeConversionError(
     lineNumber: Int, 
     columnName: String, 
     value: String, 
     targetType: String,
     rowContent: String,
     severity: ErrorSeverity = ErrorSeverity.Warning
   ) extends CsvError {
     def message = s"Cannot convert value '$value' in column '$columnName' to type $targetType at line $lineNumber"
   }

   /** Missing value in a non-nullable column */
   case class MissingValueError(
     lineNumber: Int, 
     columnName: String,
     rowContent: String,
     severity: ErrorSeverity = ErrorSeverity.Warning
   ) extends CsvError {
     def message = s"Missing value in non-nullable column '$columnName' at line $lineNumber"
   }

   /** Error recovery strategies 
    *
    * These strategies complement the compile-time typing system by providing 
    * a preprocessing mechanism for CSV files with structural issues. The workflow is:
    * 
    * 1. Pre-process CSV files with structural issues using recovery strategies
    * 2. Save the fixed CSV to a known location
    * 3. Use compile-time typing with the fixed file
    * 
    * This is particularly valuable for user-uploaded or external CSV files where
    * structure cannot be guaranteed at compile time.
    */
   object RecoveryStrategy {
     trait Strategy {
       def recover[T](error: CsvError, headers: Seq[String]): Option[Seq[String]]
     }

     /** Skip rows with errors */
     object Skip extends Strategy {
       def recover[T](error: CsvError, headers: Seq[String]): Option[Seq[String]] = None
     }

     /** Pad missing fields with nulls */
     object PadWithNulls extends Strategy {
       def recover[T](error: CsvError, headers: Seq[String]): Option[Seq[String]] = 
         error match {
           case MalformedRowError(_, expected, actual, row, _) if actual < expected =>
             // Extract existing fields and pad with nulls
             val fields = CSVParser.parseLine(row)
             val padded = fields ++ Seq.fill(expected - actual)("")
             Some(padded)
           case _ => None
         }
     }

     /** Truncate excess fields */
     object Truncate extends Strategy {
       def recover[T](error: CsvError, headers: Seq[String]): Option[Seq[String]] = 
         error match {
           case MalformedRowError(_, expected, actual, row, _) if actual > expected =>
             // Extract fields and take only the expected number
             val fields = CSVParser.parseLine(row)
             Some(fields.take(expected))
           case _ => None
         }
     }
     
     /** Combine multiple recovery strategies 
      * 
      * This method allows you to create a new strategy that tries each of the
      * provided strategies in sequence until one succeeds.
      * 
      * @param strategies The recovery strategies to combine
      * @return A new strategy that tries each provided strategy in order
      */
     def combine(strategies: Strategy*): Strategy = new Strategy {
       def recover[T](error: CsvError, headers: Seq[String]): Option[Seq[String]] = 
         strategies.foldLeft[Option[Seq[String]]](None) { (result, strategy) =>
           result.orElse(strategy.recover(error, headers))
         }
     }
   }

   /** Utility methods for handling CSV errors */
   object CsvErrors {
     /** Collect all errors from parsing a CSV file */
     def collectErrors[T](csvResult: Iterator[Either[CsvError, T]]): Seq[CsvError] = {
       csvResult.collect { case Left(error) => error }.toSeq
     }
     
     /** Format errors for display */
     def formatErrors(errors: Seq[CsvError]): String = {
       errors.map(err => s"${err.message}\nSeverity: ${err.severity}\nRow content: ${err.rowContent}").mkString("\n\n")
     }
     
     /** Detect patterns in errors to highlight systematic issues */
     def detectErrorPatterns(errors: Seq[CsvError]): Map[String, Int] = {
       errors.groupBy {
         case MalformedRowError(_, _, _, _, _) => "STRUCTURE_ERROR"
         case TypeConversionError(_, column, _, _, _, _) => s"TYPE_ERROR_$column"
         case MissingValueError(_, column, _, _) => s"MISSING_VALUE_$column"
         case _ => "OTHER_ERROR"
       }.view.mapValues(_.size).toMap
     }
     
     /** Generate a summary report of errors */
     def generateErrorReport(errors: Seq[CsvError]): String = {
       val patterns = detectErrorPatterns(errors)
       val totalErrors = errors.size
       val bySeverity = errors.groupBy(_.severity).view.mapValues(_.size).toMap
       
       s"""CSV Error Report
          |=================
          |Total errors: $totalErrors
          |
          |Error Patterns:
          |${patterns.map { case (pattern, count) => f"- $pattern: $count (${"%.1f".format(count * 100.0 / totalErrors)}%%) "}.mkString("\n")}
          |
          |By Severity:
          |${bySeverity.map { case (sev, count) => f"- $sev: $count (${"%.1f".format(count * 100.0 / totalErrors)}%%)"}.mkString("\n")}
          |""".stripMargin
     }
   }