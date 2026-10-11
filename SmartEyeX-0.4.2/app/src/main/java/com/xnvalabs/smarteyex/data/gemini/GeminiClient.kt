package com.xnvalabs.smarteyex.data.gemini

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import com.xnvalabs.smarteyex.BuildConfig
import com.xnvalabs.smarteyex.core.NetworkClient
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * Direct Gemini REST client, used only when no XNAI server URL is configured.
 *
 * Direct provider access is intended for local development/debug builds only. Public release builds
 * leave GEMINI_API_KEY empty and route AI requests through the HTTPS XNAI backend, keeping provider
 * credentials server-side. All calls block: run them on Dispatchers.IO.
 */
object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var certSha1: String? = null

    val isConfigured: Boolean get() = BuildConfig.GEMINI_API_KEY.isNotBlank()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun chat(system: String, history: List<Pair<String, String>>, message: String, maxOutputTokens: Int): Result<String> {
        val contents = JSONArray()
        GeminiPrompt.turns(history, message).forEach { (role, text) -> contents.put(textTurn(role, text)) }
        return generate(system, contents, maxOutputTokens)
    }

    fun describeImage(
        jpeg: ByteArray,
        instruction: String = "Jelaskan gambar ini secara ringkas dan bacakan teks yang terlihat. Bedakan pengamatan dari dugaan.",
    ): Result<String> {
        val safeInstruction = instruction.trim().take(1800).ifBlank {
            "Jelaskan gambar ini secara ringkas dan bacakan teks yang terlihat."
        }
        val parts = JSONArray()
            .put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))))
            .put(JSONObject().put("text", safeInstruction))
        val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", parts))
        return generate(GeminiPrompt.VISION_SYSTEM, contents, 2048)
    }

    fun translate(text: String, targetLanguage: String): Result<String> {
        if (!GeminiPrompt.isValidLanguage(targetLanguage)) return Result.failure(IllegalArgumentException("Bahasa tujuan tidak valid."))
        val contents = JSONArray().put(textTurn("user", text))
        return generate(GeminiPrompt.translationSystem(targetLanguage), contents, 4096)
    }

    private fun textTurn(role: String, text: String): JSONObject =
        JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", text)))

    private fun generate(system: String, contents: JSONArray, maxOutputTokens: Int): Result<String> = runCatching {
        require(isConfigured) { "Gemini API key belum diisi untuk build ini." }
        val model = GeminiPrompt.model(BuildConfig.GEMINI_MODEL)
        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            put("contents", contents)
            put("generationConfig", JSONObject().put("maxOutputTokens", maxOutputTokens).put("temperature", 0.8))
        }
        val response = NetworkClient.postJson(
            baseUrl = BASE_URL,
            path = "/v1beta/models/$model:generateContent",
            body = body,
            requestId = UUID.randomUUID().toString(),
            extraHeaders = authHeaders(),
            sendIdempotencyKey = false,
        ).getOrThrow()
        extractText(parseOrFail(response))
    }

    private fun authHeaders(): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        headers["x-goog-api-key"] = BuildConfig.GEMINI_API_KEY
        val context = appContext
        if (context != null) {
            headers["X-Android-Package"] = context.packageName
            signingCertSha1(context)?.let { headers["X-Android-Cert"] = it }
        }
        return headers
    }

    @Suppress("DEPRECATION")
    private fun signingCertSha1(context: Context): String? {
        certSha1?.let { return it }
        val computed = runCatching {
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                context.packageManager.getPackageInfo(context.packageName, flags)
            }
            val signer = info.signingInfo?.apkContentsSigners?.firstOrNull()
            if (signer == null) null else MessageDigest.getInstance("SHA-1").digest(signer.toByteArray()).joinToString("") { "%02X".format(it) }
        }.getOrNull()
        certSha1 = computed
        return computed
    }

    private fun parseOrFail(response: NetworkClient.Response): JSONObject {
        if (response.code in 200..299) return JSONObject(response.body)
        val detail = runCatching {
            JSONObject(response.body).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("").take(160)
        val reason = when (response.code) {
            400 -> if (detail.contains("API key", ignoreCase = true)) "API key Gemini tidak valid" else "permintaan ditolak ($detail)"
            401, 403 -> "akses ditolak, cek API key dan pembatasannya"
            404 -> "model tidak ditemukan, isi GEMINI_MODEL dengan nama model yang masih aktif"
            429 -> "kuota atau batas permintaan Gemini terlampaui"
            else -> "HTTP ${response.code}"
        }
        error("Gemini gagal: $reason")
    }

    private fun extractText(json: JSONObject): String {
        val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
        val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
        val text = buildString {
            if (parts != null) {
                for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty())
            }
        }.trim()
        if (text.isNotBlank()) return text
        val blocked = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
        val finish = candidate?.optString("finishReason").orEmpty()
        error(
            when {
                blocked.isNotBlank() -> "Gemini menolak permintaan ini ($blocked)."
                finish == "MAX_TOKENS" -> "Jawaban Gemini terpotong sebelum selesai. Coba mode berpikir lebih rendah."
                finish == "SAFETY" -> "Jawaban diblokir filter keamanan Gemini."
                else -> "Gemini mengembalikan respons kosong."
            },
        )
    }
}
