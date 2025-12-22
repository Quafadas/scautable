# Running CSV Reading Benchmarks

## Overview

This benchmark compares different approaches for reading CSV files into array format:
1. **ArrayBuffer approach** (current implementation): Dynamic growth with resizing
2. **Two-pass with Java-based counting**: Count rows by iterating, then pre-allocate
3. **Two-pass with OS-level counting**: Use `wc -l` to count rows, then pre-allocate

**Result**: ArrayBuffer remains the recommended approach. Two-pass shows modest gains (4-11%) but adds complexity. OS-level counting adds process overhead that hurts small/medium files.

## Quick Start

### Generate Test Data (Required First Step)

**Note:** The benchmark CSV files are not included in the repository to keep it small. You must generate them first:

```bash
./mill benchmark.runMain scautable.benchmark.GenerateBenchmarkData
```
This creates benchmark CSV files in `benchmark/resources/`:
- `benchmark_1k.csv` - 1,000 rows (~40KB)
- `benchmark_100k.csv` - 100,000 rows (~4.3MB)  
- `benchmark_1m.csv` - 1,000,000 rows (~45MB)

These files are git-ignored and will not be committed.

### Run All Benchmarks
```bash
# Run all CSV array reading benchmarks (in-memory)
./mill benchmark.runJmh scautable.benchmark.CsvArrayReadBenchmark

# Run file I/O benchmarks
./mill benchmark.runJmh scautable.benchmark.CsvFileReadBenchmark
```

### Run Specific Benchmarks

#### Small files only (1K rows)
```bash
./mill benchmark.runJmh "CsvArrayReadBenchmark.*1k.*"
```

#### Medium files only (100K rows)
```bash
./mill benchmark.runJmh "CsvArrayReadBenchmark.*100k.*"
```

#### Large files only (1M rows)
```bash
./mill benchmark.runJmh "CsvArrayReadBenchmark.*1m.*"
```

#### Parse-only benchmarks (no type conversion)
```bash
./mill benchmark.runJmh "CsvArrayReadBenchmark.*parseOnly"
```

#### With type decoding benchmarks
```bash
./mill benchmark.runJmh "CsvArrayReadBenchmark.*withDecoding"
```

## Benchmark Configuration

The benchmarks use JMH with the following defaults:
- **Warmup**: 2 iterations, 2 seconds each
- **Measurement**: 3 iterations, 3 seconds each
- **JVM Options**: -Xms2G -Xmx4G
- **Fork**: 1

### Custom Configuration

You can override settings:
```bash
# More iterations for stability
./mill benchmark.runJmh CsvArrayReadBenchmark -wi 5 -i 10

# Different time allocations
./mill benchmark.runJmh CsvArrayReadBenchmark -wi 3 -w 3 -i 5 -r 5

# Multiple forks
./mill benchmark.runJmh CsvArrayReadBenchmark -f 3

# Save results to JSON
./mill benchmark.runJmh CsvArrayReadBenchmark -rf json -rff results.json
```

## Benchmark Methods

### CsvArrayReadBenchmark (In-Memory)

Tests reading pre-loaded CSV content from memory:

**Parse Only (String Arrays):**
- `arrayBuffer_1k_parseOnly` / `twoPass_1k_parseOnly`
- `arrayBuffer_100k_parseOnly` / `twoPass_100k_parseOnly`
- `arrayBuffer_1m_parseOnly` / `twoPass_1m_parseOnly`

**With Type Decoding:**
- `arrayBuffer_1k_withDecoding` / `twoPass_1k_withDecoding`
- `arrayBuffer_100k_withDecoding` / `twoPass_100k_withDecoding`
- `arrayBuffer_1m_withDecoding` / `twoPass_1m_withDecoding`

### CsvFileReadBenchmark (File I/O)

Tests file reading overhead:

**ArrayBuffer (single file read):**
- `arrayBufferFromFile_1k`
- `arrayBufferFromFile_100k`
- `arrayBufferFromFile_1m`

**Two-Pass (double file read):**
- `twoPassFromFile_1k`
- `twoPassFromFile_100k`
- `twoPassFromFile_1m`

## Understanding Results

### Metrics
- **Mode**: Average time per operation (lower is better)
- **Score**: Average execution time in milliseconds
- **Error**: 99.9% confidence interval
- **Units**: ms/op (milliseconds per operation)

### Example Output
```
Benchmark                                            Mode  Cnt   Score   Error  Units
CsvArrayReadBenchmark.arrayBuffer_100k_withDecoding  avgt    3  79.011 ± 5.584  ms/op
CsvArrayReadBenchmark.twoPass_100k_withDecoding      avgt    3  70.532 ± 6.340  ms/op
```

This shows:
- ArrayBuffer: 79.011 ms ± 5.584 ms
- Two-Pass: 70.532 ms ± 6.340 ms
- **Two-pass is 10.7% faster** for 100K rows with type decoding

## Troubleshooting

### Out of Memory
If you get OOM errors with 1M row benchmarks:
```bash
# Increase heap size
./mill benchmark.runJmh CsvArrayReadBenchmark -jvmArgs -Xmx8G
```

### Files Not Found
Make sure you've generated test data first:
```bash
./mill benchmark.runMain scautable.benchmark.GenerateBenchmarkData
```

### Slow Benchmarks
The full suite takes ~10 minutes. Run specific benchmarks:
```bash
# Quick test with 1K rows only
./mill benchmark.runJmh ".*1k.*" -wi 1 -i 1
```

## Analyzing Results

After running benchmarks, see:
- `benchmark/BENCHMARK_RESULTS.md` - Complete analysis and recommendations
- JMH JSON output (if using `-rf json -rff results.json`)

## Implementation Details

See:
- `benchmark/src/CsvReadingStrategies.scala` - Strategy implementations
- `scautable/src/csv.scala` (line 683-703) - Current ArrayBuffer implementation
