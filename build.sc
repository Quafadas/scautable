
import $ivy.`com.github.lolgab::mill-crossplatform::0.2.4`
import $ivy.`io.github.quafadas::mill_scala3_site_mdoc::0.0.9`
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.4.0`

import de.tobiasroeser.mill.vcs.version._
import com.github.lolgab.mill.crossplatform._
import mill._, mill.scalalib._, mill.scalajslib._, mill.scalanativelib._
import millSite.SiteModule
import mill._, scalalib._, publish._

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

  def latestVersion = T{VcsVersion.vcsState().lastTag.getOrElse("0.0.0").replace("v", "")}

  def scalaVersion = scautable.jvm .scalaVersion

  override def moduleDeps = Seq( scautable.jvm, scautable.js )

  override def scalaDocOptions = super.scalaDocOptions() ++  Seq(
    "-scastie-configuration", s"""libraryDependencies += "io.github.quafadas" %% "scautable" % "${latestVersion()}"}"""",
    "-project", "scautable",
    "-project-version", latestVersion(),
    s"-social-links:github::${scautable.jvm.pomSettings().url}"
  )

}