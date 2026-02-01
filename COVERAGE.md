# Code Coverage with Scoverage

This project uses [Scoverage](https://github.com/scoverage/scalac-scoverage-plugin) for code coverage analysis via Mill's contrib plugin.

## Running Coverage Reports

To generate code coverage reports for the JVM target:

```bash
# Run tests (which uses the scoverage-instrumented code)
./mill scautable.test.jvm.testLocal

# Generate HTML coverage report
./mill scautable.jvm.scoverage.htmlReport

# Generate console coverage report
./mill scautable.jvm.scoverage.consoleReport

# Generate XML coverage report (for CI/CD integration)
./mill scautable.jvm.scoverage.xmlReport
```

The HTML report will be generated in:
```
out/scautable/jvm/scoverage/htmlReport.dest/index.html
```

## Current Coverage Status (as of February 2026)

**Overall Coverage:**
- Statement coverage: **58.31%**
- Branch coverage: **52.71%**
- Total statements: 2,713
- Invoked statements: 1,582
- Total branches: 738
- Invoked branches: 389

**Coverage by Package:**
- `io.github.quafadas.scautable.json`: **69.13%** ✓ (Good coverage)
- `io.github.quafadas.scautable`: **57.39%** (Moderate coverage)
- `io.github.quafadas`: **7.69%** ✗ (Low coverage - needs attention)

## Recommendations for Improving Coverage

### High Priority (Low Coverage Areas)

1. **`io.github.quafadas` package (7.69% coverage)**
   - `FirstN` class: 0% coverage - Add tests for the FirstN type inferrer
   - `given_FromExpr_TypeInferrer`: 0% coverage - This is macro-related code that may be difficult to test directly
   - `table` object: 11.54% coverage - Add tests for table rendering functionality

2. **CSV module (43.40% coverage)**
   - The CSV parser and related functionality has moderate coverage
   - Focus on testing edge cases:
     - Different delimiter types
     - Quote handling
     - Escaped characters
     - Multi-line fields
     - Empty fields and rows
     - Large file handling

3. **Browser/Visualization features (64.66% coverage)**
   - Add tests for HTML table generation
   - Test different styling options
   - Test chart generation features

### Medium Priority

4. **Type Inference**
   - Ensure all type inference paths are tested
   - Test combinations of types (Int, Double, Boolean, String)
   - Test edge cases for type inference (null values, mixed types)

5. **Excel Integration (JVM-only)**
   - Ensure all Excel reading features are tested
   - Test workbook caching functionality
   - Test different Excel formats and edge cases

### Testing Strategy Suggestions

1. **Add parameterized tests** for different CSV configurations
2. **Add property-based tests** for CSV parsing (using ScalaCheck)
3. **Add integration tests** that combine multiple features
4. **Test error handling** - ensure exceptions are thrown appropriately
5. **Add tests for the macro-based APIs** (though coverage may not capture these fully)

### Notes

- Some low coverage in macro code is expected as Scoverage has limitations with inline/macro code
- The JSON module has the best coverage at 69% - this can serve as a model for other modules
- Focus first on user-facing APIs and critical parsing logic
- Coverage for browser-specific and JS-only code requires separate test runs on the JS target

## CI/CD Integration

To integrate coverage into CI/CD pipelines:

```bash
# Generate XML report for coverage tracking tools
./mill scautable.jvm.scoverage.xmlReport
```

The XML report will be at: `out/scautable/jvm/scoverage/xmlReport.dest/scoverage.xml`

This can be consumed by tools like:
- Codecov
- Coveralls
- SonarQube
- GitHub Actions coverage reporters

## Target Coverage Goals

Suggested coverage targets:
- **Minimum**: 70% statement coverage
- **Good**: 80% statement coverage  
- **Excellent**: 90% statement coverage

Current status: **58.31%** - working toward the 70% minimum target.
