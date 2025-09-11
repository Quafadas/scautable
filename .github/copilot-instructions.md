# Scautable: Scala 3 Compile-Time Dataframe Library

ALWAYS reference these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.

Scautable is a Scala 3 project using the mill build tool. It is a lightweight dataframe library with the twist that it expects the "dataframe" in question to have its structure identified by the compiler, at compile time. A dataframe here is modelled as an `Iterator[NamedTuple[K, V]]` where `K` is a compile time constant tuple of strings representing column names.

## Working Effectively
- **CRITICAL BUILD REQUIREMENT**: Mill requires Java 21. ALWAYS verify Java 21 is installed and active before running any mill commands.
- **SSL CERTIFICATE WORKAROUND**: In sandboxed environments, mill may fail with SSL certificate errors. If this occurs, document the failure and note that builds work in GitHub Actions CI environment.
- Bootstrap and build the repository:
  - `java --version` -- Must show Java 21. If not, install OpenJDK 21.
  - `sudo apt-get update && sudo apt-get install -y openjdk-21-jdk` -- Install Java 21 if needed
  - `sudo update-java-alternatives --set java-1.21.0-openjdk-amd64` -- Set Java 21 as default
  - `./mill --version` -- Verify mill works (may fail in sandboxed environments due to SSL issues)
- **NEVER CANCEL BUILDS**: Mill compilation takes 2-5 minutes. Tests take 1-3 minutes. ALWAYS set timeout to 10+ minutes.
- Compile all modules:
  - `./mill __.compile` -- Compiles all modules (JVM, JS, tests). Takes 3-5 minutes. NEVER CANCEL.
- Compile specific platforms:
  - `./mill scautable.js.compile` -- Compile Scala.js target only
  - `./mill scautable.jvm.compile` -- Compile JVM target only
- Run tests:
  - `./mill scautable.test._` -- Run all tests (JVM and JS). Takes 2-3 minutes. NEVER CANCEL.
- Format code:
  - `./mill __.reformat` -- Format all code using scalafmt
- Generate documentation:
  - `./mill site.siteGen` -- Generate documentation site (takes 1-2 minutes)

## SSL Certificate Issues in Sandboxed Environments
- Mill may fail with `javax.net.ssl.SSLHandshakeException` errors in certain environments
- This is a known limitation of mill's dependency resolution in sandboxed/firewalled environments
- Workarounds attempted: `JAVA_TOOL_OPTIONS`, `COURSIER_*` environment variables (none successful in current environment)
- **CI Environment**: All commands work correctly in GitHub Actions (see `.github/workflows/ci.yml`)
- **Development**: If SSL issues occur, note the limitation and continue with file-based exploration

## Validation
- ALWAYS test CSV functionality after making changes to CSV parsing code
- Test both JVM and JS targets when making cross-platform changes
- Run `./mill scautable.test._` to validate all functionality
- Format code with `./mill __.reformat` before committing
- ALWAYS check that examples in `examples/` still compile after core changes

## Project Structure
```
scautable/
├── src/           -- Shared Scala source code (cross-platform)
├── src-jvm/       -- JVM-specific source code (Excel support, stats)
├── src-js/        -- JavaScript-specific source code (browser integration)
├── test/
│   ├── src/       -- Cross-platform tests (munit)
│   ├── src-jvm/   -- JVM-specific tests
│   └── resources/ -- Test data (CSV files, Excel files - 23 test files)
├── package.mill   -- Module build configuration
examples/          -- Usage examples and demos
site/              -- Documentation and site generation
build.mill         -- Root build configuration
```

## Common Tasks
### Adding New CSV Tests
1. Add test CSV files to `scautable/test/resources/`
2. Create test cases in `scautable/test/src/` for cross-platform tests
3. Add JVM-specific tests in `scautable/test/src-jvm/` if needed
4. Run `./mill scautable.test._` to validate

### Working with Mill Resources
Mill separates compile resources and runtime resources. For CSV files to be available at compile time:
```scala
trait ShareCompileResources extends ScalaModule {
  override def compileResources = super.compileResources() ++ resources()
}
```

### Using scala-cli for Development
For quick iteration and testing:
```scala
//> using scala 3.7.2
//> using dep io.github.quafadas::scautable::{{latest_version}}
//> using resourceDir ./csvs

import io.github.quafadas.table.*

@main def testCsv = 
  val csv = CSV.resource("test.csv")
  csv.take(10).toSeq.ptbln
```

## Code Guidelines
- Follow `styleguide.md` for coding conventions
- Use munit for tests. Cross-platform tests go in `scautable/test/src`
- JVM-specific tests go in `scautable/test/src-jvm`
- Use Scala 3 syntax: given/using, extension methods, enum types
- Prefer compile-time type inference for CSV schemas
- Use inline methods for performance-critical code

## Key Dependencies
- Scala 3.7.2
- Mill 1.0.4 (requires Java 21)
- ScalaJS 1.19.0
- Munit for testing
- scalatags for HTML generation
- OS-lib for file operations
- Apache POI for Excel support (JVM only)

## GitHub Actions CI
The project uses GitHub Actions for CI/CD:
- **Test job**: Compiles JS, JVM, and runs all tests
- **Publish job**: Publishes to Maven Central on tags
- **Site job**: Generates and deploys documentation to GitHub Pages
- All CI commands work despite local SSL issues

## Development Tips
- The library targets "small" data - tested up to 5 million rows
- CSV type inference happens at compile time
- Use `TypeInferrer.FromAllRows` for most accurate type detection
- Use `TypeInferrer.StringType` for fastest parsing when types aren't needed
- Excel support is JVM-only (uses Apache POI)
- Browser visualization features are JS-only

Answer all questions in the style of a friendly colleague that is an expert in dataframe libraries, Scala 3, and the requirements of a statistical library. Feel free to suggest popular APIs from successful libraries, e.g., Pandas, which expose clean APIs and clear functionality to consumers of this library.