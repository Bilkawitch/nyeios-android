package ru.nya.nyeios.ui.update

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.nya.nyeios.data.download.DownloadState
import ru.nya.nyeios.data.download.InternalDownloadManager
import ru.nya.nyeios.data.model.UpdateInfo
import ru.nya.nyeios.data.model.UpdateUiState
import ru.nya.nyeios.data.update.UpdateRepository

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = UpdateRepository.getInstance(application)
    private val downloader = InternalDownloadManager.getInstance(application)

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    /** Вызывается при старте MainActivity. Запускает проверку в фоне. */
    fun checkForUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            val info = repo.checkForUpdate() ?: return@launch
            _uiState.value = UpdateUiState.UpdateAvailable(info)
        }
    }

    /** Юзер нажал X — скрываем баннер, иконка начинает пульсировать. */
    fun dismiss() {
        val current = _uiState.value
        if (current is UpdateUiState.UpdateAvailable) {
            _uiState.value = UpdateUiState.Dismissed(current.info)
        }
    }

    /** Клик по пульсирующей иконке — возвращаем баннер. */
    fun restoreBanner() {
        val current = _uiState.value
        if (current is UpdateUiState.Dismissed) {
            _uiState.value = UpdateUiState.UpdateAvailable(current.info)
        }
    }

    /** Начать скачивание APK. */
    fun downloadApk(info: UpdateInfo) {
        val fileName = "NyEIOS-${info.version}.apk"
        _uiState.value = UpdateUiState.Downloading(0f, 0L, info.apkSize, info)

        downloader.downloadAttachment(info.apkUrl, fileName)

        // Наблюдаем за прогрессом в фоне
        viewModelScope.launch {
            downloader.downloadStates.collect { states ->
                val state = states[info.apkUrl] ?: return@collect
                when (state) {
                    is DownloadState.Downloading -> {
                        _uiState.value = UpdateUiState.Downloading(
                            progress = if (state.progress < 0f) 0f else state.progress,
                            bytesNow = state.bytesDownloaded,
                            bytesTotal = if (state.totalBytes < 0) info.apkSize else state.totalBytes,
                            info = info
                        )
                    }
                    is DownloadState.Completed -> {
                        _uiState.value = UpdateUiState.ReadyToInstall(state.file, info)
                    }
                    is DownloadState.Failed -> {
                        _uiState.value = UpdateUiState.Error(state.error)
                    }
                    else -> Unit
                }
            }
        }
    }

    /** Отменить скачивание. */
    fun cancelDownload() {
        val current = _uiState.value
        if (current is UpdateUiState.Downloading) {
            downloader.cancelDownload(current.info.apkUrl)
            _uiState.value = UpdateUiState.UpdateAvailable(current.info)
        }
    }

    /** Запустить установщик APK. */
    fun installApk(context: Context) {
        val current = _uiState.value as? UpdateUiState.ReadyToInstall ?: return
        val file = current.file

        if (!file.exists()) {
            _uiState.value = UpdateUiState.Error("Файл APK не найден. Попробуйте скачать снова.")
            return
        }

        // Android 8+: проверяем разрешение на установку
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                // Открываем настройки для выдачи разрешения
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(settingsIntent)
                } catch (e: ActivityNotFoundException) {
                    val fallback = Intent(Settings.ACTION_SECURITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(fallback)
                }
                return
            }
        }

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _uiState.value = UpdateUiState.Error("Не удалось запустить установщик: ${e.localizedMessage}")
        }
    }

    /** Сбросить ошибку и вернуться к баннеру, если info известен. */
    fun retryFromError(info: UpdateInfo) {
        _uiState.value = UpdateUiState.UpdateAvailable(info)
    }
}
