package dev.shizzi.vpn

/**
 * Turns a [ProxyConfig] into the JSON the embedded Xray core boots from.
 *
 * There is deliberately no inbound: traffic reaches Xray from the Go datapath
 * (core.Dial on the VPN's TUN), not from a local SOCKS port, so nothing listens
 * on the device and no other app can use the tunnel.
 */
object XrayConfigBuilder {

    const val PROXY_TAG = "proxy"
    const val DIRECT_TAG = "direct"
    const val BLOCK_TAG = "block"

    /** Ranges that never belong on the proxy. */
    private val localRanges = listOf(
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16", "224.0.0.0/4",
        "fc00::/7", "fe80::/10",
    )

    /**
     * @param connectAddress an IP to dial instead of [ProxyConfig.host]. The service resolves the
     * host *before* the VPN comes up, because afterwards the only resolver in reach is the one
     * that would have to go through the tunnel being built. TLS server name and HTTP Host keep
     * the original hostname so certificates and virtual hosts still match.
     */
    fun build(config: ProxyConfig, connectAddress: String? = null, logLevel: String = "warning"): String {
        val root = linkedMapOf<String, Any?>(
            "log" to mapOf("loglevel" to logLevel),
            "dns" to mapOf("servers" to listOf("1.1.1.1", "8.8.8.8")),
            "outbounds" to listOf(
                proxyOutbound(config, connectAddress ?: config.host),
                mapOf("protocol" to "freedom", "tag" to DIRECT_TAG),
                mapOf("protocol" to "blackhole", "tag" to BLOCK_TAG),
            ),
            "routing" to mapOf(
                "domainStrategy" to "AsIs",
                "rules" to listOf(
                    mapOf("type" to "field", "ip" to localRanges, "outboundTag" to DIRECT_TAG),
                ),
            ),
        )
        return MiniJson.stringify(root)
    }

    private fun proxyOutbound(c: ProxyConfig, address: String): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>("tag" to PROXY_TAG)

        when (c.protocol) {
            ProxyProtocol.VLESS -> {
                out["protocol"] = "vless"
                out["settings"] = mapOf(
                    "vnext" to listOf(
                        mapOf(
                            "address" to address,
                            "port" to c.port,
                            "users" to listOf(
                                linkedMapOf<String, Any?>(
                                    "id" to c.secret,
                                    "encryption" to c.encryption.ifBlank { "none" },
                                ).also { if (c.flow.isNotBlank()) it["flow"] = c.flow },
                            ),
                        ),
                    ),
                )
            }

            ProxyProtocol.VMESS -> {
                out["protocol"] = "vmess"
                out["settings"] = mapOf(
                    "vnext" to listOf(
                        mapOf(
                            "address" to address,
                            "port" to c.port,
                            "users" to listOf(
                                mapOf(
                                    "id" to c.secret,
                                    "security" to c.method.ifBlank { "auto" },
                                ),
                            ),
                        ),
                    ),
                )
            }

            ProxyProtocol.TROJAN -> {
                out["protocol"] = "trojan"
                out["settings"] = mapOf(
                    "servers" to listOf(
                        mapOf("address" to address, "port" to c.port, "password" to c.secret),
                    ),
                )
            }

            ProxyProtocol.SHADOWSOCKS -> {
                out["protocol"] = "shadowsocks"
                out["settings"] = mapOf(
                    "servers" to listOf(
                        mapOf(
                            "address" to address,
                            "port" to c.port,
                            "method" to c.method,
                            "password" to c.secret,
                        ),
                    ),
                )
            }
        }

        if (c.protocol != ProxyProtocol.SHADOWSOCKS) {
            out["streamSettings"] = streamSettings(c)
        }
        return out
    }

    private fun streamSettings(c: ProxyConfig): Map<String, Any?> {
        val stream = linkedMapOf<String, Any?>(
            "network" to c.network,
            "security" to c.security,
        )

        // When the address was swapped for an IP, the real hostname still has to
        // reach the TLS handshake and the HTTP Host header.
        val serverName = c.sni.ifBlank { c.hostHeader.ifBlank { c.host } }
        val hostHeader = c.hostHeader.ifBlank { c.host }

        when (c.security) {
            "tls" -> stream["tlsSettings"] = linkedMapOf<String, Any?>("serverName" to serverName).also {
                if (c.alpn.isNotEmpty()) it["alpn"] = c.alpn
                if (c.fingerprint.isNotBlank()) it["fingerprint"] = c.fingerprint
                if (c.allowInsecure) it["allowInsecure"] = true
            }

            "reality" -> stream["realitySettings"] = linkedMapOf<String, Any?>(
                "serverName" to serverName,
                // Reality requires a fingerprint; Chrome is what servers expect to see.
                "fingerprint" to c.fingerprint.ifBlank { "chrome" },
                "publicKey" to c.publicKey,
                "shortId" to c.shortId,
            ).also { if (c.spiderX.isNotBlank()) it["spiderX"] = c.spiderX }
        }

        val path = c.path.ifBlank { "/" }
        when (c.network) {
            "ws" -> stream["wsSettings"] = mapOf("path" to path, "headers" to mapOf("Host" to hostHeader))

            "grpc" -> stream["grpcSettings"] = mapOf(
                "serviceName" to c.serviceName,
                "multiMode" to (c.mode == "multi"),
            )

            "httpupgrade" -> stream["httpupgradeSettings"] = mapOf("path" to path, "host" to hostHeader)

            "xhttp" -> stream["xhttpSettings"] = mapOf(
                "path" to path,
                "host" to hostHeader,
                "mode" to c.mode.ifBlank { "auto" },
            )

            "tcp" -> if (c.headerType == "http") {
                stream["tcpSettings"] = mapOf(
                    "header" to mapOf(
                        "type" to "http",
                        "request" to mapOf(
                            "path" to listOf(path),
                            "headers" to mapOf("Host" to listOf(hostHeader)),
                        ),
                    ),
                )
            }
        }
        return stream
    }
}
