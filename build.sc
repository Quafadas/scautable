
import $ivy.`com.github.lolgab::mill-crossplatform::0.2.4`
import $ivy.`io.github.quafadas::millSite::0.0.12`
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.4.0`

import de.tobiasroeser.mill.vcs.version._
import com.github.lolgab.mill.crossplatform._
import mill._, mill.scalalib._, mill.scalajslib._, mill.scalanativelib._
import io.github.quafadas.millSite.SiteModule
import io.github.quafadas.millSite.QuickChange
import mill._, scalalib._, publish._

import mill.api.Result


trait Common extends ScalaModule  with PublishModule {
  def scalaVersion = "3.3.1"

  override def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"com.lihaoyi::scalatags::0.12.0",
    ivy"com.lihaoyi::os-lib:0.9.1"
  )

  def publishVersion = VcsVersion.vcsState().format()

  override def pomSettings = T {
    PomSettings(
      description = "Automatically generate html tables from scala case classes",
      organization = "io.github.quafadas",
      url = "https://github.com/Quafadas/scautable",
      licenses = Seq(License.`Apache-2.0`),
      versionControl =
        VersionControl.github("quafadas", "scautable"),
      developers = Seq(
        Developer("quafadas", "Simon Parten", "https://github.com/quafadas")
      )
    )
  }

}

trait CommonJS extends ScalaJSModule {
  def scalaJSVersion = "1.14.0"
}
trait CommonTests extends TestModule.Munit {
  def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"org.scalameta::munit::1.0.0-M10"
  )
}


object scautable extends CrossPlatform {
  trait Shared extends CrossPlatformScalaModule with Common {
    // common `core` settings here
    trait SharedTests extends CommonTests {
      // common `core` test settings here
    }
  }
  object jvm extends Shared {
    // jvm specific settings here
    object test extends ScalaTests with SharedTests
  }
  object js extends Shared with CommonJS {
    // js specific settings here
    object test extends ScalaJSTests with SharedTests
  }

}

object site extends SiteModule {

  def scalaVersion = scautable.jvm.scalaVersion

  override def moduleDeps = Seq(scautable.jvm)

    private def fixAssets(docFile: os.Path) = {
    if (docFile.ext == "md") {
      val fixyFixy = os.read(docFile).replace("../_assets/", "")
      os.write.over(docFile, fixyFixy.getBytes())
    }
  }


  override def docOnlyGen: T[QuickChange] = T {
    val md = mdoc().path
    val origDocs = mdocSourceDir().path
    val javadocDir = T.dest / "javadoc"
    os.makeDir.all(javadocDir)
    val combinedStaticDir = T.dest / "static"
    os.makeDir.all(combinedStaticDir)

    // copy mdoccd files in
    for {
      aDoc <- os.walk(md)
      rel = (combinedStaticDir / aDoc.subRelativeTo(md))
    } {
      // println(rel)
      os.copy.over(aDoc, rel)
      fixAssets(rel) // pure filth, report as bug?
    }

    // copy all other doc files
    for {
      aDoc <- os.walk(origDocs)
      rel = (combinedStaticDir / aDoc.subRelativeTo(mdocDir));
      if !os.exists(rel)
    } {
      os.copy(aDoc, rel)
      // fixAssets(rel) // pure filth, report as bug?
    }

    // if (os.exists(assetDir)) {
    //   os.copy(assetDir, javadocDir, mergeFolders = true, replaceExisting = true)
    // }

    val compileCp = compileCpArg
    val options = Seq(
      "-d",
      javadocDir.toNIO.toString,
      "-siteroot",
      combinedStaticDir.toNIO.toString
    )

    val localCp = Lib
      .findSourceFiles(Seq(fakeSource().classes), Seq("tasty"))
      .map(_.toString()) // fake api to skip potentially slow doc generation

    zincWorker()
      .worker()
      .docJar(
        scalaVersion(),
        scalaOrganization(),
        scalaDocClasspath(),
        scalacPluginClasspath(),
        options ++ compileCpArg() ++ scalaDocOptions()
          ++ localCp
      ) match {
      case true =>
        Result.Success(
          QuickChange(
            os.walk(javadocDir / "docs")
              .filter(os.isFile)
              .map(PathRef(_))
              .toSeq,
            PathRef(javadocDir, true)
          )
        )
      case false =>
        Result.Failure(
          s"""Documentation generatation failed. Cause could include be no sources files in : ${sources()} or no doc files in ${docSources()}, or an error message printed above... """
        )
    }
  }

}