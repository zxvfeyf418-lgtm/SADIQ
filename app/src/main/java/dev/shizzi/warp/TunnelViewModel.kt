package dev.shizzi.warp

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the «الاتصال» card needs. */
data class ConnectionUiState(
    val configs: List<SavedConfig> = emptyList(),
    val selected: String? = null,
    val status: TunnelStatus = TunnelStatus.Down,
    val message: String? = null,
) {
    val selectedConfig: SavedConfig? get() = configs.firstOrNull { it.name == selected }
}

class TunnelViewModel(application: Application) : AndroidViewModel(application) {

    private val store = ConfigStore(File(application.filesDir, "tunnels"))
    private val controller = TunnelController.get(application)
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private data class Local(
        val configs: List<SavedConfig> = emptyList(),
        val selected: String? = null,
        val message: String? = null,
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<ConnectionUiState> =
        combine(local, controller.status) { current, status ->
            ConnectionUiState(current.configs, current.selected, status, current.message)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionUiState())

    init {
        viewModelScope.launch {
            val configs = withContext(Dispatchers.IO) { store.list() }
            val saved = prefs.getString(KEY_SELECTED, null)
            local.update {
                it.copy(configs = configs, selected = pick(configs, saved))
            }
        }
    }

    fun select(name: String) {
        prefs.edit().putString(KEY_SELECTED, name).apply()
        local.update { it.copy(selected = name, message = null) }
    }

    fun delete(name: String) {
        val status = controller.status.value
        val isActive = (status as? TunnelStatus.Up)?.name == name ||
            (status as? TunnelStatus.Connecting)?.name == name

        if (isActive) {
            showMessage("أوقف الاتصال أولاً وبعدها احذف الملف.")
            return
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(name) }
            refresh(message = "تم حذف الملف.")
        }
    }

    /** Reads a .conf picked from the device and saves it under a clean, unique name. */
    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { runCatching { readAndSave(uri) } }
            val saved = outcome.getOrNull()

            when {
                saved is Imported.Ok -> {
                    prefs.edit().putString(KEY_SELECTED, saved.config.name).apply()
                    refresh(selected = saved.config.name, message = "تم استيراد الملف: ${saved.config.name}")
                }
                saved is Imported.Rejected -> showMessage(TunnelController.problemMessage(saved.problem))
                else -> showMessage("تعذّر قراءة الملف. جرّب ملف ثاني.")
            }
        }
    }

    /** Saves a freshly generated profile (replacing the same profile saved earlier) and selects it. */
    fun saveGenerated(rawName: String, text: String, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                store.save(TunnelNames.sanitize(rawName), text)
            }
            prefs.edit().putString(KEY_SELECTED, saved.name).apply()
            refresh(selected = saved.name, message = null)
            onSaved(saved.name)
        }
    }

    fun connect(name: String) {
        val config = state.value.configs.firstOrNull { it.name == name }

        if (config == null) {
            showMessage("ما لقينا الملف. اختر ملف من القائمة.")
            return
        }

        local.update { it.copy(message = null) }
        viewModelScope.launch { controller.connect(config.name, config.text) }
    }

    fun disconnect() {
        viewModelScope.launch { controller.disconnect() }
    }

    fun onVpnPermissionDenied() {
        controller.reportFailure("ما انمنحت صلاحية الـ VPN. اضغط تشغيل ووافق على الطلب.")
    }

    fun dismissMessage() {
        local.update { it.copy(message = null) }
        controller.clearFailure()
    }

    private sealed interface Imported {
        data class Ok(val config: SavedConfig) : Imported
        data class Rejected(val problem: ConfigProblem) : Imported
    }

    private fun readAndSave(uri: Uri): Imported {
        val resolver = getApplication<Application>().contentResolver

        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.lastPathSegment
            ?: ""

        val bytes = resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(ConfigSanity.MAX_BYTES + 1)
            var length = 0
            while (length < buffer.size) {
                val read = input.read(buffer, length, buffer.size - length)
                if (read < 0) break
                length += read
            }
            buffer.copyOf(length)
        } ?: error("could not open the file")

        val text = String(bytes, Charsets.UTF_8).removePrefix("﻿")
        val problem = ConfigSanity.check(text)
        if (problem != null) return Imported.Rejected(problem)

        return Imported.Ok(store.saveAsNew(displayName, text))
    }

    private suspend fun refresh(selected: String? = null, message: String?) {
        val configs = withContext(Dispatchers.IO) { store.list() }

        local.update {
            val wanted = selected ?: it.selected
            it.copy(configs = configs, selected = pick(configs, wanted), message = message)
        }
    }

    private fun showMessage(message: String) = local.update { it.copy(message = message) }

    private fun pick(configs: List<SavedConfig>, wanted: String?): String? =
        configs.firstOrNull { it.name == wanted }?.name ?: configs.firstOrNull()?.name

    private companion object {
        const val PREFS = "tunnels"
        const val KEY_SELECTED = "selected"
    }
}
