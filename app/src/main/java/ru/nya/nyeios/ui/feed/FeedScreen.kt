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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
            .background(ru.nya.nyeios.ui.theme.NierPanelAlt)
            .border(1.dp, ru.nya.nyeios.ui.theme.NierBorderLight)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: Avatar, Author, Destination and Time with bottom border
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(
                            color = ru.nya.nyeios.ui.theme.NierBorderLight,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Square NieR Avatar box
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(ru.nya.nyeios.ui.theme.NierDark)
                        .border(1.5.dp, ru.nya.nyeios.ui.theme.NierDark),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = ru.nya.nyeios.ui.theme.NierBg,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                        fontSize = 11.sp
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.authorName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                        color = ru.nya.nyeios.ui.theme.NierDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (post.destination.isNotEmpty()) {
                        Text(
                            text = "→ ${post.destination}",
                            color = ru.nya.nyeios.ui.theme.NierDim,
                            fontSize = 9.sp,
                            fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (post.postTime.isNotEmpty()) {
                    Text(
                        text = post.postTime,
                        fontSize = 9.sp,
                        fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                        color = ru.nya.nyeios.ui.theme.NierDim,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }

            // Post Text
            if (post.textClean.isNotEmpty()) {
                SelectionContainer {
                    Text(
                        text = post.textClean,
                        color = ru.nya.nyeios.ui.theme.NierDark,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Attachments block
            if (post.attachments.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
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
    val isDownloaded = state is DownloadState.Completed
    val isDownloading = state is DownloadState.Downloading

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ru.nya.nyeios.ui.theme.NierPanel)
            .border(
                1.dp,
                if (isDownloaded) ru.nya.nyeios.ui.theme.NierGreen else ru.nya.nyeios.ui.theme.NierBorderLight
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (isDownloaded) "✅" else "📄",
                fontSize = 14.sp
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                    color = if (isDownloaded) ru.nya.nyeios.ui.theme.NierGreen else ru.nya.nyeios.ui.theme.NierDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val completedSize = (state as? DownloadState.Completed)?.file?.length() ?: 0L
                if (completedSize > 0) {
                    Text(
                        text = downloadManager.formatFileSize(completedSize),
                        fontSize = 9.sp,
                        fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                        color = ru.nya.nyeios.ui.theme.NierDim
                    )
                }
            }
        }

        // Progress bar for active download
        if (isDownloading) {
            val progress = (state as DownloadState.Downloading).progress
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(ru.nya.nyeios.ui.theme.NierBlue.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(ru.nya.nyeios.ui.theme.NierBlue)
                )
            }
        }

        // Action Buttons Row (NieR styled bordered buttons)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (state) {
                is DownloadState.Completed -> {
                    NierActionButton(text = "↗ Открыть", isPrimary = true, onClick = onOpen)
                    NierActionButton(text = "⤴ Поделиться", isPrimary = false, onClick = onShare)
                    NierActionButton(text = "💾 Сохранить", isPrimary = false, onClick = onSaveToPublic)
                }
                is DownloadState.Downloading -> {
                    Text(
                        text = "Загрузка ${(state.progress * 100).toInt()}%...",
                        fontSize = 9.sp,
                        fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                        color = ru.nya.nyeios.ui.theme.NierBlue
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    NierActionButton(text = "✕ Отмена", isDanger = true, onClick = onCancel)
                }
                else -> {
                    NierActionButton(text = "⬇ Скачать", isPrimary = true, onClick = onDownload)
                }
            }
        }
    }
}

@Composable
fun NierActionButton(
    text: String,
    isPrimary: Boolean = false,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                when {
                    isPrimary -> ru.nya.nyeios.ui.theme.NierDark
                    else -> Color.Transparent
                }
            )
            .border(
                1.dp,
                when {
                    isDanger -> ru.nya.nyeios.ui.theme.NierRed
                    else -> ru.nya.nyeios.ui.theme.NierDark
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
            color = when {
                isPrimary -> ru.nya.nyeios.ui.theme.NierBg
                isDanger -> ru.nya.nyeios.ui.theme.NierRed
                else -> ru.nya.nyeios.ui.theme.NierDark
            }
        )
    }
}


@Composable
fun FeedSyncProgressCard(
    progress: FeedSyncProgress,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedTime = remember(progress.elapsedSeconds) {
        val m = progress.elapsedSeconds / 60
        val s = progress.elapsedSeconds % 60
        String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ru.nya.nyeios.ui.theme.NierGreen.copy(alpha = 0.05f))
            .border(1.dp, ru.nya.nyeios.ui.theme.NierGreen)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header Row: Cloud Icon + Title + Timer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "☁", fontSize = 14.sp, color = ru.nya.nyeios.ui.theme.NierGreen)
                Column {
                    Text(
                        text = "Синхронизация Живой ленты",
                        color = ru.nya.nyeios.ui.theme.NierDark,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Загрузка данных с eios.gukolomna.ru",
                        color = ru.nya.nyeios.ui.theme.NierDim,
                        fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                        fontSize = 9.sp
                    )
                }
            }

            Text(
                text = formattedTime,
                color = ru.nya.nyeios.ui.theme.NierDark,
                fontWeight = FontWeight.Bold,
                fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                fontSize = 12.sp
            )
        }

        // Progress Bar
        val total = progress.totalBytes.coerceAtLeast(1L)
        val frac = if (progress.bytesDownloaded > 0) (progress.bytesDownloaded.toFloat() / total).coerceIn(0.05f, 1f) else 0.2f

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(ru.nya.nyeios.ui.theme.NierGreen.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac)
                    .fillMaxHeight()
                    .background(ru.nya.nyeios.ui.theme.NierGreen)
            )
        }

        // Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatBytesLocal(progress.bytesDownloaded)} / ~7.5 МБ",
                color = ru.nya.nyeios.ui.theme.NierDim,
                fontSize = 9.sp,
                fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily
            )
            Text(
                text = progress.statusText.ifEmpty { "Загрузка..." },
                color = ru.nya.nyeios.ui.theme.NierDim,
                fontSize = 9.sp,
                fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily
            )
        }

        // Cancel button
        NierActionButton(
            text = "✕ Отменить загрузку",
            isDanger = true,
            onClick = onCancel
        )
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
