package com.retailassistant.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val context: Context) {
    companion object {
        private val Context.dataStore by preferencesDataStore("settings")
        
        private val PERMANENT_STORAGE_ENABLED = booleanPreferencesKey("permanent_storage_enabled")
        private val EXTRACTION_ENABLED = booleanPreferencesKey("extraction_enabled")
        private val DARK_MODE_ENABLED = booleanPreferencesKey("dark_mode_enabled")
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }

    val permanentStorageEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[PERMANENT_STORAGE_ENABLED] ?: false }

    val extractionEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[EXTRACTION_ENABLED] ?: true }

    val darkModeEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[DARK_MODE_ENABLED] ?: false }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[NOTIFICATIONS_ENABLED] ?: true }

    suspend fun setPermanentStorageEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PERMANENT_STORAGE_ENABLED] = enabled
        }
    }

    suspend fun setExtractionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[EXTRACTION_ENABLED] = enabled
        }
    }

    suspend fun setDarkModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE_ENABLED] = enabled
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED] = enabled
        }
    }
}