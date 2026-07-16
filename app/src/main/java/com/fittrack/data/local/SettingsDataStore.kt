package com.fittrack.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

// -- Roadmap 1.5: optional sound at the end of rest -----------------------------
// A single boolean toggle doesn't warrant a new Room column + migration, so this uses
// the DataStore dependency that was already in build.gradle.kts but unused until now.
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val REST_SOUND_ENABLED = booleanPreferencesKey("rest_sound_enabled")
    }

    val restSoundEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[Keys.REST_SOUND_ENABLED] ?: false }

    suspend fun setRestSoundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.REST_SOUND_ENABLED] = enabled }
    }
}
