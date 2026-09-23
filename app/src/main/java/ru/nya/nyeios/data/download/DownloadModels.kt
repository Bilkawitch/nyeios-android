package ru.nya.nyeios.data.download

import java.io.File

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(
        val progress: Float, // 0.0f..1.0f, or -1f if unknown
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadState
    data class Completed(
        val file: File,
        val sizeBytes: Long
    ) : DownloadState
    data class Failed(
        val error: String
    ) : DownloadState
}

data class DownloadedFile(
    val id: String,
    val name: String,
    val url: String,
    val file: File,
    val sizeBytes: Long,
    val downloadedAt: Long,
    val mimeType: String
)
