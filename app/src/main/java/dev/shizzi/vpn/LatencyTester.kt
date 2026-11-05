package dev.shizzi.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * TCP connect time to the server, in milliseconds, or null if unreachable.
 *
 * It measures the path to the server's port, not a full proxy handshake, so it
 * tells you which servers are reachable and roughly how far away they are, not
 * whether the credentials work. While the VPN is up the probe goes through the
 * tunnel, so the figure then includes the tunnel's own overhead.
 */
object LatencyTester {

    suspend fun tcpPing(config: ProxyConfig, timeoutMillis: Int = 3000): Int? = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                val start = System.nanoTime()
                socket.connect(InetSocketAddress(config.host, config.port), timeoutMillis)
                ((System.nanoTime() - start) / 1_000_000).toInt().coerceAtLeast(1)
            }
        }.getOrNull()
    }
}
