package com.abc.expensetracker

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class SettingsStore(private val context: Context) {
    private val importDoneKey = booleanPreferencesKey("historical_import_done")
    private val themeKey = stringPreferencesKey("theme_mode")

    val historicalImportDone: Flow<Boolean> =
        context.dataStore.data.map { it[importDoneKey] ?: false }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        runCatching { ThemeMode.valueOf(it[themeKey] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun setHistoricalImportDone() {
        context.dataStore.edit { it[importDoneKey] = true }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }
}
