package com.xnvalabs.smarteyex.core

import com.xnvalabs.smarteyex.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** Hardened JSON HTTP helper used by every SmartEyeX cloud feature. */
object NetworkClient {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 45_000
    private const val MAX_RESPONSE_BYTES = 1_000_000
    private const val MAX_ATTEMPTS = 3
    private val RETRY_DELAYS_MS = longArrayOf(350L, 900L)
    private val RETRYABLE_CODES = setOf(408, 425, 429, 500, 502, 503, 504)

    data class Response(val code: Int, val body: String)

    fun postJson(
        baseUrl: String,
        path: String,
        body: JSONObject,
        requestId: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        sendIdempotencyKey: Boolean = true,
    ): Result<Response> {
        return runCatching {
            var lastResponse: Response? = null
            // Only retry calls whose server contract supports idempotency. A
            // provider call without an Idempotency-Key must not be repeated
            // automatically because it can duplicate quota/cost.
            val attempts = if (!requestId.isNullOrBlank() && sendIdempotencyKey) MAX_ATTEMPTS else 1
            for (attempt in 0 until attempts) {
                val response = executeOnce(baseUrl, path, body, requestId, extraHeaders, sendIdempotencyKey)
                lastResponse = response
                if (response.code !in RETRYABLE_CODES || attempt == attempts - 1) break
                Thread.sleep(RETRY_DELAYS_MS[attempt])
            }
            lastResponse ?: error("Tidak ada respons dari server.")
        }
    }

    fun requireSuccess(response: Response, operation: String): JSONObject {
        if (response.code !in 200..299) {
            val safeMessage = runCatching {
                JSONObject(response.body).optString("message").take(300)
            }.getOrNull().orEmpty()
            val detail = if (safeMessage.isBlank()) "HTTP ${response.code}" else safeMessage
            error("$operation gagal: $detail")
        }
        return JSONObject(response.body)
    }

    private fun executeOnce(
        baseUrl: String,
        path: String,
        body: JSONObject,
        requestId: String?,
        extraHeaders: Map<String, String>,
        sendIdempotencyKey: Boolean,
    ): Response {
        val connection = createConnection(baseUrl, path, requestId, extraHeaders, sendIdempotencyKey)
        return try {
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let { readBounded(it) }.orEmpty()
            Response(code, text)
        } finally {
            connection.disconnect()
        }
    }

    private fun createConnection(
        baseUrl: String,
        path: String,
        requestId: String?,
        extraHeaders: Map<String, String>,
        sendIdempotencyKey: Boolean,
    ): HttpURLConnection {
        val normalizedBase = baseUrl.trim().removeSuffix("/")
        val url = URL(normalizedBase + "/" + path.trimStart('/'))
        require(url.protocol.equals("https", ignoreCase = true)) {
            "SmartEyeX cloud endpoint wajib menggunakan HTTPS."
        }
        require(url.host.isNotBlank() && url.userInfo == null && url.query == null && url.ref == null) {
            "SmartEyeX cloud endpoint harus berupa URL HTTPS yang valid tanpa kredensial/query/fragment."
        }
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            useCaches = false
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Charset", "UTF-8")
            setRequestProperty("User-Agent", "SmartEyeX/${BuildConfig.VERSION_NAME}")
            requestId?.takeIf { sendIdempotencyKey && it.isNotBlank() }?.let { setRequestProperty("Idempotency-Key", it) }
            extraHeaders.forEach { (name, value) -> setRequestProperty(name, value) }
        }
    }

    private fun readBounded(input: java.io.InputStream): String {
        input.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count <= 0) break
                total += count
                if (total > MAX_RESPONSE_BYTES) error("Respons server terlalu besar.")
                output.write(buffer, 0, count)
            }
            return output.toString(StandardCharsets.UTF_8.name())
        }
    }
}
