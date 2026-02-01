# Sample Scoverage Output

This document shows sample output from running scoverage on the scautable project.

## Console Report

```
Statement coverage.: 58.31%
Branch coverage....: 52.71%
```

## Detailed Coverage by Package

### Overall Statistics
- **Lines of code**: 3,699
- **Files**: 27
- **Classes**: 52
- **Methods**: 211
- **Total statements**: 2,713
- **Invoked statements**: 1,582
- **Total branches**: 738
- **Invoked branches**: 389

### Package Coverage

| Package | Coverage |
|---------|----------|
| io.github.quafadas.scautable.json | 69.13% |
| io.github.quafadas.scautable | 57.39% |
| io.github.quafadas | 7.69% |

### Notable Areas

#### High Coverage (Good!)
- JSON module: **69.13%** - Well-tested JSON parsing functionality
- Browser table rendering: **64.66%**

#### Medium Coverage (Needs Improvement)
- CSV module: **43.40%** - Core CSV parsing needs more edge case testing
- Overall scautable package: **57.39%**

#### Low Coverage (Priority!)
- Base package: **7.69%** - Contains macro code and type inference utilities
  - `FirstN` class: 0% coverage
  - `given_FromExpr_TypeInferrer`: 0% coverage
  - `table` object: 11.54% coverage

## HTML Report Location

After running `./mill scautable.jvm.scoverage.htmlReport`, the report is available at:

```
out/scautable/jvm/scoverage/htmlReport.dest/index.html
```

Open this file in a browser to see:
- Line-by-line coverage visualization
- Red/green highlighting of covered/uncovered code
- Drill-down by package, class, and method
- Sortable tables of coverage metrics

## XML Report for CI

The XML report (Cobertura format) is available at:

```
out/scautable/jvm/scoverage/xmlReport.dest/scoverage.xml
```

This can be used with CI tools like:
- Codecov
- Coveralls
- SonarQube
- GitHub Actions coverage reporters

## How to Reproduce

```bash
# Clean and run tests with coverage
./mill clean
./mill scautable.test.jvm.testLocal

# Generate reports
./mill scautable.jvm.scoverage.htmlReport
./mill scautable.jvm.scoverage.consoleReport
./mill scautable.jvm.scoverage.xmlReport
```
