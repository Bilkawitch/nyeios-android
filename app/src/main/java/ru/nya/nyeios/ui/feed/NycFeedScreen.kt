package ru.nya.nyeios.ui.feed

import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nya.nyeios.data.download.DownloadState
import ru.nya.nyeios.data.download.InternalDownloadManager
import ru.nya.nyeios.data.model.FeedAttachment
import ru.nya.nyeios.data.model.FeedPost
import ru.nya.nyeios.data.model.FeedSyncProgress
import ru.nya.nyeios.data.model.FeedUiState
import ru.nya.nyeios.ui.download.DownloadsBottomSheet
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.NycMonoFamily
import ru.nya.nyeios.ui.theme.NycSansFamily
import ru.nya.nyeios.ui.theme.nycCard
import ru.nya.nyeios.ui.theme.nycRaised
import ru.nya.nyeios.ui.theme.nycWell

// Parallel NyC-modern feed interface from mockup nyeios_redesign.html (screen 4)
// and reference 4.png. Same state, same ViewModel calls, same download behavior
// as FeedScreen; only rendering differs. Legacy FeedScreen is untouched.
//
// Mockup notes: per-attachment share/save actions live in the downloads sheet
// (per file), the attach panel tap opens-or-downloads via the same handler.

// Mockup fixed tokens.
private val nycText = Color(0xFFDBE4F0)
private val nycBody = Color(0xFFB9C4D4)
private val nycMuted = Color(0xFF8794A7)
private val nycFaint = Color(0xFF5B6A7E)
private val nycBlue = Color(0xFF6FA8FF)
private val nycGreen = Color(0xFF46E08C)
private val nycAmber = Color(0xFFF0B44C)
private val nycRed = Color(0xFFF26D6D)
private val nycViolet = Color(0xFFA78BFA)

@Composable
fun NycFeedScreen(
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
        NycSyncDialog(
            onDismiss = { feedViewModel?.dismissSyncConfirmationDialog() },
            onConfirm = { feedViewModel?.confirmSyncFeed() }
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
                            CircularProgressIndicator(color = NycCyan)
                            Text(
                                text = "Загрузка сохранённой ленты...",
                                color = nycMuted,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            FeedUiState.NotLoggedIn -> {
                Box(modifier = Modifier.fillMaxSize())
            }

            is FeedUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .nycCard()
                                .padding(20.dp)
                        ) {
                            Text(
                                text = "СИСТЕМНОЕ ОПОВЕЩЕНИЕ",
                                fontFamily = NycSansFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp,
                                color = nycRed
                            )
                            Text(
                                text = uiState.message,
                                fontFamily = NycMonoFamily,
                                fontSize = 11.sp,
                                color = nycMuted,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = 16.dp)
                                    .height(38.dp)
                                    .nycRaised(11.dp)
                                    .clickable { onRefresh() }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "ПОВТОРИТЬ",
                                    fontFamily = NycSansFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp,
                                    color = NycCyan
                                )
                            }
                        }
                    }
                }
            }

            is FeedUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (syncProgress.isSyncing) {
                        item {
                            FeedSyncProgressCard(
                                progress = syncProgress,
                                onCancel = { feedViewModel?.cancelSyncFeed() }
                            )
                        }
                    }

                    // 1. Feed status card.
                    item {
                        NycStatusCard(
                            icon = Icons.Default.Sync,
                            title = "Живая лента ЭИОС",
                            subtitle = "СОХРАНЕНО ОБЪЯВЛЕНИЙ: ${uiState.posts.size}",
                            actionText = "ОБНОВИТЬ",
                            actionIcon = Icons.Default.Refresh,
                            dimAction = false,
                            onAction = {
                                if (!isUserLoggedIn) {
                                    onOpenLogin()
                                } else {
                                    feedViewModel?.requestSyncFeed() ?: onRefresh()
                                }
                            }
                        )
                    }

                    // 2. Downloads manager card.
                    item {
                        val totalBytes = remember(downloadedFiles) { downloadedFiles.sumOf { it.sizeBytes } }
                        val activeCount = remember(downloadStates) {
                            downloadStates.count { it.value is DownloadState.Downloading }
                        }
                        val sub = if (activeCount > 0) {
                            "СКАЧИВАЕТСЯ ФАЙЛОВ: $activeCount"
                        } else {
                            "ЛОКАЛЬНОЕ ХРАНИЛИЩЕ · ${downloadedFiles.size} ФАЙЛОВ" +
                                if (totalBytes > 0) " · ${downloadManager.formatFileSize(totalBytes)}" else ""
                        }
                        NycStatusCard(
                            icon = Icons.Default.FolderOpen,
                            title = "Менеджер загрузок",
                            subtitle = sub,
                            actionText = "ОТКРЫТЬ",
                            actionIcon = null,
                            dimAction = true,
                            onAction = { openSheet() }
                        )
                    }

                    if (uiState.posts.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .nycCard()
                                    .padding(vertical = 24.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "— ОБЪЯВЛЕНИЙ ПОКА НЕТ —",
                                    fontFamily = NycMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp,
                                    color = nycMuted
                                )
                            }
                        }
                    }

                    items(uiState.posts, key = { it.id }) { post ->
                        NycPostCard(
                            post = post,
                            downloadStates = downloadStates,
                            downloadManager = downloadManager,
                            onDownload = { handleDownload(it) },
                            onOpen = { handleOpen(it) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }

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
private fun NycStatusCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionText: String,
    actionIcon: ImageVector?,
    dimAction: Boolean,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .nycCard()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .nycWell(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NycCyan,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = NycSansFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                letterSpacing = 0.5.sp,
                color = nycText
            )
            Text(
                text = subtitle,
                fontFamily = NycMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 9.sp,
                letterSpacing = 0.5.sp,
                color = nycFaint,
                modifier = Modifier.padding(top = 3.dp)
            )
        }

        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .then(
                    if (dimAction) {
                        Modifier
                            .background(Color(150, 185, 235, alpha = 15))
                            .border(1.dp, Color(150, 185, 235, alpha = 19), RoundedCornerShape(11.dp))
                    } else {
                        Modifier
                            .background(NycCyan.copy(alpha = 0.12f))
                            .border(1.dp, NycCyan.copy(alpha = 0.34f), RoundedCornerShape(11.dp))
                    }
                )
                .clickable { onAction() }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (actionIcon != null) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = null,
                    tint = if (dimAction) nycMuted else NycCyan,
                    modifier = Modifier.size(13.dp)
                )
            } else if (dimAction) {
                Text(text = "›", fontSize = 13.sp, color = nycMuted)
            }
            Text(
                text = actionText,
                fontFamily = NycMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                color = if (dimAction) nycMuted else NycCyan
            )
        }
    }
}

@Composable
private fun NycPostCard(
    post: FeedPost,
    downloadStates: Map<String, DownloadState>,
    downloadManager: InternalDownloadManager,
    onDownload: (FeedAttachment) -> Unit,
    onOpen: (FeedAttachment) -> Unit
) {
    val isOfficial = remember(post.authorName) {
        val n = post.authorName.lowercase()
        n.contains("деканат") || n.contains("кафедр") || n.contains("факультет") || n.contains("гсгу")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .nycCard()
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .then(if (isOfficial) Modifier.nycWell(12.dp) else Modifier.nycRaised(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isOfficial) Icons.Default.Business else Icons.Default.Person,
                    contentDescription = null,
                    tint = NycCyan,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = post.authorName,
                    fontFamily = NycSansFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = nycText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = buildString {
                    if (post.destination.isNotEmpty()) append(post.destination.uppercase())
                    if (post.destination.isNotEmpty() && post.postTime.isNotEmpty()) append(" · ")
                    append(post.postTime)
                }
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 8.5.sp,
                        letterSpacing = 1.sp,
                        color = nycFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        if (post.textClean.isNotEmpty()) {
            SelectionContainer {
                Text(
                    text = post.textClean,
                    color = nycBody,
                    fontSize = 11.5.sp,
                    lineHeight = 18.sp,
                    fontFamily = NycSansFamily,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }

        if (post.attachments.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 11.dp)
            ) {
                post.attachments.forEach { att ->
                    val state = downloadStates[att.url] ?: downloadManager.getDownloadState(att.url, att.name)
                    NycAttachment(
                        attachment = att,
                        state = state,
                        downloadManager = downloadManager,
                        onDownload = { onDownload(att) },
                        onOpen = { onOpen(att) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NycAttachment(
    attachment: FeedAttachment,
    state: DownloadState,
    downloadManager: InternalDownloadManager,
    onDownload: () -> Unit,
    onOpen: () -> Unit
) {
    val isDownloaded = state is DownloadState.Completed
    val isDownloading = state is DownloadState.Downloading
    val progress = (state as? DownloadState.Downloading)?.progress ?: 0f

    val (tagColor, extLabel) = remember(attachment.name) {
        val lower = attachment.name.lowercase()
        when {
            lower.endsWith(".pdf") -> Pair(nycRed, "PDF")
            lower.endsWith(".docx") || lower.endsWith(".doc") -> Pair(nycBlue, "DOC")
            lower.endsWith(".xlsx") || lower.endsWith(".xls") -> Pair(nycGreen, "XLS")
            lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") -> Pair(nycAmber, "ZIP")
            lower.endsWith(".ppt") || lower.endsWith(".pptx") -> Pair(nycViolet, "PPT")
            lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mkv") -> Pair(nycViolet, "MP4")
            else -> Pair(nycMuted, "FILE")
        }
    }

    val completedSize = (state as? DownloadState.Completed)?.file?.length() ?: 0L
    val sizeText = if (completedSize > 0) {
        "$extLabel · ${downloadManager.formatFileSize(completedSize)}"
    } else {
        extLabel
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .nycWell(13.dp)
            .clickable { onOpen() }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                modifier = Modifier
                    .height(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tagColor.copy(alpha = 0.10f))
                    .border(1.dp, tagColor.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = extLabel,
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    letterSpacing = 1.sp,
                    color = tagColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.name,
                    fontFamily = NycSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = nycText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = sizeText,
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 8.5.sp,
                    letterSpacing = 0.5.sp,
                    color = nycFaint,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (isDownloaded) {
                Row(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(NycCyan.copy(alpha = 0.12f))
                        .border(1.dp, NycCyan.copy(alpha = 0.34f), RoundedCornerShape(9.dp))
                        .clickable { onOpen() }
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = NycCyan,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "ОТКРЫТЬ",
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.5.sp,
                        letterSpacing = 1.sp,
                        color = NycCyan
                    )
                }
            } else {
                // Progress ring: idle tap downloads, live arc while downloading.
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clickable { if (!isDownloading) onDownload() },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(34.dp)) {
                        drawArc(
                            color = NycCyan.copy(alpha = 0.14f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                        if (isDownloading) {
                            drawArc(
                                color = NycCyan,
                                startAngle = -90f,
                                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                                useCenter = false,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .nycRaised(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = if (isDownloading) "Скачивается" else "Скачать",
                            tint = NycCyan,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        if (isDownloading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .nycWell(3.dp)
                        .clip(RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(NycCyan)
                    )
                }
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 8.5.sp,
                    color = NycCyan
                )
            }
        }
    }
}

@Composable
private fun NycSyncDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF182334),
        titleContentColor = nycText,
        textContentColor = nycMuted,
        shape = RoundedCornerShape(16.dp),
        icon = {
            Icon(
                imageVector = Icons.Default.CloudSync,
                contentDescription = null,
                tint = nycGreen,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                "Синхронизация Живой ленты",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                fontFamily = NycSansFamily
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Сервер университета формирует Живую ленту монолитным документом (~7.5 МБ). В зависимости от нагрузки генерация может занять от 20 секунд до нескольких минут.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = nycText,
                    fontFamily = NycSansFamily
                )
                Text(
                    text = "• Во время загрузки разделы «Расписание» и «БРС» будут работать из сохранённого кэша.\n• В приложении отображается живой таймер и объём скачанных данных.\n• Вы сможете отменить загрузку в любой момент.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = nycMuted,
                    fontFamily = NycSansFamily
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = nycGreen,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(11.dp)
            ) {
                Text("Начать загрузку", fontWeight = FontWeight.Bold, fontFamily = NycSansFamily)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(11.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(150, 185, 235, alpha = 19)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = nycMuted)
            ) {
                Text("Отмена", fontFamily = NycSansFamily)
            }
        }
    )
}
