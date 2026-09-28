package ru.nya.nyeios.data

import java.util.Locale

/**
 * Data-layer modules have no access to the Activity's localized Context (the application context
 * keeps the system configuration), so user-facing text produced outside Compose is resolved from
 * the process default locale, which LanguageManager keeps in sync with the in-app language picker.
 */
object AppLocale {

    val isEnglish: Boolean
        get() = Locale.getDefault().language.equals("en", ignoreCase = true)

    fun pick(ru: String, en: String): String = if (isEnglish) en else ru
}
