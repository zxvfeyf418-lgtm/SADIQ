package dev.shizzi.warp

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.amnezia.awg.backend.BackendException
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.NoopTunnelActionHandler
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.BadConfigException
import org.amnezia.awg.config.Config

/** What the connection card shows. */
sealed interface TunnelStatus {

    object Down : TunnelStatus

    data class Connecting(val name: String) : TunnelStatus

    /** [rxBytes] stays 0 until the server has answered, so it doubles as a handshake signal. */
    data class Up(val name: String, val rxBytes: Long = 0, val txBytes: Long = 0) : TunnelStatus {
        val hasHandshake: Boolean get() = rxBytes > 0
    }

    data class Failed(val message: String) : TunnelStatus
}

/**
 * Owns the AmneziaWG [GoBackend]. It is a process-wide singleton because the backend
 * remembers which tunnel is up: a second instance would not know about the first one's VPN.
 */
class TunnelController private constructor(context: Context) {

    private val appContext = context.applicationContext

    // Creating the backend loads the native library, so it is deferred to the first connect
    // and any failure is reported as a normal connection error instead of crashing at start.
    private val backend by lazy { GoBackend(appContext, NoopTunnelActionHandler()) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val mutableStatus = MutableStateFlow<TunnelStatus>(TunnelStatus.Down)
    val status: StateFlow<TunnelStatus> = mutableStatus.asStateFlow()

    @Volatile private var current: NamedTunnel? = null
    private var statsJob: Job? = null

    /** Brings [name] up from [text], replacing any tunnel that is already running. */
    suspend fun connect(name: String, text: String) {
        lock.withLock { withContext(Dispatchers.IO) { connectLocked(name, text) } }
    }

    suspend fun disconnect() {
        lock.withLock { withContext(Dispatchers.IO) { disconnectLocked() } }
    }

    private fun connectLocked(name: String, text: String) {
        val problem = ConfigSanity.check(text)
        if (problem != null) {
            mutableStatus.value = TunnelStatus.Failed(problemMessage(problem))
            return
        }

        mutableStatus.value = TunnelStatus.Connecting(name)
        stopStats()

        try {
            val config = Config.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
            val tunnel = NamedTunnel(name)

            backend.setState(tunnel, Tunnel.State.UP, config)

            current = tunnel
            mutableStatus.value = TunnelStatus.Up(name)
            startStats(tunnel)
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure

            // The backend restores the previous tunnel when a switch fails.
            val previous = current

            if (previous != null && isUp(previous)) {
                mutableStatus.value = TunnelStatus.Up(previous.label)
                startStats(previous)
            } else {
                mutableStatus.value = TunnelStatus.Failed(failureMessage(failure))
            }
        }
    }

    private fun disconnectLocked() {
        stopStats()
        val tunnel = current

        if (tunnel == null) {
            mutableStatus.value = TunnelStatus.Down
            return
        }

        try {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
            current = null
            mutableStatus.value = TunnelStatus.Down
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            mutableStatus.value = TunnelStatus.Failed(failureMessage(failure))
        }
    }

    /** Lets the UI clear an old error without touching a running tunnel. */
    fun clearFailure() {
        if (mutableStatus.value is TunnelStatus.Failed) mutableStatus.value = TunnelStatus.Down
    }

    fun reportFailure(message: String) {
        if (current == null) mutableStatus.value = TunnelStatus.Failed(message)
    }

    private fun isUp(tunnel: NamedTunnel): Boolean =
        runCatching { backend.getState(tunnel) == Tunnel.State.UP }.getOrDefault(false)

    private fun startStats(tunnel: NamedTunnel) {
        stopStats()
        statsJob = scope.launch {
            while (isActive) {
                delay(STATS_INTERVAL_MILLIS)
                val stats = runCatching { backend.getStatistics(tunnel) }.getOrNull() ?: continue
                val up = mutableStatus.value as? TunnelStatus.Up ?: continue
                if (up.name != tunnel.label) continue

                mutableStatus.value = up.copy(rxBytes = stats.totalRx(), txBytes = stats.totalTx())
            }
        }
    }

    private fun stopStats() {
        statsJob?.cancel()
        statsJob = null
    }

    private inner class NamedTunnel(val label: String) : Tunnel {

        override fun getName(): String = label

        override fun onStateChange(newState: Tunnel.State) {
            // Only an outside stop (the system revoking the VPN, say) of the tunnel that is
            // still current matters; switching tunnels also reports DOWN for the old one.
            if (newState != Tunnel.State.DOWN || this !== current) return
            if (lock.isLocked) return

            current = null
            stopStats()
            mutableStatus.value = TunnelStatus.Down
        }

        override fun isIpv4ResolutionPreferred(): Boolean = true

        override fun isMetered(): Boolean = false
    }

    companion object {

        private const val STATS_INTERVAL_MILLIS = 2_000L

        @Volatile private var instance: TunnelController? = null

        fun get(context: Context): TunnelController =
            instance ?: synchronized(this) {
                instance ?: TunnelController(context).also { instance = it }
            }

        fun problemMessage(problem: ConfigProblem): String = when (problem) {
            ConfigProblem.EMPTY -> "الملف فارغ."
            ConfigProblem.TOO_LARGE -> "الملف كبير بشكل غير طبيعي؛ ما يبدو ملف اتصال."
            ConfigProblem.NOT_A_CONFIG -> "هذا مو ملف اتصال: ما لقينا قسم [Interface]."
            ConfigProblem.NO_PRIVATE_KEY -> "الملف ناقص: ما فيه PrivateKey."
            ConfigProblem.NO_PEER -> "الملف ناقص: ما فيه قسم [Peer]."
        }

        fun failureMessage(failure: Throwable): String = when (failure) {
            is BadConfigException -> "ملف الإعداد غير صالح أو فيه سطر ما نفهمه. تأكد من الملف أو أنشئ ملف جديد."
            is BackendException -> when (failure.reason) {
                BackendException.Reason.VPN_NOT_AUTHORIZED ->
                    "ما انمنحت صلاحية الـ VPN. اضغط تشغيل ووافق على الطلب."
                BackendException.Reason.UNABLE_TO_START_VPN,
                BackendException.Reason.SERVICE_NOT_RUNNING ->
                    "تعذّر تشغيل خدمة الـ VPN. جرّب مرة ثانية."
                BackendException.Reason.TUN_CREATION_ERROR ->
                    "تعذّر إنشاء واجهة الـ VPN. تأكد أن تطبيق VPN ثاني مو شغّال."
                BackendException.Reason.DNS_RESOLUTION_FAILURE ->
                    "ما قدرنا نحوّل عنوان السيرفر. تأكد من اتصال الإنترنت."
                BackendException.Reason.GO_ACTIVATION_ERROR_CODE ->
                    "فشل تشغيل النفق. إذا كان الملف AmneziaWG جرّب ملف WireGuard عادي."
                else -> "فشل تشغيل الاتصال. تأكد من الملف وجرّب مرة ثانية."
            }
            is IOException -> "تعذّر قراءة الملف."
            is UnsatisfiedLinkError -> "مكتبة الاتصال غير متوفرة على هذا الجهاز."
            else -> "صار خطأ غير متوقع في الاتصال. جرّب مرة ثانية."
        }
    }
}
