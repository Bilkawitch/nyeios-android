package ru.nya.nyeios.ui.language

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

enum class AppLanguage(val code: String, val titleResName: String, val nativeTitle: String) {
    SYSTEM("system", "language_system_title", "По умолчанию (система)"),
    RU("ru", "language_ru_title", "Русский"),
    EN("en", "language_en_title", "English")
}

object LanguageManager {
    private const val PREFS_NAME = "nyeios_language_prefs"
    private const val KEY_LANGUAGE = "selected_language"

    var currentLanguage: AppLanguage by mutableStateOf(AppLanguage.SYSTEM)
        private set

    val effectiveLocale: Locale
        get() = when (currentLanguage) {
            AppLanguage.RU -> Locale("ru")
            AppLanguage.EN -> Locale("en")
            AppLanguage.SYSTEM -> Locale.getDefault()
        }

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.code)
        currentLanguage = AppLanguage.entries.firstOrNull { it.code == saved } ?: AppLanguage.SYSTEM
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        currentLanguage = language
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.code)
            .apply()
    }

    fun applyLocaleContext(context: Context, language: AppLanguage = currentLanguage): Context {
        val targetLocale = when (language) {
            AppLanguage.RU -> Locale("ru")
            AppLanguage.EN -> Locale("en")
            AppLanguage.SYSTEM -> Locale.getDefault()
        }
        Locale.setDefault(targetLocale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(targetLocale)
        config.setLayoutDirection(targetLocale)
        return context.createConfigurationContext(config)
    }
}
