package dev.shizzi.warp

import java.io.File

/** A saved tunnel config: [name] is a valid tunnel name, [text] the raw .conf. */
data class SavedConfig(val name: String, val text: String)

/** Why a config text was refused before it ever reached the VPN library. */
enum class ConfigProblem { EMPTY, TOO_LARGE, NOT_A_CONFIG, NO_PRIVATE_KEY, NO_PEER }

/** A cheap structural check, so an obviously wrong file gets a clear Arabic message. */
object ConfigSanity {

    const val MAX_BYTES = 64 * 1024

    fun check(text: String): ConfigProblem? {
        if (text.isBlank()) return ConfigProblem.EMPTY
        if (text.length > MAX_BYTES) return ConfigProblem.TOO_LARGE

        val lines = text.lineSequence().map { it.trim() }.toList()
        val sections = lines.filter { it.startsWith("[") }.map { it.lowercase() }

        if ("[interface]" !in sections) return ConfigProblem.NOT_A_CONFIG
        if (lines.none { it.lowercase().replace(" ", "").startsWith("privatekey=") }) {
            return ConfigProblem.NO_PRIVATE_KEY
        }
        if ("[peer]" !in sections) return ConfigProblem.NO_PEER

        return null
    }
}

/**
 * Keeps saved configs as one `<name>.conf` file each under [directory].
 * Plain java.io so it runs in JVM unit tests.
 */
class ConfigStore(private val directory: File) {

    private fun fileFor(name: String) = File(directory, "$name.conf")

    fun list(): List<SavedConfig> =
        directory.listFiles { file -> file.isFile && file.name.endsWith(".conf") }
            .orEmpty()
            .mapNotNull { file ->
                val name = file.name.removeSuffix(".conf")
                if (!TunnelNames.isValid(name)) return@mapNotNull null
                runCatching { SavedConfig(name, file.readText()) }.getOrNull()
            }
            .sortedBy { it.name.lowercase() }

    fun names(): List<String> = list().map { it.name }

    fun read(name: String): SavedConfig? {
        if (!TunnelNames.isValid(name)) return null
        val file = fileFor(name)
        if (!file.isFile) return null
        return runCatching { SavedConfig(name, file.readText()) }.getOrNull()
    }

    /** Saves under [name], replacing a file with the same name. Returns the saved entry. */
    fun save(name: String, text: String): SavedConfig {
        require(TunnelNames.isValid(name)) { "invalid tunnel name: $name" }
        directory.mkdirs()
        fileFor(name).writeText(text)
        return SavedConfig(name, text)
    }

    /** Saves under a fresh name derived from [rawName]; never overwrites an existing file. */
    fun saveAsNew(rawName: String, text: String): SavedConfig {
        val name = TunnelNames.unique(TunnelNames.sanitize(rawName), names())
        return save(name, text)
    }

    fun delete(name: String): Boolean {
        if (!TunnelNames.isValid(name)) return false
        return fileFor(name).delete()
    }
}
