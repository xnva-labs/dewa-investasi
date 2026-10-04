package com.xnvalabs.xnai

import android.content.Context
import org.json.JSONObject

object XnaiBootstrap {
    private const val PREF = "xnai_bootstrap"

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (prefs.getBoolean("initialized", false)) return
        try {
            val repo = XnaiRepository(context.applicationContext)
            val library = XnaiLibraryStore(context.applicationContext)
            repo.artifacts().forEach { library.append("knowledge", JSONObject().apply {
                put("id", "artifact-${it.id}"); put("title", it.title); put("format", it.format); put("category", it.category); put("content", it.content); put("status", it.status); put("createdAt", it.createdAt); put("provenance", "migrated-from-local-repository")
            }) }
            repo.formulas().forEach { library.append("knowledge", JSONObject().apply {
                put("id", "formula-${it.id}"); put("name", it.name); put("domain", it.domain); put("formula", it.formula); put("meaning", it.meaning); put("variables", it.variables); put("example", it.example); put("provenance", "migrated-from-local-repository")
            }) }
            repo.nodes().forEach { library.append("knowledge", JSONObject().apply {
                put("id", "node-${it.id}"); put("name", it.name); put("representation", it.representation); put("definition", it.definition); put("confidence", it.confidence); put("provenance", "migrated-from-local-repository")
            }) }
            repo.snippets().forEach { library.append("knowledge", JSONObject().apply {
                put("id", "snippet-${it.id}"); put("title", it.title); put("language", it.language); put("code", it.code); put("note", it.note); put("provenance", "migrated-from-local-repository")
            }) }
            prefs.edit().putBoolean("initialized", true).apply()
        } catch (_: Throwable) {
            prefs.edit().putBoolean("initialized", false).apply()
        }
    }
}
