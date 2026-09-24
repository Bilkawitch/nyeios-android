package ru.nya.nyeios.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import ru.nya.nyeios.data.net.EiosLastGetInfo
import ru.nya.nyeios.data.net.PingResult
import ru.nya.nyeios.data.update.GithubRateLimitState
import ru.nya.nyeios.ui.common.NierCheckbox
import ru.nya.nyeios.ui.common.NierDotRow
import ru.nya.nyeios.ui.theme.ThemeManager
import ru.nya.nyeios.ui.theme.NierThemeMode
import ru.nya.nyeios.ui.theme.NierAmber
import ru.nya.nyeios.ui.theme.NierBg
import ru.nya.nyeios.ui.theme.NierBlue
import ru.nya.nyeios.ui.theme.NierBorder
import ru.nya.nyeios.ui.theme.NierBorderLight
import ru.nya.nyeios.ui.theme.NierDark
import ru.nya.nyeios.ui.theme.NierDim
import ru.nya.nyeios.ui.theme.NierGreen
import ru.nya.nyeios.ui.theme.NierPanel
import ru.nya.nyeios.ui.theme.NierPanelAlt
import ru.nya.nyeios.ui.theme.NierRed
import ru.nya.nyeios.ui.theme.NierSelection
import ru.nya.nyeios.ui.theme.NierSelectionText
import ru.nya.nyeios.ui.theme.RajdhaniFamily
import ru.nya.nyeios.ui.theme.ShareTechMonoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NierBg)
    ) {
        // Subtabs Selector (ОБЩЕЕ / ТЕМА)
        SettingsSubtabBar(
            currentSubtab = uiState.currentSubtab,
            onSelectSubtab = { viewModel.selectSubtab(it) }
        )

        NierDotRow()

        // Content Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (uiState.currentSubtab) {
                SettingsSubtab.GENERAL -> {
                    GeneralSettingsContent(
                        uiState = uiState,
                        onMeasurePing = { viewModel.measurePing() },
                        onRefreshIp = { viewModel.fetchExternalIp() },
                        onRefreshAll = { viewModel.refreshDiagnostics() }
                    )
                }
                SettingsSubtab.THEME -> {
                    ThemeSettingsContent()
                }
            }
        }
    }
}

@Composable
private fun SettingsSubtabBar(
    currentSubtab: SettingsSubtab,
    onSelectSubtab: (SettingsSubtab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NierPanel)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsSubtab.entries.forEach { subtab ->
            val isSelected = subtab == currentSubtab

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (isSelected) NierSelection else NierPanelAlt)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) NierDark else NierBorderLight,
                        shape = RoundedCornerShape(0.dp)
                    )
                    .clickable { onSelectSubtab(subtab) }
                    .padding(vertical = 8.dp, horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // NieR Checkbox indicator [✕] / [☐]
                    NierCheckbox(
                        checked = isSelected,
                        color = if (isSelected) NierSelectionText else NierDark,
                        size = 14.dp
                    )

                    Text(
                        text = subtab.title,
                        color = if (isSelected) NierSelectionText else NierDark,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = RajdhaniFamily,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneralSettingsContent(
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 01. ПОДКЛЮЧЕНИЕ К СЕРВЕРУ ЭИОС ───────────────────────────────
        SectionHeader(title = "[ 01 · СВЯЗЬ С СЕРВЕРОМ EIOS.GUKOLOMNA.RU ]")

        NierCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Host info
                DiagnosticRow(
                    label = "СЕРВЕР",
                    value = "eios.gukolomna.ru",
                    subValue = "IP: 87.242.111.49:443 // РОСТЕЛЕКОМ, Г. КОЛОМНА"
                )

                // Ping Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ПИНГ (TCP RTT)",
                            color = NierDim,
                            fontSize = 10.sp,
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )

                        when (val ping = uiState.pingResult) {
                            is PingResult.Measuring -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = NierBlue
                                    )
                                    Text(
                                        text = "ЗАМЕР СЕТЕВОЙ ЗАДЕРЖКИ...",
                                        color = NierBlue,
                                        fontSize = 12.sp,
                                        fontFamily = ShareTechMonoFamily
                                    )
                                }
                            }
                            is PingResult.Success -> {
                                val pingColor = when {
                                    ping.latencyMs < 120 -> NierGreen
                                    ping.latencyMs < 300 -> NierAmber
                                    else -> NierRed
                                }
                                Text(
                                    text = "${ping.latencyMs} мс",
                                    color = pingColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = ShareTechMonoFamily
                                )
                            }
                            is PingResult.Error -> {
                                Text(
                                    text = "ОШИБКА: ${ping.message}",
                                    color = NierRed,
                                    fontSize = 12.sp,
                                    fontFamily = ShareTechMonoFamily
                                )
                            }
                            is PingResult.Idle -> {
                                Text(
                                    text = "— (НЕ ЗАМЕРЯЛСЯ)",
                                    color = NierDim,
                                    fontSize = 12.sp,
                                    fontFamily = ShareTechMonoFamily
                                )
                            }
                        }
                    }

                    // Button to measure ping
                    NieROutlineButton(
                        text = if (uiState.isMeasuringPing) "ЗАМЕР..." else "ЗАМЕРИТЬ",
                        enabled = !uiState.isMeasuringPing,
                        color = NierBlue,
                        onClick = onMeasurePing
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NierBorderLight)
                )

                // Last GET timing
                LastGetRow(getInfo = uiState.lastGetInfo)
            }
        }

        // ── 02. РЕЙТ-ЛИМИТ GITHUB API ─────────────────────────────────────
        SectionHeader(title = "[ 02 · ЛИМИТ ЗАПРОСОВ GITHUB API ]")

        NierCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val remaining = uiState.githubRateLimit.remaining
                val resetMs = uiState.githubRateLimit.resetTimeMs

                DiagnosticRow(
                    label = "ОСТАТОК КВОТЫ",
                    value = if (remaining != null) "$remaining / 60 ЗАПРОСОВ" else "60 / 60 (ПРОВЕРКА ЕЩЁ НЕ ВЫПОЛНЯЛАСЬ)",
                    subValue = if (resetMs != null && resetMs > System.currentTimeMillis()) {
                        val minsLeft = maxOf(1, ((resetMs - System.currentTimeMillis()) / 60000).toInt())
                        "Сброс окна квоты GitHub через ~$minsLeft мин."
                    } else {
                        "Сохранено с последнего выполненного запроса к API обновлений"
                    },
                    valueColor = when {
                        remaining == null -> NierDark
                        remaining < 30 -> NierRed
                        remaining < 45 -> NierAmber
                        else -> NierGreen
                    }
                )

                // Warning Banner if remaining < 30
                if (uiState.githubRateLimit.isLow) {
                    VpnRateLimitWarningBanner()
                }
            }
        }

        // ── 03. IP УСТРОЙСТВА И СЕТЕВОЙ ИНТЕРФЕЙС ─────────────────────────
        SectionHeader(title = "[ 03 · IP УСТРОЙСТВА И СЕТЕВОЙ ИНТЕРФЕЙС ]")

        NierCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Outbound external IP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ИСХОДЯЩИЙ IP (WAN)",
                            color = NierDim,
                            fontSize = 10.sp,
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )

                        Text(
                            text = uiState.externalIp ?: if (uiState.isFetchingIp) "ОПРЕДЕЛЕНИЕ..." else "НЕ ОПРЕДЕЛЕН",
                            color = NierDark,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ShareTechMonoFamily
                        )

                        Text(
                            text = "Адрес, с которого отправляются запросы на ЭИОС и GitHub",
                            color = NierDim,
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        )
                    }

                    NieROutlineButton(
                        text = if (uiState.isFetchingIp) "..." else "ОБНОВИТЬ",
                        enabled = !uiState.isFetchingIp,
                        color = NierDark,
                        onClick = onRefreshIp
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NierBorderLight)
                )

                // Local IP
                DiagnosticRow(
                    label = "ЛОКАЛЬНЫЙ IP (LAN)",
                    value = uiState.localIp ?: "НЕ ОПРЕДЕЛЕН",
                    subValue = "Интерфейс локальной подсети устройства"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NierBorderLight)
                )

                // Network Type & VPN Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ТИП СЕТИ",
                            color = NierDim,
                            fontSize = 10.sp,
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = uiState.networkType,
                            color = NierDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ShareTechMonoFamily
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "СТАТУС VPN",
                            color = NierDim,
                            fontSize = 10.sp,
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(if (uiState.isVpnActive) NierAmber else NierDim)
                            )
                            Text(
                                text = if (uiState.isVpnActive) "АКТИВЕН (VPN)" else "ОТКЛЮЧЕН",
                                color = if (uiState.isVpnActive) NierAmber else NierDim,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ShareTechMonoFamily
                            )
                        }
                    }
                }
            }
        }

        // Global refresh button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(NierPanelAlt)
                .border(1.dp, NierBorderLight, RoundedCornerShape(0.dp))
                .clickable { onRefreshAll() }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = NierDark,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "ПОВТОРИТЬ ДИАГНОСТИКУ СЕТИ",
                    color = NierDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = RajdhaniFamily,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ThemeSettingsContent() {
    val context = LocalContext.current
    val currentTheme = ThemeManager.currentTheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(title = "[ 01 · ВЫБОР ТЕМЫ ОФОРМЛЕНИЯ ]")

        NierCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Theme 1: YoRHa Regular
                ThemeItemRow(
                    title = "YoRHa Regular (Светлая тема)",
                    subtitle = "Классическая индустриальная палитра NieR: Automata: песочно-оливковый фон, темный контрастный текст и акценты",
                    isSelected = currentTheme == NierThemeMode.REGULAR,
                    onClick = { ThemeManager.setTheme(context, NierThemeMode.REGULAR) }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NierBorderLight)
                )

                // Theme 2: YoRHa Night
                ThemeItemRow(
                    title = "YoRHa Night (Темная тема)",
                    subtitle = "Инвертированная палитра NieR: Automata: глубокий темный фон, теплый бежевый текст и панели",
                    isSelected = currentTheme == NierThemeMode.NIGHT,
                    onClick = { ThemeManager.setTheme(context, NierThemeMode.NIGHT) }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NierBorderLight)
                )

                // Theme 3: YoRHa Black (AMOLED)
                ThemeItemRow(
                    title = "YoRHa Black (AMOLED)",
                    subtitle = "100% черный фон для OLED-экранов с контрастными элементами темного цвета палитры YoRHa",
                    isSelected = currentTheme == NierThemeMode.BLACK,
                    onClick = { ThemeManager.setTheme(context, NierThemeMode.BLACK) }
                )
            }
        }
    }
}

@Composable
private fun ThemeItemRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        NierCheckbox(
            checked = isSelected,
            color = if (isSelected) NierDark else NierBorderLight,
            size = 14.dp,
            modifier = Modifier.padding(top = 2.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    color = NierDark,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    fontFamily = RajdhaniFamily
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .background(NierDark)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "АКТИВНО",
                            color = NierBg,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = RajdhaniFamily,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
            Text(
                text = subtitle,
                color = NierDim,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun VpnRateLimitWarningBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(NierAmber.copy(alpha = 0.12f))
            .border(
                width = 1.5.dp,
                color = NierAmber,
                shape = RoundedCornerShape(0.dp)
            )
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = NierAmber,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "ВНИМАНИЕ // НИЗКИЙ ОСТАТОК КВОТЫ",
                    color = NierDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = RajdhaniFamily,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Осталось мало запросов на проверку обновления. Может быть у вас включен VPN?",
                    color = NierDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun LastGetRow(getInfo: EiosLastGetInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = "ВРЕМЯ ПОСЛЕДНЕГО GET С САЙТА",
            color = NierDim,
            fontSize = 10.sp,
            fontFamily = RajdhaniFamily,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        if (getInfo.durationMs != null) {
            val formattedTime = if (getInfo.timestamp != null) {
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(getInfo.timestamp))
            } else ""

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${getInfo.durationMs} мс",
                    color = NierDark,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ShareTechMonoFamily
                )
                if (formattedTime.isNotEmpty()) {
                    Text(
                        text = "[$formattedTime]",
                        color = NierDim,
                        fontSize = 11.sp,
                        fontFamily = ShareTechMonoFamily
                    )
                }
            }

            if (!getInfo.path.isNullOrEmpty()) {
                Text(
                    text = "Запрос: ${getInfo.path}",
                    color = NierDim,
                    fontSize = 10.sp,
                    fontFamily = ShareTechMonoFamily,
                    maxLines = 1
                )
            }
        } else {
            Text(
                text = "— (В ТЕКУЩЕЙ СЕССИИ ЗАПРОСОВ НЕ БЫЛО)",
                color = NierDim,
                fontSize = 12.sp,
                fontFamily = ShareTechMonoFamily
            )
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    subValue: String? = null,
    valueColor: Color = NierDark
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = NierDim,
            fontSize = 10.sp,
            fontFamily = RajdhaniFamily,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = ShareTechMonoFamily
        )
        if (!subValue.isNullOrEmpty()) {
            Text(
                text = subValue,
                color = NierDim,
                fontSize = 10.sp,
                lineHeight = 13.sp
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(NierDark)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = title,
            color = NierBg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RajdhaniFamily,
            letterSpacing = 1.2.sp
        )
    }
}

@Composable
private fun NierCard(
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(NierPanelAlt)
            .border(
                width = 1.dp,
                color = NierBorderLight,
                shape = RoundedCornerShape(0.dp)
            )
    ) {
        content()
    }
}

@Composable
private fun NieROutlineButton(
    text: String,
    enabled: Boolean = true,
    color: Color = NierDark,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = if (enabled) color else NierBorderLight,
                shape = RoundedCornerShape(0.dp)
            )
            .background(Color.Transparent)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) color else NierDim,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RajdhaniFamily,
            letterSpacing = 0.5.sp
        )
    }
}
