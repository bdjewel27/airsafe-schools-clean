package com.airsafe.schools.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.airSafeDataStore: DataStore<Preferences> by preferencesDataStore(name = "airsafe_preferences")

class AirSafeDataStore(private val context: Context) {
    private val dataStore = context.airSafeDataStore

    suspend fun saveStringPreference(
        keyName: String,
        value: String,
    ) {
        val prefKey = stringPreferencesKey(keyName)
        dataStore.edit { preferences ->
            preferences[prefKey] = value
        }
    }

    suspend fun getStringPreference(
        keyName: String,
        defaultValue: String? = null,
    ): String? {
        val prefKey = stringPreferencesKey(keyName)
        return try {
            val preferences =
                dataStore.data
                    .catch { exception ->
                        if (exception is IOException) {
                            emit(emptyPreferences())
                        } else {
                            throw exception
                        }
                    }
                    .first()
            preferences[prefKey] ?: defaultValue
        } catch (_: Exception) {
            defaultValue
        }
    }

    fun observeStringPreference(
        keyName: String,
        defaultValue: String? = null,
    ): Flow<String?> {
        val prefKey = stringPreferencesKey(keyName)
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[prefKey] ?: defaultValue
            }
    }

    suspend fun removePreference(keyName: String) {
        val prefKey = stringPreferencesKey(keyName)
        dataStore.edit { preferences ->
            preferences.remove(prefKey)
        }
    }

    suspend fun clearAll() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
