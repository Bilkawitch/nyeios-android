package ru.nya.nyeios.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.nya.nyeios.R

enum class NierThemeMode(val id: String, val title: String, val descResId: Int) {
    REGULAR(
        id = "regular",
        title = "YoRHa Regular",
        descResId = R.string.theme_desc_regular
    ),
    NIGHT(
        id = "night",
        title = "YoRHa Night",
        descResId = R.string.theme_desc_night
    ),
    BLACK(
        id = "black",
        title = "YoRHa Black",
        descResId = R.string.theme_desc_black
    ),
    RETRO(
        id = "retro",
        title = "YoRHa Retro",
        descResId = R.string.theme_desc_retro
    ),
    NYC_MODERN(
        id = "nyc_modern",
        title = "NyC-modern",
        descResId = R.string.theme_desc_nyc_modern
    )
}

object ThemeManager {
    private const val PREFS_NAME = "nyeios_theme_prefs"
    private const val KEY_THEME = "selected_theme_mode"

    var currentTheme: NierThemeMode by mutableStateOf(NierThemeMode.REGULAR)
        private set

    fun init(context: Context) {
        UiPreferencesManager.init(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_THEME, NierThemeMode.REGULAR.id)
        currentTheme = when (saved) {
            NierThemeMode.NIGHT.id -> NierThemeMode.NIGHT
            NierThemeMode.BLACK.id -> NierThemeMode.BLACK
            NierThemeMode.RETRO.id -> NierThemeMode.RETRO
            NierThemeMode.NYC_MODERN.id -> NierThemeMode.NYC_MODERN
            else -> NierThemeMode.REGULAR
        }
    }

    fun setTheme(context: Context, mode: NierThemeMode) {
        currentTheme = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, mode.id)
            .apply()
    }
}
