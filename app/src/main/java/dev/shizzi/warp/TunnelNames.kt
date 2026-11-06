package dev.shizzi.warp

/**
 * Tunnel names must satisfy the AmneziaWG library's own rule
 * `[a-zA-Z0-9_=+.-]{1,15}`, because the name becomes the VPN session label.
 * Pure Kotlin so it is unit-testable without Android.
 */
object TunnelNames {

    const val MAX_LENGTH = 15

    private const val FALLBACK = "tunnel"

    private val valid = Regex("[a-zA-Z0-9_=+.-]{1,$MAX_LENGTH}")
    private val invalidRun = Regex("[^a-zA-Z0-9_=+.-]+")
    private val extension = Regex("\\.(conf|txt)$", RegexOption.IGNORE_CASE)

    fun isValid(name: String): Boolean = valid.matches(name)

    /** Turns a file name or free text into a valid tunnel name (never empty). */
    fun sanitize(raw: String): String {
        val base = raw.trim()
            .substringAfterLast('/')
            .replace(extension, "")

        val cleaned = invalidRun.replace(base, "_")
            .take(MAX_LENGTH)
            .trim('_', '.', '-')

        return cleaned.ifEmpty { FALLBACK }
    }

    /** Appends -2, -3, ... (within the length limit) until [base] is not in [existing]. */
    fun unique(base: String, existing: Collection<String>): String {
        val taken = existing.mapTo(HashSet()) { it.lowercase() }
        if (base.lowercase() !in taken) return base

        var counter = 2
        while (true) {
            val suffix = "-$counter"
            val candidate = base.take(MAX_LENGTH - suffix.length).trimEnd('_', '.', '-') + suffix
            if (candidate.lowercase() !in taken) return candidate
            counter++
        }
    }
}
