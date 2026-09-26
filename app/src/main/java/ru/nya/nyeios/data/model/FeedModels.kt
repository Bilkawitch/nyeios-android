package ru.nya.nyeios.data.model

data class FeedAttachment(
    val name: String,
    val url: String
)

data class FeedPost(
    val id: String,
    val authorName: String,
    val authorAvatar: String = "",
    val destination: String = "",
    val postTime: String = "",
    val textHtml: String = "",
    val textClean: String = "",
    val attachments: List<FeedAttachment> = emptyList()
)

sealed interface FeedUiState {
    data object Loading : FeedUiState
    data object NotLoggedIn : FeedUiState
    data class Success(
        val posts: List<FeedPost>,
        val isRefreshing: Boolean = false
    ) : FeedUiState
    data class Error(
        val message: String,
        val cachedPosts: List<FeedPost>? = null
    ) : FeedUiState
}

enum class FeedSyncStage {
    IDLE,
    CONNECTING,        // Подключение к серверу
    SERVER_PROCESSING, // Сервер генерирует страницу ленты
    DOWNLOADING,       // Скачивание потока байт
    PARSING,           // Разбор HTML и вложений
    COMPLETED,
    ERROR
}

data class FeedSyncProgress(
    val isSyncing: Boolean = false,
    val stage: FeedSyncStage = FeedSyncStage.IDLE,
    val elapsedSeconds: Int = 0,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val speedBps: Long = 0L,
    val statusText: String = "",
    val subStatusText: String = "",
    val etaSeconds: Int? = null,
    val isReceivingPackets: Boolean = false,
    val errorMessage: String? = null
)
