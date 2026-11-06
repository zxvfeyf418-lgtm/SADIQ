package dev.shizzi.warp

import android.app.Application
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface WarpUiState {

    object Idle : WarpUiState

    data class Working(val step: WarpStep) : WarpUiState

    data class Done(
        val profile: WarpProfile,
        val isObfuscated: Boolean = true,
        val endpointIndex: Int = 0,
        val message: String? = null,
    ) : WarpUiState

    data class Failed(val reason: String) : WarpUiState
}

class WarpViewModel(application: Application) : AndroidViewModel(application) {

    private val provisioner = WarpProvisioner(UrlConnectionWarpHttp())

    private val localState = MutableStateFlow<WarpUiState>(WarpUiState.Idle)
    val state: StateFlow<WarpUiState> = localState.asStateFlow()

    fun generate() {
        if (localState.value is WarpUiState.Working) return

        localState.value = WarpUiState.Working(WarpStep.GENERATING_KEYS)

        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    provisioner.provision { step -> localState.value = WarpUiState.Working(step) }
                }
            }

            localState.value = outcome.fold(
                onSuccess = { WarpUiState.Done(it) },
                onFailure = { WarpUiState.Failed(it.message ?: "صار خطأ غير متوقع. حاول مرة ثانية.") },
            )
        }
    }

    fun setObfuscated(isObfuscated: Boolean) = updateDone { it.copy(isObfuscated = isObfuscated, message = null) }

    fun selectEndpoint(index: Int) = updateDone {
        it.copy(
            profile = it.profile.withEndpoint(index),
            endpointIndex = index.coerceIn(0, WarpDefaults.ENDPOINTS.lastIndex),
            message = null,
        )
    }

    fun showMessage(message: String) = updateDone { it.copy(message = message) }

    fun dismissMessage() = updateDone { it.copy(message = null) }

    /** Writes the config into the public Downloads folder, where any VPN app can pick it up. */
    fun saveToDownloads() {
        val done = localState.value as? WarpUiState.Done ?: return
        val fileName = done.profile.fileName(done.isObfuscated)
        val text = done.profile.toConfig(done.isObfuscated)

        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching { writeToDownloads(fileName, text) } }

            showMessage(
                if (saved.isSuccess) "تم حفظ الملف في مجلد التنزيلات: $fileName"
                else "تعذّر حفظ الملف. جرّب المشاركة أو النسخ بدلها.",
            )
        }
    }

    private fun writeToDownloads(fileName: String, text: String) {
        val resolver = getApplication<Application>().contentResolver

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            // text/plain is what Android maps ".conf" to, so the name is kept as written.
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("could not create the download entry")

        try {
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: error("could not open the download entry")

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (failure: Exception) {
            resolver.delete(uri, null, null)
            throw failure
        }
    }

    private fun updateDone(change: (WarpUiState.Done) -> WarpUiState.Done) {
        localState.update { if (it is WarpUiState.Done) change(it) else it }
    }
}
