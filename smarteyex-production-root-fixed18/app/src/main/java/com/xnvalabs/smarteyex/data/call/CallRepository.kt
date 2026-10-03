package com.xnvalabs.smarteyex.data.call

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.xnvalabs.smarteyex.core.AppDiagnostics
import com.xnvalabs.smarteyex.core.SecureStorage
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class QuickContact(val name: String, val phone: String)

object CallRepository {
    private const val KEY_CONTACTS = "call.contacts"
    private const val LEGACY_PREFS = "smarteyex_calls"
    private const val MAX_CONTACTS = 100
    private var initialized = false

    var contacts = mutableStateOf(emptyList<QuickContact>())
        private set

    fun init(context: Context) {
        if (initialized) return
        SecureStorage.init(context)
        val legacy = context.applicationContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val secure = SecureStorage.getString(KEY_CONTACTS)
        val raw = secure ?: legacy.getString("contacts", null)
        contacts.value = load(raw)
        val ready = secure != null || raw == null || SecureStorage.putStringSync(KEY_CONTACTS, raw)
        if (ready) legacy.edit().clear().apply()
        initialized = true
    }

    fun add(name: String, phone: String): Boolean {
        val cleanName = name.trim().take(100)
        val cleanPhone = phone.trim().take(30)
        if (cleanName.isBlank() || !cleanPhone.matches(Regex("[+0-9() .-]{3,30}"))) return false
        return persist((contacts.value + QuickContact(cleanName, cleanPhone)).distinctBy { it.phone }.takeLast(MAX_CONTACTS))
    }

    fun remove(contact: QuickContact): Boolean = persist(contacts.value - contact)
    fun clearAll(): Boolean = persist(emptyList(), synchronous = true)

    private fun persist(next: List<QuickContact>, synchronous: Boolean = false): Boolean {
        val serialized = JSONArray().apply {
            next.takeLast(MAX_CONTACTS).forEach { c ->
                put(JSONObject().apply { put("id", UUID.randomUUID().toString()); put("name", c.name); put("phone", c.phone) })
            }
        }.toString()
        val saved = runCatching {
            if (synchronous) SecureStorage.putStringSync(KEY_CONTACTS, serialized) else SecureStorage.putString(KEY_CONTACTS, serialized)
        }.onFailure { AppDiagnostics.warn("Call contacts persistence failed", it) }.getOrDefault(false)
        if (saved) contacts.value = next.takeLast(MAX_CONTACTS)
        return saved
    }

    private fun load(raw: String?): List<QuickContact> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val name = obj.optString("name").trim()
                val phone = obj.optString("phone").trim()
                if (name.isBlank() || phone.isBlank()) null else QuickContact(name.take(100), phone.take(30))
            }.distinctBy { it.phone }.takeLast(MAX_CONTACTS)
        }.getOrDefault(emptyList())
    }
}
