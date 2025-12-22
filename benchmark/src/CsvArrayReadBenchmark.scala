package scautable.benchmark

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit
import scala.io.Source
import scautable.benchmark.CsvReadingStrategies.*

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(value = 1, jvmArgs = Array("-Xms2G", "-Xmx4G"))
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 5, time = 3)
class CsvArrayReadBenchmark:

  // CSV content cached in memory
  var csv1k: String = scala.compiletime.uninitialized
  var csv100k: String = scala.compiletime.uninitialized
  var csv1m: String = scala.compiletime.uninitialized
  
  @Setup(Level.Trial)
  def setup(): Unit =
    // Load CSV files into memory for consistent benchmarking
    csv1k = Source.fromFile("benchmark/resources/benchmark_1k.csv").mkString
    csv100k = Source.fromFile("benchmark/resources/benchmark_100k.csv").mkString
    csv1m = Source.fromFile("benchmark/resources/benchmark_1m.csv").mkString
    
    println(s"Loaded benchmark data:")
    println(s"  1K rows: ${csv1k.length} bytes")
    println(s"  100K rows: ${csv100k.length} bytes")
    println(s"  1M rows: ${csv1m.length} bytes")
  end setup

  // ========== Small file (1K rows) ==========
  
  @Benchmark
  def arrayBuffer_1k_parseOnly(bh: Blackhole): Unit =
    val (headers, buffers) = readWithArrayBuffer(csv1k)
    bh.consume(headers)
    bh.consume(buffers)
  end arrayBuffer_1k_parseOnly

  @Benchmark
  def arrayBuffer_1k_withDecoding(bh: Blackhole): Unit =
    val (headers, buffers) = readWithArrayBuffer(csv1k)
    val decoded = decodeColumns(buffers)
    bh.consume(headers)
    bh.consume(decoded)
  end arrayBuffer_1k_withDecoding

  @Benchmark
  def twoPass_1k_parseOnly(bh: Blackhole): Unit =
    val (headers, columns) = readWithTwoPass(csv1k)
    bh.consume(headers)
    bh.consume(columns)
  end twoPass_1k_parseOnly

  @Benchmark
  def twoPass_1k_withDecoding(bh: Blackhole): Unit =
    val (headers, columns) = readWithTwoPass(csv1k)
    val decoded = decodeColumnsFromArrays(columns)
    bh.consume(headers)
    bh.consume(decoded)
  end twoPass_1k_withDecoding

  // ========== Medium file (100K rows) ==========
  
  @Benchmark
  def arrayBuffer_100k_parseOnly(bh: Blackhole): Unit =
    val (headers, buffers) = readWithArrayBuffer(csv100k)
    bh.consume(headers)
    bh.consume(buffers)
  end arrayBuffer_100k_parseOnly

  @Benchmark
  def arrayBuffer_100k_withDecoding(bh: Blackhole): Unit =
    val (headers, buffers) = readWithArrayBuffer(csv100k)
    val decoded = decodeColumns(buffers)
    bh.consume(headers)
    bh.consume(decoded)
  end arrayBuffer_100k_withDecoding

  @Benchmark
  def twoPass_100k_parseOnly(bh: Blackhole): Unit =
    val (headers, columns) = readWithTwoPass(csv100k)
    bh.consume(headers)
    bh.consume(columns)
  end twoPass_100k_parseOnly

  @Benchmark
  def twoPass_100k_withDecoding(bh: Blackhole): Unit =
    val (headers, columns) = readWithTwoPass(csv100k)
    val decoded = decodeColumnsFromArrays(columns)
    bh.consume(headers)
    bh.consume(decoded)
  end twoPass_100k_withDecoding

  // ========== Large file (1M rows) ==========
  
  @Benchmark
  def arrayBuffer_1m_parseOnly(bh: Blackhole): Unit =
    val (headers, buffers) = readWithArrayBuffer(csv1m)
    bh.consume(headers)
    bh.consume(buffers)
  end arrayBuffer_1m_parseOnly

  @Benchmark
  def arrayBuffer_1m_withDecoding(bh: Blackhole): Unit =
    val (headers, buffers) = readWithArrayBuffer(csv1m)
    val decoded = decodeColumns(buffers)
    bh.consume(headers)
    bh.consume(decoded)
  end arrayBuffer_1m_withDecoding

  @Benchmark
  def twoPass_1m_parseOnly(bh: Blackhole): Unit =
    val (headers, columns) = readWithTwoPass(csv1m)
    bh.consume(headers)
    bh.consume(columns)
  end twoPass_1m_parseOnly

  @Benchmark
  def twoPass_1m_withDecoding(bh: Blackhole): Unit =
    val (headers, columns) = readWithTwoPass(csv1m)
    val decoded = decodeColumnsFromArrays(columns)
    bh.consume(headers)
    bh.consume(decoded)
  end twoPass_1m_withDecoding

end CsvArrayReadBenchmark


/** Additional benchmark measuring file I/O overhead for two-pass approach */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(value = 1, jvmArgs = Array("-Xms2G", "-Xmx4G"))
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 5, time = 3)
class CsvFileReadBenchmark:

  // Test two-pass approach with actual file I/O (reading file twice)
  
  @Benchmark
  def twoPassFromFile_1k(bh: Blackhole): Unit =
    val (headers, columns) = readWithTwoPassFromFile("benchmark/resources/benchmark_1k.csv")
    bh.consume(headers)
    bh.consume(columns)
  end twoPassFromFile_1k

  @Benchmark
  def twoPassFromFile_100k(bh: Blackhole): Unit =
    val (headers, columns) = readWithTwoPassFromFile("benchmark/resources/benchmark_100k.csv")
    bh.consume(headers)
    bh.consume(columns)
  end twoPassFromFile_100k

  @Benchmark
  def twoPassFromFile_1m(bh: Blackhole): Unit =
    val (headers, columns) = readWithTwoPassFromFile("benchmark/resources/benchmark_1m.csv")
    bh.consume(headers)
    bh.consume(columns)
  end twoPassFromFile_1m
  
  // Compare with single file read (ArrayBuffer approach)
  
  @Benchmark
  def arrayBufferFromFile_1k(bh: Blackhole): Unit =
    val content = Source.fromFile("benchmark/resources/benchmark_1k.csv").mkString
    val (headers, buffers) = readWithArrayBuffer(content)
    bh.consume(headers)
    bh.consume(buffers)
  end arrayBufferFromFile_1k

  @Benchmark
  def arrayBufferFromFile_100k(bh: Blackhole): Unit =
    val content = Source.fromFile("benchmark/resources/benchmark_100k.csv").mkString
    val (headers, buffers) = readWithArrayBuffer(content)
    bh.consume(headers)
    bh.consume(buffers)
  end arrayBufferFromFile_100k

  @Benchmark
  def arrayBufferFromFile_1m(bh: Blackhole): Unit =
    val content = Source.fromFile("benchmark/resources/benchmark_1m.csv").mkString
    val (headers, buffers) = readWithArrayBuffer(content)
    bh.consume(headers)
    bh.consume(buffers)
  end arrayBufferFromFile_1m

end CsvFileReadBenchmark
