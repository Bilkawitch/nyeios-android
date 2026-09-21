package ru.nya.nyeios.ui.download

import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nya.nyeios.data.download.DownloadState
import ru.nya.nyeios.data.download.DownloadedFile
import ru.nya.nyeios.data.download.InternalDownloadManager
import ru.nya.nyeios.ui.theme.ExamRed
import ru.nya.nyeios.ui.theme.ExamRedBg
import ru.nya.nyeios.ui.theme.LabAmber
import ru.nya.nyeios.ui.theme.LectureBlue
import ru.nya.nyeios.ui.theme.LectureBlueBg
import ru.nya.nyeios.ui.theme.ObsidianBg
import ru.nya.nyeios.ui.theme.ObsidianBorder
import ru.nya.nyeios.ui.theme.ObsidianCard
import ru.nya.nyeios.ui.theme.ObsidianSurface
import ru.nya.nyeios.ui.theme.OtherPurple
import ru.nya.nyeios.ui.theme.PracticeGreen
import ru.nya.nyeios.ui.theme.TextMuted
import ru.nya.nyeios.ui.theme.TextPrimary
import ru.nya.nyeios.ui.theme.TextSecondary
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
        containerColor = ObsidianSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ObsidianBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(LectureBlueBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = LectureBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Загрузки ЭИОС",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (downloadedFiles.isEmpty()) {
                                "Нет сохраненных файлов"
                            } else {
                                "Файлов: ${downloadedFiles.size} • ${downloadManager.formatFileSize(totalBytes)}"
                            },
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                if (downloadedFiles.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearConfirm = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Очистить всё",
                            tint = ExamRed.copy(alpha = 0.85f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Active Downloads Section
            if (activeDownloads.isNotEmpty()) {
                Text(
                    text = "Идёт скачивание (${activeDownloads.size}):",
                    color = LectureBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
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

            // Downloaded Files List
            if (downloadedFiles.isEmpty() && activeDownloads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                            .background(ObsidianCard),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Text(
                            text = "Файлов пока нет",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "Нажимайте на вложения в Живой ленте — они скачаются прямо в приложение и будут доступны без интернета.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = downloadedFiles,
                        key = { it.file.absolutePath }
                    ) { item ->
                        DownloadedFileItem(
                            item = item,
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
            containerColor = ObsidianCard,
            title = {
                Text(
                    text = "Удалить все файлы?",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Все скачанные вложения (${downloadedFiles.size} шт.) будут удалены из локального хранилища приложения. Вы всегда сможете скачать их заново из Живой ленты.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExamRed)
                ) {
                    Text("Удалить всё", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Отмена", color = TextMuted)
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
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianCard)
            .border(1.dp, LectureBlue.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cleanName,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (state.totalBytes > 0) {
                        "${(state.progress * 100).toInt()}% • ${downloadManager.formatFileSize(state.bytesDownloaded)} / ${downloadManager.formatFileSize(state.totalBytes)}"
                    } else {
                        "Загрузка... ${downloadManager.formatFileSize(state.bytesDownloaded)}"
                    },
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Отмена",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

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
}

@Composable
private fun DownloadedFileItem(
    item: DownloadedFile,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSaveToDownloads: () -> Unit,
    onDelete: () -> Unit,
    downloadManager: InternalDownloadManager
) {
    val (iconColor, extLabel) = remember(item.name) {
        val lower = item.name.lowercase()
        when {
            lower.endsWith(".pdf") -> Pair(ExamRed, "PDF")
            lower.endsWith(".docx") || lower.endsWith(".doc") -> Pair(LectureBlue, "DOC")
            lower.endsWith(".xlsx") || lower.endsWith(".xls") -> Pair(PracticeGreen, "XLS")
            lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") -> Pair(LabAmber, "ZIP")
            lower.endsWith(".ppt") || lower.endsWith(".pptx") -> Pair(OtherPurple, "PPT")
            else -> Pair(TextSecondary, "ФАЙЛ")
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianCard)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpen() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = extLabel,
                    color = iconColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${downloadManager.formatFileSize(item.sizeBytes)}${if (dateFormatted.isNotEmpty()) " • $dateFormatted" else ""}",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onOpen,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LectureBlueBg,
                    contentColor = LectureBlue
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Открыть",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            IconButton(
                onClick = onShare,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Поделиться",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = onSaveToDownloads,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SaveAlt,
                    contentDescription = "Сохранить в папку Загрузки",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Удалить",
                    tint = ExamRed.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
