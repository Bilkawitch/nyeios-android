package ru.nya.nyeios.data.model

import java.io.File

data class GithubRelease(
    val tagName: String,
    val body: String,
    val assets: List<GithubAsset>
)

data class GithubAsset(
    val name: String,
    val browserDownloadUrl: String,
    val size: Long
)

data class UpdateInfo(
    val version: String,
    val changelog: String,
    val apkUrl: String,
    val apkSize: Long
)

sealed interface UpdateUiState {
    data object Idle : UpdateUiState

    /** Доступно обновление, баннер показан */
    data class UpdateAvailable(val info: UpdateInfo) : UpdateUiState

    /** Юзер нажал X — баннер скрыт, иконка пульсирует */
    data class Dismissed(val info: UpdateInfo) : UpdateUiState

    /** Идёт скачивание APK */
    data class Downloading(
        val progress: Float,
        val bytesNow: Long,
        val bytesTotal: Long,
        val info: UpdateInfo
    ) : UpdateUiState

    /** APK скачан, ждёт установки */
    data class ReadyToInstall(val file: File, val info: UpdateInfo) : UpdateUiState

    data class Error(val message: String) : UpdateUiState
}
