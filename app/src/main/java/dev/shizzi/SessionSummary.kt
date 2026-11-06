package dev.shizzi

fun describeSession(elapsedMs: Long, traffic: Traffic): String =
    "ملخص الجلسة: مدة النشاط ${formatDuration(elapsedMs)}، " +
        "الرفع ${Traffic.format(traffic.up)}، التنزيل ${Traffic.format(traffic.down)}"

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        hours > 0 -> "$hours س $minutes د"
        minutes > 0 -> "$minutes د $seconds ث"
        else -> "$seconds ث"
    }
}
