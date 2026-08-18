package io.github.quafadas.scautable.parquet

import org.apache.parquet.ParquetReadOptions
import org.apache.parquet.hadoop.ParquetFileReader
import org.apache.parquet.io.LocalInputFile

import java.nio.file.Path as JPath
import java.nio.file.Paths

/** Where a parquet file lives.
  *
  * The macro captures one of these so that the *runtime* lookup mirrors the *compile-time* lookup. In particular [[ParquetSource.Resource]] re-resolves the resource against the
  * runtime classloader rather than baking the compiler's absolute path into the generated code.
  */
enum ParquetSource:
  /** A file on the java classpath, resolved via the classloader. */
  case Resource(name: String)

  /** An absolute path on the local filesystem. */
  case AbsolutePath(path: String)

  /** A path relative to the working directory. */
  case RelativePath(path: String)
end ParquetSource

object ParquetSource:

  extension (source: ParquetSource)
    /** Resolve this source to a local filesystem path. */
    def localPath: JPath = source match
      case ParquetSource.AbsolutePath(p) => Paths.get(p)
      case ParquetSource.RelativePath(p) => Paths.get(".").toAbsolutePath.normalize().resolve(p)
      case ParquetSource.Resource(name)  =>
        val url = Option(getClass.getClassLoader.getResource(name))
          .getOrElse(throw new java.io.FileNotFoundException(s"Parquet resource not found on the classpath: '$name'"))
        if url.getProtocol != "file" then
          throw new UnsupportedOperationException(
            s"Parquet resource '$name' resolved to '$url'. Parquet requires random access, so resources inside a jar are not supported - use Parquet.absolutePath instead."
          )
        end if
        Paths.get(url.toURI)

    /** Open a [[ParquetFileReader]] over this source. Callers are responsible for closing it. */
    def openReader(): ParquetFileReader =
      ParquetFileReader.open(new LocalInputFile(source.localPath), ParquetReadOptions.builder().build())
  end extension

end ParquetSource
