package com.monandroido.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.monandroido.data.model.AppSettings
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { prefs ->
            AppSettings(
                developerFeePercent = prefs[DEVELOPER_FEE_KEY] ?: 10,
                advancedModeEnabled = prefs[ADVANCED_MODE_KEY] ?: false,
            )
        }

    suspend fun setDeveloperFeePercent(value: Int) {
        context.settingsDataStore.edit { it[DEVELOPER_FEE_KEY] = value.coerceIn(0, 100) }
    }

    suspend fun setAdvancedModeEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[ADVANCED_MODE_KEY] = value }
    }

    companion object {
        private val DEVELOPER_FEE_KEY = intPreferencesKey("developer_fee_percent")
        private val ADVANCED_MODE_KEY = booleanPreferencesKey("advanced_mode_enabled")
    }
}
