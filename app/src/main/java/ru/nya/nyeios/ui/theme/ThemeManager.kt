package ru.nya.nyeios.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class NierThemeMode(val id: String, val title: String, val description: String) {
    REGULAR(
        id = "regular",
        title = "YoRHa Regular",
        description = "Классическая индустриальная палитра: светлый песочно-оливковый фон и контрастные темные элементы"
    ),
    NIGHT(
        id = "night",
        title = "YoRHa Night",
        description = "Инвертированная темная палитра: глубокий темный фон и теплые бежевые акценты"
    ),
    BLACK(
        id = "black",
        title = "YoRHa Black",
        description = "AMOLED-палитра: 100% черный фон с контрастными элементами темного цвета палитры YoRHa"
    ),
    RETRO(
        id = "retro",
        title = "YoRHa Retro",
        description = "Аутентичная винтажная палитра терминалов YoRHa: теплый песочный фон, глубокие чернила и насыщенный синий"
    ),
    NYC_MODERN(
        id = "nyc_modern",
        title = "NyC-modern",
        description = "Мягкая скругленная тема по мокапу Night Skeuomorph: циановый акцент, объемные панели. ВНИМАНИЕ: требуется перезагрузка приложения для полного применения."
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
