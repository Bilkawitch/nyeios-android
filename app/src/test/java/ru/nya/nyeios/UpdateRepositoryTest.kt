package ru.nya.nyeios

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.update.AppVersion

class UpdateRepositoryTest {

    @Test
    fun `version with letter suffix is newer than base version`() {
        val v012 = AppVersion.parse("0.1.2")
        val v012a = AppVersion.parse("0.1.2a")
        assertTrue("0.1.2a should be newer than 0.1.2", v012a > v012)
    }

    @Test
    fun `identical versions return false for isNewer`() {
        val v1 = AppVersion.parse("0.1.2a")
        val v2 = AppVersion.parse("0.1.2a")
        assertFalse("Same versions should not be newer", v1 > v2)
    }

    @Test
    fun `next letter suffix is newer`() {
        val v012a = AppVersion.parse("0.1.2a")
        val v012b = AppVersion.parse("0.1.2b")
        assertTrue("0.1.2b should be newer than 0.1.2a", v012b > v012a)
    }

    @Test
    fun `higher patch number is newer than letter suffix`() {
        val v012a = AppVersion.parse("0.1.2a")
        val v013 = AppVersion.parse("0.1.3")
        assertTrue("0.1.3 should be newer than 0.1.2a", v013 > v012a)
    }

    @Test
    fun `base version is not newer than letter suffix`() {
        val v012 = AppVersion.parse("0.1.2")
        val v012a = AppVersion.parse("0.1.2a")
        assertFalse("0.1.2 should NOT be newer than 0.1.2a", v012 > v012a)
    }

    @Test
    fun `leading v prefix is stripped correctly`() {
        val vRemote = AppVersion.parse("v0.1.2a")
        val vLocal = AppVersion.parse("0.1.2")
        assertTrue("v0.1.2a should be newer than 0.1.2", vRemote > vLocal)
    }

    @Test
    fun `multi-digit patch comparison works`() {
        val v9 = AppVersion.parse("0.1.9")
        val v10 = AppVersion.parse("0.1.10")
        assertTrue("0.1.10 should be newer than 0.1.9", v10 > v9)
    }

    @Test
    fun `major version bump comparison works`() {
        val vOld = AppVersion.parse("0.9.9")
        val vNew = AppVersion.parse("1.0.0")
        assertTrue("1.0.0 should be newer than 0.9.9", vNew > vOld)
    }

    @Test
    fun `version 0_1_4a is newer than 0_1_4`() {
        val vBase = AppVersion.parse("0.1.4")
        val vAlpha = AppVersion.parse("0.1.4a")
        assertTrue("0.1.4a should be newer than 0.1.4", vAlpha > vBase)
        assertFalse("0.1.4 should NOT be newer than 0.1.4a", vBase > vAlpha)
    }

    @Test
    fun `remote v0_1_4 is not newer than local 0_1_4a`() {
        val vRemote = AppVersion.parse("v0.1.4")
        val vLocal = AppVersion.parse("0.1.4a")
        assertFalse("v0.1.4 should NOT trigger update when running 0.1.4a", vRemote > vLocal)
    }

    @Test
    fun `same version with v prefix is not newer`() {
        val vRemote = AppVersion.parse("v0.1.4a")
        val vLocal = AppVersion.parse("0.1.4a")
        assertFalse("v0.1.4a should NOT trigger update when running 0.1.4a", vRemote > vLocal)
    }
}

