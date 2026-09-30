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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import ru.nya.nyeios.R
import ru.nya.nyeios.data.model.UpdateInfo
import ru.nya.nyeios.data.net.EiosLastGetInfo
import ru.nya.nyeios.data.net.EndpointHealthItem
import ru.nya.nyeios.data.net.EndpointStatus
import ru.nya.nyeios.data.net.PingResult
import ru.nya.nyeios.ui.theme.ListDensityMode
import ru.nya.nyeios.ui.theme.NierThemeMode
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.NycLed
import ru.nya.nyeios.ui.theme.NycMonoFamily
import ru.nya.nyeios.ui.theme.NycSansFamily
import ru.nya.nyeios.ui.theme.ThemeManager
import ru.nya.nyeios.ui.theme.UiAnimatedVisibility
import ru.nya.nyeios.ui.theme.UiPreferencesManager
import ru.nya.nyeios.ui.theme.applyThemeWithRestart
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
private val nycBody = Color(0xFFB9C4D4)
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
                        onRefreshAll = { viewModel.refreshDiagnostics() },
                        onCheckEndpoints = { viewModel.checkEndpoints() }
                    )
                }
                SettingsSubtab.THEME -> {
                    NycThemeContent()
                }
                SettingsSubtab.VERSION -> {
                    NycVersionContent(
                        uiState = uiState,
                        onCheckForUpdates = { viewModel.checkForUpdates() },
                        onDownloadUpdate = { info: UpdateInfo ->
                            updateViewModel?.downloadApk(info)
                        }
                    )
                }
                SettingsSubtab.LANGUAGE -> {
                    NycLanguageContent()
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
            text = ru.nya.nyeios.ui.language.typewriterText(
                androidx.compose.ui.res.stringResource(ru.nya.nyeios.R.string.settings_tab_general),
                0.15f
            ),
            onClick = { onSelectSubtab(SettingsSubtab.GENERAL) }
        )
        NycSegButton(
            selected = currentSubtab == SettingsSubtab.THEME,
            icon = Icons.Default.Visibility,
            text = ru.nya.nyeios.ui.language.typewriterText(
                androidx.compose.ui.res.stringResource(ru.nya.nyeios.R.string.settings_tab_theme),
                0.17f
            ),
            onClick = { onSelectSubtab(SettingsSubtab.THEME) }
        )
        NycSegButton(
            selected = currentSubtab == SettingsSubtab.VERSION,
            icon = Icons.Default.Description,
            text = ru.nya.nyeios.ui.language.typewriterText(
                androidx.compose.ui.res.stringResource(ru.nya.nyeios.R.string.settings_tab_version),
                0.19f
            ),
            onClick = { onSelectSubtab(SettingsSubtab.VERSION) }
        )
        NycSegButton(
            selected = currentSubtab == SettingsSubtab.LANGUAGE,
            icon = Icons.Default.Language,
            text = ru.nya.nyeios.ui.language.typewriterText(
                androidx.compose.ui.res.stringResource(ru.nya.nyeios.R.string.settings_tab_language),
                0.21f
            ),
            onClick = { onSelectSubtab(SettingsSubtab.LANGUAGE) }
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
    onRefreshAll: () -> Unit,
    onCheckEndpoints: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        NycSecHdr(stringResource(R.string.settings_server_conn_title))

        // Server card.
        NycSetCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.settings_server_label),
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
                        text = stringResource(R.string.settings_ip_label),
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
                text = stringResource(R.string.settings_provider_location),
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
                        text = stringResource(R.string.settings_ping_label),
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
                                    text = stringResource(R.string.settings_action_measuring),
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
                                text = stringResource(R.string.settings_ms_fmt, ping.latencyMs),
                                fontFamily = NycMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = pingColor,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                        is PingResult.Error -> {
                            Text(
                                text = stringResource(R.string.settings_ping_error_fmt, ping.message),
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
                        val stampStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(pingStamp))
                        Text(
                            text = stringResource(R.string.settings_ping_stamp_fmt, stampStr),
                            fontFamily = NycMonoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 8.5.sp,
                            letterSpacing = 0.5.sp,
                            color = nycFaint,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    NycMiniButton(
                        text = if (uiState.isMeasuringPing)
                            stringResource(R.string.settings_action_measuring)
                        else
                            stringResource(R.string.settings_action_measure),
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

        // Endpoints diagnostics card.
        NycSetCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_endpoints_diag_title),
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 8.sp,
                        letterSpacing = 1.5.sp,
                        color = nycFaint
                    )
                    Text(
                        text = stringResource(R.string.settings_endpoints_diag_desc),
                        fontFamily = NycSansFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = nycText,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                NycMiniButton(
                    text = if (uiState.isCheckingEndpoints)
                        stringResource(R.string.settings_action_checking)
                    else
                        stringResource(R.string.settings_action_check),
                    icon = Icons.Default.Sync,
                    enabled = !uiState.isCheckingEndpoints,
                    onClick = onCheckEndpoints
                )
            }

            Spacer(Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                uiState.endpointsHealth.forEach { ep ->
                    NycEndpointHealthRow(item = ep)
                }
            }
        }

        NycSecHdr(stringResource(R.string.settings_github_limit_title))

        NycSetCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_quota_remaining_label),
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 8.sp,
                        letterSpacing = 1.5.sp,
                        color = nycFaint
                    )
                    val remaining = uiState.githubRateLimit.remaining
                    Text(
                        text = stringResource(R.string.settings_quota_requests_fmt, remaining ?: 60),
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
                    text = stringResource(R.string.action_refresh).uppercase(),
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
                    stringResource(R.string.settings_quota_reset_fmt, minsLeft)
                } else {
                    stringResource(R.string.settings_quota_sub_default)
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
                    stringResource(R.string.settings_vpn_warning_vpn)
                } else {
                    stringResource(R.string.settings_vpn_warning_no_vpn)
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

        NycSecHdr(stringResource(R.string.settings_ip_section_title))

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
                            text = stringResource(R.string.settings_wan_short),
                            fontFamily = NycMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 8.sp,
                            letterSpacing = 1.5.sp,
                            color = nycFaint
                        )
                        val detectingStr = stringResource(R.string.settings_detecting)
                        val notDetectedStr = stringResource(R.string.settings_not_detected)
                        Text(
                            text = uiState.externalIp ?: if (uiState.isFetchingIp) detectingStr else notDetectedStr,
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
                            text = stringResource(R.string.settings_lan_short),
                            fontFamily = NycMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 8.sp,
                            letterSpacing = 1.5.sp,
                            color = nycFaint
                        )
                        val notDetectedStr = stringResource(R.string.settings_not_detected)
                        Text(
                            text = uiState.localIp ?: notDetectedStr,
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
                        text = if (uiState.isVpnActive)
                            stringResource(R.string.settings_vpn_active_badge)
                        else
                            stringResource(R.string.settings_vpn_disabled_badge),
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp,
                        letterSpacing = 1.sp,
                        color = vpnColor
                    )
                }
                NycMiniButton(
                    text = if (uiState.isFetchingIp) "..." else stringResource(R.string.action_refresh).uppercase(),
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
                text = stringResource(R.string.settings_action_repeat_diag),
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
                text = stringResource(R.string.unit_ms_caps),
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
                    text = stringResource(R.string.settings_last_get_title),
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 8.sp,
                    letterSpacing = 1.5.sp,
                    color = nycFaint
                )
                Text(
                    text = if (getInfo.durationMs != null) stringResource(R.string.settings_ms_fmt, getInfo.durationMs) else "—",
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
                text = "${stringResource(R.string.settings_request_label).uppercase()} ${getInfo.path}",
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

@Composable
private fun NycEndpointHealthRow(item: EndpointHealthItem) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val (statusColor, statusBadge) = when (item.status) {
        EndpointStatus.IDLE -> Pair(nycFaint, stringResource(R.string.settings_endpoint_not_checked))
        EndpointStatus.PENDING -> Pair(nycFaint, stringResource(R.string.settings_endpoint_pending))
        EndpointStatus.CHECKING -> Pair(NycCyan, stringResource(R.string.settings_endpoint_checking))
        EndpointStatus.OK -> Pair(nycGreen, stringResource(R.string.settings_endpoint_available))
        EndpointStatus.DEGRADED -> Pair(nycAmber, stringResource(R.string.settings_endpoint_degraded))
        EndpointStatus.AUTH_REQUIRED -> Pair(nycAmber, stringResource(R.string.settings_endpoint_auth_required))
        EndpointStatus.ERROR -> Pair(nycRed, stringResource(R.string.settings_endpoint_error))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(150, 185, 235, alpha = 7))
            .border(1.dp, if (isExpanded) NycCyan.copy(alpha = 0.35f) else Color(150, 185, 235, alpha = 13), RoundedCornerShape(8.dp))
            .clickable { isExpanded = !isExpanded }
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    val displayName = when (item.id) {
                        "timetable" -> stringResource(R.string.tab_schedule).uppercase()
                        "feed" -> stringResource(R.string.tab_feed).uppercase()
                        "curriculum" -> stringResource(R.string.settings_endpoint_curriculum)
                        else -> item.name
                    }
                    Text(
                        text = displayName,
                        fontFamily = NycSansFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = nycText
                    )
                    Text(
                        text = item.path,
                        fontFamily = NycMonoFamily,
                        fontSize = 9.5.sp,
                        color = nycFaint
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(statusColor.copy(alpha = 0.12f))
                            .border(1.dp, statusColor.copy(alpha = 0.28f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = statusBadge,
                            fontFamily = NycMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp,
                            letterSpacing = 0.5.sp,
                            color = statusColor,
                            maxLines = 1
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) stringResource(R.string.settings_collapse_log) else stringResource(R.string.settings_expand_log),
                        tint = nycFaint,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            if (item.message != null || item.latencyMs != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.message ?: "",
                        fontFamily = NycMonoFamily,
                        fontSize = 9.sp,
                        color = when (item.status) {
                            EndpointStatus.ERROR -> nycRed
                            EndpointStatus.DEGRADED -> nycAmber
                            EndpointStatus.CHECKING -> NycCyan
                            else -> nycBody
                        },
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.latencyMs != null) {
                        Text(
                            text = stringResource(R.string.settings_ms_fmt, item.latencyMs),
                            fontFamily = NycMonoFamily,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = nycMuted
                        )
                    }
                }
            }

            // Выпадающий технический журнал запроса (drop-out с сырыми машинными логами)
            UiAnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(10, 15, 25, alpha = 200))
                        .border(1.dp, Color(150, 185, 235, alpha = 25), RoundedCornerShape(6.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_raw_log_header),
                            fontFamily = NycMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = nycMuted,
                            letterSpacing = 0.5.sp
                        )

                        if (!item.rawLog.isNullOrEmpty()) {
                            val logCopiedToastMsg = stringResource(R.string.settings_log_copied_toast)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NycCyan.copy(alpha = 0.15f))
                                    .border(1.dp, NycCyan.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(item.rawLog))
                                        Toast.makeText(context, logCopiedToastMsg, Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_copy_log_btn),
                                    fontFamily = NycMonoFamily,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NycCyan
                                )
                            }
                        }
                    }

                    val logText = item.rawLog ?: when (item.status) {
                        EndpointStatus.CHECKING -> stringResource(R.string.settings_endpoint_diag_checking_msg)
                        EndpointStatus.PENDING -> stringResource(R.string.settings_endpoint_diag_pending_msg)
                        else -> stringResource(R.string.settings_endpoint_diag_idle_msg)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(5, 8, 15))
                            .padding(8.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = logText,
                            fontFamily = NycMonoFamily,
                            fontSize = 8.5.sp,
                            color = nycText,
                            lineHeight = 12.5.sp
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// NyC THEME TAB
// ---------------------------------------------------------------------------

@Composable
private fun NycThemeContent() {
    val context = LocalContext.current
    val currentTheme = ThemeManager.currentTheme
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ── 01. ВЫБОР ТЕМЫ ОФОРМЛЕНИЯ ──
        NycSecHdr(stringResource(R.string.theme_section_title))

        NycSetCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                NycThemeOptionRow(
                    title = stringResource(R.string.theme_regular_title),
                    subtitle = stringResource(R.string.theme_regular_desc),
                    isSelected = currentTheme == NierThemeMode.REGULAR,
                    onClick = { applyThemeWithRestart(context, NierThemeMode.REGULAR) }
                )
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(150, 185, 235, alpha = 15)))

                NycThemeOptionRow(
                    title = stringResource(R.string.theme_night_title),
                    subtitle = stringResource(R.string.theme_night_desc),
                    isSelected = currentTheme == NierThemeMode.NIGHT,
                    onClick = { applyThemeWithRestart(context, NierThemeMode.NIGHT) }
                )
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(150, 185, 235, alpha = 15)))

                NycThemeOptionRow(
                    title = stringResource(R.string.theme_black_title),
                    subtitle = stringResource(R.string.theme_black_desc),
                    isSelected = currentTheme == NierThemeMode.BLACK,
                    onClick = { applyThemeWithRestart(context, NierThemeMode.BLACK) }
                )
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(150, 185, 235, alpha = 15)))

                NycThemeOptionRow(
                    title = stringResource(R.string.theme_retro_title),
                    subtitle = stringResource(R.string.theme_retro_desc),
                    isSelected = currentTheme == NierThemeMode.RETRO,
                    onClick = { applyThemeWithRestart(context, NierThemeMode.RETRO) }
                )
            }
        }

        // ── 02. ОСОБЫЕ ТЕМЫ ОФОРМЛЕНИЯ ──
        NycSecHdr(stringResource(R.string.theme_special_title))

        NycSetCard {
            NycThemeOptionRow(
                title = stringResource(R.string.theme_nyc_title),
                subtitle = stringResource(R.string.theme_nyc_desc),
                isSelected = currentTheme == NierThemeMode.NYC_MODERN,
                onClick = { ThemeManager.setTheme(context, NierThemeMode.NYC_MODERN) }
            )
        }

        // ── 03. ИНТЕРФЕЙС ──
        NycSecHdr(stringResource(R.string.theme_ui_section_title))

        NycSetCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                NycToggleRow(
                    title = stringResource(R.string.theme_large_font_title),
                    subtitle = stringResource(R.string.theme_large_font_desc),
                    checked = UiPreferencesManager.largeScheduleFont,
                    onCheckedChange = { UiPreferencesManager.setLargeScheduleFont(context, it) }
                )
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(150, 185, 235, alpha = 15)))

                NycToggleRow(
                    title = stringResource(R.string.theme_animations_title),
                    subtitle = stringResource(R.string.theme_animations_desc),
                    checked = UiPreferencesManager.animationsEnabled,
                    onCheckedChange = { UiPreferencesManager.setAnimationsEnabled(context, it) }
                )
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(150, 185, 235, alpha = 15)))

                NycToggleRow(
                    title = stringResource(R.string.theme_thick_borders_title),
                    subtitle = stringResource(R.string.theme_thick_borders_desc),
                    checked = UiPreferencesManager.thickBorders,
                    onCheckedChange = { UiPreferencesManager.setThickBorders(context, it) }
                )
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(150, 185, 235, alpha = 15)))

                NycToggleRow(
                    title = stringResource(R.string.theme_show_seconds_title),
                    subtitle = stringResource(R.string.theme_show_seconds_desc),
                    checked = UiPreferencesManager.showSecondsInSync,
                    onCheckedChange = { UiPreferencesManager.setShowSecondsInSync(context, it) }
                )
            }
        }

        // ── 04. ПЛОТНОСТЬ СПИСКОВ ──
        NycSecHdr(stringResource(R.string.theme_density_section_title))

        NycSetCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ListDensityMode.entries.forEach { density ->
                    val isSelected = density == UiPreferencesManager.listDensity
                    val densityTitle = when (density) {
                        ListDensityMode.COMPACT -> stringResource(R.string.density_compact)
                        ListDensityMode.STANDARD -> stringResource(R.string.density_standard)
                        ListDensityMode.SPACIOUS -> stringResource(R.string.density_spacious)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .background(NycCyan.copy(alpha = 0.15f))
                                        .border(1.5.dp, NycCyan, RoundedCornerShape(10.dp))
                                } else {
                                    Modifier
                                        .background(Color(150, 185, 235, alpha = 12))
                                        .border(1.dp, Color(150, 185, 235, alpha = 20), RoundedCornerShape(10.dp))
                                }
                            )
                            .clickable { UiPreferencesManager.setListDensity(context, density) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = densityTitle,
                            color = if (isSelected) NycCyan else nycFaint,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = NycMonoFamily,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// NyC VERSION TAB
// ---------------------------------------------------------------------------

@Composable
private fun NycVersionContent(
    uiState: SettingsUiState,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: (UpdateInfo) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ── 01. СВЕДЕНИЯ О ВЕРСИИ СИСТЕМЫ ──
        NycSecHdr(stringResource(R.string.version_section_title))

        NycSetCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .nycWell(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.logo_nyc_modern),
                            contentDescription = "Logo",
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "NyEIOS // EIOS CLIENT",
                            color = nycText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = NycSansFamily,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = stringResource(
                                R.string.version_label_fmt,
                                uiState.currentVersion.ifEmpty { "0.2.7" },
                                17
                            ),
                            color = nycFaint,
                            fontSize = 10.5.sp,
                            fontFamily = NycMonoFamily
                        )

                        val update = uiState.availableUpdate
                        if (update != null) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(nycAmber.copy(alpha = 0.15f))
                                    .border(1.dp, nycAmber.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.version_available_fmt, update.version),
                                    color = nycAmber,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = NycMonoFamily,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(nycGreen.copy(alpha = 0.15f))
                                    .border(1.dp, nycGreen.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.version_latest_badge),
                                    color = nycGreen,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = NycMonoFamily,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(150, 185, 235, alpha = 15)))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val rem = uiState.githubRateLimit.remaining
                        Text(
                            text = stringResource(
                                R.string.version_quota_fmt,
                                if (rem != null) "$rem/60" else "60/60"
                            ),
                            color = nycFaint,
                            fontSize = 9.sp,
                            fontFamily = NycMonoFamily
                        )
                        if (!uiState.updateCheckStatus.isNullOrEmpty()) {
                            Text(
                                text = uiState.updateCheckStatus,
                                color = if (uiState.availableUpdate != null) nycAmber else nycText,
                                fontSize = 10.sp,
                                fontFamily = NycMonoFamily
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NycMiniButton(
                            text = if (uiState.isCheckingUpdate)
                                stringResource(R.string.version_checking_btn)
                            else
                                stringResource(R.string.version_check_btn),
                            icon = Icons.Default.Refresh,
                            enabled = !uiState.isCheckingUpdate,
                            onClick = onCheckForUpdates
                        )
                        if (uiState.availableUpdate != null) {
                            NycMiniButton(
                                text = stringResource(R.string.version_download_btn),
                                icon = Icons.Default.Download,
                                enabled = true,
                                onClick = { onDownloadUpdate(uiState.availableUpdate) }
                            )
                        }
                    }
                }
            }
        }

        // ── 02. ИСТОРИЯ ИЗМЕНЕНИЙ ──
        NycSecHdr(stringResource(R.string.version_changelog_title))

        ChangelogHistory.releases.forEach { release ->
            val isCurrentInstalled = release.version == uiState.currentVersion || (uiState.currentVersion.isEmpty() && release.isLatest)

            NycSetCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "v${release.version}",
                                color = NycCyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = NycSansFamily
                            )
                            if (release.isLatest) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(150, 185, 235, alpha = 20))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "LATEST",
                                        color = nycText,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = NycMonoFamily
                                    )
                                }
                            }
                            if (isCurrentInstalled) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFE2E8F0))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.version_installed_badge),
                                        color = Color(0xFF0F172A),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = NycMonoFamily
                                    )
                                }
                            }
                        }

                        Text(
                            text = release.releaseDate,
                            color = nycFaint,
                            fontSize = 9.sp,
                            fontFamily = NycMonoFamily
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .nycWell(9.dp)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            release.sections.forEach { section ->
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text = "■  ${section.title}",
                                        color = NycCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = NycMonoFamily,
                                        letterSpacing = 0.5.sp
                                    )
                                    section.items.forEach { item ->
                                        Row(
                                            modifier = Modifier.padding(start = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = "—",
                                                color = nycFaint,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = NycMonoFamily
                                            )
                                            Text(
                                                text = item,
                                                color = nycBody,
                                                fontSize = 9.5.sp,
                                                lineHeight = 13.sp,
                                                fontFamily = NycSansFamily
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// NyC LANGUAGE TAB
// ---------------------------------------------------------------------------

@Composable
private fun NycLanguageContent() {
    val context = LocalContext.current
    val currentLang = ru.nya.nyeios.ui.language.LanguageManager.currentLanguage
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        NycSecHdr(
            ru.nya.nyeios.ui.language.typewriterText(
                stringResource(R.string.language_settings_title),
                order = 0.30f
            )
        )

        NycSetCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                NycThemeOptionRow(
                    title = ru.nya.nyeios.ui.language.typewriterText(
                        stringResource(R.string.language_system_title),
                        order = 0.40f
                    ),
                    subtitle = ru.nya.nyeios.ui.language.typewriterText(
                        stringResource(R.string.language_system_desc),
                        order = 0.45f
                    ),
                    isSelected = currentLang == ru.nya.nyeios.ui.language.AppLanguage.SYSTEM,
                    onClick = {
                        ru.nya.nyeios.ui.language.LanguageTypewriterManager.triggerLanguageChange(context, ru.nya.nyeios.ui.language.AppLanguage.SYSTEM)
                    }
                )

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(150, 185, 235, alpha = 15)))

                NycThemeOptionRow(
                    title = ru.nya.nyeios.ui.language.typewriterText(
                        stringResource(R.string.language_ru_title),
                        order = 0.52f
                    ),
                    subtitle = ru.nya.nyeios.ui.language.typewriterText(
                        stringResource(R.string.language_ru_desc),
                        order = 0.57f
                    ),
                    isSelected = currentLang == ru.nya.nyeios.ui.language.AppLanguage.RU,
                    onClick = {
                        ru.nya.nyeios.ui.language.LanguageTypewriterManager.triggerLanguageChange(context, ru.nya.nyeios.ui.language.AppLanguage.RU)
                    }
                )

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(150, 185, 235, alpha = 15)))

                NycThemeOptionRow(
                    title = ru.nya.nyeios.ui.language.typewriterText(
                        stringResource(R.string.language_en_title),
                        order = 0.64f
                    ),
                    subtitle = ru.nya.nyeios.ui.language.typewriterText(
                        stringResource(R.string.language_en_desc),
                        order = 0.69f
                    ),
                    isSelected = currentLang == ru.nya.nyeios.ui.language.AppLanguage.EN,
                    onClick = {
                        ru.nya.nyeios.ui.language.LanguageTypewriterManager.triggerLanguageChange(context, ru.nya.nyeios.ui.language.AppLanguage.EN)
                    }
                )
            }
        }

        NycSecHdr(
            ru.nya.nyeios.ui.language.typewriterText(
                stringResource(R.string.language_server_note_title),
                order = 0.76f
            )
        )

        NycSetCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = ru.nya.nyeios.ui.language.typewriterText(
                        stringResource(R.string.language_server_note_desc),
                        order = 0.84f
                    ),
                    color = nycMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontFamily = NycSansFamily
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// NyC OPTION & TOGGLE ROWS
// ---------------------------------------------------------------------------

@Composable
private fun NycThemeOptionRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Modern rounded checkbox
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .then(
                    if (isSelected) {
                        Modifier
                            .background(NycCyan.copy(alpha = 0.18f))
                            .border(1.5.dp, NycCyan, RoundedCornerShape(6.dp))
                    } else {
                        Modifier
                            .background(Color(150, 185, 235, alpha = 12))
                            .border(1.dp, Color(150, 185, 235, alpha = 24), RoundedCornerShape(6.dp))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = NycCyan,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = NycSansFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                letterSpacing = 0.3.sp,
                color = if (isSelected) NycCyan else nycText
            )
            Text(
                text = subtitle,
                fontFamily = NycMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 8.5.sp,
                lineHeight = 11.sp,
                letterSpacing = 0.2.sp,
                color = nycFaint,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFE2E8F0))
                    .padding(horizontal = 7.dp, vertical = 2.5.dp)
            ) {
                Text(
                    text = "АКТИВНО",
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    letterSpacing = 0.8.sp,
                    color = Color(0xFF0F172A)
                )
            }
        }
    }
}

@Composable
private fun NycToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = NycSansFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.3.sp,
                color = nycText
            )
            Text(
                text = subtitle,
                fontFamily = NycMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 8.5.sp,
                lineHeight = 11.sp,
                letterSpacing = 0.2.sp,
                color = nycFaint,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Modern Pill Switch
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(22.dp)
                .clip(CircleShape)
                .background(if (checked) NycCyan else Color(150, 185, 235, alpha = 20))
                .border(1.dp, if (checked) NycCyan else Color(150, 185, 235, alpha = 30), CircleShape)
                .padding(2.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (checked) Color(0xFF0F172A) else nycFaint)
            )
        }
    }
}

