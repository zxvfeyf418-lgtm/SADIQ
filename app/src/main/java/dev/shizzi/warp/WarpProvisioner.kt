package dev.shizzi.warp

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.time.Instant
import java.time.format.DateTimeFormatter

/** The steps shown to the user while a profile is made. */
enum class WarpStep { GENERATING_KEYS, REGISTERING, CHECKING_CLOUDFLARE }

/** A failure whose message is already written for the user. */
class WarpException(message: String) : Exception(message)

data class WarpHttpResponse(val status: Int, val body: String)

/** The one seam to the network, so registration can be tested without it. */
interface WarpHttp {
    @Throws(IOException::class)
    fun request(method: String, url: String, headers: Map<String, String>, body: String?): WarpHttpResponse
}

class UrlConnectionWarpHttp(private val timeoutMillis: Int = 15_000) : WarpHttp {

    override fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): WarpHttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = method
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()

            return WarpHttpResponse(status, text)
        } finally {
            connection.disconnect()
        }
    }
}

/** Registers a fresh device with Cloudflare WARP and returns its tunnel profile. */
class WarpProvisioner(
    private val http: WarpHttp,
    private val random: SecureRandom = SecureRandom(),
    private val now: () -> Instant = { Instant.now() },
) {

    @Throws(WarpException::class)
    fun provision(onStep: (WarpStep) -> Unit = {}): WarpProfile {
        onStep(WarpStep.GENERATING_KEYS)
        val keys = WireGuardKeys.generate(random)

        onStep(WarpStep.REGISTERING)
        val profile = register(keys)

        onStep(WarpStep.CHECKING_CLOUDFLARE)
        val city = runCatching { fetchCity() }.getOrNull()

        return profile.copy(city = city)
    }

    private fun register(keys: WireGuardKeys): WarpProfile {
        val request = MiniJson.stringify(
            mapOf(
                "key" to keys.publicKey,
                "install_id" to "",
                "fcm_token" to "",
                "tos" to DateTimeFormatter.ISO_INSTANT.format(now()),
                "type" to "Android",
                "model" to "PC",
                "locale" to "en_US",
            ),
        )

        val response = try {
            http.request("POST", REGISTER_URL, REGISTER_HEADERS, request)
        } catch (failure: IOException) {
            throw WarpException("تعذّر الاتصال بـ Cloudflare. تأكد من الإنترنت وحاول مرة ثانية.")
        }

        if (response.status !in 200..299) {
            throw WarpException("رفض Cloudflare تسجيل الجهاز (الرمز ${response.status}). حاول مرة ثانية بعد شوية.")
        }

        return parseRegistration(response.body, keys)
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parseRegistration(body: String, keys: WireGuardKeys): WarpProfile {
        val root = runCatching { MiniJson.parseObject(body) }
            .getOrElse { throw WarpException("رد Cloudflare غير مفهوم. حاول مرة ثانية.") }

        val config = root["config"] as? Map<String, Any?>
        val addresses = (config?.get("interface") as? Map<String, Any?>)?.get("addresses") as? Map<String, Any?>
        val peer = (config?.get("peers") as? List<Any?>)?.firstOrNull() as? Map<String, Any?>

        val v4 = (addresses?.get("v4") as? String)?.takeIf { it.isNotBlank() }
            ?: throw WarpException("رد Cloudflare ما فيه عنوان للجهاز. حاول مرة ثانية.")
        val v6 = (addresses?.get("v6") as? String)?.takeIf { it.isNotBlank() }
        val peerKey = (peer?.get("public_key") as? String)?.takeIf { it.isNotBlank() }
            ?: WarpDefaults.PEER_PUBLIC_KEY

        return WarpProfile(
            name = "2N5A-WARP-" + nameSuffix(keys.publicKey),
            privateKey = keys.privateKey,
            publicKey = keys.publicKey,
            peerPublicKey = peerKey,
            addressV4 = v4,
            addressV6 = v6,
        )
    }

    /** The Cloudflare city this connection exits from, if it can be read. */
    @Suppress("UNCHECKED_CAST")
    private fun fetchCity(): String? {
        val response = http.request("GET", META_URL, mapOf("Accept" to "application/json"), null)
        if (response.status !in 200..299) return null

        val meta = MiniJson.parseObject(response.body)
        val direct = meta["city"] as? String
        val nested = (meta["colo"] as? Map<String, Any?>)?.get("city") as? String

        return (direct ?: nested)?.takeIf { it.isNotBlank() }
    }

    private fun nameSuffix(publicKey: String): String {
        val letters = publicKey.filter { it.isLetterOrDigit() }.uppercase()
        return letters.take(4).padEnd(4, '0')
    }

    private companion object {
        const val REGISTER_URL = "https://api.devices.cloudflare.com/v0a4005/reg"
        const val META_URL = "https://speed.cloudflare.com/meta"

        val REGISTER_HEADERS = mapOf(
            "User-Agent" to "okhttp/3.12.1",
            "CF-Client-Version" to "a-6.3-1922",
            "Content-Type" to "application/json; charset=UTF-8",
            "Accept" to "application/json",
        )
    }
}
