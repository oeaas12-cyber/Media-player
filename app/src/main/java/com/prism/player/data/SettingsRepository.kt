package com.prism.player.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore("prism_settings")

class SettingsRepository(private val context: Context) {
    private val key = stringPreferencesKey("app_settings")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        prefs[key]?.let { runCatching { Net.json.decodeFromString(AppSettings.serializer(), it) }.getOrNull() }
            ?: AppSettings()
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val cur = prefs[key]?.let { runCatching { Net.json.decodeFromString(AppSettings.serializer(), it) }.getOrNull() }
                ?: AppSettings()
            var next = transform(cur)
            if (next.deviceId.isBlank()) next = next.copy(deviceId = UUID.randomUUID().toString())
            prefs[key] = Net.json.encodeToString(AppSettings.serializer(), next)
        }
    }

    suspend fun ensureDeviceId() = update { it }
}
