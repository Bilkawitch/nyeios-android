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

    /** Device locale captured before the picker ever overrides [Locale.getDefault]. */
    private val deviceDefaultLocale: Locale = Locale.getDefault()

    var currentLanguage: AppLanguage by mutableStateOf(AppLanguage.SYSTEM)
        private set

    val effectiveLocale: Locale
        get() = localeFor(currentLanguage)

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.code)
        currentLanguage = AppLanguage.entries.firstOrNull { it.code == saved } ?: AppLanguage.SYSTEM
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        currentLanguage = language
        // Switches happen in-place (no Activity restart), so the process locale must follow here:
        // data-layer copy resolved through AppLocale has no Activity context of its own.
        applyProcessLocale(language)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.code)
            .apply()
    }

    /** Aligns [Locale.getDefault] with the picker without touching stored preferences. */
    fun applyProcessLocale(language: AppLanguage = currentLanguage) {
        Locale.setDefault(localeFor(language))
    }

    fun applyLocaleContext(context: Context, language: AppLanguage = currentLanguage): Context {
        val targetLocale = localeFor(language)
        Locale.setDefault(targetLocale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(targetLocale)
        config.setLayoutDirection(targetLocale)
        return context.createConfigurationContext(config)
    }

    private fun localeFor(language: AppLanguage): Locale = when (language) {
        AppLanguage.RU -> Locale("ru")
        AppLanguage.EN -> Locale("en")
        AppLanguage.SYSTEM -> deviceDefaultLocale
    }
}
