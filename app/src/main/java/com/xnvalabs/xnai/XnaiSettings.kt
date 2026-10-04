package com.xnvalabs.xnai

import android.content.Context
import android.net.Uri
import org.json.JSONObject

class XnaiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("xnai_settings", Context.MODE_PRIVATE)
    private val secure = SecureSecretStore(context)

    var autonomyEnabled: Boolean
        get() = prefs.getBoolean("autonomy_enabled", true)
        set(value) = prefs.edit().putBoolean("autonomy_enabled", value).apply()

    var sandboxPolicy: SandboxPolicy
        get() = SandboxPolicy(
            enabled = prefs.getBoolean("sandbox_enabled", true),
            maxExperimentMs = prefs.getLong("max_experiment_ms", 2_000L),
            maxMemoryBytes = prefs.getLong("max_memory_bytes", 64L * 1024L * 1024L),
            maxGeneratedItems = prefs.getInt("max_generated_items", 10_000),
            allowNetworkResearch = prefs.getBoolean("network_research", true),
            allowExternalActions = prefs.getBoolean("external_actions", false),
            allowCodeExecution = prefs.getBoolean("code_execution", false),
            maxNetworkRequestsPerCycle = prefs.getInt("network_budget", 6)
        )
        set(value) = prefs.edit()
            .putBoolean("sandbox_enabled", value.enabled)
            .putLong("max_experiment_ms", value.maxExperimentMs)
            .putLong("max_memory_bytes", value.maxMemoryBytes)
            .putInt("max_generated_items", value.maxGeneratedItems)
            .putBoolean("network_research", value.allowNetworkResearch)
            .putBoolean("external_actions", value.allowExternalActions)
            .putBoolean("code_execution", value.allowCodeExecution)
            .putInt("network_budget", value.maxNetworkRequestsPerCycle)
            .apply()

    var provider: ProviderConfig
        get() = ProviderConfig(
            baseUrl = prefs.getString("provider_url", "https://api.openai.com/v1") ?: "https://api.openai.com/v1",
            model = prefs.getString("provider_model", "gpt-4o-mini") ?: "gpt-4o-mini",
            enabled = prefs.getBoolean("provider_enabled", false),
            researchEnabled = prefs.getBoolean("provider_research", true)
        )
        set(value) = prefs.edit()
            .putString("provider_url", value.baseUrl)
            .putString("provider_model", value.model)
            .putBoolean("provider_enabled", value.enabled)
            .putBoolean("provider_research", value.researchEnabled)
            .apply()

    var github: GitHubConfig
        get() = GitHubConfig(
            owner = prefs.getString("gh_owner", "") ?: "",
            repo = prefs.getString("gh_repo", "") ?: "",
            branch = prefs.getString("gh_branch", "main") ?: "main",
            pathPrefix = prefs.getString("gh_prefix", "XNAI_LIBRARY") ?: "XNAI_LIBRARY",
            enabled = prefs.getBoolean("gh_enabled", false)
        )
        set(value) = prefs.edit()
            .putString("gh_owner", value.owner)
            .putString("gh_repo", value.repo)
            .putString("gh_branch", value.branch)
            .putString("gh_prefix", value.pathPrefix)
            .putBoolean("gh_enabled", value.enabled)
            .apply()


    fun githubAccounts(): List<GitHubAccount> {
        val raw = prefs.getString("gh_accounts", "")?.trim().orEmpty()
        if (raw.isNotBlank()) {
            return try {
                val array = org.json.JSONArray(raw)
                buildList {
                    for (i in 0 until array.length()) {
                        val o = array.optJSONObject(i) ?: continue
                        val id = o.optString("id").trim()
                        val owner = o.optString("owner").trim()
                        val repo = o.optString("repo").trim()
                        if (id.isBlank() || owner.isBlank() || repo.isBlank()) continue
                        add(GitHubAccount(
                            id = id, owner = owner, repo = repo,
                            branch = o.optString("branch", "main"),
                            pathPrefix = o.optString("pathPrefix", "XNAI_LIBRARY"),
                            enabled = o.optBoolean("enabled", true)
                        ))
                    }
                }
            } catch (_: Throwable) { emptyList() }
        }
        // Backward-compatible bridge from the original single-account settings.
        val legacy = github
        return if (legacy.enabled && legacy.owner.isNotBlank() && legacy.repo.isNotBlank() && githubToken() != null) {
            listOf(GitHubAccount("primary", legacy.owner, legacy.repo, legacy.branch, legacy.pathPrefix, true))
        } else emptyList()
    }

    fun githubTokenFor(accountId: String): String? = secure.get("github_token_$accountId")

    fun saveGitHubAccount(account: GitHubAccount, token: String) {
        require(account.id.matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "ID akun GitHub tidak valid." }
        val accounts = githubAccounts().filterNot { it.id == account.id }.toMutableList().apply { add(account) }
        val array = org.json.JSONArray()
        accounts.forEach { a -> array.put(org.json.JSONObject().apply {
            put("id", a.id); put("owner", a.owner); put("repo", a.repo);
            put("branch", a.branch); put("pathPrefix", a.pathPrefix); put("enabled", a.enabled)
        }) }
        prefs.edit().putString("gh_accounts", array.toString()).apply()
        secure.put("github_token_${account.id}", token.trim())
    }

    fun setGitHubAccountEnabled(accountId: String, enabled: Boolean) {
        val accounts = githubAccounts().map { if (it.id == accountId) it.copy(enabled = enabled) else it }
        val array = org.json.JSONArray()
        accounts.forEach { a -> array.put(org.json.JSONObject().apply {
            put("id", a.id); put("owner", a.owner); put("repo", a.repo);
            put("branch", a.branch); put("pathPrefix", a.pathPrefix); put("enabled", a.enabled)
        }) }
        prefs.edit().putString("gh_accounts", array.toString()).apply()
    }

    fun removeGitHubAccount(accountId: String) {
        val accounts = githubAccounts().filterNot { it.id == accountId }
        val array = org.json.JSONArray()
        accounts.forEach { a -> array.put(org.json.JSONObject().apply {
            put("id", a.id); put("owner", a.owner); put("repo", a.repo);
            put("branch", a.branch); put("pathPrefix", a.pathPrefix); put("enabled", a.enabled)
        }) }
        prefs.edit().putString("gh_accounts", array.toString()).apply()
        secure.remove("github_token_$accountId")
    }

    fun setProviderApiKey(value: String) = secure.put("provider_api_key", value)
    fun providerApiKey(): String? = secure.get("provider_api_key")
    fun clearProviderApiKey() = secure.remove("provider_api_key")

    fun setGitHubToken(value: String) = secure.put("github_token", value)
    fun githubToken(): String? = secure.get("github_token")
    fun clearGitHubToken() = secure.remove("github_token")

    fun importDictionaryUri(context: Context, uri: Uri): Int {
        var count = 0
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
            lines.forEach { raw ->
                raw.trim().takeIf { it.isNotEmpty() }?.let { count++ }
            }
        }
        return count
    }

    fun exportPublicSettings(): JSONObject = JSONObject().apply {
        put("autonomyEnabled", autonomyEnabled)
        put("sandbox", JSONObject().apply {
            put("enabled", sandboxPolicy.enabled)
            put("maxExperimentMs", sandboxPolicy.maxExperimentMs)
            put("maxMemoryBytes", sandboxPolicy.maxMemoryBytes)
            put("maxGeneratedItems", sandboxPolicy.maxGeneratedItems)
            put("allowNetworkResearch", sandboxPolicy.allowNetworkResearch)
            put("allowExternalActions", sandboxPolicy.allowExternalActions)
            put("allowCodeExecution", sandboxPolicy.allowCodeExecution)
            put("maxNetworkRequestsPerCycle", sandboxPolicy.maxNetworkRequestsPerCycle)
        })
        put("provider", JSONObject().apply {
            put("baseUrl", provider.baseUrl)
            put("model", provider.model)
            put("enabled", provider.enabled)
            put("researchEnabled", provider.researchEnabled)
        })
        put("github", JSONObject().apply {
            put("owner", github.owner)
            put("repo", github.repo)
            put("branch", github.branch)
            put("pathPrefix", github.pathPrefix)
            put("enabled", github.enabled)
        })
    }
}
