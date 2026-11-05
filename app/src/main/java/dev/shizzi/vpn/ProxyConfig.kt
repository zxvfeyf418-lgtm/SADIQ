package dev.shizzi.vpn

enum class ProxyProtocol(val label: String) {
    VLESS("VLESS"),
    VMESS("VMess"),
    TROJAN("Trojan"),
    SHADOWSOCKS("SS"),
}

/**
 * One parsed server, independent of how it was shared. [uri] is the original
 * share link and is what gets persisted, so a parser improvement later re-reads
 * old servers correctly.
 */
data class ProxyConfig(
    val protocol: ProxyProtocol,
    val name: String,
    val host: String,
    val port: Int,

    /** UUID for VLESS/VMess; password for Trojan/Shadowsocks. */
    val secret: String,

    /** Shadowsocks method, or VMess `scy` (defaults to "auto"). */
    val method: String = "",

    /** VLESS only. */
    val flow: String = "",
    val encryption: String = "none",

    /** tcp | ws | grpc | httpupgrade | xhttp */
    val network: String = "tcp",

    /** none | tls | reality */
    val security: String = "none",

    val sni: String = "",
    val alpn: List<String> = emptyList(),
    val fingerprint: String = "",
    val allowInsecure: Boolean = false,

    /** Reality. */
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",

    /** Transport details. */
    val path: String = "",
    val hostHeader: String = "",
    val serviceName: String = "",
    val mode: String = "",
    val headerType: String = "none",

    val uri: String = "",
) {
    /** "host:port" as shown under the server name in the UI. */
    val endpoint: String
        get() = if (host.contains(':')) "[$host]:$port" else "$host:$port"
}
