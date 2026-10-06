package com.xnvalabs.xnai

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** GitHub transport. Tokens are supplied at runtime from SecureSecretStore and never serialized. */
class GitHubSyncClient(
    private val accounts: () -> List<GitHubAccount>,
    private val tokenFor: (String) -> String?
) {
    constructor(config: () -> GitHubConfig, token: () -> String?) : this(
        accounts = {
            val c = config()
            if (!c.enabled || c.owner.isBlank() || c.repo.isBlank()) emptyList()
            else listOf(GitHubAccount("primary", c.owner, c.repo, c.branch, c.pathPrefix, true))
        },
        tokenFor = { id -> if (id == "primary") token() else null }
    )

    data class UploadResult(val uploaded: Int, val skipped: Int, val errors: List<String>)

    fun uploadFiles(files: List<java.io.File>, cacheDir: java.io.File): UploadResult {
        val usable = accounts().filter { it.enabled && it.owner.isNotBlank() && it.repo.isNotBlank() && !tokenFor(it.id).isNullOrBlank() }
        if (usable.isEmpty()) return UploadResult(0, files.size, listOf("GitHub sync belum dikonfigurasi lengkap."))
        var uploaded = 0
        var skipped = 0
        val errors = mutableListOf<String>()
        files.forEach { logical ->
            try {
                val transport = XnaiLibraryStore.splitFileForTransport(logical, cacheDir)
                val manifest = transport.firstOrNull()?.parentFile?.resolve("manifest.json")
                val payload = transport + listOfNotNull(manifest?.takeIf { it.exists() })
                payload.forEachIndexed { index, file ->
                    // Deterministic placement spreads chunks across authorized repositories.
                    val account = usable[(index + logical.name.hashCode().ushr(1)) % usable.size]
                    val token = tokenFor(account.id) ?: return@forEachIndexed
                    val remote = "${account.pathPrefix.trim('/')}/${logical.name}/${file.name}"
                    val bytes = file.readBytes()
                    val body = JSONObject().apply {
                        put("message", "XNAI library sync: ${logical.name} ${file.name}")
                        put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
                        put("branch", account.branch)
                        getFileSha(remote, account, token)?.let { put("sha", it) }
                    }
                    putFile(remote, body.toString(), account, token)
                    uploaded++
                }
            } catch (e: Throwable) {
                errors += "${logical.name}: ${e.message ?: "error"}"
            }
        }
        return UploadResult(uploaded, skipped, errors)
    }

    private fun getFileSha(path: String, cfg: GitHubAccount, auth: String): String? {
        return try {
            val c = open("https://api.github.com/repos/${cfg.owner}/${cfg.repo}/contents/${path.urlEncode()}", auth, "GET")
            if (c.responseCode !in 200..299) return null
            JSONObject(c.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() })
                .optString("sha").takeIf { it.isNotBlank() }
        } catch (_: Throwable) { null }
    }

    private fun putFile(path: String, body: String, cfg: GitHubAccount, auth: String) {
        val c = open("https://api.github.com/repos/${cfg.owner}/${cfg.repo}/contents/${path.urlEncode()}", auth, "PUT")
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        val code = c.responseCode
        if (code !in 200..299) {
            val msg = (c.errorStream ?: c.inputStream).bufferedReader(StandardCharsets.UTF_8).use { it.readText().take(1000) }
            error("GitHub HTTP $code: $msg")
        }
    }

    private fun open(url: String, auth: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $auth")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "XNAI-Native/1.0")
        }

    private fun String.urlEncode(): String = java.net.URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
        .replace("+", "%20").replace("%2F", "/")
}
