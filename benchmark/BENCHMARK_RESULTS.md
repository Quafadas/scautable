# CSV Reading Efficiency Benchmark Results

## Executive Summary

Benchmarked two approaches for reading CSV files into array format:
1. **ArrayBuffer approach** (current implementation): Dynamic ArrayBuffer growth
2. **Two-pass approach**: Pre-allocate exact-sized arrays after counting rows

## Test Environment

- **JVM**: OpenJDK 21.0.9, 64-Bit Server VM
- **JVM Options**: -Xms2G -Xmx4G
- **Warmup**: 2 iterations, 2s each
- **Measurement**: 3 iterations, 3s each
- **Fork**: 1

## Test Data

Three CSV files with mixed column types (Int, String, Int, Double, Boolean, Double):
- **Small**: 1,000 rows (~40KB)
- **Medium**: 100,000 rows (~4.3MB)  
- **Large**: 1,000,000 rows (~45MB)

## Benchmark Results

### In-Memory Parsing (CSV content pre-loaded in memory)

#### Parse Only (String Arrays)

| File Size | ArrayBuffer | Two-Pass | Difference |
|-----------|-------------|----------|------------|
| 1K rows   | 0.453 ms    | 0.441 ms | **-2.6%** (two-pass faster) |
| 100K rows | 50.276 ms   | 46.719 ms | **-7.1%** (two-pass faster) |
| 1M rows   | 770.123 ms  | 839.652 ms | **+9.0%** (ArrayBuffer faster) |

#### With Type Decoding (Typed Arrays)

| File Size | ArrayBuffer | Two-Pass | Difference |
|-----------|-------------|----------|------------|
| 1K rows   | 0.614 ms    | 0.559 ms | **-9.0%** (two-pass faster) |
| 100K rows | 79.011 ms   | 70.532 ms | **-10.7%** (two-pass faster) |
| 1M rows   | 1040.376 ms | 996.149 ms | **-4.3%** (two-pass faster) |

### File I/O Overhead (Reading from disk)

| File Size | ArrayBuffer (1 read) | Two-Pass (2 reads) | Difference |
|-----------|---------------------|-------------------|------------|
| 1K rows   | 0.498 ms           | 0.532 ms          | **+6.8%** overhead |
| 100K rows | 52.429 ms          | 54.125 ms         | **+3.2%** overhead |
| 1M rows   | 806.552 ms         | 769.829 ms        | **-4.6%** (two-pass faster!) |

## Key Findings

### 1. Two-Pass Advantages
- **Better for type decoding**: 4-11% faster when converting to typed arrays
- **Predictable memory usage**: Exact-sized allocation eliminates resize overhead
- **Scales well with decoding**: Larger files benefit more from avoiding ArrayBuffer.toArray copy

### 2. ArrayBuffer Advantages  
- **Simpler implementation**: Single pass through data
- **Better for parse-only**: Slight edge for very large files (1M+) without type conversion
- **No file re-read penalty**: For in-memory content, avoids List materialization cost

### 3. File I/O Insights
- **Two file reads overhead is minimal**: Only 3-7% for small/medium files
- **Large files**: Two-pass can be faster even with double I/O due to better memory patterns
- **File system caching**: Second read benefits from OS page cache

## Recommendations

### Use Two-Pass When:
1. **Type decoding is required** (most common use case)
2. **Memory predictability matters** (production environments)
3. **File size < 1M rows** (clear performance wins)
4. **Files are read from disk** (caching helps second pass)

### Use ArrayBuffer When:
1. **Parse-only to string arrays** (no type conversion)
2. **Very large files (5M+ rows)** without type decoding
3. **Streaming from network/non-seekable sources** (two-pass not possible)
4. **Content already in memory as String** (avoid List materialization)

## Implementation Considerations

### Two-Pass In-Memory Variant
The benchmarked two-pass approach converts iterator to List to count rows. For very large in-memory content:
- List materialization costs ~9% for 1M rows parse-only
- Still wins overall when type decoding is included
- Alternative: Scan content for newlines before parsing (not benchmarked)

### Two-Pass File Variant
Reading file twice has minimal overhead:
- OS page caching makes second read very fast
- Header parsing happens only once
- Pre-allocation prevents fragmentation

## Conclusion

**The two-pass approach with pre-allocation shows 4-11% performance improvements** for the common case where CSV data needs to be decoded to typed arrays:
- Small files (1K): 9% faster
- Medium files (100K): 11% faster
- Large files (1M): 4% faster

However, after evaluating the results, **the decision was made not to implement the two-pass approach in production code**. The reasons:
- The performance gains (4-11%) are relatively modest
- The implementation complexity doesn't justify the minor improvements
- The current ArrayBuffer approach is simpler and more maintainable
- ArrayBuffer handles edge cases (streaming, unknown sizes) more naturally

This benchmark serves as a record of the experiment and provides valuable insights into the trade-offs between the two approaches. The current ArrayBuffer implementation remains the recommended approach for scautable.

### If You Need Better Performance

For users who need maximum performance and can accept the constraints:
- Use the two-pass file-based approach from `benchmark/src/CsvReadingStrategies.scala`
- Ideal for: Known file sizes, repeated processing, memory-constrained environments
- Not suitable for: Streaming sources, unknown data sizes, simplicity requirements

## Code Location

Current implementation: https://github.com/Quafadas/scautable/blob/main/scautable/src/csv.scala#L683-L703

Benchmark implementation: `benchmark/src/CsvArrayReadBenchmark.scala`
Strategy implementations: `benchmark/src/CsvReadingStrategies.scala`
