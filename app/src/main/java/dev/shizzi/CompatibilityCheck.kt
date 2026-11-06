package dev.shizzi

import android.content.Context
import org.json.JSONObject

enum class Capability { TEST_NETWORK, PREFER_TEST_NETWORKS }

data class CapabilityResult(
    val capability: Capability,
    val isPresent: Boolean,
    val detail: String,
)

class CompatibilityCheck(private val context: Context) {

    fun run(): List<CapabilityResult> = Capability.entries.map { capability ->
        when (capability) {
            Capability.TEST_NETWORK -> checkTestNetwork()
            Capability.PREFER_TEST_NETWORKS -> checkPreferTestNetworks()
        }
    }

    private fun checkTestNetwork(): CapabilityResult {
        val api = TestNetworkApi(context)

        return CapabilityResult(
            capability = Capability.TEST_NETWORK,
            isPresent = api.isAvailable,
            detail = when {
                api.isAvailable -> "خدمة test_network متاحة"
                else -> "خدمة test_network أو الصنف TestNetworkManager غير متاح"
            },
        )
    }

    private fun checkPreferTestNetworks(): CapabilityResult {
        val failure = TetheringPreferenceApi(context).resolutionFailure()

        return CapabilityResult(
            capability = Capability.PREFER_TEST_NETWORKS,
            isPresent = failure == null,
            detail = failure ?: "الدالة setPreferTestNetworks متاحة",
        )
    }
}

fun List<CapabilityResult>.toJson(): String = JSONObject().apply {
    this@toJson.forEach { result ->
        put(
            result.capability.name,
            JSONObject().apply {
                put("present", result.isPresent)
                put("detail", result.detail)
            },
        )
    }
}.toString()

fun parseCapabilities(report: String): List<CapabilityResult> {
    val parsed = runCatching { JSONObject(report) }.getOrNull()

    return Capability.entries.map { capability ->
        val entry = parsed?.optJSONObject(capability.name)

        CapabilityResult(
            capability = capability,
            isPresent = entry?.optBoolean("present") == true,
            detail = entry?.optString("detail").orEmpty()
                .ifEmpty { "لم تُبلّغ بها العملية المميّزة" },
        )
    }
}
