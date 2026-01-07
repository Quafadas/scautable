package io.github.quafadas.scautable.json

import scala.io.Source
import com.github.plokhotnyuk.jsoniter_scala.core.*

/** Streaming JSON parser for reading JSON arrays incrementally.
  *
  * While jsoniter-scala doesn't have native streaming for arrays, we can parse incrementally by reading one object at a time. For compile-time type inference, we only read what we
  * need.
  */
private[json] object StreamingJsonParser:

  /** Represents a JSON value that can be used for type inference */
  sealed trait JsonValue
  case object JsonNull extends JsonValue
  case class JsonNumber(value: BigDecimal) extends JsonValue
  case class JsonString(value: String) extends JsonValue
  case class JsonBool(value: Boolean) extends JsonValue
  case class JsonObject(fields: Map[String, JsonValue]) extends JsonValue

  /** Parse JSON array by manually walking through the byte array
    *
    * This is a manual parser that finds JSON object boundaries and parses them individually.
    */
  def parseArrayStream(bytes: Array[Byte], maxObjects: Int = Int.MaxValue): Iterator[JsonObject] =
    new Iterator[JsonObject]:
      private var pos = 0
      private var objectsRead = 0
      private var arrayStarted = false
      private var arrayEnded = false

      // Skip whitespace to array start
      skipWhitespace()
      if pos < bytes.length && bytes(pos) == '[' then
        pos += 1
        arrayStarted = true
      else arrayEnded = true
      end if

      def hasNext: Boolean =
        if arrayEnded || objectsRead >= maxObjects then false
        else
          skipWhitespace()
          pos < bytes.length && bytes(pos) != ']'

      def next(): JsonObject =
        if !hasNext then throw new NoSuchElementException("No more objects")
        end if

        skipWhitespace()

        // Find object boundaries
        val start = pos
        if bytes(pos) != '{' then throw new Exception("Expected '{' at start of object")
        end if

        var braceCount = 0
        var inString = false
        var escape = false

        while pos < bytes.length do
          val ch = bytes(pos).toChar
          if escape then escape = false
          else if ch == '\\' && inString then escape = true
          else if ch == '"' && !escape then inString = !inString
          else if !inString then
            if ch == '{' then braceCount += 1
            else if ch == '}' then
              braceCount -= 1
              if braceCount == 0 then
                pos += 1
                // Extract and parse this object
                val objectBytes = java.util.Arrays.copyOfRange(bytes, start, pos)
                objectsRead += 1

                // Skip comma if present
                skipWhitespace()
                if pos < bytes.length && bytes(pos) == ',' then pos += 1
                end if

                return parseObject(objectBytes)
              end if
          end if
          pos += 1
        end while

        throw new Exception("Unexpected end of JSON array")
      end next

      private def skipWhitespace(): Unit =
        while pos < bytes.length && (bytes(pos) == ' ' || bytes(pos) == '\n' || bytes(pos) == '\r' || bytes(pos) == '\t') do pos += 1

      private def parseObject(bytes: Array[Byte]): JsonObject =
        // Use a simple JSON parser for individual objects
        val json = new String(bytes, "UTF-8")
        val fields = parseJsonObject(json)
        JsonObject(fields)
      end parseObject

      private def parseJsonObject(json: String): Map[String, JsonValue] =
        val trimmed = json.trim
        if !trimmed.startsWith("{") || !trimmed.endsWith("}") then throw new Exception("Invalid JSON object")
        end if

        val content = trimmed.substring(1, trimmed.length - 1).trim
        if content.isEmpty then return Map.empty
        end if

        // Simple parser for flat JSON objects
        val fields = scala.collection.mutable.Map[String, JsonValue]()
        var i = 0

        while i < content.length do
          // Skip whitespace
          while i < content.length && content(i).isWhitespace do i += 1
          end while

          if i >= content.length then i = content.length
          else
            // Read key
            if content(i) != '"' then throw new Exception("Expected quote at start of key")
            end if
            i += 1
            val keyStart = i
            while i < content.length && content(i) != '"' do i += 1
            end while
            val key = content.substring(keyStart, i)
            i += 1

            // Skip whitespace and colon
            while i < content.length && (content(i).isWhitespace || content(i) == ':') do i += 1
            end while

            // Read value
            val (value, newI) = parseValue(content, i)
            fields(key) = value
            i = newI

            // Skip whitespace and comma
            while i < content.length && (content(i).isWhitespace || content(i) == ',') do i += 1
            end while
          end if
        end while

        fields.toMap
      end parseJsonObject

      private def parseValue(json: String, start: Int): (JsonValue, Int) =
        var i = start
        while i < json.length && json(i).isWhitespace do i += 1
        end while

        if i >= json.length then throw new Exception("Unexpected end of JSON")
        end if

        json(i) match
          case 'n' => // null
            if json.substring(i, i + 4) == "null" then (JsonNull, i + 4)
            else throw new Exception("Invalid null value")
          case 't' => // true
            if json.substring(i, i + 4) == "true" then (JsonBool(true), i + 4)
            else throw new Exception("Invalid boolean value")
          case 'f' => // false
            if json.substring(i, i + 5) == "false" then (JsonBool(false), i + 5)
            else throw new Exception("Invalid boolean value")
          case '"' => // string
            i += 1
            val start = i
            while i < json.length && json(i) != '"' do
              if json(i) == '\\' then i += 2
              else i += 1
            end while
            val value = json.substring(start, i)
            i += 1
            (JsonString(value), i)
          case ch if ch.isDigit || ch == '-' => // number
            val start = i
            if ch == '-' then i += 1
            end if
            while i < json.length && (json(i).isDigit || json(i) == '.' || json(i) == 'e' || json(i) == 'E' || json(i) == '+' || json(i) == '-') do i += 1
            end while
            val numStr = json.substring(start, i)
            (JsonNumber(BigDecimal(numStr)), i)
          case _ =>
            throw new Exception(s"Unexpected character: ${json(i)}")
        end match
      end parseValue
    end new
  end parseArrayStream

  /** Parse JSON from a Source (for compile-time use) */
  def parseArrayFromSource(source: Source, maxObjects: Int = Int.MaxValue): Iterator[JsonObject] =
    val bytes = source.mkString.getBytes("UTF-8")
    parseArrayStream(bytes, maxObjects)
  end parseArrayFromSource

end StreamingJsonParser
