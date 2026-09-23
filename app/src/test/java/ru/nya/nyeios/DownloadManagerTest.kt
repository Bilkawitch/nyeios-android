package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.download.DownloadState
import ru.nya.nyeios.data.download.InternalDownloadManager
import java.io.File

class DownloadManagerTest {

    @Test
    fun testSanitizeFileName_removesIllegalChars() {
        val rawName = "Задание: №1 / Тема * \"Важно\" <v1> | ? .pdf"
        val sanitized = InternalDownloadManager.sanitizeFileName(rawName)
        assertTrue(!sanitized.contains(":"))
        assertTrue(!sanitized.contains("/"))
        assertTrue(!sanitized.contains("*"))
        assertTrue(!sanitized.contains("\""))
        assertTrue(!sanitized.contains("<"))
        assertTrue(!sanitized.contains(">"))
        assertTrue(!sanitized.contains("|"))
        assertTrue(!sanitized.contains("?"))
        assertEquals("Задание_ №1 _ Тема _ _Важно_ _v1_ _ _ .pdf", sanitized)
    }

    @Test
    fun testSanitizeFileName_emptyFallback() {
        val sanitized = InternalDownloadManager.sanitizeFileName("   ")
        assertTrue(sanitized.startsWith("file_"))
    }

    @Test
    fun testFormatFileSize() {
        assertEquals("0 Б", InternalDownloadManager.formatFileSize(0))
        assertEquals("512 Б", InternalDownloadManager.formatFileSize(512))
        assertEquals("2.0 КБ", InternalDownloadManager.formatFileSize(2048))
        assertEquals("1.5 МБ", InternalDownloadManager.formatFileSize((1.5 * 1024 * 1024).toLong()))
        assertEquals("12.8 МБ", InternalDownloadManager.formatFileSize((12.8 * 1024 * 1024).toLong()))
    }

    @Test
    fun testDownloadStateHierarchy() {
        val idle: DownloadState = DownloadState.Idle
        assertEquals(DownloadState.Idle, idle)

        val downloading: DownloadState = DownloadState.Downloading(
            progress = 0.45f,
            bytesDownloaded = 450,
            totalBytes = 1000
        )
        assertTrue(downloading is DownloadState.Downloading)
        assertEquals(0.45f, (downloading as DownloadState.Downloading).progress, 0.001f)

        val dummyFile = File("test.pdf")
        val completed: DownloadState = DownloadState.Completed(dummyFile, 1024)
        assertTrue(completed is DownloadState.Completed)
        assertEquals(1024L, (completed as DownloadState.Completed).sizeBytes)

        val failed: DownloadState = DownloadState.Failed("Сетевая ошибка")
        assertTrue(failed is DownloadState.Failed)
        assertEquals("Сетевая ошибка", (failed as DownloadState.Failed).error)
    }
}
