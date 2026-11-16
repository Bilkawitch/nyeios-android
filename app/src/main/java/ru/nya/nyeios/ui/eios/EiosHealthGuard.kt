package ru.nya.nyeios.ui.eios

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ru.nya.nyeios.R
import ru.nya.nyeios.data.net.EiosMonitorClient
import ru.nya.nyeios.data.net.EiosMonitorSummary
import ru.nya.nyeios.ui.theme.NierAmber
import ru.nya.nyeios.ui.theme.NierDim
import ru.nya.nyeios.ui.theme.NierGreen
import ru.nya.nyeios.ui.theme.NierRed
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.NycMonoFamily
import ru.nya.nyeios.ui.theme.NycSansFamily
import ru.nya.nyeios.ui.theme.NycShapes
import ru.nya.nyeios.ui.theme.ObsidianBg
import ru.nya.nyeios.ui.theme.ObsidianBorder
import ru.nya.nyeios.ui.theme.ObsidianSurface
import ru.nya.nyeios.ui.theme.RajdhaniFamily
import ru.nya.nyeios.ui.theme.ShareTechMonoFamily
import ru.nya.nyeios.ui.theme.TextMuted
import ru.nya.nyeios.ui.theme.TextPrimary
import ru.nya.nyeios.ui.theme.TextSecondary
import ru.nya.nyeios.ui.theme.appRectShape
import ru.nya.nyeios.ui.theme.nycWell

/**
 * EIOS availability guard in front of every user-triggered refresh.
 *
 * Before a refresh actually starts the guard asks the LAN monitor for its verdict. If the
 * monitor has no usable answer (unreachable, 429, malformed body) the refresh proceeds
 * silently — the gate must never block work or show an error; it is a fail-open heuristic.
 * Only an explicit "not healthy" verdict opens the themed warning popup with the pending
 * refresh action preserved, so the user decides whether to try anyway.
 */
data class EiosHealthUiState(
    /** Non-null while the warning popup is visible; the answer of the monitor. */
    val warning: EiosMonitorSummary? = null,
    /** True while the monitor probe is in flight, so repeated taps are ignored. */
    val isProbing: Boolean = false,
)

class EiosHealthViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(EiosHealthUiState())
    val uiState: StateFlow<EiosHealthUiState> = _uiState.asStateFlow()

    /** Refresh action waiting for the user's decision in the popup. */
    private var pendingAction: (() -> Unit)? = null

    /**
     * Probes the monitor and either runs [action] right away (healthy site or no monitor
     * answer — fail-open) or arms the warning popup and keeps [action] pending.
     */
    fun requestRefresh(action: () -> Unit) {
        val state = _uiState.value
        if (state.isProbing || state.warning != null) return
        _uiState.value = state.copy(isProbing = true)
        viewModelScope.launch {
            val fetch = EiosMonitorClient.getInstance(getApplication()).fetch()
            val summary = fetch?.summary
            if (summary != null && !summary.isHealthy) {
                pendingAction = action
                _uiState.value = EiosHealthUiState(warning = summary)
            } else {
                _uiState.value = EiosHealthUiState()
                action()
            }
        }
    }

    /** User chose "try to refresh anyway": run the pending refresh and close the popup. */
    fun proceed() {
        val action = pendingAction
        pendingAction = null
        _uiState.value = EiosHealthUiState()
        action?.invoke()
    }

    /** User aborted the refresh: drop the pending action and close the popup. */
    fun cancel() {
        pendingAction = null
        _uiState.value = EiosHealthUiState()
    }
}

/** Warning popup in the two app families: YoRHa terminal (0dp) and NyC modern (16dp card). */
@Composable
fun EiosHealthWarningDialog(
    isNyc: Boolean,
    summary: EiosMonitorSummary,
    onCancel: () -> Unit,
    onProceed: () -> Unit,
) {
    if (isNyc) {
        NycEiosHealthWarningDialog(summary = summary, onCancel = onCancel, onProceed = onProceed)
    } else {
        YorhaEiosHealthWarningDialog(summary = summary, onCancel = onCancel, onProceed = onProceed)
    }
}

// ==========================================
// Shared digest of the monitor payload
// ==========================================

private data class EiosHealthMetric(val labelRes: Int, val value: String)

/** Ages above this are shown as "at HH:MM" instead of a huge number of seconds. */
internal const val EIOS_HEALTH_OLD_DATA_AGE_SEC = 15 * 60L

/**
 * Age of the monitor snapshot. Long-cached data (the public GitHub fallback may be minutes old)
 * is displayed as publication time per the public feed documentation, not as a raw second count.
 */
@Composable
internal fun eiosHealthAgeValue(summary: EiosMonitorSummary): String {
    val oldEnough = summary.ageSec >= EIOS_HEALTH_OLD_DATA_AGE_SEC && summary.ts > 0L
    return if (oldEnough) {
        val time = remember(summary.ts) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(summary.ts))
        }
        stringResource(R.string.eios_health_value_age_at_fmt, time)
    } else {
        stringResource(R.string.eios_health_value_age_fmt, summary.ageSec)
    }
}

@Composable
private fun eiosHealthMetrics(summary: EiosMonitorSummary): List<EiosHealthMetric> {
    val dash = stringResource(R.string.eios_health_value_dash)
    val loading = summary.loading
    return listOf(
        EiosHealthMetric(
            labelRes = R.string.eios_health_field_ttfb,
            value = stringResource(R.string.eios_health_value_ttfb_fmt, loading.ttfbMs),
        ),
        EiosHealthMetric(
            labelRes = R.string.eios_health_field_workers,
            value = loading.freeWorkers?.let {
                stringResource(R.string.eios_health_value_workers_fmt, it)
            } ?: dash,
        ),
        EiosHealthMetric(
            labelRes = R.string.eios_health_field_queue,
            value = loading.queueRatio?.let {
                stringResource(R.string.eios_health_value_queue_fmt, it)
            } ?: dash,
        ),
        EiosHealthMetric(
            labelRes = R.string.eios_health_field_age,
            value = eiosHealthAgeValue(summary),
        ),
    )
}

/** Badge copy comes from the monitor itself (`state`, falling back to the coarse `status`). */
private fun eiosHealthBadgeText(summary: EiosMonitorSummary): String =
    summary.state.ifBlank { summary.status }.uppercase()

/** Semantic status color: up = green, degraded = amber, down = red, unknown = faint. */
private fun eiosHealthStatusColor(summary: EiosMonitorSummary, isNyc: Boolean): Color =
    when (summary.status) {
        EiosMonitorSummary.STATUS_UP -> if (isNyc) nycHealthGreen else NierGreen
        EiosMonitorSummary.STATUS_DEGRADED -> if (isNyc) nycHealthAmber else NierAmber
        EiosMonitorSummary.STATUS_DOWN -> if (isNyc) nycHealthRed else NierRed
        else -> if (isNyc) nycHealthFaint else NierDim
    }

// ==========================================
// YoRHa family: 0dp corners, 1dp borders, Rajdhani + Share Tech Mono
// ==========================================

@Composable
private fun YorhaEiosHealthWarningDialog(
    summary: EiosMonitorSummary,
    onCancel: () -> Unit,
    onProceed: () -> Unit,
) {
    val statusColor = eiosHealthStatusColor(summary, isNyc = false)
    val metrics = eiosHealthMetrics(summary)
    val labels = RajdhaniFamily
    val mono = ShareTechMonoFamily

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = ObsidianSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = appRectShape(),
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.eios_health_dialog_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = 0.5.sp,
                fontFamily = labels,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Verdict: status badge + server label + short reason.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder)
                        .background(ObsidianBg)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = eiosHealthBadgeText(summary),
                            color = statusColor,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp,
                            fontFamily = mono,
                            modifier = Modifier
                                .border(1.dp, statusColor)
                                .background(statusColor.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        if (summary.label.isNotBlank()) {
                            Text(
                                text = summary.label,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = labels,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (summary.reason.isNotBlank()) {
                        Text(
                            text = summary.reason,
                            color = TextSecondary,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            fontFamily = mono
                        )
                    }
                }

                // Metrics grid: 2 x 2 bordered cells.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    metrics.chunked(2).forEach { rowMetrics ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowMetrics.forEach { metric ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(1.dp, ObsidianBorder)
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = stringResource(metric.labelRes),
                                        color = TextMuted,
                                        fontSize = 8.sp,
                                        lineHeight = 11.sp,
                                        letterSpacing = 0.5.sp,
                                        fontFamily = mono
                                    )
                                    Text(
                                        text = metric.value,
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = mono
                                    )
                                }
                            }
                            if (rowMetrics.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                // Data quality flags, shown only when the monitor reports them.
                if (summary.stale || !summary.confirmed) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (summary.stale) {
                            YorhaHealthFlag(
                                text = stringResource(R.string.eios_health_badge_stale),
                                color = NierAmber
                            )
                        }
                        if (!summary.confirmed) {
                            YorhaHealthFlag(
                                text = stringResource(R.string.eios_health_badge_unconfirmed),
                                color = NierRed
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ObsidianBorder)
                )

                Text(
                    text = stringResource(R.string.eios_health_dialog_message),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontFamily = labels
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onProceed,
                shape = appRectShape(),
                modifier = Modifier.heightIn(min = 44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TextPrimary,
                    contentColor = ObsidianSurface
                )
            ) {
                Text(
                    text = stringResource(R.string.eios_health_action_proceed),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFamily = labels
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                shape = appRectShape(),
                modifier = Modifier.heightIn(min = 44.dp),
                border = BorderStroke(1.dp, ObsidianBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    fontSize = 13.sp,
                    fontFamily = labels
                )
            }
        }
    )
}

@Composable
private fun YorhaHealthFlag(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 8.sp,
        letterSpacing = 1.sp,
        fontFamily = ShareTechMonoFamily,
        modifier = Modifier
            .border(1.dp, color)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

// ==========================================
// NyC family: 16dp card, 11dp buttons, NyC cyan + status accents
// ==========================================

private val nycHealthCard = Color(0xFF182334)
private val nycHealthText = Color(0xFFDBE4F0)
private val nycHealthBody = Color(0xFFB9C4D4)
private val nycHealthMuted = Color(0xFF8794A7)
private val nycHealthFaint = Color(0xFF5B6A7E)
private val nycHealthGreen = Color(0xFF46E08C)
private val nycHealthAmber = Color(0xFFF0B44C)
private val nycHealthRed = Color(0xFFF26D6D)
private val nycHealthEdge = Color(150, 185, 235, alpha = 19)
private val nycHealthOnCyan = Color(0xFF06141A)

@Composable
private fun NycEiosHealthWarningDialog(
    summary: EiosMonitorSummary,
    onCancel: () -> Unit,
    onProceed: () -> Unit,
) {
    val statusColor = eiosHealthStatusColor(summary, isNyc = true)
    val metrics = eiosHealthMetrics(summary)

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = nycHealthCard,
        titleContentColor = nycHealthText,
        textContentColor = nycHealthMuted,
        shape = RoundedCornerShape(16.dp),
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.eios_health_dialog_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                fontFamily = NycSansFamily,
                color = nycHealthText
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Verdict: status badge + server label + short reason.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .nycWell(corner = 12.dp)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = eiosHealthBadgeText(summary),
                            color = statusColor,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp,
                            fontFamily = NycMonoFamily,
                            modifier = Modifier
                                .background(
                                    color = statusColor.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = statusColor.copy(alpha = 0.34f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                        if (summary.label.isNotBlank()) {
                            Text(
                                text = summary.label,
                                color = nycHealthText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = NycSansFamily,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (summary.reason.isNotBlank()) {
                        Text(
                            text = summary.reason,
                            color = nycHealthMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontFamily = NycSansFamily
                        )
                    }
                }

                // Metrics grid: 2 x 2 recessed wells.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    metrics.chunked(2).forEach { rowMetrics ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowMetrics.forEach { metric ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .nycWell(corner = 9.dp)
                                        .padding(horizontal = 9.dp, vertical = 7.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = stringResource(metric.labelRes),
                                        color = nycHealthFaint,
                                        fontSize = 8.sp,
                                        lineHeight = 11.sp,
                                        letterSpacing = 0.5.sp,
                                        fontFamily = NycMonoFamily
                                    )
                                    Text(
                                        text = metric.value,
                                        color = nycHealthText,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = NycMonoFamily
                                    )
                                }
                            }
                            if (rowMetrics.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                // Data quality flags, shown only when the monitor reports them.
                if (summary.stale || !summary.confirmed) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (summary.stale) {
                            NycHealthFlag(
                                text = stringResource(R.string.eios_health_badge_stale),
                                color = nycHealthAmber
                            )
                        }
                        if (!summary.confirmed) {
                            NycHealthFlag(
                                text = stringResource(R.string.eios_health_badge_unconfirmed),
                                color = nycHealthRed
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(nycHealthEdge)
                )

                Text(
                    text = stringResource(R.string.eios_health_dialog_message),
                    color = nycHealthBody,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontFamily = NycSansFamily
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onProceed,
                shape = RoundedCornerShape(NycShapes.button),
                modifier = Modifier.heightIn(min = 44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NycCyan,
                    contentColor = nycHealthOnCyan
                )
            ) {
                Text(
                    text = stringResource(R.string.eios_health_action_proceed),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFamily = NycSansFamily
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(NycShapes.button),
                modifier = Modifier.heightIn(min = 44.dp),
                border = BorderStroke(1.dp, nycHealthEdge),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = nycHealthMuted)
            ) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    fontSize = 13.sp,
                    fontFamily = NycSansFamily
                )
            }
        }
    )
}

@Composable
private fun NycHealthFlag(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 8.sp,
        letterSpacing = 1.sp,
        fontFamily = NycMonoFamily,
        modifier = Modifier
            .background(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp))
            .border(width = 1.dp, color = color.copy(alpha = 0.34f), shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}
