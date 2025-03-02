import $ivy.`com.github.lolgab::mill-crossplatform::0.2.4`
import $ivy.`io.github.quafadas:millSite_mill0.12_2.13:0.0.38`
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.4.0`
import $ivy.`com.lihaoyi::mill-contrib-buildinfo:`

import mill.contrib.buildinfo.BuildInfo
import de.tobiasroeser.mill.vcs.version._
import com.github.lolgab.mill.crossplatform._
import mill._, mill.scalalib._, mill.scalajslib._, mill.scalanativelib._
import io.github.quafadas.millSite.SiteModule
import io.github.quafadas.millSite.QuickChange
import mill._, scalalib._, publish._

import mill.api.Result

trait Common extends ScalaModule with PublishModule {
  def scalaVersion = "3.6.2"

  override def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"com.lihaoyi::scalatags::0.13.1",
    ivy"com.lihaoyi::os-lib:0.11.3",
    ivy"com.lihaoyi::fansi::0.5.0"
  )

  def ammoniteVersion                        = "3.0.0"
  override def scalacOptions: T[Seq[String]] = super.scalacOptions() ++ Seq("-experimental", "-language:experimental.namedTuples")

  def publishVersion = VcsVersion.vcsState().format()

  override def pomSettings = T {
    PomSettings(
      description = "Automatically generate html tables from scala case classes",
      organization = "io.github.quafadas",
      url = "https://github.com/Quafadas/scautable",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github("quafadas", "scautable"),
      developers = Seq(
        Developer("quafadas", "Simon Parten", "https://github.com/quafadas")
      )
    )
  }

}

trait CommonJS extends ScalaJSModule {
  def scalaJSVersion = "1.17.0"
  def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"com.raquo::laminar::17.2.0"
  )
}
trait CommonTests extends TestModule.Munit {
  def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"org.scalameta::munit::1.0.3"
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
    object test extends ScalaTests with SharedTests with BuildInfo {

      def buildInfoPackageName: String = "io.github.quafadas.scautable"


      override def generatedSources: T[Seq[PathRef]] = T{
        val resourceDir = resources().map(_.path).zipWithIndex.map{case (str, i) => s"""final val resourceDir$i = \"\"\"$str${java.io.File.separator}\"\"\""""  }.mkString("\n\t")
        val fileName = "BuildInfo.scala"
        val code = s"""

package io.github.quafadas.scautable

/**
Resources are not available at compile time. This is a workaround to get the path to the resource directory, (allowing unit testing of a macro based on a local file).
*/

object Generated {$resourceDir
}
"""
        val dest = T.ctx().dest / "BuildInfo.scala"
        os.write(dest , code)
        Seq(PathRef(dest))

      }

    }
  }
  object js extends Shared with CommonJS {
    // js specific settings here
    object test extends ScalaJSTests with SharedTests
  }

}

object site extends SiteModule {

  def scalaVersion = scautable.jvm.scalaVersion

  override def moduleDeps = Seq(scautable.jvm)

}
