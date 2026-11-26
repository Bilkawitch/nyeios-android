package ru.nya.nyeios.ui.autoupdate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ru.nya.nyeios.R
import ru.nya.nyeios.data.autoupdate.AutoUpdateConfig
import ru.nya.nyeios.data.autoupdate.AutoUpdateNetwork
import ru.nya.nyeios.data.autoupdate.AutoUpdateSettingsStore
import ru.nya.nyeios.data.reminders.ReminderPermissions
import ru.nya.nyeios.ui.common.NierCheckbox
import ru.nya.nyeios.ui.theme.NierAmber
import ru.nya.nyeios.ui.theme.NierBorder
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
import ru.nya.nyeios.ui.theme.UiPreferencesManager

/**
 * Попап автообновления в стиле YoRHa (0dp, моноширинный, индустриальный терминал).
 *
 * NyC-вариант живёт отдельно (`NycAutoUpdateSheet`), поэтому здесь только прямоугольная
 * геометрия: никаких скруглений, 1dp рамки, Nier-палитра. Шторка намеренно сделана
 * `Dialog`, чтобы M3-`ModalBottomSheet` не притащил скругления верхних углов.
 *
 * Состояние читается/пишется через [AutoUpdateSettingsStore] (single source of truth);
 * при включении уведомлений на Android 13+ запрашивается POST_NOTIFICATIONS, а без
 * разрешения переключатель не включается.
 */
@Composable
fun AutoUpdateSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember { AutoUpdateSettingsStore.getInstance(context) }
    val config by store.config.collectAsState()

    // Показ красной подсказки, если пользователь отказал в разрешении на уведомления.
    var permissionDenied by remember { mutableStateOf(false) }
    // Исключение из оптимизации батареи проверяется на открытии и после возврата
    // из системных настроек (ON_RESUME).
    var batteryExempt by remember { mutableStateOf(ReminderPermissions.isIgnoringBatteryOptimizations(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryExempt = ReminderPermissions.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDenied = false
            store.setNotificationsEnabled(true)
        } else {
            permissionDenied = true
        }
    }

    /** Включает уведомления: на Android 13+ сначала спрашивает POST_NOTIFICATIONS. */
    fun requestNotifications() {
        val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        if (needsRuntimePermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (ReminderPermissions.notificationsEnabled(context)) {
            permissionDenied = false
            store.setNotificationsEnabled(true)
        } else {
            // Разрешение уже выдано, но уведомления выключены на уровне системы.
            permissionDenied = true
        }
    }

    val notificationsHint: Pair<String, Color>? = when {
        !config.enabled -> stringResource(R.string.autoupdate_notifications_need_enable) to NierDim
        permissionDenied -> stringResource(R.string.autoupdate_permission_denied) to NierRed
        else -> null
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = screenHeight * 0.86f)
                .background(NierPanel)
                .border(UiPreferencesManager.borderWidth, NierBorder)
        ) {
            // Заголовочная плашка панели
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NierDark)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.autoupdate_title),
                    fontFamily = RajdhaniFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.4.sp,
                    color = NierSelectionText
                )
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(NierGreen)
                    )
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(NierGreen)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                YorhaToggleRow(
                    title = stringResource(R.string.autoupdate_enable_title),
                    description = stringResource(R.string.autoupdate_enable_desc),
                    checked = config.enabled,
                    enabled = true,
                    onToggle = {
                        permissionDenied = false
                        store.setEnabled(!config.enabled)
                    }
                )

                if (config.enabled) {
                    YorhaSectionLabel(text = stringResource(R.string.autoupdate_interval_title))
                    AutoUpdateConfig.ALLOWED_INTERVALS.chunked(INTERVALS_PER_ROW).forEach { rowHours ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowHours.forEach { hours ->
                                YorhaChip(
                                    label = stringResource(R.string.autoupdate_interval_hours_fmt, hours),
                                    selected = config.intervalHours == hours,
                                    onClick = { store.setIntervalHours(hours) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(INTERVALS_PER_ROW - rowHours.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    YorhaSectionLabel(text = stringResource(R.string.autoupdate_network_title))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NETWORK_OPTIONS.forEach { (network, labelRes) ->
                            YorhaChip(
                                label = stringResource(labelRes),
                                selected = config.network == network,
                                onClick = { store.setNetwork(network) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                YorhaToggleRow(
                    title = stringResource(R.string.autoupdate_notifications_title),
                    description = stringResource(R.string.autoupdate_notifications_desc),
                    checked = config.notificationsEnabled,
                    enabled = config.enabled,
                    hint = notificationsHint?.first,
                    hintColor = notificationsHint?.second ?: NierDim,
                    onToggle = {
                        if (config.notificationsEnabled) {
                            permissionDenied = false
                            store.setNotificationsEnabled(false)
                        } else {
                            requestNotifications()
                        }
                    }
                )

                // Предупреждение про агрессивную оптимизацию батареи: без исключения
                // система может выгрузить приложение и фоновая работа не выполнится.
                if (config.enabled) {
                    if (batteryExempt) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, NierGreen)
                                .background(NierGreen.copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(NierGreen)
                            )
                            Text(
                                text = stringResource(R.string.autoupdate_battery_ok),
                                fontFamily = ShareTechMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                color = NierGreen
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, NierAmber)
                                .background(NierAmber.copy(alpha = 0.08f))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.autoupdate_battery_title),
                                fontFamily = RajdhaniFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.8.sp,
                                color = NierAmber
                            )
                            Text(
                                text = stringResource(R.string.autoupdate_battery_message),
                                fontFamily = ShareTechMonoFamily,
                                fontSize = 9.sp,
                                lineHeight = 13.sp,
                                color = NierDim
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .border(1.dp, NierAmber)
                                    .clickable {
                                        ReminderPermissions.requestBatteryOptimizationExemption(context)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.autoupdate_battery_action),
                                    fontFamily = RajdhaniFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp,
                                    color = NierAmber
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NierBorder)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .background(NierDark)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.autoupdate_close),
                        fontFamily = RajdhaniFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.5.sp,
                        color = NierSelectionText
                    )
                }
            }
        }
    }
}

/** Сколько чипов интервала помещается в один ряд при 44dp минимального тач-таргета. */
private const val INTERVALS_PER_ROW = 3

private val NETWORK_OPTIONS: List<Pair<AutoUpdateNetwork, Int>> = listOf(
    AutoUpdateNetwork.WIFI_ONLY to R.string.autoupdate_network_wifi,
    AutoUpdateNetwork.MOBILE_ONLY to R.string.autoupdate_network_mobile,
    AutoUpdateNetwork.ANY to R.string.autoupdate_network_any
)

@Composable
private fun YorhaSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        fontFamily = ShareTechMonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        letterSpacing = 1.2.sp,
        color = NierDim
    )
}

@Composable
private fun YorhaToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    hint: String? = null,
    hintColor: Color = NierDim,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontFamily = RajdhaniFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (enabled) NierDark else NierDim
            )
            Text(
                text = description,
                fontFamily = ShareTechMonoFamily,
                fontSize = 9.sp,
                lineHeight = 12.sp,
                color = NierDim
            )
            if (hint != null) {
                Text(
                    text = hint,
                    fontFamily = ShareTechMonoFamily,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    color = hintColor
                )
            }
        }
        NierCheckbox(
            checked = checked,
            color = when {
                !enabled -> NierDim
                checked -> NierGreen
                else -> NierDark
            }
        )
    }
}

@Composable
private fun YorhaChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .background(if (selected) NierSelection else NierPanelAlt)
            .border(1.dp, if (selected) NierDark else NierBorder)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = RajdhaniFamily,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 12.sp,
            color = if (selected) NierSelectionText else NierDark
        )
    }
}
