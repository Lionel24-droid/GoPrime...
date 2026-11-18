package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AppThemeMode(val labelFr: String) {
    SYSTEM("Système"),
    LIGHT("Clair"),
    DARK("Sombre");

    companion object {
        fun fromRaw(raw: String?): AppThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

private val Context.goPrimeThemeDataStore by preferencesDataStore(name = "goprime_settings")

class ThemePreferencesRepository(private val context: Context) {

    val themeModeFlow: Flow<AppThemeMode> = context.goPrimeThemeDataStore.data.map { prefs ->
        AppThemeMode.fromRaw(prefs[THEME_MODE_KEY])
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.goPrimeThemeDataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode.name
        }
    }

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("app_theme_mode")
    }
}
