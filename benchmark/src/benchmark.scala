package scautable.benchmark

import org.openjdk.jmh.annotations.*

import java.util.Random
import java.util.concurrent.TimeUnit
import scala.compiletime.uninitialized

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Fork(value = 1)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
abstract class TableBenchmark:

  @Setup
  def setupImplementation: Unit =

    ()
end TableBenchmark
