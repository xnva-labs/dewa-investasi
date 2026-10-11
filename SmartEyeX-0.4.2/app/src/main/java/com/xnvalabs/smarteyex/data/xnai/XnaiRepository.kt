package com.xnvalabs.smarteyex.data.xnai

import com.xnvalabs.smarteyex.BuildConfig
import com.xnvalabs.smarteyex.core.AppDiagnostics
import com.xnvalabs.smarteyex.core.NetworkClient
import com.xnvalabs.smarteyex.data.memory.MemoryRepository
import com.xnvalabs.smarteyex.data.memory.MemoryType
import com.xnvalabs.smarteyex.data.companion.CompanionRepository
import com.xnvalabs.smarteyex.data.gemini.GeminiClient
import com.xnvalabs.smarteyex.data.gemini.GeminiPrompt
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
    /** Release builds inject XNAI_BASE_URL at build time; no endpoint is hard-coded into source. */
    var endpointUrl: String = BuildConfig.XNAI_BASE_URL.trim().removeSuffix("/")
        private set

    /** A cloud brain exists: either an XNAI server URL or a direct Gemini key. */
    val isConfigured: Boolean get() = endpointUrl.isNotBlank() || GeminiClient.isConfigured

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
        if (!isConfigured) {
            return@withContext Result.failure(IllegalStateException("Layanan XNAI belum dikonfigurasi untuk build ini."))
        }
        if (endpointUrl.isBlank()) return@withContext sendViaGemini(message, history, thinkMode)

        val requestId = UUID.randomUUID().toString()
        val body = JSONObject().apply {
            put("requestId", requestId)
            put("message", message)
            put("thinkMode", thinkMode.take(40))
            put("appVersion", BuildConfig.VERSION_NAME)
            put("platform", "android")
            put("reasoning", reasoningFor(thinkMode))
            put("companion", companionFor())
            put("context", buildContext())
            put("history", JSONArray().apply {
                history.takeLast(MAX_HISTORY).forEach { msg ->
                    val role = if (msg.role == "assistant") "assistant" else "user"
                    put(JSONObject().apply { put("role", role); put("text", msg.text.take(MAX_MESSAGE_CHARS)) })
                }
            })
        }

        runCatching {
            val response = NetworkClient.postJson(endpointUrl, "/chat", body, requestId).getOrThrow()
            val json = NetworkClient.requireSuccess(response, "XNAI")
            val reply = json.optString("reply").trim()
            require(reply.isNotBlank()) { "XNAI mengembalikan respons kosong." }
            reply.take(MAX_REPLY_CHARS)
        }.onFailure { AppDiagnostics.warn("XNAI request failed", it) }
    }

    /** The soul is always sent; personalization (style from memory) only when Memory is ON. */
    private fun companionFor(): String =
        if (PrivacyRepository.settings.value.memoryEnabled) CompanionRepository.companionContext() else CompanionRepository.baseContext()

    private fun sendViaGemini(message: String, history: List<XnaiMessage>, thinkMode: String): Result<String> {
        val system = GeminiPrompt.system(companionFor(), reasoningFor(thinkMode), buildContext())
        val turns = history.takeLast(MAX_HISTORY).map { it.role to it.text.take(MAX_MESSAGE_CHARS) }
        return GeminiClient.chat(system, turns, message, GeminiPrompt.maxTokens(thinkMode))
            .map { it.take(MAX_REPLY_CHARS) }
            .onFailure { AppDiagnostics.warn("Gemini request failed", it) }
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

