package ru.nya.nyeios.ui.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.nya.nyeios.data.net.NetworkLogEntry
import ru.nya.nyeios.data.net.NetworkLogLevel
import ru.nya.nyeios.data.net.NetworkLogger
import ru.nya.nyeios.data.update.UpdateRepository
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
import ru.nya.nyeios.ui.theme.NierRed
import ru.nya.nyeios.ui.theme.NierSurface
import ru.nya.nyeios.ui.theme.RajdhaniFamily
import ru.nya.nyeios.ui.theme.ShareTechMonoFamily
import ru.nya.nyeios.ui.update.UpdateBanner
import ru.nya.nyeios.ui.update.UpdateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkLogsBottomSheet(
    onDismiss: () -> Unit,
    updateViewModel: UpdateViewModel? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    val logs by NetworkLogger.logs.collectAsState()
    val scope = rememberCoroutineScope()
    var isCheckingUpdate by remember { mutableStateOf(false) }

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
                .fillMaxHeight(0.85f)
                .padding(horizontal = 14.dp)
                .padding(bottom = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
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
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = NierBg,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = "СЕТЕВЫЕ ЛОГИ",
                        color = NierDark,
                        fontFamily = RajdhaniFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        letterSpacing = 1.sp
                    )

                    Box(
                        modifier = Modifier
                            .background(NierBlue)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "${logs.size}",
                            color = Color.White,
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Check for updates button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .border(1.dp, NierBorderLight)
                            .background(NierPanelAlt)
                            .clickable {
                                if (!isCheckingUpdate) {
                                    isCheckingUpdate = true
                                    scope.launch(Dispatchers.IO) {
                                        NetworkLogger.logInfo(
                                            tag = "UPDATE",
                                            message = "Проверка обновлений…",
                                            details = "Запрос к GitHub Releases API"
                                        )
                                        try {
                                            val repo = UpdateRepository.getInstance(context)
                                            val info = repo.checkForUpdate(forceCheck = true)
                                            if (info != null) {
                                                NetworkLogger.logSuccess(
                                                    tag = "UPDATE",
                                                    message = "Доступна версия ${info.version}",
                                                    details = "APK: ${info.apkUrl}\nРазмер: ${info.apkSize / 1_048_576.0} МБ\n\nЧто нового:\n${info.changelog}"
                                                )
                                                withContext(Dispatchers.Main) {
                                                    updateViewModel?.setUpdateAvailable(info)
                                                }
                                            } else {
                                                NetworkLogger.logInfo(
                                                    tag = "UPDATE",
                                                    message = "Обновлений нет — установлена актуальная версия"
                                                )
                                            }
                                        } catch (e: Exception) {
                                            NetworkLogger.logError(
                                                tag = "UPDATE",
                                                message = "Ошибка проверки обновлений",
                                                error = e
                                            )
                                        } finally {
                                            isCheckingUpdate = false
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp,
                                color = NierBlue
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = "Проверить обновления",
                                tint = NierDark,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    // Close Button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .border(1.dp, NierBorderLight)
                            .background(NierPanelAlt)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = NierDark,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Action buttons: Copy all & Clear
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, NierDark)
                        .background(NierPanelAlt)
                        .clickable {
                            val formatted = NetworkLogger.getAllFormatted()
                            if (formatted.isNotEmpty()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("NyEIOS Network Logs", formatted)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Логи скопированы в буфер", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Логи пусты", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = NierDark
                        )
                        Text(
                            text = "КОПИРОВАТЬ ВСЁ",
                            fontFamily = ShareTechMonoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = NierDark
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, NierRed)
                        .background(NierPanelAlt)
                        .clickable {
                            NetworkLogger.clear()
                            Toast.makeText(context, "Журнал очищен", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = NierRed
                        )
                        Text(
                            text = "ОЧИСТИТЬ",
                            fontFamily = ShareTechMonoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = NierRed
                        )
                    }
                }
            }

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
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
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                tint = NierDim,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = "СЕТЕВЫХ ЗАПРОСОВ ПОКА НЕТ",
                            color = NierDark,
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = "Журнал запросов начнет наполняться при синхронизации расписания, Живой ленты или учебного плана.",
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(logs, key = { _, it -> it.id }) { index, entry ->
                        LogEntryCard(entry = entry, isAlt = index % 2 == 1)
                    }
                }
            }

            if (updateViewModel != null) {
                val updateUiState by updateViewModel.uiState.collectAsState()
                UpdateBanner(
                    state = updateUiState,
                    viewModel = updateViewModel,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun LogEntryCard(entry: NetworkLogEntry, isAlt: Boolean) {
    var isExpanded by remember { mutableStateOf(false) }

    val (badgeColor, badgeLabel) = when (entry.level) {
        NetworkLogLevel.REQUEST -> Pair(NierBlue, entry.tag.ifEmpty { "REQ" })
        NetworkLogLevel.RESPONSE -> Pair(NierGreen, entry.tag.ifEmpty { "RES" })
        NetworkLogLevel.SUCCESS -> Pair(NierGreen, entry.tag.ifEmpty { "OK" })
        NetworkLogLevel.ERROR -> Pair(NierRed, entry.tag.ifEmpty { "ERR" })
        NetworkLogLevel.INFO -> Pair(NierAmber, entry.tag.ifEmpty { "INFO" })
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isAlt) NierPanelAlt else NierPanel)
            .border(0.5.dp, NierBorderLight)
            .clickable { isExpanded = !isExpanded }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Main log row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Level Badge
            Box(
                modifier = Modifier
                    .background(badgeColor.copy(alpha = 0.15f))
                    .border(0.5.dp, badgeColor)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = badgeLabel,
                    fontSize = 9.sp,
                    fontFamily = RajdhaniFamily,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                    letterSpacing = 0.5.sp
                )
            }

            // Message / URL
            Text(
                text = entry.message,
                fontSize = 11.sp,
                fontFamily = ShareTechMonoFamily,
                fontWeight = FontWeight.Bold,
                color = if (entry.level == NetworkLogLevel.ERROR) NierRed else NierDark,
                maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Timestamp
            Text(
                text = entry.timestamp,
                fontSize = 9.sp,
                fontFamily = ShareTechMonoFamily,
                color = NierDim
            )

            if (!entry.details.isNullOrEmpty()) {
                Text(
                    text = if (isExpanded) "▲" else "▼",
                    fontSize = 9.sp,
                    fontFamily = ShareTechMonoFamily,
                    color = NierDim
                )
            }
        }

        // Expanded details
        if (isExpanded && !entry.details.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .background(NierSurface)
                    .border(0.5.dp, NierBorder)
                    .padding(6.dp)
            ) {
                Text(
                    text = entry.details,
                    fontSize = 10.sp,
                    fontFamily = ShareTechMonoFamily,
                    color = NierDarkSecondary,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
