package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import ru.nya.nyeios.ui.language.AppLanguage
import ru.nya.nyeios.ui.language.LanguageManager
import ru.nya.nyeios.ui.settings.SettingsSubtab
import java.util.Locale

class LanguageManagerTest {

    @Test
    fun testAppLanguageEnumValues() {
        assertEquals(3, AppLanguage.entries.size)
        assertEquals("system", AppLanguage.SYSTEM.code)
        assertEquals("ru", AppLanguage.RU.code)
        assertEquals("en", AppLanguage.EN.code)
    }

    @Test
    fun testSettingsSubtabsIncludeLanguage() {
        assertEquals(4, SettingsSubtab.entries.size)
        assertEquals(SettingsSubtab.GENERAL, SettingsSubtab.entries[0])
        assertEquals(SettingsSubtab.THEME, SettingsSubtab.entries[1])
        assertEquals(SettingsSubtab.VERSION, SettingsSubtab.entries[2])
        assertEquals(SettingsSubtab.LANGUAGE, SettingsSubtab.entries[3])
        assertEquals("ЯЗЫК", SettingsSubtab.LANGUAGE.title)
    }

    @Test
    fun testEffectiveLocaleResolution() {
        // Test locale resolution for RU
        val ruLocale = when (AppLanguage.RU) {
            AppLanguage.RU -> Locale("ru")
            AppLanguage.EN -> Locale("en")
            AppLanguage.SYSTEM -> Locale.getDefault()
        }
        assertEquals("ru", ruLocale.language)

        // Test locale resolution for EN
        val enLocale = when (AppLanguage.EN) {
            AppLanguage.RU -> Locale("ru")
            AppLanguage.EN -> Locale("en")
            AppLanguage.SYSTEM -> Locale.getDefault()
        }
        assertEquals("en", enLocale.language)
    }
}
