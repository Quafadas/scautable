package io.github.quafadas.scautable

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.quoted.*

/** Compile time resolution of file paths anchored to something more stable than the compiler's working directory.
  *
  * A macro that reads a file to infer its schema has to find that file twice - once during compilation, and again when
  * the generated code runs. Anchoring the path to the source file, or to the project root discovered above it, makes the
  * compile time half of that reproducible no matter where the build was invoked from.
  *
  * Shared by [[CSV]], [[Excel]], `JsonTable` and `Parquet` so they all resolve paths the same way.
  */
private[scautable] object SourceAnchor:

  /** A path resolved at compile time, together with the project root it was anchored against. Readers use the root to
    * build a runtime fallback for the case where the file has moved relative to the machine that compiled it.
    */
  final case class Anchored(absolutePath: Path, projectRoot: Path)

  /** Files that mark the top of a project. */
  val rootMarkers: Set[String] = Set(
    "build.sbt",
    "project.scala",
    "build.sc",
    "build.mill",
    "build.mill.scala",
    ".scala-build",
    ".git",
    "pom.xml",
    "build.gradle"
  )

  private def ancestors(path: Path): LazyList[Path] =
    LazyList
      .iterate(Option(path))(_.flatMap(p => Option(p.getParent)))
      .takeWhile(_.isDefined)
      .map(_.get)

  /** Directory holding the source file that expanded this macro, if that call site has a file on disk.
    *
    * Notebook and REPL front ends (almond, ammonite, the scala REPL) compile cells from memory, so there is no source
    * path to anchor to and this is `None`. Callers fall back to [[workingDir]] in that case.
    */
  def callSiteDir(using Quotes): Option[Path] =
    import quotes.reflect.*
    Position.ofMacroExpansion.sourceFile.getJPath.map { jPath =>
      val abs = jPath.toAbsolutePath.normalize
      val sourceDir = Option(abs.getParent).getOrElse(abs)
      ancestors(sourceDir)
        .find(p => Option(p.getFileName).exists(_.toString == ".scala-build"))
        .flatMap(p => Option(p.getParent))
        .getOrElse(sourceDir)
    }
  end callSiteDir

  /** Working directory of the compiler. In notebooks and REPLs the macro expands in the same JVM the code runs in, so
    * this is the kernel's directory - which jupyter sets to the notebook's own directory.
    */
  def workingDir: Path = Paths.get("").toAbsolutePath.normalize

  /** First ancestor of `from` containing one of [[rootMarkers]], or `from` itself if there is none. */
  def projectRootFrom(from: Path): Path =
    ancestors(from)
      .find(d => rootMarkers.exists(marker => Files.exists(d.resolve(marker))))
      .getOrElse(from)
  end projectRootFrom

  /** Paths given to the anchored constructors are always relative to their anchor, so a leading separator is a slip
    * rather than a request for the filesystem root. Drop it, otherwise `resolve` discards the anchor and looks for the
    * file at `/`.
    */
  def anchorRelative(path: String): String = path.dropWhile(c => c == '/' || c == '\\')

  /** Announces that an anchored call site had no source file, and that [[workingDir]] is standing in for it. */
  private def warnNoSourceFile(anchor: Path)(using Quotes): Unit =
    import quotes.reflect.*
    val pos = Position.ofMacroExpansion
    report.warning(
      s"scautable: no file on disk for this call site (${pos.sourceFile.path}), so paths resolve against the working directory '$anchor' instead of the source file. Use absolutePath or resource to be explicit.",
      pos
    )
  end warnNoSourceFile

  /** Resolves `path` against the directory of the source file that expanded this macro. */
  def relativeToSource(path: String)(using Quotes): Anchored =
    val relative = anchorRelative(path)
    callSiteDir match
      case Some(sourceDir) => Anchored(sourceDir.resolve(relative).toAbsolutePath.normalize, projectRootFrom(sourceDir))
      case None            =>
        val cwd = workingDir
        warnNoSourceFile(cwd)
        Anchored(Paths.get(relative).toAbsolutePath.normalize, projectRootFrom(cwd))
    end match
  end relativeToSource

  /** Resolves `path` against the project root discovered above the call site's source file. */
  def projectRoot(path: String)(using Quotes): Anchored =
    val root = callSiteDir match
      case Some(sourceDir) => projectRootFrom(sourceDir)
      case None            =>
        val cwd = workingDir
        warnNoSourceFile(cwd)
        projectRootFrom(cwd)
    Anchored(root.resolve(anchorRelative(path)).toAbsolutePath.normalize, root)
  end projectRoot

end SourceAnchor
