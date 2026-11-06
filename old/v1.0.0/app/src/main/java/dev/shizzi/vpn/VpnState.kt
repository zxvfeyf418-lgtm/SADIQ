package dev.shizzi.vpn

/** What the tunnel is doing right now, observed by the UI. */
sealed interface VpnState {
    data object Idle : VpnState
    data class Connecting(val server: ProxyConfig) : VpnState
    data class Connected(val server: ProxyConfig, val sinceMillis: Long) : VpnState
    data class Failed(val message: String) : VpnState
}

val VpnState.isActive: Boolean get() = this is VpnState.Connecting || this is VpnState.Connected
