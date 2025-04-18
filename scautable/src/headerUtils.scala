package io.github.quafadas.scautable

import scala.util.matching.Regex
import io.github.quafadas.scautable.HeaderUtils._

/**
 * Utilities for managing and normalizing CSV headers.
 * 
 * This module provides functionality for:
 * - Normalizing headers to valid Scala identifiers
 * - Validating headers for common problems
 * - Inferring column names based on content
 * - Suggesting improvements for poor header names
 * 
 * Example usage:
 * {{{
 *   // Normalize headers
 *   val normalized = HeaderUtils.normalizeHeaders(Seq("First Name", "Last Name"))
 *   // Result: Seq("First_Name", "Last_Name")
 *   
 *   // Validate headers
 *   val result = HeaderUtils.validateHeaders(Seq("First Name", "", "First Name"))
 *   if (!result.isValid) {
 *     println(s"Problems found: ${result.problems.map(_.message).mkString(", ")}")
 *   }
 *   
 *   // Get header suggestions
 *   val suggestions = HeaderUtils.suggestImprovedHeaders(
 *     Seq("fn", "ln", "ID"),
 *     Seq(Seq("John", "Doe", "12345"))
 *   )
 * }}}
 */
object HeaderUtils {
  /** Regex for valid Scala identifiers */
  private val ValidScalaIdent = "^[a-zA-Z_][a-zA-Z0-9_]*$".r

  /** Set of reserved Scala keywords */
  private val ReservedKeywords = Set(
    "abstract", "case", "catch", "class", "def", "do", "else", "extends",
    "false", "final", "finally", "for", "forSome", "if", "implicit",
    "import", "lazy", "match", "new", "null", "object", "override",
    "package", "private", "protected", "return", "sealed", "super",
    "this", "throw", "trait", "try", "true", "type", "val", "var",
    "while", "with", "yield"
  )

  /**
   * Result of header validation containing:
   * - Original headers
   * - Validation status
   * - List of problems found
   * - Normalized headers
   */
  case class HeaderValidationResult(
    headers: Seq[String],
    isValid: Boolean,
    problems: Seq[HeaderProblem],
    normalizedHeaders: Seq[String]
  )

  /**
   * Base trait for header problems.
   * Each problem type provides:
   * - A descriptive message
   * - Severity level (Error/Warning/Info)
   * - List of affected headers with their positions
   */
  sealed trait HeaderProblem {
    def message: String
    def severity: ProblemSeverity
    def affectedHeaders: Seq[(Int, String)] // (position, header)
  }

  /** Severity levels for header problems */
  enum ProblemSeverity {
    case Error, Warning, Info
  }

  /**
   * Problem with empty headers.
   * @param positions Indices of empty headers
   */
  case class EmptyHeader(positions: Seq[Int]) extends HeaderProblem {
    def message = s"Empty headers found at positions: ${positions.map(_ + 1).mkString(", ")}"
    def severity = ProblemSeverity.Error
    def affectedHeaders = positions.map(pos => (pos, ""))
  }

  /**
   * Problem with duplicate headers.
   * @param headers List of duplicate header names
   */
  case class DuplicateHeader(headers: Seq[String]) extends HeaderProblem {
    def message = s"Duplicate headers found: ${headers.mkString(", ")}"
    def severity = ProblemSeverity.Warning
    def affectedHeaders = {
      // Find all positions for each duplicate header
      val headerToPositions = headers.flatMap { h =>
        val allPositions = headers.zipWithIndex.filter(_._1 == h).map(_._2)
        allPositions.map(pos => (pos, h))
      }
      headerToPositions
    }
  }

  /**
   * Problem with invalid characters in headers.
   * @param headers List of (position, header) pairs with invalid characters
   */
  case class InvalidCharactersHeader(headers: Seq[(Int, String)]) extends HeaderProblem {
    def message = s"Headers with invalid characters found: ${headers.map(_._2).mkString(", ")}"
    def severity = ProblemSeverity.Warning
    def affectedHeaders = headers
  }

  /**
   * Validates headers and returns a result containing any problems found.
   * 
   * Checks for:
   * - Empty headers
   * - Duplicate headers
   * - Invalid characters
   * - Reserved keywords
   * 
   * @param headers Headers to validate
   * @return Validation result with problems and normalized headers
   */
  def validateHeaders(headers: Seq[String]): HeaderValidationResult = {
    val problems = scala.collection.mutable.ArrayBuffer.empty[HeaderProblem]
    
    // Check for empty headers
    val emptyHeaderPositions = headers.zipWithIndex.filter(_._1.trim.isEmpty).map(_._2)
    if (emptyHeaderPositions.nonEmpty) {
      problems += EmptyHeader(emptyHeaderPositions)
    }
    
    // Check for duplicates
    val duplicateHeaders = headers.groupBy(identity).filter(_._2.size > 1).keys.toSeq
    if (duplicateHeaders.nonEmpty) {
      problems += DuplicateHeader(duplicateHeaders)
    }
    
    // Check for invalid characters for Scala identifiers
    val invalidHeaders = headers.zipWithIndex.filter { case (h, _) => 
      h.trim.nonEmpty && !ValidScalaIdent.matches(h)
    }.map { case (h, idx) => (idx, h) }
    
    if (invalidHeaders.nonEmpty) {
      problems += InvalidCharactersHeader(invalidHeaders)
    }
    
    // Check for reserved keywords
    val reservedKeywordHeaders = headers.zipWithIndex.filter { case (h, _) =>
      ReservedKeywords.contains(h)
    }.map { case (h, idx) => (idx, h) }
    
    if (reservedKeywordHeaders.nonEmpty) {
      problems += new HeaderProblem {
        def message = s"Headers using reserved Scala keywords: ${reservedKeywordHeaders.map(_._2).mkString(", ")}"
        def severity = ProblemSeverity.Error
        def affectedHeaders = reservedKeywordHeaders
      }
    }
    
    // Create normalized headers
    val normalizedHeaders = normalizeHeaders(headers)
    
    HeaderValidationResult(
      headers = headers,
      isValid = problems.forall(_.severity != ProblemSeverity.Error),
      problems = problems.toSeq,
      normalizedHeaders = normalizedHeaders
    )
  }
  
  /**
   * Normalizes CSV headers to be valid Scala identifiers.
   * 
   * Handles:
   * - Spaces and special characters
   * - Numeric prefixes
   * - Duplicate headers
   * - Reserved keywords
   * - Empty headers
   * 
   * @param headers Raw headers from CSV file
   * @return Normalized headers suitable for Scala identifiers
   */
  def normalizeHeaders(headers: Seq[String]): Seq[String] = {
    // Convert headers to valid Scala identifiers
    val sanitized = headers.map { header =>
      Option(header).filter(_.trim.nonEmpty).getOrElse("column")
        .trim
        .replaceAll("[^a-zA-Z0-9_]", "_")  // Replace invalid chars with underscore
        .replaceAll("^([0-9])", "_$1")     // Ensure doesn't start with a number
        .replaceAll("_+", "_")             // Replace multiple underscores with single one
        .replaceAll("_$", "")              // Remove trailing underscore
    }
    
    // Handle reserved keywords
    val safeFromKeywords = sanitized.map { header =>
      if (ReservedKeywords.contains(header)) s"${header}_" else header
    }
    
    // Ensure uniqueness
    val result = scala.collection.mutable.ArrayBuffer.empty[String]
    val seen = scala.collection.mutable.Set.empty[String]
    
    for (header <- safeFromKeywords) {
      if (!seen.contains(header)) {
        seen += header
        result += header
      } else {
        // Find a unique name by adding numbers
        var i = 1
        var uniqueHeader = s"${header}_$i"
        while (seen.contains(uniqueHeader)) {
          i += 1
          uniqueHeader = s"${header}_$i"
        }
        seen += uniqueHeader
        result += uniqueHeader
      }
    }
    
    result.toSeq
  }
  
  /**
   * Suggests better column names based on content analysis.
   * 
   * Provides suggestions for:
   * - Empty or very short headers
   * - Headers containing spaces
   * - ALL_CAPS headers
   * 
   * @param headers Original column headers
   * @param sampleData Sample of data rows to analyze
   * @return Map of original headers to (suggestion, explanation) pairs
   */
  def suggestImprovedHeaders(
    headers: Seq[String],
    sampleData: Seq[Seq[String]]
  ): Map[String, (String, String)] = {
    headers.zipWithIndex.collect {
      case (header, idx) if header.trim.isEmpty || header.length <= 2 =>
        // For empty or very short headers, analyze column data
        val columnValues = sampleData.flatMap(row => 
          if (idx < row.length) Some(row(idx)) else None
        )
        
        val suggestion = inferColumnNameFromContent(columnValues)
        (header, (suggestion, "Based on column content analysis"))
      
      case (header, _) if header.contains(" ") =>
        // Convert space-separated to camelCase
        val camelCase = header.split("\\s+").map(_.capitalize).mkString
        val camelCaseHeader = camelCase.head.toLower + camelCase.tail
        (header, (camelCaseHeader, "Converted to camelCase"))
        
      case (header, _) if header.toUpperCase == header && header.length > 3 =>
        // Convert ALL_CAPS to camelCase
        val normalized = header.toLowerCase.split("_").map(_.capitalize).mkString
        val camelCaseHeader = normalized.head.toLower + normalized.tail
        (header, (camelCaseHeader, "Converted from ALL_CAPS to camelCase"))
    }.toMap
  }
  
  /**
   * Infers a meaningful column name based on content analysis.
   * 
   * Recognizes patterns for:
   * - Numbers (amount, value, category)
   * - Dates
   * - Email addresses
   * - URLs
   * - Boolean values
   * - Short codes
   * - Names
   * 
   * @param values Sample values from the column
   * @return Inferred column name
   */
  def inferColumnNameFromContent(values: Seq[String]): String = {
    val nonEmpty = values.filterNot(_.trim.isEmpty)
    if (nonEmpty.isEmpty) return "unknown"
    
    // Check for common patterns
    
    // Numbers only
    if (nonEmpty.forall(v => v.matches("-?\\d+(\\.\\d+)?"))) {
      if (nonEmpty.exists(_.contains("."))) return "amount"
      
      // Check for small integers that might be flags/categories
      val allInts = nonEmpty.map(_.toInt)
      if (allInts.forall(_ >= 0) && allInts.forall(_ < 10)) return "category"
      
      return "value"
    }
    
    // Dates
    if (nonEmpty.forall(v => 
        v.matches("\\d{4}-\\d{2}-\\d{2}") || 
        v.matches("\\d{2}/\\d{2}/\\d{4}") ||
        v.matches("\\d{1,2}/\\d{1,2}/\\d{2,4}")
      )) {
      return "date"
    }
    
    // Email addresses
    if (nonEmpty.forall(_.matches(".*@.*\\..+"))) {
      return "email"
    }
    
    // URLs
    if (nonEmpty.forall(v => v.startsWith("http") || v.startsWith("www."))) {
      return "url"
    }
    
    // Boolean-like
    if (nonEmpty.forall(v => 
      Seq("true", "false", "yes", "no", "y", "n", "0", "1").contains(v.toLowerCase))) {
      return "flag"
    }
    
    // If mostly short strings (1-2 chars), likely a code
    if (nonEmpty.forall(_.length <= 2)) {
      return "code"
    }
    
    // Check for common name patterns
    if (nonEmpty.forall(_.split("\\s+").length >= 2)) {
      if (nonEmpty.exists(_.toLowerCase.contains("name"))) return "fullName"
      return "name"
    }
    
    // Default
    "field"
  }
  
  /**
   * Creates a mapping table between original and normalized headers.
   * 
   * @param original Original headers
   * @param normalized Normalized headers
   * @return Markdown-formatted table showing the mapping
   */
  def createHeaderMappingTable(original: Seq[String], normalized: Seq[String]): String = {
    val rows = original.zip(normalized).zipWithIndex.map { case ((orig, norm), idx) =>
      s"| ${idx + 1} | $orig | $norm |"
    }
    
    s"""| # | Original Header | Normalized Header |
        |---|---|-----------------|-------------------|
        |${rows.mkString("\n")}
        |""".stripMargin
  }
}