package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.ui.settings.ChangelogHistory

class ChangelogHistoryTest {

    @Test
    fun testLatestReleaseIs023() {
        val releases = ChangelogHistory.releases
        assertTrue("Changelog should contain releases", releases.isNotEmpty())

        val latest = releases.first()
        assertEquals("Latest release must be 0.2.3", "0.2.3", latest.version)
        assertTrue("Latest release should have isLatest = true", latest.isLatest)
    }

    @Test
    fun testReleasesIntegrity() {
        val releases = ChangelogHistory.releases
        val versions = mutableSetOf<String>()

        releases.forEach { release ->
            assertTrue("Version name must not be blank", release.version.isNotBlank())
            assertTrue("Release date must not be blank", release.releaseDate.isNotBlank())
            assertTrue("Release ${release.version} must have sections", release.sections.isNotEmpty())
            assertTrue("Version ${release.version} must be unique", versions.add(release.version))

            release.sections.forEach { section ->
                assertTrue("Section title in ${release.version} must not be blank", section.title.isNotBlank())
                assertTrue("Section '${section.title}' in ${release.version} must have items", section.items.isNotEmpty())
                section.items.forEach { item ->
                    assertTrue("Item in '${section.title}' must not be blank", item.isNotBlank())
                }
            }
        }
    }
}
