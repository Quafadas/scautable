package io.github.quafadas.scautable

private[scautable] type PlatformPath = os.Path

extension (p: PlatformPath) private[scautable] def platformPathString: String = p.toString
end extension

private[scautable] def platformDetectWidth(fallback: Int): Int =
  import org.jline.terminal.TerminalBuilder

  try
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
