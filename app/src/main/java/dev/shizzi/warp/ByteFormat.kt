package dev.shizzi.warp

/** Short traffic size for the status line: 0 B, 12 KB, 3.4 MB. */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "${bytes.coerceAtLeast(0)} B"

    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0

    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }

    val shown = if (value >= 100) "%.0f".format(java.util.Locale.US, value) else "%.1f".format(java.util.Locale.US, value)
    return "$shown ${units[unit]}"
}
