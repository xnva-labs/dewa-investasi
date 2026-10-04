package com.xnvalabs.xnai

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** OpenAI-compatible adapter. No API key is embedded in source. */
class XnaiModelProvider(private val config: () -> ProviderConfig, private val apiKey: () -> String?) {
    fun complete(system: String, user: String, maxTokens: Int = 1200): String? {
        val cfg = config()
        val key = apiKey()?.takeIf { it.isNotBlank() } ?: return null
        if (!cfg.enabled || cfg.baseUrl.isBlank() || cfg.model.isBlank()) return null
        val url = cfg.baseUrl.trimEnd('/') + "/chat/completions"
        val body = JSONObject().apply {
            put("model", cfg.model)
            put("temperature", 0.2)
            put("max_tokens", maxTokens)
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", system) })
                put(JSONObject().apply { put("role", "user"); put("content", user) })
            })
        }.toString()
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 60_000
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $key")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = (stream ?: return null).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        return JSONObject(response)
            .optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content")
            ?.takeIf { it.isNotBlank() }
    }
}
