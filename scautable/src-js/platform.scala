package io.github.quafadas.scautable

private[scautable] type PlatformPath = String

extension (p: PlatformPath) private[scautable] def platformPathString: String = p
end extension

// No real terminal / tty concept in JS - always fall back.
private[scautable] def platformDetectWidth(fallback: Int): Int = fallback
