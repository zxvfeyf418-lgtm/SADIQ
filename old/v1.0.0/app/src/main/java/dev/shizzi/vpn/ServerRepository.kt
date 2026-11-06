package dev.shizzi.vpn

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ServerList(val servers: List<ProxyConfig> = emptyList(), val selectedUri: String? = null) {
    val selected: ProxyConfig? get() = servers.firstOrNull { it.uri == selectedUri } ?: servers.firstOrNull()
}

data class SaveOutcome(val added: Int, val duplicates: Int, val failures: List<String>)

/**
 * Persists saved servers as their original share links, one per line, so a later
 * parser fix re-reads old entries correctly instead of freezing today's parse.
 */
class ServerRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("2n5a_servers", Context.MODE_PRIVATE)

    private val mutable = MutableStateFlow(load())
    val state: StateFlow<ServerList> = mutable.asStateFlow()

    fun save(text: String): SaveOutcome {
        val results = ConfigParser.parseAll(text)
        val current = mutable.value.servers.toMutableList()

        var added = 0
        var duplicates = 0
        val failures = mutableListOf<String>()
        var firstAdded: String? = null

        for (result in results) {
            val config = result.getOrNull()
            if (config == null) {
                failures += result.exceptionOrNull()?.message ?: "unknown error"
            } else if (current.any { it.uri == config.uri }) {
                duplicates++
            } else {
                current += config
                added++
                if (firstAdded == null) firstAdded = config.uri
            }
        }

        if (added > 0) commit(ServerList(current, firstAdded ?: mutable.value.selectedUri))
        return SaveOutcome(added, duplicates, failures)
    }

    fun select(uri: String) {
        if (mutable.value.servers.any { it.uri == uri }) commit(mutable.value.copy(selectedUri = uri))
    }

    fun remove(uri: String) {
        val remaining = mutable.value.servers.filterNot { it.uri == uri }
        val selected = mutable.value.selectedUri.takeIf { it != uri } ?: remaining.firstOrNull()?.uri
        commit(ServerList(remaining, selected))
    }

    private fun commit(list: ServerList) {
        mutable.value = list
        prefs.edit()
            .putString(KEY_URIS, list.servers.joinToString("\n") { it.uri })
            .putString(KEY_SELECTED, list.selectedUri)
            .apply()
    }

    private fun load(): ServerList {
        val servers = prefs.getString(KEY_URIS, "").orEmpty()
            .lineSequence().filter { it.isNotBlank() }
            .mapNotNull { ConfigParser.parse(it).getOrNull() }
            .toList()
        return ServerList(servers, prefs.getString(KEY_SELECTED, null))
    }

    private companion object {
        const val KEY_URIS = "uris"
        const val KEY_SELECTED = "selected"
    }
}
