package ru.nya.nyeios.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.nya.nyeios.data.model.FeedUiState
import ru.nya.nyeios.data.repository.EiosRepository

import android.content.Context
import ru.nya.nyeios.data.download.DownloadState
import ru.nya.nyeios.data.download.DownloadedFile
import ru.nya.nyeios.data.download.InternalDownloadManager
import ru.nya.nyeios.data.model.FeedAttachment
import java.io.File

class FeedViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EiosRepository.getInstance(application.applicationContext)
    val downloadManager = InternalDownloadManager.getInstance(application.applicationContext)

    private val _uiState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    val downloadStates: StateFlow<Map<String, DownloadState>> = downloadManager.downloadStates
    val downloadedFiles: StateFlow<List<DownloadedFile>> = downloadManager.downloadedFiles

    private val _isDownloadsSheetVisible = MutableStateFlow(false)
    val isDownloadsSheetVisible: StateFlow<Boolean> = _isDownloadsSheetVisible.asStateFlow()

    private var feedJob: kotlinx.coroutines.Job? = null

    init {
        loadCachedOnly()
    }

    private fun loadCachedOnly() {
        val cached = repository.getCachedFeed()
        _uiState.value = FeedUiState.Success(cached ?: emptyList())
    }

    fun showDownloadsSheet() {
        _isDownloadsSheetVisible.value = true
    }

    fun hideDownloadsSheet() {
        _isDownloadsSheetVisible.value = false
    }

    fun downloadAttachment(attachment: FeedAttachment) {
        downloadManager.downloadAttachment(attachment.url, attachment.name)
    }

    fun cancelDownload(attachment: FeedAttachment) {
        downloadManager.cancelDownload(attachment.url)
    }

    fun openAttachment(context: Context, attachment: FeedAttachment): Result<Unit> {
        val file = downloadManager.getFileForAttachment(attachment.url, attachment.name)
            ?: return Result.failure(Exception("Файл еще не скачан"))
        return downloadManager.openDownloadedFile(context, file)
    }

    fun shareAttachment(context: Context, attachment: FeedAttachment): Result<Unit> {
        val file = downloadManager.getFileForAttachment(attachment.url, attachment.name)
            ?: return Result.failure(Exception("Файл еще не скачан"))
        return downloadManager.shareDownloadedFile(context, file)
    }

    fun saveAttachmentToDownloads(context: Context, attachment: FeedAttachment): Result<String> {
        val file = downloadManager.getFileForAttachment(attachment.url, attachment.name)
            ?: return Result.failure(Exception("Файл еще не скачан"))
        return downloadManager.saveToPublicDownloads(context, file)
    }

    fun openFile(context: Context, file: File): Result<Unit> {
        return downloadManager.openDownloadedFile(context, file)
    }

    fun shareFile(context: Context, file: File): Result<Unit> {
        return downloadManager.shareDownloadedFile(context, file)
    }

    fun saveFileToPublicDownloads(context: Context, file: File): Result<String> {
        return downloadManager.saveToPublicDownloads(context, file)
    }

    fun deleteDownloadedFile(file: File) {
        downloadManager.deleteDownloadedFile(file)
    }

    fun clearAllDownloads() {
        downloadManager.clearAllDownloads()
    }

    val feedSyncProgress: StateFlow<ru.nya.nyeios.data.model.FeedSyncProgress> = repository.feedSyncProgress

    private val _showSyncConfirmationDialog = MutableStateFlow(false)
    val showSyncConfirmationDialog: StateFlow<Boolean> = _showSyncConfirmationDialog.asStateFlow()

    fun requestSyncFeed() {
        _showSyncConfirmationDialog.value = true
    }

    fun dismissSyncConfirmationDialog() {
        _showSyncConfirmationDialog.value = false
    }

    fun confirmSyncFeed() {
        _showSyncConfirmationDialog.value = false
        refresh()
    }

    fun cancelSyncFeed() {
        feedJob?.cancel()
        repository.cancelFeedSync()
    }

    fun isUserLoggedIn(): Boolean = repository.isUserLoggedIn()

    fun loadFeed(forceNetwork: Boolean = false) {
        feedJob?.cancel()
        feedJob = viewModelScope.launch {
            val currentPosts = (_uiState.value as? FeedUiState.Success)?.posts
            if (forceNetwork && currentPosts != null) {
                _uiState.value = FeedUiState.Success(currentPosts, isRefreshing = true)
            } else if (!forceNetwork && currentPosts == null) {
                _uiState.value = FeedUiState.Loading
            }

            val result = repository.getFeed(forceNetwork)
            result.onSuccess { posts ->
                _uiState.value = FeedUiState.Success(posts, isRefreshing = false)
            }.onFailure { error ->
                _uiState.value = FeedUiState.Error(
                    message = error.localizedMessage ?: "Не удалось загрузить живую ленту",
                    cachedPosts = currentPosts
                )
            }
        }
    }

    fun refresh() {
        loadFeed(forceNetwork = true)
    }
}
