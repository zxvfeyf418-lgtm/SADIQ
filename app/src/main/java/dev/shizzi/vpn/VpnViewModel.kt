package dev.shizzi.vpn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.shizzi.App
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the settings card shows after pressing "حفظ الكود". */
sealed interface SaveFeedback {
    data class Saved(val added: Int, val duplicates: Int, val failed: Int) : SaveFeedback
    data class Invalid(val reason: String) : SaveFeedback
}

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ServerRepository = getApplication<App>().serverRepository

    val servers: StateFlow<ServerList> = repository.state
    val vpn: StateFlow<VpnState> = XrayVpnService.state

    private val mutableLatency = MutableStateFlow<Map<String, Int?>>(emptyMap())

    /** uri -> milliseconds, or null once a probe found the server unreachable. */
    val latency: StateFlow<Map<String, Int?>> = mutableLatency.asStateFlow()

    private val mutableFeedback = MutableStateFlow<SaveFeedback?>(null)
    val feedback: StateFlow<SaveFeedback?> = mutableFeedback.asStateFlow()

    fun save(text: String) {
        val outcome = repository.save(text)
        mutableFeedback.value = when {
            outcome.added == 0 && outcome.duplicates == 0 ->
                SaveFeedback.Invalid(outcome.failures.firstOrNull() ?: "لا يوجد رابط في النص")
            else -> SaveFeedback.Saved(outcome.added, outcome.duplicates, outcome.failures.size)
        }
        if (outcome.added > 0) testAll()
    }

    fun clearFeedback() {
        mutableFeedback.value = null
    }

    fun select(config: ProxyConfig) = repository.select(config.uri)

    fun remove(config: ProxyConfig) {
        repository.remove(config.uri)
        mutableLatency.update { it - config.uri }
    }

    fun testAll() {
        val list = repository.state.value.servers
        viewModelScope.launch {
            list.map { config ->
                async { mutableLatency.update { it + (config.uri to LatencyTester.tcpPing(config)) } }
            }.awaitAll()
        }
    }
}
