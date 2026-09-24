package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.ui.theme.NierPaletteNight
import ru.nya.nyeios.ui.theme.NierPaletteRegular
import ru.nya.nyeios.ui.theme.NierThemeMode

class ThemeManagerTest {

    @Test
    fun `theme modes include regular and night`() {
        val modes = NierThemeMode.entries
        assertEquals(2, modes.size)
        assertEquals("regular", NierThemeMode.REGULAR.id)
        assertEquals("night", NierThemeMode.NIGHT.id)
    }

    @Test
    fun `regular theme has sand background and dark primary`() {
        val bg = NierPaletteRegular.bg
        val dark = NierPaletteRegular.dark

        // In Regular theme, background is light sand and primary text/dark is dark charcoal
        assertTrue("Regular bg red component should be high", bg.red > 0.7f)
        assertTrue("Regular dark red component should be low", dark.red < 0.3f)
    }

    @Test
    fun `night theme inverts background to dark and text to sand`() {
        val bg = NierPaletteNight.bg
        val dark = NierPaletteNight.dark

        // In Night theme, background is dark charcoal and primary text/dark is light sand
        assertTrue("Night bg red component should be low", bg.red < 0.3f)
        assertTrue("Night dark (primary text) red component should be high", dark.red > 0.7f)
    }
}
