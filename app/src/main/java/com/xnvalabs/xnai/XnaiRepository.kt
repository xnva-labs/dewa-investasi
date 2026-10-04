package com.xnvalabs.xnai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Artifact(
    val id: Long,
    val title: String,
    val format: String,
    val category: String,
    val content: String,
    val status: String = "active",
    val createdAt: Long = System.currentTimeMillis()
)

data class FormulaItem(
    val id: Long,
    val name: String,
    val domain: String,
    val formula: String,
    val meaning: String,
    val variables: String,
    val example: String
)

data class KnowledgeNode(
    val id: Long,
    val name: String,
    val representation: String,
    val definition: String,
    val confidence: Int = 80
)

data class Snippet(
    val id: Long,
    val title: String,
    val language: String,
    val code: String,
    val note: String
)

data class ChatMessage(
    val id: Long,
    val text: String,
    val fromUser: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

data class ActivityLog(
    val id: Long,
    val action: String,
    val title: String,
    val time: Long = System.currentTimeMillis()
)

class XnaiRepository(context: Context) {
    private val prefs = context.getSharedPreferences("xnai_native", Context.MODE_PRIVATE)

    fun artifacts(): List<Artifact> {
        val array = JSONArray(prefs.getString("artifacts", "[]"))
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    Artifact(
                        id = o.getLong("id"),
                        title = o.getString("title"),
                        format = o.getString("format"),
                        category = o.getString("category"),
                        content = o.getString("content"),
                        status = o.optString("status", "active"),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    fun saveArtifact(item: Artifact) {
        val list = artifacts().filterNot { it.id == item.id }.toMutableList()
        list.add(0, item)
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("title", it.title)
                    put("format", it.format)
                    put("category", it.category)
                    put("content", it.content)
                    put("status", it.status)
                    put("createdAt", it.createdAt)
                }
            )
        }
        prefs.edit().putString("artifacts", array.toString()).apply()
    }

    fun deleteArtifact(id: Long) {
        val list = artifacts().filterNot { it.id == id }
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("title", it.title)
                    put("format", it.format)
                    put("category", it.category)
                    put("content", it.content)
                    put("status", it.status)
                    put("createdAt", it.createdAt)
                }
            )
        }
        prefs.edit().putString("artifacts", array.toString()).apply()
    }

    fun formulas(): List<FormulaItem> {
        val array = JSONArray(prefs.getString("formulas", seedFormulas().toString()))
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    FormulaItem(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        domain = o.getString("domain"),
                        formula = o.getString("formula"),
                        meaning = o.getString("meaning"),
                        variables = o.getString("variables"),
                        example = o.getString("example")
                    )
                )
            }
        }
    }

    fun saveFormula(item: FormulaItem) {
        val list = formulas().filterNot { it.id == item.id }.toMutableList()
        list.add(0, item)
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("name", it.name)
                    put("domain", it.domain)
                    put("formula", it.formula)
                    put("meaning", it.meaning)
                    put("variables", it.variables)
                    put("example", it.example)
                }
            )
        }
        prefs.edit().putString("formulas", array.toString()).apply()
    }

    fun deleteFormula(id: Long) {
        saveFormulas(formulas().filterNot { it.id == id })
    }

    private fun saveFormulas(list: List<FormulaItem>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("name", it.name)
                    put("domain", it.domain)
                    put("formula", it.formula)
                    put("meaning", it.meaning)
                    put("variables", it.variables)
                    put("example", it.example)
                }
            )
        }
        prefs.edit().putString("formulas", array.toString()).apply()
    }

    fun nodes(): List<KnowledgeNode> {
        val array = JSONArray(prefs.getString("nodes", seedNodes().toString()))
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    KnowledgeNode(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        representation = o.getString("representation"),
                        definition = o.getString("definition"),
                        confidence = o.optInt("confidence", 80)
                    )
                )
            }
        }
    }

    fun saveNode(node: KnowledgeNode) {
        val list = nodes().filterNot { it.id == node.id }.toMutableList()
        list.add(0, node)
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("name", it.name)
                    put("representation", it.representation)
                    put("definition", it.definition)
                    put("confidence", it.confidence)
                }
            )
        }
        prefs.edit().putString("nodes", array.toString()).apply()
    }

    fun snippets(): List<Snippet> {
        val array = JSONArray(prefs.getString("snippets", "[]"))
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    Snippet(
                        o.getLong("id"),
                        o.getString("title"),
                        o.getString("language"),
                        o.getString("code"),
                        o.getString("note")
                    )
                )
            }
        }.ifEmpty {
            listOf(
                Snippet(1, "Parser JSON", "JavaScript", """const parseKnowledge = text => JSON.parse(text);""", "Contoh parser pembelajaran."),
                Snippet(2, "Loop latihan", "Python", """for concept in concepts:\n    print(concept)""", "Contoh iterasi."),
                Snippet(3, "Query konsep", "SQL", """SELECT * FROM knowledge WHERE tag = 'fisika';""", "Contoh pencarian."),
            )
        }
    }

    fun saveSnippet(item: Snippet) {
        val list = snippets().filterNot { it.id == item.id }.toMutableList()
        list.add(0, item)
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("title", it.title)
                    put("language", it.language)
                    put("code", it.code)
                    put("note", it.note)
                }
            )
        }
        prefs.edit().putString("snippets", array.toString()).apply()
    }

    fun logs(): List<ActivityLog> {
        val array = JSONArray(prefs.getString("logs", "[]"))
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(ActivityLog(o.getLong("id"), o.getString("action"), o.getString("title"), o.getLong("time")))
            }
        }.sortedByDescending { it.time }.take(100)
    }

    fun log(action: String, title: String) {
        val list = logs().toMutableList()
        list.add(0, ActivityLog(System.currentTimeMillis(), action, title))
        val array = JSONArray()
        list.take(100).forEach {
            array.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("action", it.action)
                    put("title", it.title)
                    put("time", it.time)
                }
            )
        }
        prefs.edit().putString("logs", array.toString()).apply()
    }

    fun clearDemoData() {
        prefs.edit().clear().apply()
    }

    private fun seedFormulas(): JSONArray =
        JSONArray().apply {
            put(
                JSONObject().apply {
                    put("id", 1001L)
                    put("name", "Hukum Newton II")
                    put("domain", "Fisika")
                    put("formula", "F = m × a")
                    put("meaning", "Resultan gaya menyebabkan percepatan.")
                    put("variables", "F = gaya; m = massa; a = percepatan")
                    put("example", "m = 2 kg, a = 3 m/s² → F = 6 N")
                }
            )
            put(
                JSONObject().apply {
                    put("id", 1002L)
                    put("name", "Kecepatan")
                    put("domain", "Fisika")
                    put("formula", "v = d / t")
                    put("meaning", "Kecepatan rata-rata adalah jarak dibagi waktu.")
                    put("variables", "v = kecepatan; d = jarak; t = waktu")
                    put("example", "d = 20 m, t = 4 s → v = 5 m/s")
                }
            )
        }

    private fun seedNodes(): JSONArray =
        JSONArray().apply {
            listOf(
                KnowledgeNode(1, "Fisika", "F = ma", "Hubungan gaya, massa, dan percepatan."),
                KnowledgeNode(2, "Kecepatan", "v = d/t", "Perubahan jarak per satuan waktu."),
                KnowledgeNode(3, "Matematika", "x / y", "Bahasa simbolik untuk hubungan besaran.")
            ).forEach {
                put(
                    JSONObject().apply {
                        put("id", it.id)
                        put("name", it.name)
                        put("representation", it.representation)
                        put("definition", it.definition)
                        put("confidence", it.confidence)
                    }
                )
            }
        }
}

object Ids {
    fun next(): Long = System.currentTimeMillis()
}
