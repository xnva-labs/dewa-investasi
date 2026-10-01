package com.xnvalabs.smarteyex.data.call

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.xnvalabs.smarteyex.core.SecureStorage
import org.json.JSONArray
import org.json.JSONObject

/** A saved quick-dial contact. */
data class QuickContact(val name: String, val phone: String)

object CallRepository {
    private const val KEY_CONTACTS = "call.contacts"
    private const val LEGACY_PREFS = "smarteyex_call"
    private const val MAX_CONTACTS = 100
    private var initialized = false

    var contacts = mutableStateOf(emptyList<QuickContact>())
        private set

    fun init(context: Context) {
        if (initialized) return
        SecureStorage.init(context)
        val legacy = context.applicationContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val raw = SecureStorage.getString(KEY_CONTACTS) ?: legacy.getString("contacts", null)
        contacts.value = load(raw)
        val secureReady = SecureStorage.getString(KEY_CONTACTS) != null || (raw != null && SecureStorage.putStringSync(KEY_CONTACTS, raw))
        if (secureReady || raw == null) legacy.edit().clear().apply()
        initialized = true
    }

    fun add(name: String, phone: String): Boolean {
        val cleanName = name.trim().take(100)
        val cleanPhone = phone.trim().take(30)
        if (cleanName.isBlank() || !cleanPhone.matches(Regex("[+0-9() .-]{3,30}"))) return false
        persist((contacts.value + QuickContact(cleanName, cleanPhone)).distinctBy { it.phone }.takeLast(MAX_CONTACTS))
        return true
    }

    fun remove(contact: QuickContact) = persist(contacts.value - contact)
    fun clearAll() = persist(emptyList(), synchronous = true)

    private fun persist(next: List<QuickContact>, synchronous: Boolean = false) {
        contacts.value = next
        val serialized = JSONArray().apply {
            next.forEach { c -> put(JSONObject().apply { put("name", c.name); put("phone", c.phone) }) }
        }.toString()
        if (synchronous) SecureStorage.putStringSync(KEY_CONTACTS, serialized) else SecureStorage.putString(KEY_CONTACTS, serialized)
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
