package com.example.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppTheme(val id: String, val displayName: String) {
    NEON_LIME("neon_lime", "Neon Lime"),
    ELECTRIC_CYAN("electric_cyan", "Electric Cyan"),
    PULSE_RED("pulse_red", "Pulse Red"),
    MUTED_AMBER("muted_amber", "Muted Amber"),
    SOFT_GREEN("soft_green", "Soft Green")
}

enum class ThemeMode(val id: String, val displayName: String) {
    SYSTEM("system", "System/Auto"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark")
}

object ThemeManager {
    private const val PREFS_NAME = "pacepulse_theme_prefs"
    private const val KEY_THEME = "selected_theme_id"
    private const val KEY_THEME_MODE = "selected_theme_mode_id"

    private val _currentTheme = MutableStateFlow(AppTheme.NEON_LIME)
    val currentTheme: StateFlow<AppTheme> = _currentTheme.asStateFlow()

    private val _currentThemeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val currentThemeMode: StateFlow<ThemeMode> = _currentThemeMode.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedThemeId = prefs?.getString(KEY_THEME, AppTheme.NEON_LIME.id) ?: AppTheme.NEON_LIME.id
        setTheme(savedThemeId)
        
        val savedThemeModeId = prefs?.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.id) ?: ThemeMode.SYSTEM.id
        setThemeMode(savedThemeModeId)
    }

    fun setTheme(themeId: String) {
        val theme = AppTheme.entries.find { it.id == themeId } ?: AppTheme.NEON_LIME
        _currentTheme.value = theme
        prefs?.edit()?.putString(KEY_THEME, theme.id)?.apply()
    }

    fun setThemeMode(modeId: String) {
        val mode = ThemeMode.entries.find { it.id == modeId } ?: ThemeMode.SYSTEM
        _currentThemeMode.value = mode
        prefs?.edit()?.putString(KEY_THEME_MODE, mode.id)?.apply()
    }
}
