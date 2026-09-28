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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import ru.nya.nyeios.R
import ru.nya.nyeios.ui.language.typewriterText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import ru.nya.nyeios.data.net.EiosLastGetInfo
import ru.nya.nyeios.data.net.PingResult
import ru.nya.nyeios.data.update.GithubRateLimitState
import android.widget.Toast
import ru.nya.nyeios.ui.common.NierCheckbox
import ru.nya.nyeios.ui.common.NierDotRow
import ru.nya.nyeios.ui.theme.ThemeManager
import ru.nya.nyeios.ui.theme.applyThemeWithRestart
import ru.nya.nyeios.ui.theme.appRectShape
import ru.nya.nyeios.ui.theme.isNycModern
import ru.nya.nyeios.ui.theme.NierThemeMode
import ru.nya.nyeios.ui.theme.UiAnimatedVisibility
import ru.nya.nyeios.ui.theme.UiPreferencesManager
import ru.nya.nyeios.ui.theme.ListDensityMode
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
import ru.nya.nyeios.ui.theme.NierSelection
import ru.nya.nyeios.ui.theme.NierSelectionText
import ru.nya.nyeios.ui.theme.NierSurface
import ru.nya.nyeios.ui.theme.RajdhaniFamily
import ru.nya.nyeios.ui.theme.ShareTechMonoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ru.nya.nyeios.data.model.UpdateInfo
import ru.nya.nyeios.data.net.EndpointHealthItem
import ru.nya.nyeios.data.net.EndpointStatus
import ru.nya.nyeios.ui.update.UpdateViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    updateViewModel: UpdateViewModel? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NierBg)
    ) {
        // Subtabs Selector (ОБЩЕЕ / ТЕМА / ВЕРСИЯ)
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
                        onRefreshAll = { viewModel.refreshDiagnostics() },
                        onCheckEndpoints = { viewModel.checkEndpoints() }
                    )
                }
                SettingsSubtab.THEME -> {
                    ThemeSettingsContent()
                }
                SettingsSubtab.VERSION -> {
                    VersionSettingsContent(
                        uiState = uiState,
                        onCheckForUpdates = { viewModel.checkForUpdates() },
                        onDownloadUpdate = { info ->
                            updateViewModel?.downloadApk(info)
                        }
                    )
                }
                SettingsSubtab.LANGUAGE -> {
                    LanguageSettingsContent()
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
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                        shape = appRectShape()
                    )
                    .clickable { onSelectSubtab(subtab) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // NieR Checkbox indicator [✕] / [☐]
                    NierCheckbox(
                        checked = isSelected,
                        color = if (isSelected) NierSelectionText else NierDark,
                        size = 12.dp
                    )

                    val animSubtabTitle = ru.nya.nyeios.ui.language.typewriterText(
                        text = androidx.compose.ui.res.stringResource(subtab.titleResId),
                        order = 0.15f + (subtab.ordinal * 0.02f)
                    )
                    Text(
                        text = animSubtabTitle,
                        color = if (isSelected) NierSelectionText else NierDark,
                        fontSize = 10.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = RajdhaniFamily,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        softWrap = false
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
    onRefreshAll: () -> Unit,
    onCheckEndpoints: () -> Unit
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
        SectionHeader(title = stringResource(R.string.settings_server_conn_title))

        NierCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Host info
                DiagnosticRow(
                    label = stringResource(R.string.settings_server_label),
                    value = "eios.gukolomna.ru",
                    subValue = stringResource(R.string.settings_server_ip_subvalue)
                )

                // Ping Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_ping_label),
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
                                        text = stringResource(R.string.settings_ping_measuring),
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
                                    text = stringResource(R.string.settings_ms_fmt, ping.latencyMs),
                                    color = pingColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = ShareTechMonoFamily
                                )
                            }
                            is PingResult.Error -> {
                                Text(
                                    text = stringResource(R.string.settings_ping_error_fmt, ping.message),
                                    color = NierRed,
                                    fontSize = 12.sp,
                                    fontFamily = ShareTechMonoFamily
                                )
                            }
                            is PingResult.Idle -> {
                                Text(
                                    text = stringResource(R.string.settings_ping_idle),
                                    color = NierDim,
                                    fontSize = 12.sp,
                                    fontFamily = ShareTechMonoFamily
                                )
                            }
                        }
                    }

                    // Button to measure ping
                    NieROutlineButton(
                        text = if (uiState.isMeasuringPing) stringResource(R.string.settings_action_measuring) else stringResource(R.string.settings_action_measure),
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NierBorderLight)
                )

                // Endpoints Diagnostics Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_endpoints_diag_title),
                            color = NierDim,
                            fontSize = 10.sp,
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = stringResource(R.string.settings_endpoints_diag_desc),
                            color = NierDarkSecondary,
                            fontSize = 11.sp,
                            fontFamily = RajdhaniFamily
                        )
                    }

                    NieROutlineButton(
                        text = if (uiState.isCheckingEndpoints) stringResource(R.string.settings_action_checking) else stringResource(R.string.settings_action_check),
                        enabled = !uiState.isCheckingEndpoints,
                        color = NierBlue,
                        onClick = onCheckEndpoints
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    uiState.endpointsHealth.forEach { ep ->
                        EndpointHealthRow(item = ep)
                    }
                }
            }
        }

        // ── 02. РЕЙТ-ЛИМИТ GITHUB API ─────────────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_github_limit_title))

        NierCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val remaining = uiState.githubRateLimit.remaining
                val resetMs = uiState.githubRateLimit.resetTimeMs

                DiagnosticRow(
                    label = stringResource(R.string.settings_quota_remaining_label),
                    value = if (remaining != null) stringResource(R.string.settings_quota_requests_fmt, remaining) else stringResource(R.string.settings_quota_not_checked),
                    subValue = if (resetMs != null && resetMs > System.currentTimeMillis()) {
                        val minsLeft = maxOf(1, ((resetMs - System.currentTimeMillis()) / 60000).toInt())
                        stringResource(R.string.settings_quota_reset_fmt, minsLeft)
                    } else {
                        stringResource(R.string.settings_quota_sub_default)
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
                    VpnRateLimitWarningBanner(isVpnActive = uiState.isVpnActive)
                }
            }
        }

        // ── 03. IP УСТРОЙСТВА И СЕТЕВОЙ ИНТЕРФЕЙС ─────────────────────────
        SectionHeader(title = stringResource(R.string.settings_ip_section_title))

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
                            text = stringResource(R.string.settings_wan_ip_label),
                            color = NierDim,
                            fontSize = 10.sp,
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )

                        Text(
                            text = uiState.externalIp ?: if (uiState.isFetchingIp) stringResource(R.string.settings_detecting) else stringResource(R.string.settings_not_detected),
                            color = NierDark,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ShareTechMonoFamily
                        )

                        Text(
                            text = stringResource(R.string.settings_wan_ip_desc),
                            color = NierDim,
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        )
                    }

                    NieROutlineButton(
                        text = if (uiState.isFetchingIp) "..." else stringResource(R.string.action_refresh).uppercase(),
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
                    label = stringResource(R.string.settings_lan_ip_label),
                    value = uiState.localIp ?: stringResource(R.string.settings_not_detected),
                    subValue = stringResource(R.string.settings_lan_ip_desc)
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
                            text = stringResource(R.string.settings_network_type_label),
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
                            text = stringResource(R.string.settings_vpn_status_label),
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
                                text = if (uiState.isVpnActive) stringResource(R.string.settings_vpn_active) else stringResource(R.string.settings_vpn_disabled),
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
                .border(1.dp, NierBorderLight, appRectShape())
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
                    text = stringResource(R.string.settings_action_repeat_diag),
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
internal fun ThemeSettingsContent() {
    val context = LocalContext.current
    val currentTheme = ThemeManager.currentTheme
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(title = stringResource(R.string.theme_section_title))

        NierCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Theme 1: YoRHa Regular
                ThemeItemRow(
                    title = stringResource(R.string.theme_regular_title),
                    subtitle = stringResource(R.string.theme_regular_desc),
                    isSelected = currentTheme == NierThemeMode.REGULAR,
                    onClick = { applyThemeWithRestart(context, NierThemeMode.REGULAR) }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NierBorderLight)
                )

                // Theme 2: YoRHa Night
                ThemeItemRow(
                    title = stringResource(R.string.theme_night_title),
                    subtitle = stringResource(R.string.theme_night_desc),
                    isSelected = currentTheme == NierThemeMode.NIGHT,
                    onClick = { applyThemeWithRestart(context, NierThemeMode.NIGHT) }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NierBorderLight)
                )

                // Theme 3: YoRHa Black (AMOLED)
                ThemeItemRow(
                    title = stringResource(R.string.theme_black_title),
                    subtitle = stringResource(R.string.theme_black_desc),
                    isSelected = currentTheme == NierThemeMode.BLACK,
                    onClick = { applyThemeWithRestart(context, NierThemeMode.BLACK) }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NierBorderLight)
                )

                // Theme 4: YoRHa Retro
                ThemeItemRow(
                    title = stringResource(R.string.theme_retro_title),
                    subtitle = stringResource(R.string.theme_retro_desc),
                    isSelected = currentTheme == NierThemeMode.RETRO,
                    onClick = { applyThemeWithRestart(context, NierThemeMode.RETRO) }
                )
            }
        }

        // ── 02. ОСОБЫЕ ТЕМЫ ОФОРМЛЕНИЯ ─────────────────────────────────────
        SectionHeader(title = stringResource(R.string.theme_special_title))

        NierCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ThemeItemRow(
                    title = stringResource(R.string.theme_nyc_title),
                    subtitle = stringResource(R.string.theme_nyc_desc),
                    isSelected = currentTheme == NierThemeMode.NYC_MODERN,
                    onClick = {
                        ThemeManager.setTheme(context, NierThemeMode.NYC_MODERN)
                    }
                )
            }
        }

        // ── 03. ИНТЕРФЕЙС ─────────────────────────────────────────────────
        SectionHeader(title = stringResource(R.string.theme_ui_section_title))

        NierCard {
            Column {
                NierToggleRow(
                    title = stringResource(R.string.theme_large_font_title),
                    subtitle = stringResource(R.string.theme_large_font_desc),
                    checked = UiPreferencesManager.largeScheduleFont,
                    onCheckedChange = { UiPreferencesManager.setLargeScheduleFont(context, it) }
                )

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NierBorderLight))

                NierToggleRow(
                    title = stringResource(R.string.theme_animations_title),
                    subtitle = stringResource(R.string.theme_animations_desc),
                    checked = UiPreferencesManager.animationsEnabled,
                    onCheckedChange = { UiPreferencesManager.setAnimationsEnabled(context, it) }
                )

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NierBorderLight))

                NierToggleRow(
                    title = stringResource(R.string.theme_thick_borders_title),
                    subtitle = stringResource(R.string.theme_thick_borders_desc),
                    checked = UiPreferencesManager.thickBorders,
                    onCheckedChange = { UiPreferencesManager.setThickBorders(context, it) }
                )

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NierBorderLight))

                NierToggleRow(
                    title = stringResource(R.string.theme_show_seconds_title),
                    subtitle = stringResource(R.string.theme_show_seconds_desc),
                    checked = UiPreferencesManager.showSecondsInSync,
                    onCheckedChange = { UiPreferencesManager.setShowSecondsInSync(context, it) }
                )
            }
        }

        // ── 04. ПЛОТНОСТЬ СПИСКОВ ─────────────────────────────────────────
        SectionHeader(title = stringResource(R.string.theme_density_section_title))

        NierCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                            .background(if (isSelected) NierBlue else NierPanelAlt)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) NierBlue else NierBorderLight,
                                shape = appRectShape()
                            )
                            .clickable { UiPreferencesManager.setListDensity(context, density) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = densityTitle,
                            color = if (isSelected) Color.White else NierDark,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = RajdhaniFamily,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun LanguageSettingsContent() {
    val context = LocalContext.current
    val currentLang = ru.nya.nyeios.ui.language.LanguageManager.currentLanguage
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = ru.nya.nyeios.ui.language.typewriterText(
                androidx.compose.ui.res.stringResource(ru.nya.nyeios.R.string.language_settings_title),
                order = 0.30f
            )
        )

        NierCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ThemeItemRow(
                    title = ru.nya.nyeios.ui.language.typewriterText(
                        androidx.compose.ui.res.stringResource(ru.nya.nyeios.R.string.language_system_title),
                        order = 0.40f
                    ),
                    subtitle = ru.nya.nyeios.ui.language.typewriterText(
                        androidx.compose.ui.res.stringResource(ru.nya.nyeios.R.string.language_system_desc),
                        order = 0.45f
                    ),
                    isSelected = currentLang == ru.nya.nyeios.ui.language.AppLanguage.SYSTEM,
                    onClick = {
                        ru.nya.nyeios.ui.language.LanguageTypewriterManager.triggerLanguageChange(context, ru.nya.nyeios.ui.language.AppLanguage.SYSTEM)
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NierBorderLight)
                )

                ThemeItemRow(
                    title = ru.nya.nyeios.ui.language.typewriterText(
                        androidx.compose.ui.res.stringResource(ru.nya.nyeios.R.string.language_ru_title),
                        order = 0.52f
                    ),
                    subtitle = ru.nya.nyeios.ui.language.typewriterText(
                        androidx.compose.ui.res.stringResource(ru.nya.nyeios.R.string.language_ru_desc),
                        order = 0.57f
                    ),
                    isSelected = currentLang == ru.nya.nyeios.ui.language.AppLanguage.RU,
                    onClick = {
                        ru.nya.nyeios.ui.language.LanguageTypewriterManager.triggerLanguageChange(context, ru.nya.nyeios.ui.language.AppLanguage.RU)
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NierBorderLight)
                )

                ThemeItemRow(
                    title = ru.nya.nyeios.ui.language.typewriterText(
                        androidx.compose.ui.res.stringResource(ru.nya.nyeios.R.string.language_en_title),
                        order = 0.64f
                    ),
                    subtitle = ru.nya.nyeios.ui.language.typewriterText(
                        androidx.compose.ui.res.stringResource(ru.nya.nyeios.R.string.language_en_desc),
                        order = 0.69f
                    ),
                    isSelected = currentLang == ru.nya.nyeios.ui.language.AppLanguage.EN,
                    onClick = {
                        ru.nya.nyeios.ui.language.LanguageTypewriterManager.triggerLanguageChange(context, ru.nya.nyeios.ui.language.AppLanguage.EN)
                    }
                )
            }
        }

        SectionHeader(
            title = ru.nya.nyeios.ui.language.typewriterText(
                androidx.compose.ui.res.stringResource(ru.nya.nyeios.R.string.language_server_note_title),
                order = 0.76f
            )
        )

        NierCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = ru.nya.nyeios.ui.language.typewriterText(
                        androidx.compose.ui.res.stringResource(ru.nya.nyeios.R.string.language_server_note_desc),
                        order = 0.84f
                    ),
                    color = NierDim,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontFamily = ShareTechMonoFamily
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun NierToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val trackShape = if (isNycModern) RoundedCornerShape(percent = 50) else RoundedCornerShape(0.dp)
    val thumbShape = if (isNycModern) CircleShape else RoundedCornerShape(0.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = NierDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = RajdhaniFamily
            )
            Text(
                text = subtitle,
                color = NierDim,
                fontSize = 9.5.sp,
                fontFamily = ShareTechMonoFamily,
                letterSpacing = 0.5.sp
            )
        }

        Box(
            modifier = Modifier
                .size(width = 42.dp, height = 22.dp)
                .background(
                    color = if (checked) NierBlue.copy(alpha = 0.15f) else Color.Transparent,
                    shape = trackShape
                )
                .border(
                    width = 1.dp,
                    color = if (checked) NierBlue else NierBorderLight,
                    shape = trackShape
                )
                .padding(if (isNycModern) 3.dp else 2.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        color = if (checked) NierBlue else NierDim,
                        shape = thumbShape
                    )
            )
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
            color = if (isSelected) NierDark else if (isNycModern) NierDim else NierBorderLight,
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
                            text = stringResource(R.string.badge_active),
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
private fun VpnRateLimitWarningBanner(isVpnActive: Boolean) {
    val warningText = if (isVpnActive) {
        stringResource(R.string.settings_vpn_warning_vpn)
    } else {
        stringResource(R.string.settings_vpn_warning_no_vpn)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(NierAmber.copy(alpha = 0.12f))
            .border(
                width = 1.5.dp,
                color = NierAmber,
                shape = appRectShape()
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
                    text = stringResource(R.string.settings_vpn_warning_title),
                    color = NierDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = RajdhaniFamily,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = warningText,
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
            text = stringResource(R.string.settings_last_get_time),
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
                    text = stringResource(R.string.settings_ms_fmt, getInfo.durationMs),
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
                    text = "${stringResource(R.string.settings_request_label)} ${getInfo.path}",
                    color = NierDim,
                    fontSize = 10.sp,
                    fontFamily = ShareTechMonoFamily,
                    maxLines = 1
                )
            }
        } else {
            Text(
                text = stringResource(R.string.settings_last_get_none),
                color = NierDim,
                fontSize = 12.sp,
                fontFamily = ShareTechMonoFamily
            )
        }
    }
}

@Composable
private fun EndpointHealthRow(item: EndpointHealthItem) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val (statusColor, statusBadge) = when (item.status) {
        EndpointStatus.IDLE -> Pair(NierDim, stringResource(R.string.settings_endpoint_not_checked))
        EndpointStatus.PENDING -> Pair(NierDim, stringResource(R.string.settings_endpoint_pending))
        EndpointStatus.CHECKING -> Pair(NierBlue, stringResource(R.string.settings_endpoint_checking))
        EndpointStatus.OK -> Pair(NierGreen, stringResource(R.string.settings_endpoint_available))
        EndpointStatus.DEGRADED -> Pair(NierAmber, stringResource(R.string.settings_endpoint_degraded))
        EndpointStatus.AUTH_REQUIRED -> Pair(NierAmber, stringResource(R.string.settings_endpoint_auth_required))
        EndpointStatus.ERROR -> Pair(NierRed, stringResource(R.string.settings_endpoint_error))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NierPanelAlt)
            .border(0.5.dp, if (isExpanded) NierBorder else NierBorderLight)
            .clickable { isExpanded = !isExpanded }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
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
                    fontFamily = RajdhaniFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = NierDark
                )
                Text(
                    text = item.path,
                    fontFamily = ShareTechMonoFamily,
                    fontSize = 10.sp,
                    color = NierDim
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.15f))
                        .border(0.5.dp, statusColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusBadge,
                        fontSize = 9.sp,
                        fontFamily = RajdhaniFamily,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        letterSpacing = 0.5.sp,
                        maxLines = 1
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = NierDim,
                    modifier = Modifier.size(16.dp)
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
                    fontFamily = ShareTechMonoFamily,
                    fontSize = 10.sp,
                    color = when (item.status) {
                        EndpointStatus.ERROR -> NierRed
                        EndpointStatus.DEGRADED -> NierAmber
                        EndpointStatus.CHECKING -> NierBlue
                        else -> NierDarkSecondary
                    },
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.latencyMs != null) {
                    Text(
                        text = stringResource(R.string.settings_ms_fmt, item.latencyMs),
                        fontFamily = ShareTechMonoFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NierDim
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
                    .background(NierBg)
                    .border(0.5.dp, NierBorderLight)
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
                        fontFamily = RajdhaniFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = NierDim,
                        letterSpacing = 0.5.sp
                    )

                    if (!item.rawLog.isNullOrEmpty()) {
                        NieROutlineButton(
                            text = stringResource(R.string.settings_copy_log_btn),
                            color = NierBlue,
                            onClick = {
                                clipboardManager.setText(AnnotatedString(item.rawLog))
                                Toast.makeText(context, context.getString(R.string.settings_log_copied_toast), Toast.LENGTH_SHORT).show()
                            }
                        )
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
                        .background(NierDark)
                        .padding(8.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = logText,
                        fontFamily = ShareTechMonoFamily,
                        fontSize = 9.5.sp,
                        color = NierBg,
                        lineHeight = 13.5.sp
                    )
                }
            }
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
    // NyC-modern: mockup sechdr is a dark inset strip with muted mono text.
    // YoRHa themes keep the solid dark bar with canvas text.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isNycModern) NierSurface else NierDark)
            .border(
                width = 1.dp,
                color = if (isNycModern) NierBorderLight else NierDark,
                shape = appRectShape()
            )
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = title,
            color = if (isNycModern) NierDarkSecondary else NierBg,
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
                shape = appRectShape()
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
                shape = appRectShape()
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

@Composable
internal fun VersionSettingsContent(
    uiState: SettingsUiState,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: (UpdateInfo) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 01. СВЕДЕНИЯ О ВЕРСИИ СИСТЕМЫ ─────────────────────────────────
        SectionHeader(title = typewriterText(stringResource(R.string.version_section_title), 0.23f))

        NierCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(NierDark)
                            .border(1.dp, NierBorderLight, appRectShape()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Ny",
                            color = NierBg,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = RajdhaniFamily
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "NyEIOS // EIOS CLIENT",
                            color = NierDark,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = RajdhaniFamily,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = typewriterText(
                                stringResource(
                                    R.string.version_label_fmt,
                                    uiState.currentVersion.ifEmpty { "0.2.3" },
                                    17
                                ),
                                0.25f
                            ),
                            color = NierDim,
                            fontSize = 12.sp,
                            fontFamily = ShareTechMonoFamily
                        )

                        val update = uiState.availableUpdate
                        if (update != null) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .background(NierAmber.copy(alpha = 0.15f))
                                    .border(1.dp, NierAmber, appRectShape())
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = typewriterText(
                                        stringResource(R.string.version_available_fmt, update.version),
                                        0.27f
                                    ),
                                    color = NierAmber,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = RajdhaniFamily,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .background(NierGreen.copy(alpha = 0.15f))
                                    .border(1.dp, NierGreen, appRectShape())
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = typewriterText(
                                        stringResource(R.string.version_latest_badge),
                                        0.27f
                                    ),
                                    color = NierGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = RajdhaniFamily,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NierBorderLight)
                )

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
                            text = typewriterText(
                                stringResource(
                                    R.string.version_quota_fmt,
                                    if (rem != null) "$rem/60" else "60/60"
                                ),
                                0.29f
                            ),
                            color = NierDim,
                            fontSize = 10.sp,
                            fontFamily = ShareTechMonoFamily
                        )
                        if (!uiState.updateCheckStatus.isNullOrEmpty()) {
                            Text(
                                text = uiState.updateCheckStatus,
                                color = if (uiState.availableUpdate != null) NierAmber else NierDark,
                                fontSize = 11.sp,
                                fontFamily = ShareTechMonoFamily
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NieROutlineButton(
                            text = if (uiState.isCheckingUpdate)
                                stringResource(R.string.version_checking_btn)
                            else
                                stringResource(R.string.version_check_btn),
                            enabled = !uiState.isCheckingUpdate,
                            color = NierBlue,
                            onClick = onCheckForUpdates
                        )
                        if (uiState.availableUpdate != null) {
                            NieROutlineButton(
                                text = stringResource(R.string.version_download_btn),
                                color = NierGreen,
                                onClick = { onDownloadUpdate(uiState.availableUpdate) }
                            )
                        }
                    }
                }
            }
        }

        // ── 02. ИСТОРИЯ ИЗМЕНЕНИЙ // CHANGELOG ────────────────────────────
        SectionHeader(title = typewriterText(stringResource(R.string.version_changelog_title), 0.31f))

        ChangelogHistory.releases.forEach { release ->
            val isCurrentInstalled = release.version == uiState.currentVersion || (uiState.currentVersion.isEmpty() && release.isLatest)

            NierCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Header row: version, tags, date
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
                                color = NierDark,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = RajdhaniFamily
                            )

                            if (release.isLatest) {
                                Box(
                                    modifier = Modifier
                                        .background(NierDark)
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "LATEST",
                                        color = NierBg,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = RajdhaniFamily,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            if (isCurrentInstalled) {
                                Box(
                                    modifier = Modifier
                                        .background(NierSelection)
                                        .border(1.dp, NierDark, appRectShape())
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = typewriterText(
                                            stringResource(R.string.version_installed_badge),
                                            0.33f
                                        ),
                                        color = NierSelectionText,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = RajdhaniFamily,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }

                        Text(
                            text = release.releaseDate,
                            color = NierDim,
                            fontSize = 11.sp,
                            fontFamily = ShareTechMonoFamily
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(NierBorderLight)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        release.sections.forEach { section ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "■  ${section.title}",
                                    color = NierDark,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = RajdhaniFamily,
                                    letterSpacing = 0.6.sp
                                )

                                section.items.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 6.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "—",
                                            color = NierDim,
                                            fontSize = 11.sp,
                                            fontFamily = ShareTechMonoFamily
                                        )
                                        Text(
                                            text = item,
                                            color = NierDark,
                                            fontSize = 11.sp,
                                            fontFamily = ShareTechMonoFamily,
                                            lineHeight = 15.sp,
                                            modifier = Modifier.weight(1f)
                                        )
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

