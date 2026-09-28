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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.nya.nyeios.data.net.NetworkLogEntry
import ru.nya.nyeios.data.net.NetworkLogLevel
import ru.nya.nyeios.data.net.NetworkLogger
import ru.nya.nyeios.data.update.UpdateRepository
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.NycLed
import ru.nya.nyeios.ui.theme.NycMonoFamily
import ru.nya.nyeios.ui.theme.NycSansFamily
import ru.nya.nyeios.ui.theme.appSheetShape
import ru.nya.nyeios.ui.theme.nycWell
import ru.nya.nyeios.ui.update.UpdateBanner
import ru.nya.nyeios.ui.update.UpdateViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Parallel NyC-modern network log sheet from mockup nyeios_redesign.html
// (screen 7) and reference 7.png. Same log flow, same copy/clear/update-check
// actions as NetworkLogsBottomSheet; only rendering differs. Legacy sheet is
// untouched.
//
// Mockup notes: rows are HTTP-centric (method + path + code + ms + time) while
// the log flow stores request/response as separate entries, so rows are derived
// per entry with display-only parsing (no pairing, nothing is hidden in ВСЕ).
// Copy/clear/update-check have no mockup counterpart and live in one slim row.

// Mockup fixed tokens.
private val nycText = Color(0xFFDBE4F0)
private val nycBody = Color(0xFFB9C4D4)
private val nycMuted = Color(0xFF8794A7)
private val nycFaint = Color(0xFF5B6A7E)
private val nycGreen = Color(0xFF46E08C)
private val nycBlue = Color(0xFF6FA8FF)
private val nycRed = Color(0xFFF26D6D)
private val nycViolet = Color(0xFFA78BFA)

private val codeGet = Regex("""^GET\b""")
private val codePost = Regex("""^POST\b""")
private val codeNum = Regex("""Код:\s*(\d{3})""")
private val codeMs = Regex("""Время ответа:\s*(\d+)\s*мс""")
private val urlLine = Regex("""URL:\s*(\S+)""")
private val schemeHost = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://[^/]+""")

private val nycAmber = Color(0xFFF59E0B)

internal data class NycLogRow(
    val method: String,
    val methodColor: Color,
    val path: String,
    val code: Int?,
    val ms: Long?,
    val time: String,
    val isBad: Boolean,
    val isDegraded: Boolean = false,
    val badgeText: String? = null
)

internal fun deriveRow(entry: NetworkLogEntry): NycLogRow {
    val tag = entry.tag.uppercase().removePrefix("HTTP ").trim()
    val isDegraded = entry.isDegraded || entry.level == NetworkLogLevel.WARNING || tag.contains("ПУСТО") || tag.contains("СБОЙ")
    val (method, methodColor) = when {
        codeGet.containsMatchIn(tag) -> "GET" to NycCyan
        codePost.containsMatchIn(tag) -> "POST" to nycViolet
        isDegraded -> "GET" to nycAmber
        entry.level == NetworkLogLevel.ERROR -> (tag.ifEmpty { "ERR" }) to nycRed
        else -> (tag.ifEmpty { "LOG" }) to nycMuted
    }
    val rawUrl = urlLine.find(entry.details ?: "")?.groupValues?.getOrNull(1)
    val path = when {
        rawUrl != null -> schemeHost.replace(rawUrl, "").ifEmpty { "/" }
        else -> entry.message
    }
    val code = codeNum.find(entry.details ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
    val ms = codeMs.find(entry.details ?: "")?.groupValues?.getOrNull(1)?.toLongOrNull()
    val time = entry.timestamp.take(8)
    val isBad = (code != null && code >= 500) || entry.level == NetworkLogLevel.ERROR
    val badgeText = if (isDegraded) "200 ПУСТО" else code?.toString()
    return NycLogRow(method, methodColor, path, code, ms, time, isBad, isDegraded, badgeText)
}

internal fun codeColor(code: Int?, isDegraded: Boolean = false): Color = when {
    isDegraded -> nycAmber
    code == null -> nycFaint
    code in 200..299 -> nycGreen
    code in 300..399 -> nycBlue
    code in 400..499 -> nycMuted
    else -> nycRed
}

// Filter chip index: 0=ВСЕ 1=2XX 2=3XX 3=4XX 4=5XX.
internal fun passesLogFilter(code: Int?, filter: Int): Boolean = when (filter) {
    1 -> code in 200..299
    2 -> code in 300..399
    3 -> code in 400..499
    4 -> (code ?: 0) >= 500
    else -> true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NycLogsSheet(
    onDismiss: () -> Unit,
    updateViewModel: UpdateViewModel? = null
) {
    val context = LocalContext.current
    val logs by NetworkLogger.logs.collectAsState()
    val scope = rememberCoroutineScope()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var filter by remember { mutableIntStateOf(0) } // 0=ВСЕ 1=2XX 2=3XX 3=4XX 4=5XX

    val clock by produceState(initialValue = currentTime()) {
        while (true) {
            delay(1000L)
            value = currentTime()
        }
    }

    val rows = remember(logs, filter) {
        logs.map { it to deriveRow(it) }.filter { (_, r) -> passesLogFilter(r.code, filter) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF131C2A),
        shape = appSheetShape(),
        scrimColor = Color(0xCC04070B),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 9.dp, bottom = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(150, 185, 235, alpha = 46))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .padding(horizontal = 14.dp)
                .padding(bottom = 12.dp)
        ) {
            // Head: title + LIVE + clock.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Text(
                    text = "Сетевые логи",
                    fontFamily = NycSansFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = nycText
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    NycLed(color = nycGreen, diameter = 5.dp)
                    Text(
                        text = "LIVE",
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 8.sp,
                        letterSpacing = 1.5.sp,
                        color = nycGreen
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .nycWell(9.dp)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = clock,
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.5.sp,
                        color = nycMuted
                    )
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .nycWell(9.dp)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = nycMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Filters.
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 11.dp, bottom = 10.dp)
            ) {
                val labels = listOf("ВСЕ", "2XX", "3XX", "4XX", "5XX")
                labels.forEachIndexed { index, label ->
                    val on = filter == index
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .then(
                                if (on) {
                                    Modifier
                                        .background(NycCyan.copy(alpha = 0.12f))
                                        .border(1.dp, NycCyan.copy(alpha = 0.34f), RoundedCornerShape(9.dp))
                                } else {
                                    Modifier
                                        .background(Color(150, 185, 235, alpha = 7))
                                        .border(1.dp, Color(150, 185, 235, alpha = 19), RoundedCornerShape(9.dp))
                                }
                            )
                            .clickable { filter = index }
                            .padding(horizontal = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontFamily = NycMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.5.sp,
                            letterSpacing = 1.sp,
                            color = if (on) NycCyan else nycMuted
                        )
                    }
                }
            }

            // List.
            if (rows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .nycWell(12.dp)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (logs.isEmpty()) "СЕТЕВЫХ ЗАПРОСОВ ПОКА НЕТ" else "В ЭТОМ ФИЛЬТРЕ ПУСТО",
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = nycMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(rows, key = { it.first.id }) { (entry, row) ->
                        NycLogEntryRow(entry = entry, row = row)
                    }
                }
            }

            // Slim debug actions (no mockup counterpart; kept minimal).
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                NycLogAction(
                    text = "КОПИРОВАТЬ",
                    enabled = logs.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val formatted = NetworkLogger.getAllFormatted()
                        if (formatted.isNotEmpty()) {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("NyEIOS Network Logs", formatted)
                            )
                            Toast.makeText(context, "Логи скопированы в буфер", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Логи пусты", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                NycLogAction(
                    text = if (isCheckingUpdate) "..." else "ОБНОВЛЕНИЯ",
                    enabled = !isCheckingUpdate,
                    modifier = Modifier.weight(1f),
                    onClick = {
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
                )
                NycLogAction(
                    text = "ОЧИСТИТЬ",
                    enabled = logs.isNotEmpty(),
                    danger = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        NetworkLogger.clear()
                        Toast.makeText(context, "Журнал очищен", Toast.LENGTH_SHORT).show()
                    }
                )
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

private fun currentTime(): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

@Composable
private fun NycLogEntryRow(entry: NetworkLogEntry, row: NycLogRow) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val is200Response = entry.httpCode == 200 ||
            row.code == 200 ||
            row.isDegraded ||
            entry.isDegraded ||
            entry.tag.contains("200")
    val hasRawResponse = entry.rawResponse != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (row.isBad) {
                    Modifier
                        .background(nycRed.copy(alpha = 0.05f))
                        .border(1.dp, nycRed.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                } else {
                    Modifier
                        .background(Color(150, 185, 235, alpha = 7))
                        .border(1.dp, Color(150, 185, 235, alpha = 13), RoundedCornerShape(12.dp))
                }
            )
            .clickable { isExpanded = !isExpanded }
            .padding(horizontal = 10.dp, vertical = 13.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(row.methodColor.copy(alpha = 0.12f))
                    .border(1.dp, row.methodColor.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = row.method,
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 7.5.sp,
                    letterSpacing = 1.sp,
                    color = row.methodColor
                )
            }

            Text(
                text = row.path,
                fontFamily = NycMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                color = nycBody,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (row.code != null || row.isDegraded) {
                val cc = codeColor(row.code, row.isDegraded)
                val badge = row.badgeText ?: "${row.code}"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(cc.copy(alpha = if (row.isDegraded) 0.18f else 0.09f))
                        .border(1.dp, cc.copy(alpha = if (row.isDegraded) 0.40f else 0.22f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badge,
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.5.sp,
                        color = cc
                    )
                }
            }

            if (is200Response || hasRawResponse) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(150, 185, 235, alpha = 15))
                        .border(1.dp, nycBlue.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val text = entry.rawResponse.orEmpty()
                            val clip = ClipData.newPlainText("NyEIOS Raw Response", text)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, if (text.isEmpty()) "Ответ сервера пуст" else "Raw ответ скопирован", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Скопировать raw ответ",
                        tint = nycBlue,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            if (row.ms != null) {
                Text(
                    text = "${row.ms} мс",
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 8.5.sp,
                    color = nycMuted,
                    modifier = Modifier.width(52.dp)
                )
            }

            Text(
                text = row.time,
                fontFamily = NycMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 8.sp,
                color = nycFaint,
                modifier = Modifier.width(52.dp)
            )
        }

        if (isExpanded && !entry.details.isNullOrEmpty()) {
            Text(
                text = entry.details,
                fontFamily = NycMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 9.sp,
                lineHeight = 13.sp,
                color = nycMuted,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun NycLogAction(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (danger) nycRed else nycMuted
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(150, 185, 235, alpha = 7))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(9.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = NycMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 8.5.sp,
            letterSpacing = 1.sp,
            color = color.copy(alpha = if (enabled) 1f else 0.4f)
        )
    }
}
