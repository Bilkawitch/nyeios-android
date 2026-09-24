package ru.nya.nyeios.ui.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nya.nyeios.data.download.DownloadState
import ru.nya.nyeios.data.download.DownloadedFile
import ru.nya.nyeios.data.download.InternalDownloadManager
import ru.nya.nyeios.ui.theme.NierAmber
import ru.nya.nyeios.ui.theme.NierBg
import ru.nya.nyeios.ui.theme.NierBlue
import ru.nya.nyeios.ui.theme.NierBorder
import ru.nya.nyeios.ui.theme.NierBorderLight
import ru.nya.nyeios.ui.theme.NierDark
import ru.nya.nyeios.ui.theme.NierDarkSecondary
import ru.nya.nyeios.ui.theme.NierDim
import ru.nya.nyeios.ui.theme.NierGreen
import ru.nya.nyeios.ui.theme.NierPanel
import ru.nya.nyeios.ui.theme.NierPanelAlt
import ru.nya.nyeios.ui.theme.NierPurple
import ru.nya.nyeios.ui.theme.NierRed
import ru.nya.nyeios.ui.theme.RajdhaniFamily
import ru.nya.nyeios.ui.theme.ShareTechMonoFamily
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsBottomSheet(
    downloadedFiles: List<DownloadedFile>,
    downloadStates: Map<String, DownloadState>,
    onOpenFile: (File) -> Unit,
    onShareFile: (File) -> Unit,
    onSaveToDownloads: (File) -> Unit,
    onDeleteFile: (File) -> Unit,
    onClearAll: () -> Unit,
    onCancelDownload: (String) -> Unit,
    onDismiss: () -> Unit,
    downloadManager: InternalDownloadManager
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showClearConfirm by remember { mutableStateOf(false) }

    val totalBytes = remember(downloadedFiles) {
        downloadedFiles.sumOf { it.sizeBytes }
    }

    val activeDownloads = remember(downloadStates) {
        downloadStates.filterValues { it is DownloadState.Downloading }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NierPanel,
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 6.dp)
                    .size(width = 32.dp, height = 3.dp)
                    .background(NierBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 28.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(NierDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = NierBg,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = "ЗАГРУЗКИ",
                        color = NierDark,
                        fontFamily = RajdhaniFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        letterSpacing = 1.sp
                    )

                    if (downloadedFiles.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(NierBlue)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "${downloadedFiles.size}",
                                color = Color.White,
                                fontFamily = RajdhaniFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                if (downloadedFiles.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .border(1.dp, NierRed)
                            .background(Color.Transparent)
                            .clickable { showClearConfirm = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ОЧИСТИТЬ ВСЁ",
                            color = NierRed,
                            fontFamily = ShareTechMonoFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            if (downloadedFiles.isNotEmpty()) {
                Text(
                    text = "ЛОКАЛЬНОЕ ХРАНИЛИЩЕ // ${downloadManager.formatFileSize(totalBytes)} ИСПОЛЬЗОВАНО",
                    color = NierDim,
                    fontFamily = ShareTechMonoFamily,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Active Downloads Section
            if (activeDownloads.isNotEmpty()) {
                Text(
                    text = "[ ИДЁТ СКАЧИВАНИЕ: ${activeDownloads.size} ]",
                    color = NierBlue,
                    fontFamily = ShareTechMonoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    activeDownloads.forEach { (url, state) ->
                        if (state is DownloadState.Downloading) {
                            ActiveDownloadItem(
                                url = url,
                                state = state,
                                onCancel = { onCancelDownload(url) },
                                downloadManager = downloadManager
                            )
                        }
                    }
                }
            }

            // Downloaded Files List or Empty
            if (downloadedFiles.isEmpty() && activeDownloads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NierBorderLight)
                        .background(NierPanelAlt)
                        .padding(vertical = 36.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .border(1.dp, NierBorder)
                                .background(NierPanel),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = NierDim,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = "ФАЙЛОВ ПОКА НЕТ",
                            color = NierDark,
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = "Нажимайте на вложения в Живой ленте — они сохранятся в памяти приложения и будут доступны офлайн.",
                            color = NierDim,
                            fontFamily = ShareTechMonoFamily,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(
                        items = downloadedFiles,
                        key = { _, item -> item.file.absolutePath }
                    ) { index, item ->
                        DownloadedFileItem(
                            item = item,
                            isAlt = index % 2 == 1,
                            onOpen = { onOpenFile(item.file) },
                            onShare = { onShareFile(item.file) },
                            onSaveToDownloads = { onSaveToDownloads(item.file) },
                            onDelete = { onDeleteFile(item.file) },
                            downloadManager = downloadManager
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = NierPanel,
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier.border(1.5.dp, NierDark),
            title = {
                Text(
                    text = "УДАЛИТЬ ВСЕ ФАЙЛЫ?",
                    color = NierDark,
                    fontFamily = RajdhaniFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Text(
                    text = "Все скачанные вложения (${downloadedFiles.size} шт.) будут удалены из локального хранилища приложения. Вы всегда сможете скачать их заново из Живой ленты.",
                    color = NierDarkSecondary,
                    fontFamily = ShareTechMonoFamily,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .background(NierRed)
                        .clickable {
                            onClearAll()
                            showClearConfirm = false
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "УДАЛИТЬ ВСЁ",
                        color = Color.White,
                        fontFamily = ShareTechMonoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .border(1.dp, NierBorder)
                        .clickable { showClearConfirm = false }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ОТМЕНА",
                        color = NierDark,
                        fontFamily = ShareTechMonoFamily,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        )
    }
}

@Composable
private fun ActiveDownloadItem(
    url: String,
    state: DownloadState.Downloading,
    onCancel: () -> Unit,
    downloadManager: InternalDownloadManager
) {
    val cleanName = remember(url) {
        val lastSegment = url.substringAfterLast("/").substringBefore("?")
        if (lastSegment.isNotEmpty()) downloadManager.sanitizeFileName(lastSegment) else "Файл"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NierPanelAlt)
            .border(1.dp, NierBlue)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cleanName,
                    color = NierDark,
                    fontFamily = ShareTechMonoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (state.totalBytes > 0) {
                        "${(state.progress * 100).toInt()}% • ${downloadManager.formatFileSize(state.bytesDownloaded)} / ${downloadManager.formatFileSize(state.totalBytes)}"
                    } else {
                        "Загрузка... ${downloadManager.formatFileSize(state.bytesDownloaded)}"
                    },
                    color = NierDim,
                    fontFamily = ShareTechMonoFamily,
                    fontSize = 10.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(1.dp, NierBorderLight)
                    .background(NierPanel)
                    .clickable { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Отмена",
                    tint = NierDark,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        LinearProgressIndicator(
            progress = { if (state.progress >= 0f) state.progress else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = NierBlue,
            trackColor = NierBlue.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun DownloadedFileItem(
    item: DownloadedFile,
    isAlt: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSaveToDownloads: () -> Unit,
    onDelete: () -> Unit,
    downloadManager: InternalDownloadManager
) {
    val (iconColor, extLabel) = remember(item.name) {
        val lower = item.name.lowercase()
        when {
            lower.endsWith(".pdf") -> Pair(NierRed, "PDF")
            lower.endsWith(".docx") || lower.endsWith(".doc") -> Pair(NierBlue, "DOC")
            lower.endsWith(".xlsx") || lower.endsWith(".xls") -> Pair(NierGreen, "XLS")
            lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") -> Pair(NierAmber, "ZIP")
            lower.endsWith(".ppt") || lower.endsWith(".pptx") -> Pair(NierPurple, "PPT")
            else -> Pair(NierDarkSecondary, "FILE")
        }
    }

    val dateFormatted = remember(item.downloadedAt) {
        try {
            val sdf = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())
            sdf.format(Date(item.downloadedAt))
        } catch (e: Exception) {
            ""
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isAlt) NierPanelAlt else NierPanel)
            .border(0.5.dp, NierBorderLight)
            .clickable { onOpen() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // File extension square badge
        Box(
            modifier = Modifier
                .size(32.dp)
                .border(1.dp, iconColor)
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = extLabel,
                color = iconColor,
                fontFamily = RajdhaniFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // File Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = NierDark,
                fontFamily = ShareTechMonoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${downloadManager.formatFileSize(item.sizeBytes)}${if (dateFormatted.isNotEmpty()) " // $dateFormatted" else ""}",
                color = NierDim,
                fontFamily = ShareTechMonoFamily,
                fontSize = 9.sp
            )
        }

        // Action Buttons: Open, Share, SaveToDevice, Delete
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ↗ Open
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .border(1.dp, NierBorderLight)
                    .background(NierPanel)
                    .clickable { onOpen() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Открыть",
                    tint = NierDark,
                    modifier = Modifier.size(13.dp)
                )
            }

            // ⤴ Share
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .border(1.dp, NierBorderLight)
                    .background(NierPanel)
                    .clickable { onShare() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Поделиться",
                    tint = NierDark,
                    modifier = Modifier.size(13.dp)
                )
            }

            // 💾 Save to Downloads folder
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .border(1.dp, NierBorderLight)
                    .background(NierPanel)
                    .clickable { onSaveToDownloads() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SaveAlt,
                    contentDescription = "Сохранить на устройство",
                    tint = NierDark,
                    modifier = Modifier.size(13.dp)
                )
            }

            // 🗑 Delete (Red button)
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .border(1.dp, NierRed)
                    .background(NierPanel)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Удалить",
                    tint = NierRed,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
