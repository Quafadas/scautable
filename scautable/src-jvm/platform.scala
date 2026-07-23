package io.github.quafadas.scautable

private[scautable] type PlatformPath = os.Path

extension (p: PlatformPath) private[scautable] def platformPathString: String = p.toString
end extension

private[scautable] def platformDetectWidth(fallback: Int): Int =
  import org.jline.terminal.TerminalBuilder

  try
    // `dumb(true)` allows JLine to fall back to a "dumb" terminal implementation instead of throwing when stdin/out
    // isn't attached to a real tty (e.g. CI logs, redirected output, IDE consoles). A dumb terminal typically
    // reports a width of 0, which we detect below and treat the same as "detection unavailable".
    val terminal = TerminalBuilder.builder().system(true).dumb(true).build()
    try
      val w = terminal.getWidth()
      if w > 0 then w else fallback
      end if
    finally terminal.close()
    end try
  catch case _: Throwable => fallback
  end try
end platformDetectWidth
