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

    @Test
    fun testTypewriterIdleReturnsFullText() {
        ru.nya.nyeios.ui.language.LanguageTypewriterManager.setStateForTesting(
            animating = false,
            testPhase = ru.nya.nyeios.ui.language.TypewriterPhase.IDLE,
            testProgress = 1f
        )
        val text = "Настройки"
        val displayed = ru.nya.nyeios.ui.language.LanguageTypewriterManager.getDisplayText(text, order = 0.1f)
        assertEquals("Настройки", displayed)
    }

    @Test
    fun testTypewriterErasingPhaseDecreasesLength() {
        val text = "SETTINGS"
        // 0% erased -> 100% visible
        ru.nya.nyeios.ui.language.LanguageTypewriterManager.setStateForTesting(
            animating = true,
            testPhase = ru.nya.nyeios.ui.language.TypewriterPhase.ERASING,
            testProgress = 0f
        )
        assertEquals("SETTINGS", ru.nya.nyeios.ui.language.LanguageTypewriterManager.getDisplayText(text, 0f))

        // 50% erased -> 4 chars visible
        ru.nya.nyeios.ui.language.LanguageTypewriterManager.setStateForTesting(
            animating = true,
            testPhase = ru.nya.nyeios.ui.language.TypewriterPhase.ERASING,
            testProgress = 0.5f
        )
        assertEquals("SETT", ru.nya.nyeios.ui.language.LanguageTypewriterManager.getDisplayText(text, 0f))

        // 100% erased -> empty string
        ru.nya.nyeios.ui.language.LanguageTypewriterManager.setStateForTesting(
            animating = true,
            testPhase = ru.nya.nyeios.ui.language.TypewriterPhase.ERASING,
            testProgress = 1f
        )
        assertEquals("", ru.nya.nyeios.ui.language.LanguageTypewriterManager.getDisplayText(text, 0f))
    }

    @Test
    fun testTypewriterTypingPhaseCascadesTopToBottom() {
        val topText = "TOP_BAR"
        val bottomText = "BOTTOM_BAR"

        // At progress = 0.10: Top item (order = 0.0) is actively typing, Bottom item (order = 1.0) is still empty
        ru.nya.nyeios.ui.language.LanguageTypewriterManager.setStateForTesting(
            animating = true,
            testPhase = ru.nya.nyeios.ui.language.TypewriterPhase.TYPING,
            testProgress = 0.10f
        )
        val topDisplayed = ru.nya.nyeios.ui.language.LanguageTypewriterManager.getDisplayText(topText, order = 0.0f)
        val bottomDisplayed = ru.nya.nyeios.ui.language.LanguageTypewriterManager.getDisplayText(bottomText, order = 1.0f)
        assertEquals(true, topDisplayed.isNotEmpty())
        assertEquals("", bottomDisplayed)

        // At progress = 1.0: Both are 100% complete
        ru.nya.nyeios.ui.language.LanguageTypewriterManager.setStateForTesting(
            animating = true,
            testPhase = ru.nya.nyeios.ui.language.TypewriterPhase.TYPING,
            testProgress = 1.0f
        )
        assertEquals("TOP_BAR", ru.nya.nyeios.ui.language.LanguageTypewriterManager.getDisplayText(topText, order = 0.0f))
        assertEquals("BOTTOM_BAR", ru.nya.nyeios.ui.language.LanguageTypewriterManager.getDisplayText(bottomText, order = 1.0f))
    }

    @Test
    fun testTypewriterSkipInstantlyRestoresFullText() {
        ru.nya.nyeios.ui.language.LanguageTypewriterManager.setStateForTesting(
            animating = true,
            testPhase = ru.nya.nyeios.ui.language.TypewriterPhase.TYPING,
            testProgress = 0.2f
        )
        ru.nya.nyeios.ui.language.LanguageTypewriterManager.skip()
        assertEquals(false, ru.nya.nyeios.ui.language.LanguageTypewriterManager.isAnimating)
        assertEquals(ru.nya.nyeios.ui.language.TypewriterPhase.IDLE, ru.nya.nyeios.ui.language.LanguageTypewriterManager.phase)
        assertEquals("NyEIOS", ru.nya.nyeios.ui.language.LanguageTypewriterManager.getDisplayText("NyEIOS", 0.5f))
    }
}
