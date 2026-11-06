package dev.shizzi.warp

import java.io.IOException
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WarpProfileTest {

    private fun hex(text: String): ByteArray =
        ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun x25519MatchesTheRfc7748Vectors() {
        // RFC 7748 section 5.2, first test vector.
        val result = X25519.scalarMult(
            hex("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4"),
            hex("e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c"),
        )
        assertEquals("c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552", hex(result))

        // RFC 7748 section 6.1: both sides of a Diffie-Hellman exchange agree.
        val alice = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
        val bob = hex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb")

        assertEquals("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a", hex(X25519.publicKey(alice)))
        assertEquals("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f", hex(X25519.publicKey(bob)))

        val sharedA = X25519.scalarMult(alice, X25519.publicKey(bob))
        val sharedB = X25519.scalarMult(bob, X25519.publicKey(alice))
        assertEquals("4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742", hex(sharedA))
        assertEquals(hex(sharedA), hex(sharedB))
    }

    @Test
    fun generatedKeysAreValidWireGuardKeys() {
        val keys = WireGuardKeys.generate(SecureRandom())
        val privateBytes = Base64.getDecoder().decode(keys.privateKey)
        val publicBytes = Base64.getDecoder().decode(keys.publicKey)

        assertEquals(32, privateBytes.size)
        assertEquals(32, publicBytes.size)
        assertEquals(0, privateBytes[0].toInt() and 7)
        assertEquals(64, privateBytes[31].toInt() and 192)
        assertEquals(hex(X25519.publicKey(privateBytes)), hex(publicBytes))
        assertTrue(keys.privateKey.endsWith("="))
    }

    private val registration = """
        {"id":"abc","token":"t","config":{"peers":[{"public_key":"bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=",
        "endpoint":{"host":"engage.cloudflareclient.com:2408"}}],
        "interface":{"addresses":{"v4":"172.16.0.2","v6":"2606:4700:110:8a36:df92:102a:9602:fa2b"}}}}
    """.trimIndent()

    private class FakeHttp(
        private val registerResponse: () -> WarpHttpResponse,
        private val metaResponse: () -> WarpHttpResponse,
    ) : WarpHttp {
        val requests = mutableListOf<Triple<String, String, String?>>()

        override fun request(method: String, url: String, headers: Map<String, String>, body: String?): WarpHttpResponse {
            requests += Triple(method, url, body)
            return if (url.contains("/reg")) registerResponse() else metaResponse()
        }
    }

    @Test
    fun provisioningRegistersTheKeyAndBuildsAProfile() {
        val http = FakeHttp(
            registerResponse = { WarpHttpResponse(200, registration) },
            metaResponse = { WarpHttpResponse(200, """{"city":"Baghdad","country":"IQ"}""") },
        )
        val steps = mutableListOf<WarpStep>()

        val profile = WarpProvisioner(http, now = { Instant.parse("2026-11-06T12:00:00Z") }).provision { steps += it }

        assertEquals(
            listOf(WarpStep.GENERATING_KEYS, WarpStep.REGISTERING, WarpStep.CHECKING_CLOUDFLARE),
            steps,
        )
        assertEquals("172.16.0.2", profile.addressV4)
        assertEquals("2606:4700:110:8a36:df92:102a:9602:fa2b", profile.addressV6)
        assertEquals("Baghdad", profile.city)
        assertTrue(profile.name.matches(Regex("2N5A-WARP-[A-Z0-9]{4}")))

        val (method, url, body) = http.requests.first()
        assertEquals("POST", method)
        assertEquals("https://api.devices.cloudflare.com/v0a4005/reg", url)
        assertTrue(body!!.contains("\"key\":\"${profile.publicKey}\""))
        assertTrue(body.contains("\"tos\":\"2026-11-06T12:00:00Z\""))
        assertFalse("the private key must never be sent", body.contains(profile.privateKey))
    }

    @Test
    fun theObfuscatedConfigCarriesTheJunkPacketLines() {
        val profile = sampleProfile()
        val config = profile.toConfig(obfuscated = true)

        val expected = listOf(
            "[Interface]",
            "PrivateKey = PRIVATE=",
            "Address = 172.16.0.2/32, 2606:4700:110:8a36:df92:102a:9602:fa2b/128",
            "DNS = 1.1.1.1, 1.0.0.1, 2606:4700:4700::1111, 2606:4700:4700::1001",
            "MTU = 1280",
            "Jc = 6",
            "Jmin = 10",
            "Jmax = 50",
            "I1 = ${WarpDefaults.SPECIAL_JUNK}",
            "",
            "[Peer]",
            "PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=",
            "AllowedIPs = 0.0.0.0/0, ::/0",
            "Endpoint = 188.114.99.89:4500",
            "PersistentKeepalive = 3",
        )
        assertEquals(expected, config.trimEnd().lines())
        assertEquals("2N5A-WARP-03EC.conf", profile.fileName(obfuscated = true))
    }

    @Test
    fun thePlainConfigHasNoAmneziaLines() {
        val profile = sampleProfile()
        val config = profile.toConfig(obfuscated = false)

        for (key in listOf("Jc", "Jmin", "Jmax", "I1")) {
            assertFalse("$key must not appear in a plain WireGuard file", config.contains("$key ="))
        }
        assertTrue(config.contains("[Peer]"))
        assertEquals("2N5A-WARP-03EC-WG.conf", profile.fileName(obfuscated = false))
    }

    @Test
    fun anIpv4OnlyAccountOmitsTheIpv6Address() {
        val profile = sampleProfile().copy(addressV6 = null)

        assertEquals("172.16.0.2/32", profile.addresses)
    }

    @Test
    fun endpointsCanBeSwitched() {
        val profile = sampleProfile().withEndpoint(1)

        assertEquals("188.114.98.120:2408", profile.endpoint)
        assertEquals(WarpDefaults.ENDPOINTS.last(), profile.withEndpoint(99).endpoint)
    }

    @Test
    fun theSpecialJunkPacketHasWholeBytes() {
        val hexRuns = Regex("<b 0x([0-9a-f]+)>").findAll(WarpDefaults.SPECIAL_JUNK).map { it.groupValues[1] }.toList()

        assertEquals(2, hexRuns.size)
        hexRuns.forEach { assertEquals("hex run needs an even length: $it", 0, it.length % 2) }
    }

    @Test
    fun aRejectedRegistrationIsReportedInArabic() {
        val http = FakeHttp({ WarpHttpResponse(403, "{}") }, { WarpHttpResponse(200, "{}") })

        try {
            WarpProvisioner(http).provision()
            fail("a 403 must fail")
        } catch (failure: WarpException) {
            assertTrue(failure.message!!.contains("403"))
        }
    }

    @Test
    fun aNetworkFailureIsReportedInArabic() {
        val http = FakeHttp({ throw IOException("no route") }, { WarpHttpResponse(200, "{}") })

        try {
            WarpProvisioner(http).provision()
            fail("an IOException must fail")
        } catch (failure: WarpException) {
            assertTrue(failure.message!!.contains("Cloudflare"))
        }
    }

    @Test
    fun aResponseWithoutAnAddressIsRejected() {
        val http = FakeHttp({ WarpHttpResponse(200, """{"config":{"interface":{"addresses":{}}}}""") }, { WarpHttpResponse(200, "{}") })

        try {
            WarpProvisioner(http).provision()
            fail("a response with no address must fail")
        } catch (failure: WarpException) {
            assertTrue(failure.message!!.isNotBlank())
        }
    }

    @Test
    fun aFailedCityCheckDoesNotFailTheProfile() {
        val http = FakeHttp({ WarpHttpResponse(200, registration) }, { throw IOException("blocked") })

        val profile = WarpProvisioner(http).provision()

        assertNull(profile.city)
        assertEquals("172.16.0.2", profile.addressV4)
    }

    @Test
    fun theCityIsReadFromANestedColoObjectToo() {
        val http = FakeHttp(
            { WarpHttpResponse(200, registration) },
            { WarpHttpResponse(200, """{"colo":{"iata":"BGW","city":"Baghdad"}}""") },
        )

        assertEquals("Baghdad", WarpProvisioner(http).provision().city)
    }

    private fun sampleProfile() = WarpProfile(
        name = "2N5A-WARP-03EC",
        privateKey = "PRIVATE=",
        publicKey = "PUBLIC=",
        peerPublicKey = WarpDefaults.PEER_PUBLIC_KEY,
        addressV4 = "172.16.0.2",
        addressV6 = "2606:4700:110:8a36:df92:102a:9602:fa2b",
    )
}
