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

    /** Guarantees at least one profile exists (first run / upgrade from a version without profiles). */
    suspend fun ensureProfile() = update { s ->
        if (s.profiles.isNotEmpty()) s
        else {
            val me = Profile(id = "default", name = "Me", avatar = 0, createdAt = System.currentTimeMillis())
            s.copy(profiles = listOf(me), activeProfileId = me.id)
        }
    }

    suspend fun addProfile(name: String, avatar: Int, isKids: Boolean): Profile {
        val p = Profile(UUID.randomUUID().toString(), name.trim().ifBlank { "Profile" }, avatar, isKids, System.currentTimeMillis())
        update { it.copy(profiles = it.profiles + p) }
        return p
    }

    suspend fun updateProfile(p: Profile) = update { s -> s.copy(profiles = s.profiles.map { if (it.id == p.id) p else it }) }

    suspend fun removeProfile(id: String) = update { s ->
        val remaining = s.profiles.filterNot { it.id == id }
        s.copy(
            profiles = remaining,
            activeProfileId = if (s.activeProfileId == id) remaining.firstOrNull()?.id.orEmpty() else s.activeProfileId,
        )
    }

    suspend fun setActiveProfile(id: String) = update { it.copy(activeProfileId = id) }
}
