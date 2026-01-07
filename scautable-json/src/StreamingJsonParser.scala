package io.github.quafadas.scautable.json

import scala.io.Source
import java.io.{StringReader, InputStream, InputStreamReader}
import jakarta.json.Json
import jakarta.json.stream.{JsonParser => JParser}
import jakarta.json.stream.JsonParser.Event

/** Streaming JSON parser using Java's JSON-P (JSR 374) streaming API.
  *
  * This parser uses the native Java JSON streaming API to read JSON arrays
  * incrementally without loading the entire array into memory.
  */
private[json] object StreamingJsonParser:

  /** Represents a JSON value that can be used for type inference */
  sealed trait JsonValue
  case object JsonNull extends JsonValue
  case class JsonNumber(value: BigDecimal) extends JsonValue
  case class JsonString(value: String) extends JsonValue
  case class JsonBool(value: Boolean) extends JsonValue
  case class JsonObject(fields: Map[String, JsonValue]) extends JsonValue

  /** Parse JSON objects from an InputStream one at a time using Java's streaming API.
    *
    * @param input The input stream to read from
    * @param maxObjects Maximum number of objects to read (for type inference)
    * @return Iterator of JSON objects
    */
  def parseArrayStream(input: InputStream, maxObjects: Int = Int.MaxValue): Iterator[JsonObject] =
    val reader = new InputStreamReader(input, "UTF-8")
    val parser = Json.createParser(reader)
    
    new Iterator[JsonObject]:
      private var objectsRead = 0
      private var arrayStarted = false
      private var arrayEnded = false
      private var nextObj: Option[JsonObject] = None
      
      // Skip to array start
      if !arrayEnded && !arrayStarted then
        while parser.hasNext && !arrayStarted do
          parser.next() match
            case Event.START_ARRAY =>
              arrayStarted = true
            case _ =>
              ()
      end if

      def hasNext: Boolean =
        if arrayEnded || objectsRead >= maxObjects then false
        else if nextObj.isDefined then true
        else
          nextObj = tryReadNext()
          nextObj.isDefined

      def next(): JsonObject =
        if !hasNext then throw new NoSuchElementException("No more objects")
        val result = nextObj.get
        nextObj = None
        objectsRead += 1
        result

      private def tryReadNext(): Option[JsonObject] =
        if arrayEnded || !parser.hasNext then 
          arrayEnded = true
          return None
        
        try
          parser.next() match
            case Event.START_OBJECT =>
              Some(readObject(parser))
            case Event.END_ARRAY =>
              arrayEnded = true
              None
            case _ =>
              // Skip other events
              tryReadNext()
        catch
          case e: Exception =>
            arrayEnded = true
            None
      end tryReadNext

      private def readObject(parser: JParser): JsonObject =
        val fields = scala.collection.mutable.Map[String, JsonValue]()
        
        var continue = true
        while continue && parser.hasNext do
          parser.next() match
            case Event.KEY_NAME =>
              val key = parser.getString()
              val value = readValue(parser)
              fields(key) = value
            case Event.END_OBJECT =>
              continue = false
            case _ =>
              ()
        
        JsonObject(fields.toMap)
      end readObject

      private def readValue(parser: JParser): JsonValue =
        if !parser.hasNext then return JsonNull
        
        parser.next() match
          case Event.VALUE_NULL =>
            JsonNull
          case Event.VALUE_TRUE =>
            JsonBool(true)
          case Event.VALUE_FALSE =>
            JsonBool(false)
          case Event.VALUE_STRING =>
            JsonString(parser.getString())
          case Event.VALUE_NUMBER =>
            JsonNumber(parser.getBigDecimal())
          case Event.START_ARRAY =>
            // Skip arrays for now
            skipArray(parser)
            JsonString("[]")
          case Event.START_OBJECT =>
            // Skip nested objects for now
            skipObject(parser)
            JsonString("{}")
          case other =>
            JsonNull
      end readValue

      private def skipArray(parser: JParser): Unit =
        var depth = 1
        while depth > 0 && parser.hasNext do
          parser.next() match
            case Event.START_ARRAY => depth += 1
            case Event.END_ARRAY => depth -= 1
            case _ => ()

      private def skipObject(parser: JParser): Unit =
        var depth = 1
        while depth > 0 && parser.hasNext do
          parser.next() match
            case Event.START_OBJECT => depth += 1
            case Event.END_OBJECT => depth -= 1
            case _ => ()
    end new
  end parseArrayStream

  /** Parse JSON from a Source (for compile-time use) */
  def parseArrayFromSource(source: Source, maxObjects: Int = Int.MaxValue): Iterator[JsonObject] =
    val content = source.mkString
    val input = new java.io.ByteArrayInputStream(content.getBytes("UTF-8"))
    parseArrayStream(input, maxObjects)

  /** Parse JSON from a string */
  def parseArrayFromString(json: String, maxObjects: Int = Int.MaxValue): Iterator[JsonObject] =
    val reader = new StringReader(json)
    val parser = Json.createParser(reader)
    
    // Same implementation as parseArrayStream but with a Reader
    val iter = new Iterator[JsonObject]:
      private var objectsRead = 0
      private var arrayStarted = false
      private var arrayEnded = false
      private var nextObj: Option[JsonObject] = None
      
      // Skip to array start
      if !arrayEnded && !arrayStarted then
        while parser.hasNext && !arrayStarted do
          parser.next() match
            case Event.START_ARRAY =>
              arrayStarted = true
            case _ =>
              ()
      end if

      def hasNext: Boolean =
        if arrayEnded || objectsRead >= maxObjects then false
        else if nextObj.isDefined then true
        else
          nextObj = tryReadNext()
          nextObj.isDefined

      def next(): JsonObject =
        if !hasNext then throw new NoSuchElementException("No more objects")
        val result = nextObj.get
        nextObj = None
        objectsRead += 1
        result

      private def tryReadNext(): Option[JsonObject] =
        if arrayEnded || !parser.hasNext then 
          arrayEnded = true
          return None
        
        try
          parser.next() match
            case Event.START_OBJECT =>
              Some(readObject(parser))
            case Event.END_ARRAY =>
              arrayEnded = true
              None
            case _ =>
              // Skip other events
              tryReadNext()
        catch
          case e: Exception =>
            arrayEnded = true
            None
      end tryReadNext

      private def readObject(parser: JParser): JsonObject =
        val fields = scala.collection.mutable.Map[String, JsonValue]()
        
        var continue = true
        while continue && parser.hasNext do
          parser.next() match
            case Event.KEY_NAME =>
              val key = parser.getString()
              val value = readValue(parser)
              fields(key) = value
            case Event.END_OBJECT =>
              continue = false
            case _ =>
              ()
        
        JsonObject(fields.toMap)
      end readObject

      private def readValue(parser: JParser): JsonValue =
        if !parser.hasNext then return JsonNull
        
        parser.next() match
          case Event.VALUE_NULL =>
            JsonNull
          case Event.VALUE_TRUE =>
            JsonBool(true)
          case Event.VALUE_FALSE =>
            JsonBool(false)
          case Event.VALUE_STRING =>
            JsonString(parser.getString())
          case Event.VALUE_NUMBER =>
            JsonNumber(parser.getBigDecimal())
          case Event.START_ARRAY =>
            // Skip arrays for now
            skipArray(parser)
            JsonString("[]")
          case Event.START_OBJECT =>
            // Skip nested objects for now
            skipObject(parser)
            JsonString("{}")
          case other =>
            JsonNull
      end readValue

      private def skipArray(parser: JParser): Unit =
        var depth = 1
        while depth > 0 && parser.hasNext do
          parser.next() match
            case Event.START_ARRAY => depth += 1
            case Event.END_ARRAY => depth -= 1
            case _ => ()

      private def skipObject(parser: JParser): Unit =
        var depth = 1
        while depth > 0 && parser.hasNext do
          parser.next() match
            case Event.START_OBJECT => depth += 1
            case Event.END_OBJECT => depth -= 1
            case _ => ()
    end new
    
    iter
  end parseArrayFromString

end StreamingJsonParser
