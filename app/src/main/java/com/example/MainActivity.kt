package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.ThemePreferencesRepository
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.GoPrimeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeRepo = remember(applicationContext) {
                ThemePreferencesRepository(applicationContext)
            }
            val scope = rememberCoroutineScope()
            val themeMode by themeRepo.themeModeFlow.collectAsStateWithLifecycle(
                initialValue = AppThemeMode.SYSTEM
            )
            val isDarkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            GoPrimeTheme(darkTheme = isDarkTheme) {
                AppNavigation(
                    themeMode = themeMode,
                    isDarkTheme = isDarkTheme,
                    onChangeThemeMode = { mode ->
                        scope.launch { themeRepo.setThemeMode(mode) }
                    }
                )
            }
        }
    }
}
