package com.xnvalabs.xnai

import org.json.JSONArray
import org.json.JSONObject

/** JSON mapping for learning records. Each record carries a "type" tag so mixed categories can be read back safely. */

fun ThoughtQuestionRecord.toJson(): JSONObject = JSONObject().apply {
    put("type", "question"); put("id", id); put("question", question); put("why", why)
    put("priority", priority); put("createdAt", createdAt)
}

fun HypothesisRecord.toJson(): JSONObject = JSONObject().apply {
    put("type", "hypothesis"); put("id", id); put("statement", statement)
    put("evidenceIds", JSONArray(evidenceIds)); put("confidence", confidence)
    put("status", status); put("createdAt", createdAt)
}

fun ExperimentRecord.toJson(): JSONObject = JSONObject().apply {
    put("type", "experiment"); put("id", id); put("hypothesisId", hypothesisId); put("plan", plan)
    put("status", status); put("measurement", measurement); put("conclusion", conclusion)
    put("startedAt", startedAt); put("finishedAt", finishedAt); put("reproducibilityKey", reproducibilityKey)
}

fun ReflectionRecord.toJson(): JSONObject = JSONObject().apply {
    put("type", "reflection"); put("id", id); put("experimentId", experimentId)
    put("reflection", reflection); put("nextQuestionIds", JSONArray(nextQuestionIds)); put("createdAt", createdAt)
}

fun ExperienceRecord.toJson(): JSONObject = JSONObject().apply {
    put("type", "experience"); put("id", id); put("eventType", eventType); put("summary", summary)
    put("evidence", evidence); put("outcome", outcome); put("lesson", lesson)
    put("linkedIds", JSONArray(linkedIds)); put("createdAt", createdAt)
}

fun KnowledgeRelation.toJson(): JSONObject = JSONObject().apply {
    put("type", "relation"); put("id", id); put("fromId", fromId); put("toId", toId)
    put("relation", relation); put("confidence", confidence); put("provenance", provenance); put("createdAt", createdAt)
}

fun ValidatedKnowledgeRecord.toJson(): JSONObject = JSONObject().apply {
    put("type", "validated"); put("id", id); put("title", title); put("statement", statement)
    put("evidenceIds", JSONArray(evidenceIds)); put("confidence", confidence)
    put("validationStatus", validationStatus); put("provenance", provenance)
    put("checksum", checksum); put("createdAt", createdAt)
}

fun AlgorithmProposal.toJson(): JSONObject = JSONObject().apply {
    put("type", "proposal"); put("id", id); put("name", name); put("family", family); put("strategy", strategy)
    put("operations", JSONArray(operations)); put("origin", origin); put("createdAt", createdAt)
}

fun ResearchSourceRecord.toJson(): JSONObject = JSONObject().apply {
    put("type", "source"); put("id", id); put("source", source); put("title", title); put("url", url)
    put("snippet", snippet); put("retrievedAt", retrievedAt); put("checksum", checksum); put("permission", permission)
}

private fun JSONObject.isType(t: String) = optString("type") == t

fun questionFromJson(o: JSONObject): ThoughtQuestionRecord? = if (!o.isType("question")) null else ThoughtQuestionRecord(
    o.optString("id"), o.optString("question"), o.optString("why"), o.optDouble("priority", .5), o.optLong("createdAt")
)

fun hypothesisFromJson(o: JSONObject): HypothesisRecord? = if (!o.isType("hypothesis")) null else HypothesisRecord(
    o.optString("id"), o.optString("statement"), o.optStringList("evidenceIds"),
    o.optDouble("confidence", .25), o.optString("status", "candidate"), o.optLong("createdAt")
)

fun experimentFromJson(o: JSONObject): ExperimentRecord? = if (!o.isType("experiment")) null else ExperimentRecord(
    o.optString("id"), o.optString("hypothesisId"), o.optString("plan"), o.optString("status"),
    o.optString("measurement"), o.optString("conclusion"), o.optLong("startedAt"), o.optLong("finishedAt"),
    o.optString("reproducibilityKey")
)

fun reflectionFromJson(o: JSONObject): ReflectionRecord? = if (!o.isType("reflection")) null else ReflectionRecord(
    o.optString("id"), o.optString("experimentId"), o.optString("reflection"),
    o.optStringList("nextQuestionIds"), o.optLong("createdAt")
)

fun experienceFromJson(o: JSONObject): ExperienceRecord? = if (!o.isType("experience")) null else ExperienceRecord(
    o.optString("id"), o.optString("eventType"), o.optString("summary"), o.optString("evidence"),
    o.optString("outcome"), o.optString("lesson"), o.optStringList("linkedIds"), o.optLong("createdAt")
)

fun validatedFromJson(o: JSONObject): ValidatedKnowledgeRecord? = if (!o.isType("validated")) null else ValidatedKnowledgeRecord(
    o.optString("id"), o.optString("title"), o.optString("statement"), o.optStringList("evidenceIds"),
    o.optDouble("confidence", .5), o.optString("validationStatus"), o.optString("provenance"),
    o.optString("checksum"), o.optLong("createdAt")
)

fun relationFromJson(o: JSONObject): KnowledgeRelation? = if (!o.isType("relation")) null else KnowledgeRelation(
    o.optString("id"), o.optString("fromId"), o.optString("toId"), o.optString("relation"),
    o.optDouble("confidence", .5), o.optString("provenance"), o.optLong("createdAt")
)

fun sourceFromJson(o: JSONObject): ResearchSourceRecord? = if (!o.isType("source")) null else ResearchSourceRecord(
    o.optString("id"), o.optString("source"), o.optString("title"), o.optString("url"),
    o.optString("snippet"), o.optLong("retrievedAt"), o.optString("checksum"), o.optString("permission", "public-web")
)

fun proposalFromJson(o: JSONObject): AlgorithmProposal? = if (!o.isType("proposal")) null else AlgorithmProposal(
    o.optString("id"), o.optString("name"), o.optString("family"), o.optString("strategy"),
    o.optStringList("operations"), o.optString("origin"), o.optLong("createdAt")
)
