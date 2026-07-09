package com.example.worktrack.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("settings")

enum class ThemeMode { System, Light, Dark }
enum class LanguageMode { RU, EN, ES }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val language: LanguageMode = LanguageMode.RU
)

class SettingsStore(private val context: Context) {
    private val keyTheme = stringPreferencesKey("theme")
    private val keyLanguage = stringPreferencesKey("language")

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[keyTheme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
            language = prefs[keyLanguage]?.let { runCatching { LanguageMode.valueOf(it) }.getOrNull() } ?: LanguageMode.RU
        )
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.settingsDataStore.edit { it[keyTheme] = mode.name }
    }

    suspend fun setLanguage(language: LanguageMode) {
        context.settingsDataStore.edit { it[keyLanguage] = language.name }
    }
}
