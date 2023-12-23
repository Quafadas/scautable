
import $ivy.`com.github.lolgab::mill-crossplatform::0.2.4`
import $ivy.`io.github.quafadas::mill_scala3_site_mdoc::0.0.7`

import com.github.lolgab.mill.crossplatform._
import mill._, mill.scalalib._, mill.scalajslib._, mill.scalanativelib._
import millSite.SiteModule
import mill._, scalalib._

trait Common extends ScalaModule {
  def scalaVersion = "3.3.1"

  override def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"com.lihaoyi::scalatags::0.12.0",
    ivy"com.lihaoyi::os-lib:0.9.1"
  )

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

  def scalaVersion = scautable.jvm .scalaVersion

  override def moduleDeps = Seq( scautable.jvm, scautable.js )

  override def scalaDocOptions = super.scalaDocOptions() ++ Seq(
    "-scastie-configuration", """libraryDependencies += "io.github.quafadas" %% "scautable" % "0.0.5""""
  )

}