package ru.nya.nyeios.ui.autoupdate

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ru.nya.nyeios.R
import ru.nya.nyeios.data.autoupdate.AutoUpdateConfig
import ru.nya.nyeios.data.autoupdate.AutoUpdateNetwork
import ru.nya.nyeios.data.autoupdate.AutoUpdateNotifications
import ru.nya.nyeios.data.autoupdate.AutoUpdateSettingsStore
import ru.nya.nyeios.data.reminders.ReminderPermissions
import ru.nya.nyeios.ui.theme.NierAmber
import ru.nya.nyeios.ui.theme.NierRed
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.NycLed
import ru.nya.nyeios.ui.theme.NycMonoFamily
import ru.nya.nyeios.ui.theme.NycSansFamily
import ru.nya.nyeios.ui.theme.nycCard
import ru.nya.nyeios.ui.theme.nycRaised
import ru.nya.nyeios.ui.theme.nycWell

// NyC-modern auto-update popup (long-press on the top-bar refresh button).
// Пара к YoRHa-варианту из ui/autoupdate/AutoUpdateSheet.kt: одинаковая логика
// (AutoUpdateSettingsStore + ReminderPermissions), но строго NyC-рендеринг —
// обсидиановая шторка 22dp сверху, карточки 15dp, колодцы 12dp, чипы 9dp, cyan/зелёные акценты.

// NyC fixed tokens (same as the rest of the NyC screens).
private val nycText = Color(0xFFDBE4F0)
private val nycMuted = Color(0xFF8794A7)
private val nycFaint = Color(0xFF5B6A7E)
private val nycGreen = Color(0xFF46E08C)
private val nycEdge = Color(150, 185, 235, alpha = 18)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NycAutoUpdateSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember(context) { AutoUpdateSettingsStore.getInstance(context) }
    val config by store.config.collectAsState()

    // Системные разрешения/состояния читаются при открытии и при возврате из системных
    // настроек (пользователь уходит отключать оптимизацию батареи).
    var notificationsGranted by remember {
        mutableStateOf(ReminderPermissions.notificationsEnabled(context))
    }
    var notificationsDenied by remember { mutableStateOf(false) }
    var batteryIgnored by remember {
        mutableStateOf(ReminderPermissions.isIgnoringBatteryOptimizations(context))
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsGranted = ReminderPermissions.notificationsEnabled(context)
        batteryIgnored = ReminderPermissions.isIgnoringBatteryOptimizations(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        if (granted) {
            notificationsDenied = false
            AutoUpdateNotifications.ensureChannel(context)
            store.setNotificationsEnabled(true)
        } else {
            notificationsDenied = true
        }
    }

    fun requestNotifications(onGranted: () -> Unit) {
        if (notificationsGranted) {
            notificationsDenied = false
            onGranted()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // На API < 33 разрешения нет, но уведомления могут быть выключены в настройках.
            notificationsDenied = true
        }
    }

    val notificationsSubtitle = when {
        !config.enabled -> stringResource(R.string.autoupdate_notifications_need_enable)
        notificationsDenied || !notificationsGranted ->
            stringResource(R.string.autoupdate_notifications_need_permission)
        else -> stringResource(R.string.autoupdate_notifications_desc)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = Color(0xFF0F1622),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        scrimColor = Color(0xCC04070B),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(150, 185, 235, alpha = 46))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 14.dp)
                .padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------- Header ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.autoupdate_title),
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.6.sp,
                        color = NycCyan
                    )
                    Text(
                        text = stringResource(R.string.autoupdate_enable_desc),
                        fontFamily = NycSansFamily,
                        fontSize = 10.sp,
                        color = nycFaint,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                NycLed(
                    color = if (config.enabled) nycGreen else nycFaint,
                    diameter = 8.dp
                )
            }

            // ---------- Master switch ----------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .nycCard(15.dp)
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                NycSheetToggleRow(
                    title = stringResource(R.string.autoupdate_enable_title),
                    subtitle = stringResource(R.string.autoupdate_enable_desc),
                    checked = config.enabled,
                    enabled = true,
                    onCheckedChange = { enabled -> store.setEnabled(enabled) }
                )
            }

            // ---------- Interval + network ----------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .nycCard(15.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NycSectionLabel(text = stringResource(R.string.autoupdate_interval_title))
                NycIntervalChips(
                    config = config,
                    onSelect = { hours -> store.setIntervalHours(hours) }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(nycEdge)
                )

                NycSectionLabel(text = stringResource(R.string.autoupdate_network_title))
                NycNetworkSelector(
                    config = config,
                    onSelect = { network -> store.setNetwork(network) }
                )
            }

            // ---------- Notifications ----------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .nycCard(15.dp)
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                NycSheetToggleRow(
                    title = stringResource(R.string.autoupdate_notifications_title),
                    subtitle = notificationsSubtitle,
                    checked = config.notificationsEnabled,
                    enabled = config.enabled,
                    onCheckedChange = { checked ->
                        if (!checked) {
                            notificationsDenied = false
                            store.setNotificationsEnabled(false)
                        } else {
                            requestNotifications {
                                AutoUpdateNotifications.ensureChannel(context)
                                store.setNotificationsEnabled(true)
                            }
                        }
                    }
                )
                if (notificationsDenied) {
                    Text(
                        text = stringResource(R.string.autoupdate_permission_denied),
                        fontFamily = NycSansFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        color = NierRed,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
                    )
                }
            }

            // ---------- Battery optimization ----------
            if (config.enabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .nycWell(12.dp)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        text = stringResource(R.string.autoupdate_battery_title),
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.5.sp,
                        letterSpacing = 1.3.sp,
                        color = NierAmber
                    )
                    Text(
                        text = stringResource(R.string.autoupdate_battery_message),
                        fontFamily = NycSansFamily,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = nycMuted
                    )
                    if (batteryIgnored) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            NycLed(color = nycGreen, diameter = 5.dp)
                            Text(
                                text = stringResource(R.string.autoupdate_battery_ok),
                                fontFamily = NycSansFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = nycGreen
                            )
                        }
                    } else {
                        NycSheetActionButton(
                            label = stringResource(R.string.autoupdate_battery_action),
                            onClick = {
                                ReminderPermissions.requestBatteryOptimizationExemption(context)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // ---------- Close ----------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .nycRaised(11.dp)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.autoupdate_close),
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    color = nycText
                )
            }
        }
    }
}

// ---------- Building blocks ----------

@Composable
private fun NycSectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = NycMonoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 8.5.sp,
        letterSpacing = 1.5.sp,
        color = Color(0xFF7D8EA4)
    )
}

@Composable
private fun NycSheetToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 10.dp, horizontal = 2.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = NycSansFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = nycText
            )
            Text(
                text = subtitle,
                fontFamily = NycSansFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = nycMuted,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        NycPillSwitch(checked = checked)
    }
}

/** NyC pill switch: 42x24dp capsule with a 18dp thumb (touch target — строка ≥44dp). */
@Composable
private fun NycPillSwitch(checked: Boolean) {
    Box(
        modifier = Modifier
            .width(42.dp)
            .height(24.dp)
            .clip(CircleShape)
            .background(if (checked) NycCyan else Color(150, 185, 235, alpha = 22))
            .border(1.dp, if (checked) NycCyan else Color(150, 185, 235, alpha = 34), CircleShape)
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (checked) Color(0xFF0F172A) else nycFaint)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NycIntervalChips(
    config: AutoUpdateConfig,
    onSelect: (Int) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AutoUpdateConfig.ALLOWED_INTERVALS.forEach { hours ->
            val selected = config.intervalHours == hours
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .then(
                        if (selected) {
                            Modifier
                                .background(NycCyan.copy(alpha = 0.12f))
                                .border(1.dp, NycCyan.copy(alpha = 0.38f), RoundedCornerShape(9.dp))
                        } else {
                            Modifier.nycRaised(9.dp)
                        }
                    )
                    .clickable(enabled = config.enabled) { onSelect(hours) }
                    .alpha(if (config.enabled) 1f else 0.45f)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.autoupdate_interval_hours_fmt, hours),
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (selected) NycCyan else nycMuted
                )
            }
        }
    }
}

@Composable
private fun NycNetworkSelector(
    config: AutoUpdateConfig,
    onSelect: (AutoUpdateNetwork) -> Unit
) {
    val networks = listOf(
        AutoUpdateNetwork.WIFI_ONLY,
        AutoUpdateNetwork.MOBILE_ONLY,
        AutoUpdateNetwork.ANY
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .nycWell(11.dp)
            .padding(4.dp)
            .alpha(if (config.enabled) 1f else 0.45f),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        networks.forEach { network ->
            val selected = config.network == network
            val label = when (network) {
                AutoUpdateNetwork.WIFI_ONLY -> stringResource(R.string.autoupdate_network_wifi)
                AutoUpdateNetwork.MOBILE_ONLY -> stringResource(R.string.autoupdate_network_mobile)
                AutoUpdateNetwork.ANY -> stringResource(R.string.autoupdate_network_any)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (selected) Modifier.background(NycCyan.copy(alpha = 0.14f))
                        else Modifier
                    )
                    .clickable(enabled = config.enabled) { onSelect(network) }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = if (selected) NycCyan else nycMuted,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun NycSheetActionButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(NycCyan.copy(alpha = 0.10f))
            .border(1.dp, NycCyan.copy(alpha = 0.32f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = NycMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 9.5.sp,
            letterSpacing = 1.sp,
            color = NycCyan
        )
    }
}
