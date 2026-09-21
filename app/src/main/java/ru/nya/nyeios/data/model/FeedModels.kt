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
    data class Success(
        val posts: List<FeedPost>,
        val isRefreshing: Boolean = false
    ) : FeedUiState
    data class Error(
        val message: String,
        val cachedPosts: List<FeedPost>? = null
    ) : FeedUiState
}
