package io.github.quafadas.scautable

private[scautable] trait PlatformSpecific:

  /** Reads the clipboard content as a String.
    *
    * Note: This is not supported in JavaScript environments.
    *
    * @return
    *   Empty string (clipboard access not available in JS)
    */
  def readClipboard(): String =
    throw new UnsupportedOperationException("Clipboard access is not available in JavaScript environments")
  end readClipboard

end PlatformSpecific
