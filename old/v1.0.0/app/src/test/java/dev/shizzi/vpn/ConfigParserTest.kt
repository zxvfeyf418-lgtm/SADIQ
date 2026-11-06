package dev.shizzi.vpn

import java.util.Base64
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser and Xray-config checks. Pure JVM: ConfigParser, XrayConfigBuilder and
 * MiniJson deliberately avoid Android and org.json so this runs without a device.
 */
class ConfigParserTest {

    private fun check(name: String, condition: Boolean, detail: String = "") =
        assertTrue("$name $detail".trim(), condition)

    private fun ok(link: String): ProxyConfig =
        ConfigParser.parse(link).getOrElse { throw AssertionError("parse failed for $link: ${it.message}") }

    private fun bad(link: String) = ConfigParser.parse(link).isFailure

    @Test
    fun parsesEveryProtocolAndBuildsValidXrayConfig() {

    // --- VLESS Reality (the shape in the reference screenshot)
    val v = ok("vless://9a8b7c6d-1122-4455-8899-aabbccddeeff@edge.v2rayng.org:443?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.microsoft.com&fp=chrome&pbk=AbC+dEf_123-xyz&sid=6ba85179&spx=%2F&type=tcp&headerType=none#Thmanyah-Fast-Node")
    check("vless: protocol", v.protocol == ProxyProtocol.VLESS)
    check("vless: host/port", v.host == "edge.v2rayng.org" && v.port == 443, v.endpoint)
    check("vless: name", v.name == "Thmanyah-Fast-Node", v.name)
    check("vless: reality keys", v.security == "reality" && v.publicKey == "AbC+dEf_123-xyz" && v.shortId == "6ba85179", v.publicKey)
    check("vless: '+' in pbk is not turned into a space", v.publicKey.contains('+'))
    check("vless: spx decoded", v.spiderX == "/", v.spiderX)
    check("vless: flow", v.flow == "xtls-rprx-vision")

    // --- VLESS over WebSocket + TLS, name with spaces/emoji/percent-escapes (URI would reject this)
    val ws = ok("vless://uuid-1@cdn.example.com:8443?type=ws&security=tls&path=%2Fapi%2Fv1&host=cdn.example.com&sni=cdn.example.com&alpn=h2,http%2F1.1&fp=firefox#DE %F0%9F%87%A9%F0%9F%87%AA Fast Node")
    check("vless-ws: network/path", ws.network == "ws" && ws.path == "/api/v1", ws.path)
    check("vless-ws: alpn list", ws.alpn == listOf("h2", "http/1.1"), ws.alpn.toString())
    check("vless-ws: multibyte name decoded", ws.name == "DE \uD83C\uDDE9\uD83C\uDDEA Fast Node", ws.name)

    // --- VMess base64 JSON (v2rayN)
    val vmJson = """{"v":"2","ps":"NL-VMess-WS-Stream","add":"nl.example.net","port":"443","id":"b831381d-6324-4d53-ad4f-8cda48b30811","aid":"0","scy":"auto","net":"ws","type":"none","host":"nl.example.net","path":"/stream","tls":"tls","sni":"nl.example.net"}"""
    val vm = ok("vmess://" + Base64.getEncoder().encodeToString(vmJson.toByteArray()))
    check("vmess: fields", vm.protocol == ProxyProtocol.VMESS && vm.host == "nl.example.net" && vm.port == 443 && vm.network == "ws" && vm.security == "tls", vm.toString())
    check("vmess: name", vm.name == "NL-VMess-WS-Stream")
    val vmNumeric = """{"ps":"x","add":"1.2.3.4","port":8080,"id":"u","aid":0,"net":"tcp","tls":""}"""
    check("vmess: numeric port/aid (not strings)", ok("vmess://" + Base64.getUrlEncoder().withoutPadding().encodeToString(vmNumeric.toByteArray())).port == 8080)

    // --- Trojan: TLS is the default
    val t = ok("trojan://s3cret%40pass@tr.example.org:443?sni=tr.example.org#Trojan-1")
    check("trojan: password decoded", t.secret == "s3cret@pass", t.secret)
    check("trojan: tls by default", t.security == "tls")

    // --- Shadowsocks SIP002 and legacy
    val ss1 = ok("ss://" + Base64.getUrlEncoder().withoutPadding().encodeToString("chacha20-ietf-poly1305:pa:ss".toByteArray()) + "@ss.example.com:8388#SS-One")
    check("ss sip002: method/password (password may contain ':')", ss1.method == "chacha20-ietf-poly1305" && ss1.secret == "pa:ss", "${ss1.method}|${ss1.secret}")
    val ss2 = ok("ss://" + Base64.getEncoder().encodeToString("aes-256-gcm:hunter2@1.2.3.4:443".toByteArray()) + "#Legacy")
    check("ss legacy", ss2.method == "aes-256-gcm" && ss2.host == "1.2.3.4" && ss2.port == 443 && ss2.secret == "hunter2")
    check("ss plugin rejected", bad("ss://YWVzLTI1Ni1nY206cHc=@h.com:1?plugin=v2ray-plugin"))

    // --- IPv6 literal
    val v6 = ok("vless://id@[2001:db8::1]:443?security=tls#v6")
    check("ipv6 host", v6.host == "2001:db8::1" && v6.port == 443 && v6.endpoint == "[2001:db8::1]:443", v6.endpoint)

    // --- Rejections
    check("rejects missing port", bad("vless://id@host.com?security=tls"))
    check("rejects port 0 / 70000", bad("vless://id@host.com:0") && bad("trojan://p@host.com:70000"))
    check("rejects unknown scheme", bad("http://example.com"))
    check("rejects empty / garbage", bad("") && bad("hello world"))
    check("rejects reality without pbk", bad("vless://id@h.com:443?security=reality&sni=x.com"))
    check("rejects unsupported transport", bad("vless://id@h.com:443?type=kcp"))
    check("rejects removed h2/quic transports with a clear message", bad("vless://id@h.com:443?type=h2") && bad("vless://id@h.com:443?type=http") &&
        ConfigParser.parse("vless://id@h.com:443?type=h2").exceptionOrNull()!!.message!!.contains("XHTTP"))
    check("accepts xhttp + splithttp alias + raw alias", ok("vless://id@h.com:443?type=xhttp&path=%2Fx&mode=auto").network == "xhttp" &&
        ok("vless://id@h.com:443?type=splithttp").network == "xhttp" && ok("vless://id@h.com:443?type=raw").network == "tcp")
    check("rejects vless without id", bad("vless://@h.com:443"))

    // --- parseAll: multi-line and base64 subscription
    val two = "vless://a@h1.com:443#A\ntrojan://p@h2.com:443#B\nnot-a-link"
    val all = ConfigParser.parseAll(two)
    check("parseAll: 2 ok + 1 failed", all.count { it.isSuccess } == 2 && all.count { it.isFailure } == 1, all.toString())
    val sub = Base64.getEncoder().encodeToString("vless://a@h1.com:443#A\ntrojan://p@h2.com:443#B".toByteArray())
    check("parseAll: base64 subscription", ConfigParser.parseAll(sub).count { it.isSuccess } == 2)

    // --- Builder output is valid JSON and has the expected structure
    @Suppress("UNCHECKED_CAST")
    fun cfg(c: ProxyConfig, addr: String? = null) = MiniJson.parseObject(XrayConfigBuilder.build(c, addr))
    fun outbound(m: Map<String, Any?>) = (m["outbounds"] as List<Map<String, Any?>>)[0]
    fun stream(m: Map<String, Any?>) = outbound(m)["streamSettings"] as Map<String, Any?>

    val cv = cfg(v, "203.0.113.7")
    val ov = outbound(cv)
    val vnext = ((ov["settings"] as Map<*, *>)["vnext"] as List<Map<String, Any?>>)[0]
    check("builder: vless dials the resolved IP", vnext["address"] == "203.0.113.7" && (vnext["port"] as Long) == 443L, vnext.toString())
    val reality = stream(cv)["realitySettings"] as Map<String, Any?>
    check("builder: reality keeps hostname-derived serverName + keys", reality["serverName"] == "www.microsoft.com" && reality["publicKey"] == "AbC+dEf_123-xyz" && reality["shortId"] == "6ba85179", reality.toString())
    check("builder: vless flow present", ((vnext["users"] as List<Map<String, Any?>>)[0])["flow"] == "xtls-rprx-vision")

    val cw = cfg(ws, "198.51.100.9")
    val wsSet = stream(cw)["wsSettings"] as Map<String, Any?>
    check("builder: ws Host header keeps original hostname when address is an IP", (wsSet["headers"] as Map<*, *>)["Host"] == "cdn.example.com", wsSet.toString())
    check("builder: tls serverName = sni", ((stream(cw)["tlsSettings"]) as Map<*, *>)["serverName"] == "cdn.example.com")

    val noSni = ok("vless://id@my.host.org:443?security=tls&type=ws&path=/p")
    check("builder: no sni/host -> serverName falls back to original host (not the IP)", ((stream(cfg(noSni, "9.9.9.9"))["tlsSettings"]) as Map<*, *>)["serverName"] == "my.host.org")

    val cs = cfg(ss1)
    check("builder: shadowsocks has no streamSettings", !outbound(cs).containsKey("streamSettings") && outbound(cs)["protocol"] == "shadowsocks")
    val ct = cfg(t)
    check("builder: trojan servers[].password", (((outbound(ct)["settings"] as Map<*, *>)["servers"] as List<Map<String, Any?>>)[0])["password"] == "s3cret@pass")
    val cm = cfg(vm)
    val vmUser = (((outbound(cm)["settings"] as Map<*, *>)["vnext"] as List<Map<String, Any?>>)[0]["users"] as List<Map<String, Any?>>)[0]
    check("builder: vmess user has only id+security (alterId was removed from Xray)", vmUser.keys == setOf("id", "security"), vmUser.keys.toString())

    val rules = (cv["routing"] as Map<*, *>)["rules"] as List<Map<String, Any?>>
    check("builder: no inbounds (nothing listens on the device)", !cv.containsKey("inbounds"))
    check("builder: proxy is first outbound (default route)", outbound(cv)["tag"] == "proxy")
    check("builder: LAN ranges go direct", (rules[0]["ip"] as List<*>).contains("192.168.0.0/16") && rules[0]["outboundTag"] == "direct")
    check("builder: no geoip/geosite dependency", !XrayConfigBuilder.build(v).contains("geoip") && !XrayConfigBuilder.build(v).contains("geosite"))

    // JSON writer escaping round-trip
    val tricky = mapOf("k" to "quote\" back\\slash\nnewline\ttab \u0001 unicode: \u0627\u0644\u0639\u0631\u0628\u064A\u0629")
    check("json: escape round-trip", MiniJson.parseObject(MiniJson.stringify(tricky)) == tricky)
    }
}
