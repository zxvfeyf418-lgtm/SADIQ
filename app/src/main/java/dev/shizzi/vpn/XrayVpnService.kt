package dev.shizzi.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import dev.shizzi.MainActivity
import datapath.Datapath
import datapath.Protector
import datapath.Session as DatapathSession
import dev.shizzi.SessionLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.InetAddress

/**
 * The 2n5a VPN: a real [VpnService] whose TUN feeds a gVisor netstack in the Go
 * datapath, which dials every flow through an in-process Xray core.
 *
 * Because this creates a genuine TRANSPORT_VPN network, Shizzi's existing
 * "follow VPN" logic (VpnUpstream / VpnWatchdog) finds it with no changes and
 * pins hotspot traffic to it: that is the whole integration between the two.
 */
class XrayVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startJob: Job? = null

    private var tun: ParcelFileDescriptor? = null
    private var datapathSession: DatapathSession? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> shutdown(removeNotification = true)
            else -> begin(intent?.getStringExtra(EXTRA_URI).orEmpty())
        }
        return START_NOT_STICKY
    }

    private fun begin(uri: String) {
        // A foreground service must call startForeground promptly after
        // startForegroundService, so this precedes everything that can fail.
        startForeground(NOTIFICATION_ID, notification("2n5a", "جارٍ الاتصال…"))

        val config = ConfigParser.parse(uri).getOrElse { failure ->
            fail("رابط الخادم غير صالح: ${failure.message}")
            return
        }

        startJob?.cancel()
        mutableState.value = VpnState.Connecting(config)

        startJob = scope.launch {
            runCatching { bringUp(config) }.onFailure { failure ->
                SessionLog.error("vpn start failed: ${failure.javaClass.simpleName}: ${failure.message}")
                teardown()
                fail("تعذّر تشغيل VPN: ${failure.message}")
            }
        }
    }

    private fun bringUp(config: ProxyConfig) {
        teardown()

        // Resolve now, while the physical network is still the only one: once the
        // tunnel is up, the resolver would have to go through the tunnel being built.
        val address = resolve(config.host)

        Datapath.setProtector(Protector { fd -> protect(fd.toInt()) })
        Datapath.startXray(XrayConfigBuilder.build(config, address))

        val descriptor = Builder()
            .setSession("2n5a")
            .setMtu(MTU)
            .addAddress(TUN_ADDRESS_V4, 30)
            .addAddress(TUN_ADDRESS_V6, 126)
            .addRoute("0.0.0.0", 0)
            // IPv6 is routed into the tunnel as well; leaving it out would let
            // IPv6-capable apps bypass the VPN entirely.
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .setMetered(false)
            .establish()
            ?: error("Android لم يمنح صلاحية VPN (أُلغيت الموافقة؟)")

        tun = descriptor
        datapathSession = Datapath.startVpn(descriptor.fd.toLong(), MTU.toLong())

        SessionLog.info("vpn up: ${config.protocol.label} ${config.endpoint} via xray ${Datapath.xrayVersion()}")
        mutableState.value = VpnState.Connected(config, System.currentTimeMillis())
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification("2n5a · VPN", config.name))
    }

    /** IP literals pass through; hostnames resolve to IPv4 first (most servers listen there). */
    private fun resolve(host: String): String? {
        val literal = host.all { it.isDigit() || it == '.' } || host.contains(':')
        if (literal) return null

        val all = InetAddress.getAllByName(host)
        return (all.firstOrNull { it is Inet4Address } ?: all.first()).hostAddress
    }

    private fun teardown() {
        runCatching { datapathSession?.stop() }
        datapathSession = null
        runCatching { Datapath.stopXray() }
        runCatching { tun?.close() }
        tun = null
    }

    private fun shutdown(removeNotification: Boolean) {
        startJob?.cancel()
        teardown()
        mutableState.value = VpnState.Idle
        if (removeNotification) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun fail(message: String) {
        mutableState.value = VpnState.Failed(message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** The user revoked VPN permission, or another VPN took over. */
    override fun onRevoke() {
        shutdown(removeNotification = true)
    }

    override fun onDestroy() {
        startJob?.cancel()
        teardown()
        if (mutableState.value.isActive) mutableState.value = VpnState.Idle
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(title: String, text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "2n5a VPN", NotificationManager.IMPORTANCE_LOW),
        )

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, XrayVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "إيقاف", stop).build())
            .build()
    }

    companion object {
        const val ACTION_STOP = "dev.shizzi.vpn.action.STOP"
        const val EXTRA_URI = "uri"

        private const val CHANNEL_ID = "2n5a_vpn"
        private const val NOTIFICATION_ID = 2501
        private const val MTU = 1500

        // Private link-local-style addresses for the TUN itself; never routed to.
        private const val TUN_ADDRESS_V4 = "172.19.0.1"
        private const val TUN_ADDRESS_V6 = "fdfe:dcba:9876::1"

        private val mutableState = MutableStateFlow<VpnState>(VpnState.Idle)
        val state: StateFlow<VpnState> = mutableState.asStateFlow()

        /** Caller must already hold VPN consent: see [VpnService.prepare]. */
        fun start(context: Context, config: ProxyConfig) {
            context.startForegroundService(
                Intent(context, XrayVpnService::class.java).putExtra(EXTRA_URI, config.uri),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, XrayVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
