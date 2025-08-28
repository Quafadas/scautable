package io.github.quafadas.scautable

private[scautable] trait PlatformSpecific:

  /** With scaladoc
    */
  def shouldTotallyAppearInDocs(): Unit = ()
end PlatformSpecific
