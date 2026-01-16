package io.github.quafadas.scautable

private[scautable] type PlatformPath = os.Path

extension (p: PlatformPath) private[scautable] def platformPathString: String = p.toString
end extension
