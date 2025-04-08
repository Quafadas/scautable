package io.github.quafadas.scautable

import scala.io.Source
import java.io.{File, PrintWriter}

/**
 * Utility to help preprocess CSV files with structural issues before
 * using them with compile-time typing.
 * 
 * This complements the existing Option-based approach by handling
 * structural issues that would otherwise cause runtime failures.
 */
object CsvPreprocessor {
  /**
   * Preprocesses a CSV file, fixing structural issues, and creates a new fixed file.
   * 
   * @param inputPath Path to the input CSV file
   * @param outputPath Path where the fixed CSV file should be saved
   * @param recoveryStrategy Strategy to use for recovering from errors
   * @return List of errors found (and fixed) during preprocessing
   */
  def preprocess(
    inputPath: String,
    outputPath: String,
    recoveryStrategy: RecoveryStrategy.Strategy = RecoveryStrategy.combine(
      RecoveryStrategy.PadWithNulls,
      RecoveryStrategy.Truncate
    )
  ): Seq[CsvError] = {
    val source = Source.fromFile(inputPath)
    val result = try {
      CSVParser.parseWithRecovery(source.getLines(), recoveryStrategy = recoveryStrategy).toSeq
    } finally {
      source.close()
    }
    
    // Collect errors and fixed rows
    val errors = result.collect { case Left(error) => error }
    
    // Write fixed file
    val writer = new PrintWriter(outputPath)
    try {
      result.collect { case Right(row) => row.mkString(",") }
        .foreach(writer.println)
    } finally {
      writer.close()
    }
    
    errors
  }
  
  /**
   * Generates a detailed error report from preprocessing errors
   */
  def generateErrorReport(errors: Seq[CsvError]): String = 
    CsvErrors.generateErrorReport(errors)
} 