package com.xnvalabs.xnai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class XnaiReasoningService(context: Context) {
    private val repository = XnaiRepository(context.applicationContext)
    private val settings = XnaiSettings(context.applicationContext)
    private val provider = XnaiModelProvider({ settings.provider }, { settings.providerApiKey() })

    suspend fun answer(query: String): String = withContext(Dispatchers.IO) {
        if (!XnaiOwnerLock.isUnlocked()) return@withContext "Terkunci: XNAI hanya tunduk kepada ${XnaiOwnerLock.OWNER_NAME}."
        val q = query.trim()
        if (q.isBlank()) return@withContext ""
        val local = ReasoningEngine.answer(q, repository.formulas(), repository.artifacts())
        if (!settings.provider.enabled) return@withContext local
        val context = buildString {
            append("Relevant formulas:\n")
            repository.formulas().filter { q.contains(it.domain, true) || q.contains(it.name, true) }.take(8).forEach { append("- ${it.name}: ${it.formula}\n") }
            append("\nRelevant memory:\n")
            repository.artifacts().filter { it.title.contains(q, true) || it.content.contains(q, true) }.take(8).forEach { append("- ${it.title}: ${it.content.take(500)}\n") }
        }
        provider.complete(
            system = "You are XNAI. Think critically, distinguish evidence from hypothesis, state uncertainty, and never invent sources. Use the supplied memory as evidence, not as unquestionable truth.",
            user = "Question: $q\n\n$context\n\nAnswer clearly in Indonesian.",
            maxTokens = 1200
        ) ?: local
    }
}
