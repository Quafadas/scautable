package scautable.benchmark

import java.io.PrintWriter
import scala.util.Random

object GenerateBenchmarkData:

  def generateCsv(filename: String, numRows: Int): Unit =
    val writer = new PrintWriter(filename)
    val rand = new Random(42) // Fixed seed for reproducibility

    // Write header
    writer.println("id,name,age,salary,active,score")

    // Write data rows
    for i <- 1 to numRows do
      val id = i
      val name = s"Person_${i}"
      val age = 20 + rand.nextInt(60)
      val salary = 30000.0 + rand.nextDouble() * 100000.0
      val active = rand.nextBoolean()
      val score = rand.nextDouble() * 100.0

      writer.println(f"$id,$name,$age,$salary%.2f,$active,$score%.4f")
    end for

    writer.close()
    println(s"Generated $filename with $numRows rows")
  end generateCsv

  def main(args: Array[String]): Unit =
    val outputDir = "benchmark/resources"

    // Create directory if it doesn't exist
    val dir = new java.io.File(outputDir)
    if !dir.exists() then dir.mkdirs()
    end if

    // Generate small (1K rows)
    generateCsv(s"$outputDir/benchmark_1k.csv", 1000)

    // Generate medium (100K rows)
    generateCsv(s"$outputDir/benchmark_100k.csv", 100000)

    // Generate large (1M rows)
    generateCsv(s"$outputDir/benchmark_1m.csv", 1000000)

    println("All benchmark CSV files generated successfully!")
  end main

end GenerateBenchmarkData
