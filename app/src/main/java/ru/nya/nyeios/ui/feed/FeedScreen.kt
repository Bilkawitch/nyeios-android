package ru.nya.nyeios.ui.feed

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nya.nyeios.data.model.FeedSyncProgress
import ru.nya.nyeios.data.model.FeedSyncStage
import ru.nya.nyeios.data.download.DownloadState
import ru.nya.nyeios.data.download.DownloadedFile
import ru.nya.nyeios.data.download.InternalDownloadManager
import ru.nya.nyeios.data.model.FeedAttachment
import ru.nya.nyeios.data.model.FeedPost
import ru.nya.nyeios.data.model.FeedUiState
import ru.nya.nyeios.ui.download.DownloadsBottomSheet
import ru.nya.nyeios.ui.theme.ExamRed
import ru.nya.nyeios.ui.theme.ExamRedBg
import ru.nya.nyeios.ui.theme.LabAmber
import ru.nya.nyeios.ui.theme.LectureBlue
import ru.nya.nyeios.ui.theme.LectureBlueBg
import ru.nya.nyeios.ui.theme.ObsidianBorder
import ru.nya.nyeios.ui.theme.ObsidianCard
import ru.nya.nyeios.ui.theme.ObsidianSurface
import ru.nya.nyeios.ui.theme.OtherPurple
import ru.nya.nyeios.ui.theme.PracticeGreen
import ru.nya.nyeios.ui.theme.PracticeGreenBg
import ru.nya.nyeios.ui.theme.TextMuted
import ru.nya.nyeios.ui.theme.TextPrimary
import ru.nya.nyeios.ui.theme.TextSecondary
import java.io.File
import kotlin.math.abs

@Composable
fun FeedScreen(
    uiState: FeedUiState,
    feedViewModel: FeedViewModel? = null,
    onRefresh: () -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloadManager = remember {
        feedViewModel?.downloadManager ?: InternalDownloadManager.getInstance(context)
    }

    val downloadStates by (feedViewModel?.downloadStates ?: downloadManager.downloadStates).collectAsState()
    val downloadedFiles by (feedViewModel?.downloadedFiles ?: downloadManager.downloadedFiles).collectAsState()

    val syncProgress by (feedViewModel?.feedSyncProgress ?: remember { MutableStateFlow(FeedSyncProgress()).asStateFlow() }).collectAsState()
    val showSyncDialog by (feedViewModel?.showSyncConfirmationDialog ?: remember { MutableStateFlow(false).asStateFlow() }).collectAsState()
    val isUserLoggedIn = feedViewModel?.isUserLoggedIn() ?: false

    var localDownloadsSheetVisible by remember { mutableStateOf(false) }
    val isDownloadsSheetVisible = feedViewModel?.isDownloadsSheetVisible?.collectAsState()?.value
        ?: localDownloadsSheetVisible

    if (showSyncDialog) {
        AlertDialog(
            onDismissRequest = { feedViewModel?.dismissSyncConfirmationDialog() },
            containerColor = ObsidianSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = PracticeGreen,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("Синхронизация Живой ленты", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Сервер университета формирует Живую ленту монолитным документом (~7.5 МБ). В зависимости от нагрузки генерация может занять от 20 секунд до нескольких минут.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "• Во время загрузки разделы «Расписание» и «БРС» будут работать из сохранённого кэша.\n• В приложении отображается живой таймер и объём скачанных данных.\n• Вы сможете отменить загрузку в любой момент.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = TextMuted
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { feedViewModel?.confirmSyncFeed() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PracticeGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Начать загрузку", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { feedViewModel?.dismissSyncConfirmationDialog() },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    fun openSheet() {
        if (feedViewModel != null) {
            feedViewModel.showDownloadsSheet()
        } else {
            localDownloadsSheetVisible = true
        }
    }

    fun closeSheet() {
        if (feedViewModel != null) {
            feedViewModel.hideDownloadsSheet()
        } else {
            localDownloadsSheetVisible = false
        }
    }

    fun handleDownload(att: FeedAttachment) {
        if (feedViewModel != null) {
            feedViewModel.downloadAttachment(att)
        } else {
            downloadManager.downloadAttachment(att.url, att.name)
        }
    }

    fun handleCancel(att: FeedAttachment) {
        if (feedViewModel != null) {
            feedViewModel.cancelDownload(att)
        } else {
            downloadManager.cancelDownload(att.url)
        }
    }

    fun handleOpen(att: FeedAttachment) {
        val file = downloadManager.getFileForAttachment(att.url, att.name)
        if (file != null) {
            downloadManager.openDownloadedFile(context, file).onFailure {
                Toast.makeText(context, it.localizedMessage ?: "Ошибка открытия", Toast.LENGTH_SHORT).show()
            }
        } else {
            handleDownload(att)
        }
    }

    fun handleShare(att: FeedAttachment) {
        val file = downloadManager.getFileForAttachment(att.url, att.name)
        if (file != null) {
            downloadManager.shareDownloadedFile(context, file).onFailure {
                Toast.makeText(context, it.localizedMessage ?: "Ошибка отправки", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun handleSaveToPublic(att: FeedAttachment) {
        val file = downloadManager.getFileForAttachment(att.url, att.name)
        if (file != null) {
            downloadManager.saveToPublicDownloads(context, file).onSuccess {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, it.localizedMessage ?: "Ошибка сохранения", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is FeedUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (syncProgress.isSyncing) {
                        FeedSyncProgressCard(
                            progress = syncProgress,
                            onCancel = { feedViewModel?.cancelSyncFeed() }
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = LectureBlue)
                            Text(
                                text = "Загрузка сохранённой ленты...",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            is FeedUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (syncProgress.isSyncing) {
                        FeedSyncProgressCard(
                            progress = syncProgress,
                            onCancel = { feedViewModel?.cancelSyncFeed() }
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(ExamRedBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = ExamRed,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Text(
                                text = uiState.message,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 22.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { feedViewModel?.requestSyncFeed() ?: onRefresh() },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, PracticeGreen.copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PracticeGreen),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Text("Повторить", fontWeight = FontWeight.SemiBold)
                                }

                                if (!isUserLoggedIn) {
                                    Button(
                                        onClick = onOpenLogin,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = LectureBlue,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Text("Войти в аккаунт", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            is FeedUiState.Success -> {
                if (uiState.posts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (syncProgress.isSyncing) {
                            FeedSyncProgressCard(
                                progress = syncProgress,
                                onCancel = { feedViewModel?.cancelSyncFeed() }
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(text = "📢", fontSize = 44.sp)
                                Text(
                                    text = "Нет сохранённых объявлений",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Живая лента сохраняется локально после разовой синхронизации с eios.gukolomna.ru.",
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )

                                Spacer(Modifier.height(4.dp))

                                if (!isUserLoggedIn) {
                                    Button(
                                        onClick = onOpenLogin,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = LectureBlue,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Text("Войти в аккаунт", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(
                                        onClick = { feedViewModel?.requestSyncFeed() ?: onRefresh() },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PracticeGreen,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Загрузить ленту с сервера", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Live Sync Progress or Sync Bar
                        if (syncProgress.isSyncing) {
                            item {
                                FeedSyncProgressCard(
                                    progress = syncProgress,
                                    onCancel = { feedViewModel?.cancelSyncFeed() }
                                )
                            }
                        } else {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ObsidianCard)
                                        .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(PracticeGreenBg),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudSync,
                                                contentDescription = null,
                                                tint = PracticeGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "Живая лента ЭИОС",
                                                color = TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Сохранено объявлений: ${uiState.posts.size}",
                                                color = TextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            if (!isUserLoggedIn) {
                                                onOpenLogin()
                                            } else {
                                                feedViewModel?.requestSyncFeed() ?: onRefresh()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, PracticeGreen.copy(alpha = 0.5f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PracticeGreen),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Обновить", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // 2. Downloads Banner
                        item {
                            val totalBytes = remember(downloadedFiles) { downloadedFiles.sumOf { it.sizeBytes } }
                            val activeCount = remember(downloadStates) {
                                downloadStates.count { it.value is DownloadState.Downloading }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ObsidianCard)
                                    .border(1.dp, if (activeCount > 0) LectureBlue.copy(alpha = 0.5f) else ObsidianBorder, RoundedCornerShape(12.dp))
                                    .clickable { openSheet() }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(LectureBlueBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (activeCount > 0) Icons.Default.Download else Icons.Default.FolderOpen,
                                            contentDescription = null,
                                            tint = LectureBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = "Менеджер загрузок",
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (activeCount > 0) {
                                                "Скачивается файлов: $activeCount"
                                            } else if (downloadedFiles.isNotEmpty()) {
                                                "Сохранено: ${downloadedFiles.size} • ${downloadManager.formatFileSize(totalBytes)}"
                                            } else {
                                                "Локальное хранилище файлов ЭИОС"
                                            },
                                            color = if (activeCount > 0) LectureBlue else TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Открыть",
                                        color = LectureBlue,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        tint = LectureBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        // Feed Posts
                        items(uiState.posts, key = { it.id }) { post ->
                            FeedPostCard(
                                post = post,
                                downloadStates = downloadStates,
                                downloadManager = downloadManager,
                                onDownload = { handleDownload(it) },
                                onCancel = { handleCancel(it) },
                                onOpen = { handleOpen(it) },
                                onShare = { handleShare(it) },
                                onSaveToPublic = { handleSaveToPublic(it) }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }

        // Downloads Bottom Sheet
        if (isDownloadsSheetVisible) {
            DownloadsBottomSheet(
                downloadedFiles = downloadedFiles,
                downloadStates = downloadStates,
                onOpenFile = { file ->
                    downloadManager.openDownloadedFile(context, file).onFailure {
                        Toast.makeText(context, it.localizedMessage ?: "Ошибка открытия", Toast.LENGTH_SHORT).show()
                    }
                },
                onShareFile = { file ->
                    downloadManager.shareDownloadedFile(context, file).onFailure {
                        Toast.makeText(context, it.localizedMessage ?: "Ошибка отправки", Toast.LENGTH_SHORT).show()
                    }
                },
                onSaveToDownloads = { file ->
                    downloadManager.saveToPublicDownloads(context, file).onSuccess {
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, it.localizedMessage ?: "Ошибка сохранения", Toast.LENGTH_SHORT).show()
                    }
                },
                onDeleteFile = { file ->
                    downloadManager.deleteDownloadedFile(file)
                },
                onClearAll = {
                    downloadManager.clearAllDownloads()
                },
                onCancelDownload = { url ->
                    downloadManager.cancelDownload(url)
                },
                onDismiss = { closeSheet() },
                downloadManager = downloadManager
            )
        }
    }
}

@Composable
fun FeedPostCard(
    post: FeedPost,
    downloadStates: Map<String, DownloadState>,
    downloadManager: InternalDownloadManager,
    onDownload: (FeedAttachment) -> Unit,
    onCancel: (FeedAttachment) -> Unit,
    onOpen: (FeedAttachment) -> Unit,
    onShare: (FeedAttachment) -> Unit,
    onSaveToPublic: (FeedAttachment) -> Unit
) {
    val avatarBg = remember(post.authorName) {
        val colors = listOf(LectureBlue, PracticeGreen, LabAmber, OtherPurple, Color(0xFF06B6D4), Color(0xFFEC4899))
        val idx = abs(post.authorName.hashCode()) % colors.size
        colors[idx]
    }

    val initials = remember(post.authorName) {
        val words = post.authorName.split(" ").filter { it.isNotBlank() }
        when {
            words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
            words.size == 1 -> words[0].take(2).uppercase()
            else -> "ЭИ"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianCard)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header: Avatar, Name, Destination & Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Monogram Avatar
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(avatarBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = post.authorName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (post.destination.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(LectureBlueBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "→ ${post.destination}",
                                    color = LectureBlue,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    if (post.postTime.isNotEmpty()) {
                        Text(
                            text = post.postTime,
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            // Post Text
            if (post.textClean.isNotEmpty()) {
                SelectionContainer {
                    Text(
                        text = post.textClean,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            // Attachments block with Internal Downloader
            if (post.attachments.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    post.attachments.forEach { att ->
                        val state = downloadStates[att.url] ?: downloadManager.getDownloadState(att.url, att.name)
                        FeedAttachmentItem(
                            attachment = att,
                            state = state,
                            downloadManager = downloadManager,
                            onDownload = { onDownload(att) },
                            onCancel = { onCancel(att) },
                            onOpen = { onOpen(att) },
                            onShare = { onShare(att) },
                            onSaveToPublic = { onSaveToPublic(att) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FeedAttachmentItem(
    attachment: FeedAttachment,
    state: DownloadState,
    downloadManager: InternalDownloadManager,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSaveToPublic: () -> Unit
) {
    val (iconColor, extLabel) = remember(attachment.name) {
        val lower = attachment.name.lowercase()
        when {
            lower.endsWith(".pdf") -> Pair(ExamRed, "PDF")
            lower.endsWith(".docx") || lower.endsWith(".doc") -> Pair(LectureBlue, "DOC")
            lower.endsWith(".xlsx") || lower.endsWith(".xls") -> Pair(PracticeGreen, "XLS")
            lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") -> Pair(LabAmber, "ZIP")
            lower.endsWith(".ppt") || lower.endsWith(".pptx") -> Pair(OtherPurple, "PPT")
            else -> Pair(OtherPurple, "ФАЙЛ")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianSurface)
            .border(
                1.dp,
                when (state) {
                    is DownloadState.Downloading -> LectureBlue.copy(alpha = 0.4f)
                    is DownloadState.Completed -> PracticeGreen.copy(alpha = 0.35f)
                    is DownloadState.Failed -> ExamRed.copy(alpha = 0.4f)
                    else -> ObsidianBorder
                },
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    when (state) {
                        is DownloadState.Completed -> onOpen()
                        is DownloadState.Downloading -> {} // do nothing or let user click cancel
                        else -> onDownload()
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Extension badge / icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when (state) {
                            is DownloadState.Completed -> PracticeGreenBg
                            else -> iconColor.copy(alpha = 0.15f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (state is DownloadState.Completed) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = PracticeGreen,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Filename & Status
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.name,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                when (state) {
                    is DownloadState.Downloading -> {
                        Text(
                            text = if (state.totalBytes > 0) {
                                "${(state.progress * 100).toInt()}% • ${downloadManager.formatFileSize(state.bytesDownloaded)} / ${downloadManager.formatFileSize(state.totalBytes)}"
                            } else {
                                "Загрузка... ${downloadManager.formatFileSize(state.bytesDownloaded)}"
                            },
                            color = LectureBlue,
                            fontSize = 11.sp
                        )
                    }

                    is DownloadState.Completed -> {
                        Text(
                            text = "Скачан локально • ${downloadManager.formatFileSize(state.sizeBytes)}",
                            color = PracticeGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    is DownloadState.Failed -> {
                        Text(
                            text = state.error,
                            color = ExamRed,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    DownloadState.Idle -> {
                        Text(
                            text = "Нажмите для загрузки в приложение",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Right Tag or Action Icon
            when (state) {
                is DownloadState.Downloading -> {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Отмена загрузки",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                is DownloadState.Failed -> {
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Повторить",
                            tint = ExamRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DownloadState.Idle -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(LectureBlueBg)
                            .clickable { onDownload() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = LectureBlue,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = extLabel,
                                color = LectureBlue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                is DownloadState.Completed -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(PracticeGreenBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = extLabel,
                            color = PracticeGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Progress indicator if downloading
        if (state is DownloadState.Downloading) {
            if (state.progress >= 0f) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = LectureBlue,
                    trackColor = ObsidianBorder
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = LectureBlue,
                    trackColor = ObsidianBorder
                )
            }
        }

        // Quick action buttons if Completed
        if (state is DownloadState.Completed) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpen,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LectureBlueBg,
                        contentColor = LectureBlue
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Открыть",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Поделиться",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                IconButton(
                    onClick = onSaveToPublic,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SaveAlt,
                        contentDescription = "Сохранить в папку Загрузки",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FeedSyncProgressCard(
    progress: FeedSyncProgress,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val formattedTime = remember(progress.elapsedSeconds) {
        val m = progress.elapsedSeconds / 60
        val s = progress.elapsedSeconds % 60
        String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianCard)
            .border(1.dp, PracticeGreen.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Header Row: Status Icon + Title + Pulsing Beacon + Monospace Timer Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(PracticeGreen.copy(alpha = pulseAlpha))
                )
                Text(
                    text = "Синхронизация ленты",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(PracticeGreenBg)
                    .border(1.dp, PracticeGreen.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = formattedTime,
                    color = PracticeGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Primary Status Message
        Text(
            text = progress.statusText.ifEmpty { "Подключение к eios.gukolomna.ru..." },
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        // Sub Status (Speed, packet indicator, elapsed explanation)
        if (progress.subStatusText.isNotEmpty()) {
            Text(
                text = progress.subStatusText,
                color = if (progress.isReceivingPackets) PracticeGreen else TextSecondary,
                fontSize = 11.sp,
                fontWeight = if (progress.isReceivingPackets) FontWeight.SemiBold else FontWeight.Normal
            )
        }

        // Progress Bar
        val isDownloading = progress.stage == FeedSyncStage.DOWNLOADING && progress.bytesDownloaded > 0
        if (isDownloading) {
            val total = progress.totalBytes.coerceAtLeast(1L)
            val frac = (progress.bytesDownloaded.toFloat() / total).coerceIn(0.01f, 1f)
            val percent = (frac * 100).toInt().coerceIn(1, 99)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { frac },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = PracticeGreen,
                    trackColor = ObsidianBorder
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$percent%",
                        color = PracticeGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${formatBytesLocal(progress.bytesDownloaded)} / ~7.5 МБ",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = PracticeGreen,
                trackColor = ObsidianBorder
            )
        }

        // Footer: Note & Cancel Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Таймаут: 25 мин • Фоновый режим",
                color = TextMuted,
                fontSize = 11.sp
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onCancel() }
                    .background(ExamRedBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = ExamRed,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "Отменить",
                    color = ExamRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatBytesLocal(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f МБ", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format(java.util.Locale.US, "%d КБ", bytes / 1024)
        bytes > 0 -> "$bytes Б"
        else -> "0 Б"
    }
}
