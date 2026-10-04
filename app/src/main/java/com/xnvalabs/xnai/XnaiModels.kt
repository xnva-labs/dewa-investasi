package com.xnvalabs.xnai

import org.json.JSONObject

/** Persistent records for the autonomous learning loop. */
data class ResearchSourceRecord(
    val id: String,
    val source: String,
    val title: String,
    val url: String,
    val snippet: String,
    val retrievedAt: Long,
    val checksum: String,
    val permission: String = "public-web"
)

data class ThoughtQuestionRecord(
    val id: String,
    val question: String,
    val why: String,
    val priority: Double,
    val createdAt: Long = System.currentTimeMillis()
)

data class HypothesisRecord(
    val id: String,
    val statement: String,
    val evidenceIds: List<String>,
    val confidence: Double,
    val status: String = "candidate",
    val createdAt: Long = System.currentTimeMillis()
)

data class ExperimentRecord(
    val id: String,
    val hypothesisId: String,
    val plan: String,
    val status: String,
    val measurement: String,
    val conclusion: String,
    val startedAt: Long,
    val finishedAt: Long,
    val reproducibilityKey: String
)

data class ReflectionRecord(
    val id: String,
    val experimentId: String,
    val reflection: String,
    val nextQuestionIds: List<String>,
    val createdAt: Long = System.currentTimeMillis()
)

data class ExperienceRecord(
    val id: String,
    val eventType: String,
    val summary: String,
    val evidence: String,
    val outcome: String,
    val lesson: String,
    val linkedIds: List<String>,
    val createdAt: Long = System.currentTimeMillis()
)

data class KnowledgeRelation(
    val id: String,
    val fromId: String,
    val toId: String,
    val relation: String,
    val confidence: Double,
    val provenance: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class ValidatedKnowledgeRecord(
    val id: String,
    val title: String,
    val statement: String,
    val evidenceIds: List<String>,
    val confidence: Double,
    val validationStatus: String,
    val provenance: String,
    val checksum: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class AlgorithmProposal(
    val id: String,
    val name: String,
    val family: String,
    val strategy: String,
    val operations: List<String>,
    val origin: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class GitHubAccount(
    val id: String,
    val owner: String,
    val repo: String,
    val branch: String = "main",
    val pathPrefix: String = "XNAI_LIBRARY",
    val enabled: Boolean = true
)

data class GitHubConfig(
    val owner: String = "",
    val repo: String = "",
    val branch: String = "main",
    val pathPrefix: String = "XNAI_LIBRARY",
    val enabled: Boolean = false
)

data class ProviderConfig(
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4o-mini",
    val enabled: Boolean = false,
    val researchEnabled: Boolean = true
)

data class SandboxPolicy(
    val enabled: Boolean = true,
    val maxExperimentMs: Long = 2_000,
    val maxMemoryBytes: Long = 64L * 1024L * 1024L,
    val maxGeneratedItems: Int = 10_000,
    val allowNetworkResearch: Boolean = true,
    val allowExternalActions: Boolean = false,
    val allowCodeExecution: Boolean = false,
    val maxNetworkRequestsPerCycle: Int = 6
)

data class XnaiSnapshot(
    val questions: List<ThoughtQuestionRecord>,
    val hypotheses: List<HypothesisRecord>,
    val experiments: List<ExperimentRecord>,
    val reflections: List<ReflectionRecord>,
    val experiences: List<ExperienceRecord>,
    val validated: List<ValidatedKnowledgeRecord>,
    val relations: List<KnowledgeRelation>,
    val sources: List<ResearchSourceRecord>,
    val proposals: List<AlgorithmProposal>
)

fun String.sha256Hex(): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

fun JSONObject.optStringList(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return buildList { for (i in 0 until array.length()) add(array.optString(i)) }
}
