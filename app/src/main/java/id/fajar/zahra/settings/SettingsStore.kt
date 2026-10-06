package id.fajar.zahra.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("zahra_settings")

class SettingsStore(private val context:Context){
    private val notificationsKey=booleanPreferencesKey("notifications")
    private val hapticsKey=booleanPreferencesKey("haptics")
    val notifications:Flow<Boolean>=context.settingsStore.data.map{it[notificationsKey]?:false}
    val haptics:Flow<Boolean>=context.settingsStore.data.map{it[hapticsKey]?:true}
    suspend fun setNotifications(value:Boolean){context.settingsStore.edit{it[notificationsKey]=value}}
    suspend fun setHaptics(value:Boolean){context.settingsStore.edit{it[hapticsKey]=value}}
}
