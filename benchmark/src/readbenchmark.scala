package scautable.benchmark

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import scala.compiletime.uninitialized
import io.github.quafadas.table.*
import io.github.quafadas.scautable.CSVParser

@State(Scope.Thread)
class ReadBenchmark extends TableBenchmark:

	// format: off
	@Setup(Level.Trial)
	def setup: Unit =
		()
	end setup

	private val sampleLine = "1966-01,1,41"

	@Benchmark
	def read(bh: Blackhole) =
		val data = CSV.resource("acheck.csv")
		data.toSeq
	end read

	@Benchmark
	def readIteratorOnly(bh: Blackhole) =
		val data = CSV.resource("acheck.csv")
		var count = 0
		while data.hasNext do
			val row = data.next()
			bh.consume(row)
			count += 1
    end while
		count
	end readIteratorOnly

	@Benchmark
	def readWithForeach(bh: Blackhole) =
		val data = CSV.resource("acheck.csv")
		data.foreach(bh.consume)
	end readWithForeach

	// @Benchmark
	// def parseLineOnly(bh: Blackhole) =
	// 	val parsed = CSVParser.parseLine(sampleLine)
	// 	bh.consume(parsed)
	// end parseLineOnly


end ReadBenchmark