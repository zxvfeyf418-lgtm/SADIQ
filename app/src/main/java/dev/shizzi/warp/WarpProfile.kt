package dev.shizzi.warp

/** Constants shared by every generated profile. */
object WarpDefaults {

    /** Cloudflare WARP's published WireGuard public key. */
    const val PEER_PUBLIC_KEY = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="

    const val DNS = "1.1.1.1, 1.0.0.1, 2606:4700:4700::1111, 2606:4700:4700::1001"

    const val MTU = 1280

    const val KEEPALIVE_SECONDS = 3

    // AmneziaWG junk-packet parameters: Jc packets of Jmin..Jmax bytes before the handshake.
    const val JUNK_COUNT = 6
    const val JUNK_MIN = 10
    const val JUNK_MAX = 50

    /** Special junk packet I1: a QUIC-Initial-shaped decoy that is sent before the handshake. */
    const val SPECIAL_JUNK =
        "<b 0xc30000000101fd0000405a4a66447d026dd1b6e98c1c6da64ddf571be6a41d><r 23>" +
            "<b 0xaeb453fdaf400f63b53f4cba15edfdc3fd1bdc8617de3e567e1a9a1c8385a1><r 16>"

    /** Cloudflare WARP endpoints; the first is the default. */
    val ENDPOINTS: List<String> = listOf(
        "188.114.99.89:4500",
        "188.114.98.120:2408",
        "188.114.99.254:2408",
        "188.114.98.160:500",
        "162.159.192.17:1701",
    )
}

/** A registered WARP device, ready to be written out as a tunnel config. */
data class WarpProfile(
    val name: String,
    val privateKey: String,
    val publicKey: String,
    val peerPublicKey: String,
    val addressV4: String,
    val addressV6: String?,
    val endpoint: String = WarpDefaults.ENDPOINTS.first(),
    val city: String? = null,
) {

    val addresses: String
        get() = listOfNotNull("$addressV4/32", addressV6?.let { "$it/128" }).joinToString(", ")

    /**
     * The tunnel config. With [obfuscated] it carries the AmneziaWG junk-packet
     * lines, which only AmneziaWG clients understand; without it the file is plain
     * WireGuard and loads in any WireGuard app.
     */
    fun toConfig(obfuscated: Boolean = true): String = buildString {
        appendLine("[Interface]")
        appendLine("PrivateKey = $privateKey")
        appendLine("Address = $addresses")
        appendLine("DNS = ${WarpDefaults.DNS}")
        appendLine("MTU = ${WarpDefaults.MTU}")
        if (obfuscated) {
            appendLine("Jc = ${WarpDefaults.JUNK_COUNT}")
            appendLine("Jmin = ${WarpDefaults.JUNK_MIN}")
            appendLine("Jmax = ${WarpDefaults.JUNK_MAX}")
            appendLine("I1 = ${WarpDefaults.SPECIAL_JUNK}")
        }
        appendLine()
        appendLine("[Peer]")
        appendLine("PublicKey = $peerPublicKey")
        appendLine("AllowedIPs = 0.0.0.0/0, ::/0")
        appendLine("Endpoint = $endpoint")
        appendLine("PersistentKeepalive = ${WarpDefaults.KEEPALIVE_SECONDS}")
    }

    fun fileName(obfuscated: Boolean = true): String =
        if (obfuscated) "$name.conf" else "$name-WG.conf"

    fun withEndpoint(index: Int): WarpProfile =
        copy(endpoint = WarpDefaults.ENDPOINTS[index.coerceIn(0, WarpDefaults.ENDPOINTS.lastIndex)])
}
