package ru.nya.nyeios

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.ui.update.AnnouncementSessionState

class AnnouncementSessionStateTest {

    @Test
    fun `banner is visible initially when not permanently dismissed`() {
        val state = AnnouncementSessionState(
            isPermanentlyDismissed = { false },
            savePermanentlyDismissed = {}
        )
        assertTrue(state.isVisible.value)
    }

    @Test
    fun `remindLater hides banner for current launch without permanent opt-out`() {
        var persistedOptOut = false
        val firstLaunch = AnnouncementSessionState(
            isPermanentlyDismissed = { persistedOptOut },
            savePermanentlyDismissed = { persistedOptOut = true }
        )

        assertTrue(firstLaunch.isVisible.value)
        firstLaunch.remindLater()

        assertFalse(firstLaunch.isVisible.value)
        assertFalse(persistedOptOut)

        // Simulates full app restart
        val secondLaunch = AnnouncementSessionState(
            isPermanentlyDismissed = { persistedOptOut },
            savePermanentlyDismissed = { persistedOptOut = true }
        )
        assertTrue(secondLaunch.isVisible.value)
    }

    @Test
    fun `dismissForever hides banner permanently across restarts`() {
        var persistedOptOut = false
        val firstLaunch = AnnouncementSessionState(
            isPermanentlyDismissed = { persistedOptOut },
            savePermanentlyDismissed = { persistedOptOut = true }
        )

        firstLaunch.dismissForever()

        assertFalse(firstLaunch.isVisible.value)
        assertTrue(persistedOptOut)

        // Simulates full app restart
        val secondLaunch = AnnouncementSessionState(
            isPermanentlyDismissed = { persistedOptOut },
            savePermanentlyDismissed = { persistedOptOut = true }
        )
        assertFalse(secondLaunch.isVisible.value)
    }
}
