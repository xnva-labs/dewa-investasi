package com.xnvalabs.smarteyex.data.emergency

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.xnvalabs.smarteyex.core.AppDiagnostics
import com.xnvalabs.smarteyex.core.SecureStorage

/** One saved emergency contact, encrypted locally. */
data class EmergencyContact(val name: String, val phone: String)

object EmergencyRepository {
    private const val KEY_NAME = "emergency.name"
    private const val KEY_PHONE = "emergency.phone"
    private const val LEGACY_PREFS = "smarteyex_emergency"
    private var initialized = false

    var contact = mutableStateOf<EmergencyContact?>(null)
        private set

    fun init(context: Context) {
        if (initialized) return
        SecureStorage.init(context)
        val legacy = context.applicationContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        appLegacyPrefs = legacy
        val name = SecureStorage.getString(KEY_NAME) ?: legacy.getString("name", null)
        val phone = SecureStorage.getString(KEY_PHONE) ?: legacy.getString("phone", null)
        contact.value = if (!name.isNullOrBlank() && !phone.isNullOrBlank()) EmergencyContact(name.take(100), phone.take(30)) else null
        if (!SecureStorage.getString(KEY_NAME).isNullOrBlank() && !SecureStorage.getString(KEY_PHONE).isNullOrBlank()) {
            // already migrated
        } else if (!name.isNullOrBlank() && !phone.isNullOrBlank()) {
            val nameSaved = SecureStorage.putStringSync(KEY_NAME, name.take(100))
            val phoneSaved = SecureStorage.putStringSync(KEY_PHONE, phone.take(30))
            if (!nameSaved || !phoneSaved) {
                AppDiagnostics.warn("Emergency contact migration kept legacy data")
                initialized = true
                return
            }
        }
        legacy.edit().clear().apply()
        initialized = true
    }

    fun save(name: String, phone: String): Boolean {
        val cleanName = name.trim().take(100)
        val cleanPhone = phone.trim().take(30)
        if (cleanName.isBlank() || !cleanPhone.matches(Regex("[+0-9() .-]{3,30}"))) return false
        val saved = runCatching {
            SecureStorage.putStringsSync(
                mapOf(KEY_NAME to cleanName, KEY_PHONE to cleanPhone),
            )
        }.onFailure { AppDiagnostics.warn("Emergency contact persistence failed", it) }.getOrDefault(false)
        if (!saved) return false
        contact.value = EmergencyContact(cleanName, cleanPhone)
        return true
    }

    fun clear(): Boolean {
        val nameRemoved = SecureStorage.removeSync(KEY_NAME)
        val phoneRemoved = SecureStorage.removeSync(KEY_PHONE)
        val legacyCleared = runCatching {
            appLegacyPrefs?.edit()?.clear()?.commit() ?: true
        }.getOrDefault(false)
        val ok = nameRemoved && phoneRemoved && legacyCleared
        if (ok) contact.value = null
        else AppDiagnostics.warn("Emergency contact clear did not complete")
        return ok
    }

    private var appLegacyPrefs: android.content.SharedPreferences? = null
}
