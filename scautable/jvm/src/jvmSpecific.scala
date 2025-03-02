package io.github.quafadas.scautable

import java.awt.Desktop
import io.github.quafadas.scautable.scautable.HtmlTableRender
import NamedTuple.*
// import almond.api.JupyterApi
// import almond.interpreter.api.DisplayData
// import almond.api.JupyterAPIHolder.value

trait PlatformSpecific:

  private def openBrowserWindow(uri: java.net.URI): Unit =
    if Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE) then
      Desktop.getDesktop().browse(uri)
    else
      /* Hail Mary...
        https://stackoverflow.com/questions/5226212/how-to-open-the-default-webbrowser-using-java
        If you are reading this part of the source code, it is likely because you had a crash on your OS.
        It is not easy for me to test all OSs out there!
        Websockets should work. But...
        If you wish, consider cloning
        https://github.com/Quafadas/dedav4s.git
        run:
        sbt console
        Then copy and paste...
        import viz.PlotTargets.desktopBrowser
        import viz.extensions.*
        List(1,4,6,7,4,4).plotBarChart()
        and you should have a reproduces your crash in a dev environment... and maybe fix for your OS?
        PR welcome :-) ...
       */
      val runtime = java.lang.Runtime.getRuntime()
      runtime.exec(Array[String](s"""xdg-open $uri]"""))

  def desktopShowNt[K <: Tuple, V <: Tuple](a: Seq[NamedTuple[K, V]])(using
      tableDeriveInstance: HtmlTableRender[V]
  ): os.Path =
    println("Here")
    desktopShow(a.map(_.toTuple))
  end desktopShowNt

  /** Attempts to open a browser window, and display this Seq of `Product` as a table.
    *
    * @param a
    *   \- seq of case classes
    * @param tableDeriveInstance
    *   \- summon a HtmlTableRender instance for the case class
    * @return
    */
  def desktopShow[A <: Product](a: Seq[A])(using tableDeriveInstance: HtmlTableRender[A]) =
    val asString = scautable(a).toString()
    val theHtml = raw"""
<!DOCTYPE html>
  <html>
    <head>
      <meta charset="utf-8" />
      <link rel="stylesheet" href="https://cdn.datatables.net/1.13.4/css/jquery.dataTables.min.css" />
      <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.6.3/jquery.min.js"></script>
      <script src="https://cdn.datatables.net/1.13.4/js/jquery.dataTables.min.js"></script>
    </head>
    <body>
      <div>
        $asString
      </div>
      <script>
$$(document).ready( function () {
    $$('#scautable').DataTable();
} );
      </script>
    </body>
</html>"""
    val tempFi = os.temp(theHtml, suffix = ".html", prefix = "plot-", deleteOnExit = false)
    openBrowserWindow(tempFi.toNIO.toUri())
    tempFi
  end desktopShow

  // def almondShow[A <: Product](a: Seq[A])(using tableDeriveInstance: HtmlTableRender[A]) =
  //   val kernel = summon[JupyterApi]
  //   val asString = scautable(a).toString()
  //   kernel.publish.html(asString)
end PlatformSpecific
