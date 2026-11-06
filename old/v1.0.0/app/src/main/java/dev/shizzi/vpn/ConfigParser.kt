package dev.shizzi.vpn

import java.util.Base64

/**
 * Parses share links (vless://, vmess://, trojan://, ss://) into [ProxyConfig].
 *
 * Hand-rolled instead of java.net.URI on purpose: share links routinely carry
 * spaces, emoji and unescaped characters in the #name fragment, which URI
 * rejects outright.
 */
object ConfigParser {

    fun parse(raw: String): Result<ProxyConfig> = runCatching {
        val text = raw.trim()
        require(text.isNotEmpty()) { "empty link" }

        val scheme = text.substringBefore("://", missingDelimiterValue = "").lowercase()
        when (scheme) {
            "vless" -> parseVless(text)
            "trojan" -> parseTrojan(text)
            "vmess" -> parseVmess(text)
            "ss" -> parseShadowsocks(text)
            "" -> throw IllegalArgumentException("not a share link (no scheme)")
            else -> throw IllegalArgumentException("unsupported protocol: $scheme")
        }
    }

    /**
     * Accepts one link, several links separated by whitespace, or a base64
     * subscription body. Every non-blank entry yields a result, so the caller can
     * report how many failed rather than silently dropping them.
     */
    fun parseAll(text: String): List<Result<ProxyConfig>> {
        val body = text.trim()
        if (body.isEmpty()) return emptyList()

        val lines = if (body.contains("://")) body else decodeBase64(body)?.takeIf { it.contains("://") } ?: body
        return lines.split('\n', '\r', ' ', '\t').filter { it.isNotBlank() }.map { parse(it) }
    }

    // ---- VLESS ----------------------------------------------------------

    private fun parseVless(text: String): ProxyConfig {
        val u = splitUri(text)
        val id = u.userInfo.orEmpty()
        require(id.isNotEmpty()) { "vless link has no user id" }

        val q = u.query
        val security = (q["security"] ?: "none").lowercase()
        require(security in setOf("none", "tls", "reality")) { "unsupported vless security: $security" }

        return ProxyConfig(
            protocol = ProxyProtocol.VLESS,
            name = u.fragment.ifBlank { "${u.host}:${u.port}" },
            host = u.host,
            port = u.port,
            secret = id,
            flow = q["flow"].orEmpty(),
            encryption = q["encryption"]?.ifBlank { null } ?: "none",
            security = security,
            uri = text,
        ).withTransport(q, security)
    }

    // ---- Trojan ---------------------------------------------------------

    private fun parseTrojan(text: String): ProxyConfig {
        val u = splitUri(text)
        val password = u.userInfo.orEmpty()
        require(password.isNotEmpty()) { "trojan link has no password" }

        val q = u.query
        // Trojan is TLS by default; "none" has to be asked for explicitly.
        val security = (q["security"] ?: "tls").lowercase()
        require(security in setOf("none", "tls", "reality")) { "unsupported trojan security: $security" }

        return ProxyConfig(
            protocol = ProxyProtocol.TROJAN,
            name = u.fragment.ifBlank { "${u.host}:${u.port}" },
            host = u.host,
            port = u.port,
            secret = password,
            security = security,
            uri = text,
        ).withTransport(q, security)
    }

    // ---- VMess ----------------------------------------------------------

    private fun parseVmess(text: String): ProxyConfig {
        val body = text.substringAfter("://")

        // Standard form: base64 of a JSON object (v2rayN).
        val decoded = decodeBase64(body.substringBefore('#'))
        if (decoded != null && decoded.trimStart().startsWith("{")) return parseVmessJson(text, decoded)

        // Rarer URI form: vmess://uuid@host:port?type=ws&...
        if (body.contains('@')) return parseVmessUri(text)

        throw IllegalArgumentException("vmess link is neither base64 JSON nor a URI")
    }

    private fun parseVmessJson(text: String, json: String): ProxyConfig {
        val o = MiniJson.parseObject(json)

        fun str(key: String) = o[key]?.toString()?.trim().orEmpty()

        val host = str("add")
        val port = str("port").toDoubleOrNull()?.toInt() ?: throw IllegalArgumentException("vmess port missing")
        require(host.isNotEmpty()) { "vmess address missing" }
        requirePort(port)
        val id = str("id")
        require(id.isNotEmpty()) { "vmess id missing" }

        val security = if (str("tls").equals("tls", ignoreCase = true)) "tls" else "none"
        val q = mapOf(
            "type" to str("net").ifBlank { "tcp" },
            "headertype" to str("type"),
            "host" to str("host"),
            "path" to str("path"),
            "sni" to str("sni"),
            "alpn" to str("alpn"),
            "fp" to str("fp"),
            // gRPC reuses `path` as the service name in v2rayN's format.
            "servicename" to str("path"),
        )

        return ProxyConfig(
            protocol = ProxyProtocol.VMESS,
            name = str("ps").ifBlank { "$host:$port" },
            host = host,
            port = port,
            secret = id,
            method = str("scy").ifBlank { "auto" },
            security = security,
            uri = text,
        ).withTransport(q, security)
    }

    private fun parseVmessUri(text: String): ProxyConfig {
        val u = splitUri(text)
        val id = u.userInfo.orEmpty()
        require(id.isNotEmpty()) { "vmess link has no user id" }

        val q = u.query
        val security = (q["security"] ?: "none").lowercase()
        require(security in setOf("none", "tls")) { "unsupported vmess security: $security" }

        return ProxyConfig(
            protocol = ProxyProtocol.VMESS,
            name = u.fragment.ifBlank { "${u.host}:${u.port}" },
            host = u.host,
            port = u.port,
            secret = id,
            method = q["encryption"]?.ifBlank { null } ?: "auto",
            security = security,
            uri = text,
        ).withTransport(q, security)
    }

    // ---- Shadowsocks ----------------------------------------------------

    private fun parseShadowsocks(text: String): ProxyConfig {
        var rest = text.substringAfter("://")
        val fragment = percentDecode(rest.substringAfter('#', ""))
        rest = rest.substringBefore('#')

        val query = parseQuery(rest.substringAfter('?', ""))
        require("plugin" !in query) { "shadowsocks plugins are not supported" }
        rest = rest.substringBefore('?').trimEnd('/')

        val method: String
        val password: String
        val host: String
        val port: Int

        if (rest.contains('@')) {
            // SIP002: userinfo@host:port, userinfo = base64(method:password) or plain.
            val userInfo = rest.substringBeforeLast('@')
            val (h, p) = splitHostPort(rest.substringAfterLast('@'))
            val credentials = decodeBase64(userInfo)?.takeIf { it.contains(':') } ?: percentDecode(userInfo)
            require(credentials.contains(':')) { "shadowsocks credentials are not method:password" }
            method = credentials.substringBefore(':')
            password = credentials.substringAfter(':')
            host = h
            port = p
        } else {
            // Legacy: base64(method:password@host:port).
            val decoded = decodeBase64(rest) ?: throw IllegalArgumentException("shadowsocks link is not valid base64")
            require(decoded.contains('@') && decoded.contains(':')) { "legacy shadowsocks link is malformed" }
            val credentials = decoded.substringBeforeLast('@')
            val (h, p) = splitHostPort(decoded.substringAfterLast('@'))
            method = credentials.substringBefore(':')
            password = credentials.substringAfter(':')
            host = h
            port = p
        }

        require(method.isNotEmpty() && password.isNotEmpty()) { "shadowsocks method or password is empty" }
        requirePort(port)

        return ProxyConfig(
            protocol = ProxyProtocol.SHADOWSOCKS,
            name = fragment.ifBlank { "$host:$port" },
            host = host,
            port = port,
            secret = password,
            method = method,
            uri = text,
        )
    }

    // ---- shared pieces --------------------------------------------------

    private val supportedNetworks = setOf("tcp", "ws", "grpc", "httpupgrade", "xhttp")

    /** Reads the transport and TLS/Reality parameters every URI-style link shares. */
    private fun ProxyConfig.withTransport(q: Map<String, String>, security: String): ProxyConfig {
        val network = when (val raw = (q["type"] ?: "tcp").lowercase().ifBlank { "tcp" }) {
            "raw" -> "tcp"
            "splithttp" -> "xhttp"
            else -> raw
        }
        // The HTTP/2 transport ("h2"/"http") and QUIC were removed from current
        // Xray cores, so a link that needs them cannot work here. Say so plainly
        // rather than failing later with an opaque core error.
        require(network != "h2" && network != "http" && network != "quic") {
            "the $network transport is no longer supported by Xray; ask the provider for XHTTP, WebSocket or gRPC"
        }
        require(network in supportedNetworks) { "unsupported transport: $network" }

        if (security == "reality") {
            require(!q["pbk"].isNullOrBlank()) { "reality link has no public key (pbk)" }
        }

        return copy(
            network = network,
            security = security,
            sni = (q["sni"] ?: q["peer"]).orEmpty(),
            alpn = q["alpn"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
            fingerprint = q["fp"].orEmpty(),
            allowInsecure = q["allowinsecure"] == "1" || q["allowinsecure"].equals("true", ignoreCase = true) ||
                q["insecure"] == "1",
            publicKey = q["pbk"].orEmpty(),
            shortId = q["sid"].orEmpty(),
            spiderX = q["spx"].orEmpty(),
            path = q["path"].orEmpty(),
            hostHeader = q["host"].orEmpty(),
            serviceName = q["servicename"].orEmpty(),
            mode = q["mode"].orEmpty(),
            headerType = q["headertype"]?.ifBlank { null } ?: "none",
        )
    }

    private class ParsedUri(
        val userInfo: String?,
        val host: String,
        val port: Int,
        val query: Map<String, String>,
        val fragment: String,
    )

    private fun splitUri(text: String): ParsedUri {
        var rest = text.substringAfter("://")

        val fragment = percentDecode(rest.substringAfter('#', ""))
        rest = rest.substringBefore('#')

        val query = parseQuery(rest.substringAfter('?', ""))
        rest = rest.substringBefore('?')

        // The authority ends at the first '/', the rest is a path nobody here uses.
        val authority = rest.substringBefore('/')

        val userInfo = if (authority.contains('@')) percentDecode(authority.substringBeforeLast('@')) else null
        val (host, port) = splitHostPort(authority.substringAfterLast('@'))

        return ParsedUri(userInfo, host, port, query, fragment)
    }

    private fun splitHostPort(hostPort: String): Pair<String, Int> {
        val host: String
        val portText: String

        if (hostPort.startsWith("[")) {
            val close = hostPort.indexOf(']')
            require(close > 0) { "unterminated IPv6 address" }
            host = hostPort.substring(1, close)
            portText = hostPort.substring(close + 1).removePrefix(":")
        } else {
            host = hostPort.substringBeforeLast(':', hostPort)
            portText = if (hostPort.contains(':')) hostPort.substringAfterLast(':') else ""
        }

        require(host.isNotEmpty()) { "address missing" }
        val port = portText.toIntOrNull() ?: throw IllegalArgumentException("port missing or not a number")
        requirePort(port)
        return host to port
    }

    private fun requirePort(port: Int) = require(port in 1..65535) { "port out of range: $port" }

    private fun parseQuery(text: String): Map<String, String> {
        if (text.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (pair in text.split('&')) {
            if (pair.isEmpty()) continue
            val key = percentDecode(pair.substringBefore('=')).lowercase()
            out[key] = percentDecode(pair.substringAfter('=', ""))
        }
        return out
    }

    /** Unlike URLDecoder, leaves '+' alone: in a path or Reality key it is a literal plus. */
    internal fun percentDecode(s: String): String {
        if (!s.contains('%')) return s

        val out = StringBuilder(s.length)
        val pending = java.io.ByteArrayOutputStream()

        // Escapes are UTF-8 bytes, so a run of them has to be decoded together;
        // decoding one at a time would mangle any multi-byte character.
        fun flush() {
            if (pending.size() > 0) {
                out.append(String(pending.toByteArray(), Charsets.UTF_8))
                pending.reset()
            }
        }

        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length && s[i + 1].isHexDigit() && s[i + 2].isHexDigit()) {
                pending.write(s.substring(i + 1, i + 3).toInt(16))
                i += 3
            } else {
                flush()
                out.append(c)
                i++
            }
        }
        flush()
        return out.toString()
    }

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    /** Tolerates standard and URL-safe alphabets, with or without padding. */
    internal fun decodeBase64(input: String): String? {
        val cleaned = input.trim().replace("\n", "").replace("\r", "")
        if (cleaned.isEmpty()) return null
        val normalized = cleaned.replace('-', '+').replace('_', '/').trimEnd('=')
        return runCatching {
            val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
            String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
        }.getOrNull()
    }
}
