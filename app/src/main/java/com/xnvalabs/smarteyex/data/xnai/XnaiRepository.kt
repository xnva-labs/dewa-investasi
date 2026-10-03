package com.xnvalabs.smarteyex.data.xnai

import com.xnvalabs.smarteyex.BuildConfig
import com.xnvalabs.smarteyex.core.AppDiagnostics
import com.xnvalabs.smarteyex.core.NetworkClient
import com.xnvalabs.smarteyex.core.SecureStorage
import com.xnvalabs.smarteyex.data.memory.MemoryRepository
import com.xnvalabs.smarteyex.data.memory.MemoryType
import com.xnvalabs.smarteyex.data.companion.CompanionRepository
import com.xnvalabs.smarteyex.data.intelligence.ReasoningMode
import com.xnvalabs.smarteyex.data.intelligence.ReasoningProfile
import com.xnvalabs.smarteyex.data.intelligence.UserModelRepository
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Production XNAI client contract. Model orchestration remains server-side. */
object XnaiRepository {
    /** Public repository config; contains endpoint metadata only, never API secrets. */
    private const val CONFIG_URL = "https://raw.githubusercontent.com/xnva-labs/SmartEyeX-Lite/main/xnai-config.json"
    private const val KEY_ENDPOINT_CACHE = "xnai.endpoint.cache"
    @Volatile var endpointUrl: String = ""
        private set

    /** Refresh endpoint from the version-controlled JSON config. Runs on caller's IO dispatcher. */
    fun refreshEndpoint(): Result<String> = runCatching {
        val fetched = NetworkClient.getJson(CONFIG_URL)
        val candidate = if (fetched.isSuccess) {
            // Config reachable: it is authoritative. An empty/invalid value means "backend not deployed",
            // and must NOT fall back to an older cached endpoint.
            val normalized = EndpointRules.normalize(fetched.getOrThrow().optString("backendBaseUrl"))
                ?: error("backendBaseUrl belum diatur pada xnai-config.json.")
            runCatching { SecureStorage.putStringSync(KEY_ENDPOINT_CACHE, normalized) }
            normalized
        } else {
            // Config unreachable (offline, GitHub down): reuse the last endpoint that was verified earlier.
            val cached = runCatching { SecureStorage.getString(KEY_ENDPOINT_CACHE) }.getOrNull()
                ?.let { EndpointRules.normalize(it) }
            if (cached == null) throw fetched.exceptionOrNull() ?: IllegalStateException("Config XNAI tidak dapat dimuat.")
            AppDiagnostics.warn("XNAI config unreachable; using cached endpoint", fetched.exceptionOrNull())
            cached
        }
        endpointUrl = candidate
        candidate
    }.onFailure { AppDiagnostics.warn("XNAI config load failed", it) }

    suspend fun sendMessage(
        userText: String,
        history: List<XnaiMessage>,
        thinkMode: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!PrivacyRepository.settings.value.cloudProcessingEnabled) {
            return@withContext Result.failure(IllegalStateException("Cloud Processing OFF — aktifkan di Privacy Control."))
        }
        val message = userText.trim()
        if (message.isBlank()) return@withContext Result.failure(IllegalArgumentException("Pesan kosong."))
        if (message.length > MAX_MESSAGE_CHARS) return@withContext Result.failure(IllegalArgumentException("Pesan terlalu panjang."))
        val configuredEndpoint = refreshEndpoint().getOrElse { error ->
            return@withContext Result.failure(error)
        }

        val requestId = UUID.randomUUID().toString()
        val body = JSONObject().apply {
            put("requestId", requestId)
            put("message", message)
            put("thinkMode", thinkMode.take(40))
            put("appVersion", BuildConfig.VERSION_NAME)
            put("platform", "android")
            put("reasoning", reasoningFor(thinkMode))
            put("companion", if (PrivacyRepository.settings.value.memoryEnabled) CompanionRepository.companionContext() else "mode=companion; personalization=off")
            put("context", buildContext())
            put("history", JSONArray().apply {
                history.takeLast(MAX_HISTORY).forEach { msg ->
                    val role = if (msg.role == "assistant") "assistant" else "user"
                    put(JSONObject().apply { put("role", role); put("text", msg.text.take(MAX_MESSAGE_CHARS)) })
                }
            })
        }

        runCatching {
            val response = NetworkClient.postJson(configuredEndpoint, "/chat", body, requestId).getOrThrow()
            val json = NetworkClient.requireSuccess(response, "XNAI")
            val reply = json.optString("reply").trim()
            require(reply.isNotBlank()) { "XNAI mengembalikan respons kosong." }
            reply.take(MAX_REPLY_CHARS)
        }.onFailure { AppDiagnostics.warn("XNAI request failed", it) }
    }

    private fun reasoningFor(thinkMode: String): String {
        val mode = when (thinkMode.uppercase()) {
            "HIGH" -> ReasoningMode.RESEARCHER
            "SUPERAUTOMATION" -> ReasoningMode.ENGINEER
            else -> ReasoningMode.COMPANION
        }
        return ReasoningProfile.systemGuidance(mode)
    }

    private fun buildContext(): String {
        if (!PrivacyRepository.settings.value.memoryEnabled) return ""
        val entries = MemoryRepository.entries.value
        val profile = entries.filter { it.type == MemoryType.PROFILE }
        val preferences = entries.filter { it.type == MemoryType.PREFERENCE }
        return buildString {
            val personal = UserModelRepository.contextSummary()
            if (personal.isNotBlank()) append(personal).append(" | ")
            if (profile.isNotEmpty()) append("Profile: ").append(profile.joinToString("; ") { "${it.title}: ${it.content}" }.take(MAX_CONTEXT_CHARS))
            if (preferences.isNotEmpty()) {
                if (isNotEmpty()) append(" | ")
                append("Preferences: ").append(preferences.joinToString("; ") { it.content }.take(MAX_CONTEXT_CHARS))
            }
        }.take(MAX_CONTEXT_CHARS)
    }

    private const val MAX_MESSAGE_CHARS = 4000
    private const val MAX_REPLY_CHARS = 12000
    private const val MAX_CONTEXT_CHARS = 6000
    private const val MAX_HISTORY = 20
}


/** Pure endpoint validation so the rule can be unit-tested without Android. */
object EndpointRules {
    /** Returns the endpoint without trailing slashes, or null unless it is an https URL with a host. */
    fun normalize(raw: String?): String? {
        val candidate = raw?.trim()?.trimEnd('/') ?: return null
        if (!candidate.startsWith("https://", ignoreCase = true)) return null
        val host = runCatching { java.net.URL(candidate).host }.getOrNull()
        return if (host.isNullOrBlank()) null else candidate
    }
}
