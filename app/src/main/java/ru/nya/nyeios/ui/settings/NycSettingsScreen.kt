package ru.nya.nyeios.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nya.nyeios.data.model.UpdateInfo
import ru.nya.nyeios.data.net.EiosLastGetInfo
import ru.nya.nyeios.data.net.PingResult
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.NycLed
import ru.nya.nyeios.ui.theme.NycMonoFamily
import ru.nya.nyeios.ui.theme.NycSansFamily
import ru.nya.nyeios.ui.theme.nycCard
import ru.nya.nyeios.ui.theme.nycRaised
import ru.nya.nyeios.ui.theme.nycWell
import ru.nya.nyeios.ui.update.UpdateViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Parallel NyC-modern settings interface from mockup nyeios_redesign.html
// (screen 6) and reference 6.png. Same ViewModel, same callbacks, same copy
// semantics as SettingsScreen; only rendering differs. THEME and VERSION
// subtabs reuse the legacy blocks (already Nyc-adaptive); GENERAL telemetry
// is rebuilt below. Legacy SettingsScreen is otherwise untouched.

// Mockup fixed tokens.
private val nycText = Color(0xFFDBE4F0)
private val nycMuted = Color(0xFF8794A7)
private val nycFaint = Color(0xFF5B6A7E)
private val nycGreen = Color(0xFF46E08C)
private val nycAmber = Color(0xFFF0B44C)
private val nycRed = Color(0xFFF26D6D)

@Composable
fun NycSettingsScreen(
    viewModel: SettingsViewModel,
    updateViewModel: UpdateViewModel? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        NycSubtabBar(
            currentSubtab = uiState.currentSubtab,
            onSelectSubtab = { viewModel.selectSubtab(it) }
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (uiState.currentSubtab) {
                SettingsSubtab.GENERAL -> {
                    NycGeneralContent(
                        uiState = uiState,
                        onMeasurePing = { viewModel.measurePing() },
                        onRefreshIp = { viewModel.fetchExternalIp() },
                        onRefreshAll = { viewModel.refreshDiagnostics() }
                    )
                }
                SettingsSubtab.THEME -> {
                    ThemeSettingsContent()
                }
                SettingsSubtab.VERSION -> {
                    VersionSettingsContent(
                        uiState = uiState,
                        onCheckForUpdates = { viewModel.checkForUpdates() },
                        onDownloadUpdate = { info: UpdateInfo ->
                            updateViewModel?.downloadApk(info)
                        }
                    )
                }
            }
        }
    }
}

// ---------- Subtab seg ----------

@Composable
private fun NycSubtabBar(
    currentSubtab: SettingsSubtab,
    onSelectSubtab: (SettingsSubtab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .height(46.dp)
            .nycWell(13.dp)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        NycSegButton(
            selected = currentSubtab == SettingsSubtab.GENERAL,
            icon = Icons.Default.Tune,
            text = "ОБЩЕЕ",
            onClick = { onSelectSubtab(SettingsSubtab.GENERAL) }
        )
        NycSegButton(
            selected = currentSubtab == SettingsSubtab.THEME,
            icon = Icons.Default.Visibility,
            text = "ТЕМА",
            onClick = { onSelectSubtab(SettingsSubtab.THEME) }
        )
        NycSegButton(
            selected = currentSubtab == SettingsSubtab.VERSION,
            icon = Icons.Default.Description,
            text = "ВЕРСИЯ",
            onClick = { onSelectSubtab(SettingsSubtab.VERSION) }
        )
    }
}

@Composable
private fun RowScope.NycSegButton(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(38.dp)
            .then(
                if (selected) {
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF1A2534), Color(0xFF121B28))
                            )
                        )
                        .border(1.dp, Color(150, 185, 235, alpha = 19), RoundedCornerShape(10.dp))
                } else Modifier
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) NycCyan else nycFaint,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = text,
                fontFamily = NycMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.5.sp,
                color = if (selected) NycCyan else nycFaint
            )
        }
    }
}

// ---------- General telemetry ----------

@Composable
private fun NycGeneralContent(
    uiState: SettingsUiState,
    onMeasurePing: () -> Unit,
    onRefreshIp: () -> Unit,
    onRefreshAll: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        NycSecHdr("[ 01 · СВЯЗЬ С СЕРВЕРОМ EIOS.GUKOLOMNA.RU ]")

        // Server card.
        NycSetCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "СЕРВЕР",
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 8.sp,
                        letterSpacing = 1.5.sp,
                        color = nycFaint
                    )
                    Text(
                        text = "eios.gukolomna.ru",
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = nycText,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "IP-АДРЕС",
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 8.sp,
                        letterSpacing = 1.5.sp,
                        color = nycFaint
                    )
                    Text(
                        text = "87.242.111.49:443",
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = nycText,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
            Text(
                text = "РОСТЕЛЕКОМ · Г. КОЛОМНА",
                fontFamily = NycMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 8.5.sp,
                letterSpacing = 0.5.sp,
                color = nycFaint,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // Ping card with gauge.
        NycSetCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ПИНГ (TCP RTT)",
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 8.sp,
                        letterSpacing = 1.5.sp,
                        color = nycFaint
                    )
                    when (val ping = uiState.pingResult) {
                        is PingResult.Measuring -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 3.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = nycAmber
                                )
                                Text(
                                    text = "ЗАМЕР...",
                                    fontFamily = NycMonoFamily,
                                    fontSize = 12.sp,
                                    color = nycAmber
                                )
                            }
                        }
                        is PingResult.Success -> {
                            val pingColor = when {
                                ping.latencyMs < 120 -> nycGreen
                                ping.latencyMs < 300 -> nycAmber
                                else -> nycRed
                            }
                            Text(
                                text = "${ping.latencyMs} мс",
                                fontFamily = NycMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = pingColor,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                        is PingResult.Error -> {
                            Text(
                                text = "ОШИБКА: ${ping.message}",
                                fontFamily = NycMonoFamily,
                                fontSize = 12.sp,
                                color = nycRed,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                        is PingResult.Idle -> {
                            Text(
                                text = "—",
                                fontFamily = NycMonoFamily,
                                fontSize = 14.sp,
                                color = nycFaint,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                    val pingStamp = (uiState.pingResult as? PingResult.Success)?.timestamp
                        ?: (uiState.pingResult as? PingResult.Error)?.timestamp
                    if (pingStamp != null) {
                        Text(
                            text = "ЗАМЕР ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(pingStamp))}",
                            fontFamily = NycMonoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 8.5.sp,
                            letterSpacing = 0.5.sp,
                            color = nycFaint,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    NycMiniButton(
                        text = if (uiState.isMeasuringPing) "ЗАМЕР..." else "ЗАМЕРИТЬ",
                        icon = Icons.Default.Sync,
                        enabled = !uiState.isMeasuringPing,
                        onClick = onMeasurePing,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                NycPingGauge(pingResult = uiState.pingResult)
            }
        }

        // Last GET card.
        NycSetCard {
            NycGetRow(getInfo = uiState.lastGetInfo)
        }

        NycSecHdr("[ 02 · ЛИМИТ ЗАПРОСОВ GITHUB API ]")

        NycSetCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ОСТАТОК КВОТЫ",
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 8.sp,
                        letterSpacing = 1.5.sp,
                        color = nycFaint
                    )
                    val remaining = uiState.githubRateLimit.remaining
                    Text(
                        text = if (remaining != null) "$remaining / 60 ЗАПРОСОВ" else "60 / 60 ЗАПРОСОВ",
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = when {
                            remaining == null -> nycText
                            remaining < 30 -> nycRed
                            remaining < 45 -> nycAmber
                            else -> nycGreen
                        },
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                NycMiniButton(
                    text = "ОБНОВИТЬ",
                    icon = Icons.Default.Refresh,
                    enabled = true,
                    onClick = onRefreshAll
                )
            }

            // Quota bar with ticks.
            val quotaFrac = ((uiState.githubRateLimit.remaining ?: 60) / 60f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp)
                    .height(10.dp)
                    .nycWell(5.dp)
                    .clip(RoundedCornerShape(5.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(quotaFrac)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.horizontalGradient(colors = listOf(Color(0xFF2FB4B6), NycCyan))
                        )
                )
                Row(modifier = Modifier.fillMaxSize()) {
                    repeat(6) { i ->
                        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                            if (i < 5) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(1.dp)
                                        .fillMaxSize()
                                        .background(Color(0xFF04080C).copy(alpha = 0.7f))
                                )
                            }
                        }
                    }
                }
            }

            val resetMs = uiState.githubRateLimit.resetTimeMs
            Text(
                text = if (resetMs != null && resetMs > System.currentTimeMillis()) {
                    val minsLeft = maxOf(1, ((resetMs - System.currentTimeMillis()) / 60000).toInt())
                    "СБРОС ОКНА КВОТЫ ЧЕРЕЗ ~$minsLeft МИН"
                } else {
                    "СОХРАНЕНО С ПОСЛЕДНЕГО ЗАПРОСА К API ОБНОВЛЕНИЙ"
                },
                fontFamily = NycMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 8.5.sp,
                letterSpacing = 0.5.sp,
                color = nycFaint,
                modifier = Modifier.padding(top = 7.dp)
            )

            if (uiState.githubRateLimit.isLow) {
                val warningText = if (uiState.isVpnActive) {
                    "Осталось мало запросов на проверку обновлений. У вас включен VPN, что скорее всего и является причиной."
                } else {
                    "Осталось мало запросов на проверку обновлений. Может быть, вы сидите за роутером с VPN?"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(nycAmber.copy(alpha = 0.10f))
                        .border(1.dp, nycAmber.copy(alpha = 0.28f), RoundedCornerShape(11.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = nycAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = warningText,
                        fontFamily = NycSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = nycText
                    )
                }
            }
        }

        NycSecHdr("[ 03 · IP УСТРОЙСТВА И СЕТЕВОЙ ИНТЕРФЕЙС ]")

        NycSetCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .nycWell(11.dp)
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    Column {
                        Text(
                            text = "WAN · ИСХОДЯЩИЙ",
                            fontFamily = NycMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 8.sp,
                            letterSpacing = 1.5.sp,
                            color = nycFaint
                        )
                        Text(
                            text = uiState.externalIp ?: if (uiState.isFetchingIp) "ОПРЕДЕЛЕНИЕ..." else "НЕ ОПРЕДЕЛЕН",
                            fontFamily = NycMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = nycText,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .nycWell(11.dp)
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    Column {
                        Text(
                            text = "LAN · ЛОКАЛЬНЫЙ",
                            fontFamily = NycMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 8.sp,
                            letterSpacing = 1.5.sp,
                            color = nycFaint
                        )
                        Text(
                            text = uiState.localIp ?: "НЕ ОПРЕДЕЛЕН",
                            fontFamily = NycMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = nycText,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 11.dp, start = 2.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Legacy semantics kept: VPN active renders amber, off renders dim.
                    val vpnColor = if (uiState.isVpnActive) nycAmber else nycFaint
                    NycLed(color = if (uiState.isVpnActive) nycAmber else Color(0xFF3A4656), diameter = 7.dp)
                    Text(
                        text = if (uiState.isVpnActive) "VPN АКТИВЕН" else "VPN ОТКЛЮЧЕН",
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp,
                        letterSpacing = 1.sp,
                        color = vpnColor
                    )
                }
                NycMiniButton(
                    text = if (uiState.isFetchingIp) "..." else "ОБНОВИТЬ",
                    icon = Icons.Default.Sync,
                    enabled = !uiState.isFetchingIp,
                    onClick = onRefreshIp
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 9.dp)
                .nycRaised(15.dp)
                .clickable { onRefreshAll() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ПОВТОРИТЬ ДИАГНОСТИКУ СЕТИ",
                fontFamily = NycSansFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = nycText
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ---------- Pieces ----------

@Composable
private fun NycSecHdr(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 11.dp, bottom = 9.dp)
            .height(32.dp)
            .nycWell(9.dp)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = title,
            fontFamily = NycMonoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 8.5.sp,
            letterSpacing = 1.5.sp,
            color = Color(0xFF7D8EA4)
        )
    }
}

@Composable
private fun NycSetCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .nycCard(15.dp)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        content()
    }
}

@Composable
private fun NycMiniButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(NycCyan.copy(alpha = if (enabled) 0.12f else 0.05f))
            .border(1.dp, NycCyan.copy(alpha = if (enabled) 0.34f else 0.15f), RoundedCornerShape(9.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NycCyan,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = text,
            fontFamily = NycMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 8.5.sp,
            letterSpacing = 1.sp,
            color = NycCyan
        )
    }
}

@Composable
private fun NycPingGauge(pingResult: PingResult) {
    val latency = (pingResult as? PingResult.Success)?.latencyMs
    val frac = when (pingResult) {
        is PingResult.Success -> (1f - (latency!! / 500f)).coerceIn(0.2f, 0.95f)
        is PingResult.Measuring -> 0.5f
        else -> 0f
    }
    val color = when {
        latency == null -> nycFaint
        latency < 120 -> nycGreen
        latency < 300 -> nycAmber
        else -> nycRed
    }

    Box(
        modifier = Modifier.size(74.dp),
        contentAlignment = Alignment.Center
    ) {
        CanvasGauge(fraction = frac, color = color)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = latency?.toString() ?: "—",
                fontFamily = NycMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (latency == null) nycFaint else color
            )
            Text(
                text = "МС",
                fontFamily = NycMonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 7.sp,
                letterSpacing = 1.5.sp,
                color = nycFaint
            )
        }
    }
}

@Composable
private fun CanvasGauge(fraction: Float, color: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(74.dp)) {
        drawArc(
            color = Color(150, 185, 235, alpha = 23),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
        )
        if (fraction > 0f) {
            drawArc(
                brush = Brush.linearGradient(colors = listOf(Color(0xFF2FB4B6), color)),
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun NycGetRow(getInfo: EiosLastGetInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "ВРЕМЯ ПОСЛЕДНЕГО GET С САЙТА",
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 8.sp,
                    letterSpacing = 1.5.sp,
                    color = nycFaint
                )
                Text(
                    text = if (getInfo.durationMs != null) "${getInfo.durationMs} мс" else "—",
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = nycText,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            if (getInfo.timestamp != null) {
                val t = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(getInfo.timestamp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .nycWell(9.dp)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = t,
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.5.sp,
                        color = nycMuted
                    )
                }
            }
        }
        if (!getInfo.path.isNullOrEmpty()) {
            Text(
                text = "ЗАПРОС: ${getInfo.path}",
                fontFamily = NycMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 8.5.sp,
                letterSpacing = 0.5.sp,
                color = nycFaint,
                maxLines = 1,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
