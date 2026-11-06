package dev.shizzi.vpn

/** Everything the 2n5a screens need to know about the VPN side, in one value. */
data class VpnUiState(
    val servers: ServerList = ServerList(),
    val latency: Map<String, Int?> = emptyMap(),
    val state: VpnState = VpnState.Idle,
    val feedback: SaveFeedback? = null,
)

data class VpnActions(
    val onToggle: () -> Unit,
    val onSave: (String) -> Unit,
    val onSelect: (ProxyConfig) -> Unit,
    val onRemove: (ProxyConfig) -> Unit,
    val onTestAll: () -> Unit,
    val onClearFeedback: () -> Unit,
)
