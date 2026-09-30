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
import androidx.compose.foundation.shape.RoundedCornerShape
import ru.nya.nyeios.ui.theme.appRectShape
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import ru.nya.nyeios.ui.common.SegmentedMeter
import ru.nya.nyeios.ui.common.isAuthRelatedMessage
import ru.nya.nyeios.ui.common.localizeErrorMessage
import ru.nya.nyeios.ui.theme.UiPreferencesManager
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
import androidx.compose.ui.res.stringResource
import ru.nya.nyeios.R
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
            shape = appRectShape(),
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = PracticeGreen,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(stringResource(R.string.feed_sync_dialog_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.feed_sync_dialog_desc),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.feed_sync_dialog_bullets),
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
                    shape = appRectShape()
                ) {
                    Text(stringResource(R.string.feed_sync_start), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { feedViewModel?.dismissSyncConfirmationDialog() },
                    shape = appRectShape(),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Text(stringResource(R.string.action_cancel))
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
                Toast.makeText(context, it.localizedMessage ?: context.getString(R.string.error_open_file), Toast.LENGTH_SHORT).show()
            }
        } else {
            handleDownload(att)
        }
    }

    fun handleShare(att: FeedAttachment) {
        val file = downloadManager.getFileForAttachment(att.url, att.name)
        if (file != null) {
            downloadManager.shareDownloadedFile(context, file).onFailure {
                Toast.makeText(context, it.localizedMessage ?: context.getString(R.string.error_send_file), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun handleSaveToPublic(att: FeedAttachment) {
        val file = downloadManager.getFileForAttachment(att.url, att.name)
        if (file != null) {
            downloadManager.saveToPublicDownloads(context, file).onSuccess {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, it.localizedMessage ?: context.getString(R.string.error_save_file), Toast.LENGTH_SHORT).show()
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
                                text = stringResource(R.string.feed_loading_cached),
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            FeedUiState.NotLoggedIn -> {
                // Content is obscured by LoginFullscreenGate in MainActivity
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
                                .border(1.dp, ru.nya.nyeios.ui.theme.NierBorder)
                                .background(ru.nya.nyeios.ui.theme.NierPanelAlt)
                                .padding(20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(ru.nya.nyeios.ui.theme.NierRed)
                                )
                                Text(
                                    text = stringResource(R.string.system_alert_caps),
                                    fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp,
                                    color = ru.nya.nyeios.ui.theme.NierRed
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(ru.nya.nyeios.ui.theme.NierRed.copy(alpha = 0.12f))
                                    .border(1.dp, ru.nya.nyeios.ui.theme.NierRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = ru.nya.nyeios.ui.theme.NierRed,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            Text(
                                text = localizeErrorMessage(uiState.message),
                                color = ru.nya.nyeios.ui.theme.NierDark,
                                fontSize = 13.sp,
                                fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 18.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                            )

                            Spacer(Modifier.height(20.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(38.dp)
                                        .border(1.dp, ru.nya.nyeios.ui.theme.NierBorder)
                                        .background(ru.nya.nyeios.ui.theme.NierPanel)
                                        .clickable { feedViewModel?.requestSyncFeed() ?: onRefresh() }
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.action_retry_caps),
                                        fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        letterSpacing = 1.sp,
                                        color = ru.nya.nyeios.ui.theme.NierDark
                                    )
                                }

                                val isAuthIssue = isAuthRelatedMessage(uiState.message)

                                if (isAuthIssue || !isUserLoggedIn) {
                                    Box(
                                        modifier = Modifier
                                            .height(38.dp)
                                            .border(1.dp, ru.nya.nyeios.ui.theme.NierSelection)
                                            .background(ru.nya.nyeios.ui.theme.NierSelection)
                                            .clickable { onOpenLogin() }
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.action_login_account),
                                            fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            letterSpacing = 1.sp,
                                            color = ru.nya.nyeios.ui.theme.NierSelectionText
                                        )
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, ru.nya.nyeios.ui.theme.NierBorder)
                                    .background(ru.nya.nyeios.ui.theme.NierPanelAlt)
                                    .padding(20.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(ru.nya.nyeios.ui.theme.NierDim)
                                    )
                                    Text(
                                        text = stringResource(R.string.feed_title),
                                        fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.sp,
                                        color = ru.nya.nyeios.ui.theme.NierDim
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(ru.nya.nyeios.ui.theme.NierDim.copy(alpha = 0.08f))
                                        .border(1.dp, ru.nya.nyeios.ui.theme.NierBorder),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = ru.nya.nyeios.ui.theme.NierDim,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Spacer(Modifier.height(14.dp))

                                Text(
                                    text = stringResource(R.string.feed_empty_title),
                                    color = ru.nya.nyeios.ui.theme.NierDark,
                                    fontSize = 13.sp,
                                    fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 18.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                )

                                Spacer(Modifier.height(6.dp))

                                Text(
                                    text = stringResource(R.string.feed_empty_desc),
                                    color = ru.nya.nyeios.ui.theme.NierDim,
                                    fontSize = 11.sp,
                                    fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 16.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                )

                                Spacer(Modifier.height(20.dp))

                                if (!isUserLoggedIn) {
                                    Box(
                                        modifier = Modifier
                                            .height(38.dp)
                                            .border(1.dp, ru.nya.nyeios.ui.theme.NierSelection)
                                            .background(ru.nya.nyeios.ui.theme.NierSelection)
                                            .clickable { onOpenLogin() }
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.action_login_account),
                                            fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            letterSpacing = 1.sp,
                                            color = ru.nya.nyeios.ui.theme.NierSelectionText
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .height(38.dp)
                                            .border(1.dp, ru.nya.nyeios.ui.theme.NierBorder)
                                            .background(ru.nya.nyeios.ui.theme.NierPanel)
                                            .clickable { feedViewModel?.requestSyncFeed() ?: onRefresh() }
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.feed_fetch_from_server),
                                            fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            letterSpacing = 1.sp,
                                            color = ru.nya.nyeios.ui.theme.NierDark
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = UiPreferencesManager.listSpace(12.dp)),
                        verticalArrangement = Arrangement.spacedBy(UiPreferencesManager.listSpace(14.dp))
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
                                val postsCount = uiState.posts.size
                                val lastSyncTimeState by (feedViewModel?.lastSyncTime ?: remember { MutableStateFlow(0L).asStateFlow() }).collectAsState()
                                val lastSyncTime = if (lastSyncTimeState > 0L) lastSyncTimeState else (feedViewModel?.getLastSyncTime() ?: 0L)
                                val syncDateString = remember(lastSyncTime) {
                                    if (lastSyncTime > 0L) {
                                        val sdfDate = java.text.SimpleDateFormat("dd.MM", java.util.Locale.getDefault())
                                        sdfDate.format(java.util.Date(lastSyncTime))
                                    } else {
                                        "28.09"
                                    }
                                }
                                val syncTimeString = remember(lastSyncTime) {
                                    if (lastSyncTime > 0L) {
                                        val sdfTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                        sdfTime.format(java.util.Date(lastSyncTime))
                                    } else {
                                        "11:02"
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(ru.nya.nyeios.ui.theme.NierSurface)
                                        .border(UiPreferencesManager.borderWidth, ru.nya.nyeios.ui.theme.NierBorder)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(ru.nya.nyeios.ui.theme.NierPanel)
                                            .border(1.dp, ru.nya.nyeios.ui.theme.NierBorder),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = ru.nya.nyeios.ui.theme.NierDark,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.feed_cache_count_fmt, postsCount),
                                            fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            letterSpacing = 1.2.sp,
                                            color = ru.nya.nyeios.ui.theme.NierDark
                                        )
                                        Row(
                                            modifier = Modifier.padding(top = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            SegmentedMeter(
                                                progress = 0.82f,
                                                activeColor = ru.nya.nyeios.ui.theme.NierGreen,
                                                segments = 16,
                                                height = 5.dp,
                                                modifier = Modifier.width(84.dp)
                                            )
                                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                                Text(
                                                    text = stringResource(R.string.feed_sync_date_fmt, syncDateString),
                                                    fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                                                    fontSize = 8.5.sp,
                                                    color = ru.nya.nyeios.ui.theme.NierDim,
                                                    lineHeight = 9.5.sp
                                                )
                                                Text(
                                                    text = "       $syncTimeString",
                                                    fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                                                    fontSize = 8.5.sp,
                                                    color = ru.nya.nyeios.ui.theme.NierDim,
                                                    lineHeight = 9.5.sp
                                                )
                                            }
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .border(1.dp, ru.nya.nyeios.ui.theme.NierBlue)
                                            .background(Color.Transparent)
                                            .clickable {
                                                if (!isUserLoggedIn) {
                                                    onOpenLogin()
                                                } else {
                                                    feedViewModel?.requestSyncFeed() ?: onRefresh()
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                tint = ru.nya.nyeios.ui.theme.NierBlue,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = stringResource(R.string.action_update_caps),
                                                color = ru.nya.nyeios.ui.theme.NierBlue,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                                letterSpacing = 0.5.sp
                                            )
                                        }
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
                                    .background(ru.nya.nyeios.ui.theme.NierPanelAlt)
                                    .border(1.dp, if (activeCount > 0) ru.nya.nyeios.ui.theme.NierBlue else ru.nya.nyeios.ui.theme.NierBorderLight)
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
                                            .size(32.dp)
                                            .background(ru.nya.nyeios.ui.theme.NierBlue.copy(alpha = 0.15f))
                                            .border(1.dp, ru.nya.nyeios.ui.theme.NierBlue),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (activeCount > 0) Icons.Default.Download else Icons.Default.FolderOpen,
                                            contentDescription = null,
                                            tint = ru.nya.nyeios.ui.theme.NierBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = stringResource(R.string.downloads_manager_caps),
                                            color = ru.nya.nyeios.ui.theme.NierDark,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = if (activeCount > 0) {
                                                stringResource(R.string.downloads_downloading_fmt, activeCount)
                                            } else if (downloadedFiles.isNotEmpty()) {
                                                stringResource(R.string.downloads_saved_fmt, downloadedFiles.size, downloadManager.formatFileSize(totalBytes))
                                            } else {
                                                stringResource(R.string.downloads_title)
                                            },
                                            color = if (activeCount > 0) ru.nya.nyeios.ui.theme.NierBlue else ru.nya.nyeios.ui.theme.NierDim,
                                            fontSize = 11.sp,
                                            fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .border(1.dp, ru.nya.nyeios.ui.theme.NierBlue)
                                        .background(Color.Transparent)
                                        .clickable { openSheet() }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.downloads_open_caps),
                                            color = ru.nya.nyeios.ui.theme.NierBlue,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                            letterSpacing = 0.5.sp
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            tint = ru.nya.nyeios.ui.theme.NierBlue,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
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
                        Toast.makeText(context, it.localizedMessage ?: context.getString(R.string.error_open_file), Toast.LENGTH_SHORT).show()
                    }
                },
                onShareFile = { file ->
                    downloadManager.shareDownloadedFile(context, file).onFailure {
                        Toast.makeText(context, it.localizedMessage ?: context.getString(R.string.error_send_file), Toast.LENGTH_SHORT).show()
                    }
                },
                onSaveToDownloads = { file ->
                    downloadManager.saveToPublicDownloads(context, file).onSuccess {
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, it.localizedMessage ?: context.getString(R.string.error_save_file), Toast.LENGTH_SHORT).show()
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
    val defaultInitials = stringResource(R.string.feed_post_default_initials)
    val initials = remember(post.authorName, defaultInitials) {
        val words = post.authorName.split(" ").filter { it.isNotBlank() }
        when {
            words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
            words.size == 1 -> words[0].take(2).uppercase()
            else -> defaultInitials
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
            val (iconColor, extLabel) = remember(attachment.name) {
                val lower = attachment.name.lowercase()
                when {
                    lower.endsWith(".pdf") -> Pair(ru.nya.nyeios.ui.theme.NierRed, "PDF")
                    lower.endsWith(".docx") || lower.endsWith(".doc") -> Pair(ru.nya.nyeios.ui.theme.NierBlue, "DOC")
                    lower.endsWith(".xlsx") || lower.endsWith(".xls") -> Pair(ru.nya.nyeios.ui.theme.NierGreen, "XLS")
                    lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") -> Pair(ru.nya.nyeios.ui.theme.NierAmber, "ZIP")
                    lower.endsWith(".ppt") || lower.endsWith(".pptx") -> Pair(ru.nya.nyeios.ui.theme.NierPurple, "PPT")
                    else -> Pair(ru.nya.nyeios.ui.theme.NierDarkSecondary, "FILE")
                }
            }

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(if (isDownloaded) ru.nya.nyeios.ui.theme.NierGreen.copy(alpha = 0.15f) else iconColor.copy(alpha = 0.12f))
                    .border(1.dp, if (isDownloaded) ru.nya.nyeios.ui.theme.NierGreen else iconColor),
                contentAlignment = Alignment.Center
            ) {
                if (isDownloaded) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.downloads_file_default),
                        tint = ru.nya.nyeios.ui.theme.NierGreen,
                        modifier = Modifier.size(13.dp)
                    )
                } else {
                    Text(
                        text = extLabel,
                        color = iconColor,
                        fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

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

        // Action Buttons Row (NieR styled bordered buttons with vector icons)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (state) {
                is DownloadState.Completed -> {
                    NierActionButton(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        text = stringResource(R.string.feed_action_open),
                        isPrimary = true,
                        onClick = onOpen
                    )
                    NierActionButton(
                        icon = Icons.Default.Share,
                        text = stringResource(R.string.feed_action_share),
                        isPrimary = false,
                        onClick = onShare
                    )
                    NierActionButton(
                        icon = Icons.Default.SaveAlt,
                        text = stringResource(R.string.feed_action_save),
                        isPrimary = false,
                        onClick = onSaveToPublic
                    )
                }
                is DownloadState.Downloading -> {
                    Text(
                        text = stringResource(R.string.downloads_progress_fmt, (state.progress * 100).toInt()),
                        fontSize = 9.sp,
                        fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                        color = ru.nya.nyeios.ui.theme.NierBlue
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    NierActionButton(
                        icon = Icons.Default.Close,
                        text = stringResource(R.string.action_cancel),
                        isDanger = true,
                        onClick = onCancel
                    )
                }
                else -> {
                    NierActionButton(
                        icon = Icons.Default.Download,
                        text = stringResource(R.string.feed_action_download),
                        isPrimary = true,
                        onClick = onDownload
                    )
                }
            }
        }
    }
}

@Composable
fun NierActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isPrimary: Boolean = false,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = when {
        isPrimary -> ru.nya.nyeios.ui.theme.NierBg
        isDanger -> ru.nya.nyeios.ui.theme.NierRed
        else -> ru.nya.nyeios.ui.theme.NierDark
    }

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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(11.dp)
                )
            }
            Text(
                text = text,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                color = contentColor
            )
        }
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
                        text = stringResource(R.string.feed_sync_dialog_title),
                        color = ru.nya.nyeios.ui.theme.NierDark,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                        fontSize = 13.sp
                    )
                    Text(
                        text = stringResource(R.string.feed_downloading_data),
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
        val isEn = java.util.Locale.getDefault().language.equals("en", ignoreCase = true)
        val mbTotalLabel = if (isEn) "MB" else "МБ"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatBytesLocal(progress.bytesDownloaded, isEn)} / ~7.5 $mbTotalLabel",
                color = ru.nya.nyeios.ui.theme.NierDim,
                fontSize = 9.sp,
                fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily
            )
            Text(
                text = progress.statusText.ifEmpty { stringResource(R.string.downloads_loading_default) },
                color = ru.nya.nyeios.ui.theme.NierDim,
                fontSize = 9.sp,
                fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily
            )
        }

        // Cancel button
        NierActionButton(
            text = stringResource(R.string.feed_cancel_sync),
            isDanger = true,
            onClick = onCancel
        )
    }
}

private fun formatBytesLocal(bytes: Long, isEnglish: Boolean = java.util.Locale.getDefault().language == "en"): String {
    val bUnit = if (isEnglish) "B" else "Б"
    val kbUnit = if (isEnglish) "KB" else "КБ"
    val mbUnit = if (isEnglish) "MB" else "МБ"
    return when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f $mbUnit", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format(java.util.Locale.US, "%d $kbUnit", bytes / 1024)
        bytes > 0 -> "$bytes $bUnit"
        else -> "0 $bUnit"
    }
}
