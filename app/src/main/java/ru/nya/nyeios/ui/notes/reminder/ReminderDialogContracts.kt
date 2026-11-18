package ru.nya.nyeios.ui.notes.reminder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import kotlinx.coroutines.delay
import ru.nya.nyeios.R
import ru.nya.nyeios.data.reminders.ReminderSchedulePolicy
import ru.nya.nyeios.ui.schedule.LessonTimeUtils
import ru.nya.nyeios.ui.theme.NierBg
import ru.nya.nyeios.ui.theme.NierBorder
import ru.nya.nyeios.ui.theme.NierBorderLight
import ru.nya.nyeios.ui.theme.NierDark
import ru.nya.nyeios.ui.theme.NierDim
import ru.nya.nyeios.ui.theme.NierGreen
import ru.nya.nyeios.ui.theme.NierPanel
import ru.nya.nyeios.ui.theme.NierPanelAlt
import ru.nya.nyeios.ui.theme.NierRed
import ru.nya.nyeios.ui.theme.NierSelectionText
import ru.nya.nyeios.ui.theme.NierTypography
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.NycCyanDk
import ru.nya.nyeios.ui.theme.NycMonoFamily
import ru.nya.nyeios.ui.theme.NycSansFamily
import ru.nya.nyeios.ui.theme.NycShapes
import ru.nya.nyeios.ui.theme.NycTypography
import ru.nya.nyeios.ui.theme.RajdhaniFamily
import ru.nya.nyeios.ui.theme.ShareTechMonoFamily
import ru.nya.nyeios.ui.theme.isNycModern
import ru.nya.nyeios.ui.theme.selectionAccent
import java.time.Instant
import java.time.ZoneOffset

/** Значения, которые пользователь выбрал в диалоге напоминания. */
data class ReminderDraft(
    val triggerAtUtcMs: Long,
    val content: String
)

/**
 * Диалог создания/правки напоминания: дата + время (Material3 pickers) и содержание.
 * Внешний слой передаёт уже локализованные [title] и [confirmLabel].
 *
 * Материаловские пикеры обёрнуты в [MaterialTheme] с палитрой и формами активного
 * семейства: NyC-modern — тёмный cyan-схема и скругления 6/9/11/16 dp, YoRHa — 0 dp
 * и моноширинные подписи.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDraftDialog(
    title: String,
    confirmLabel: String,
    initialTriggerAtUtcMs: Long,
    initialContent: String,
    onDismiss: () -> Unit,
    onConfirm: (ReminderDraft) -> Unit
) {
    val zone = LessonTimeUtils.moscowZone
    val tokens = reminderDialogTokens()
    val initialTime = remember(initialTriggerAtUtcMs) {
        ReminderDraftModel.timeOf(initialTriggerAtUtcMs, zone)
    }
    var selectedDate by remember(initialTriggerAtUtcMs) {
        mutableStateOf(ReminderDraftModel.dateOf(initialTriggerAtUtcMs, zone))
    }
    var selectedHour by remember(initialTriggerAtUtcMs) { mutableIntStateOf(initialTime.hour) }
    var selectedMinute by remember(initialTriggerAtUtcMs) { mutableIntStateOf(initialTime.minute) }
    var content by remember(initialContent) { mutableStateOf(initialContent) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var nowUtcMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Валидация должна «стареть» вместе с реальным временем, пока диалог открыт.
    LaunchedEffect(Unit) {
        while (true) {
            nowUtcMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val triggerAtUtcMs = remember(selectedDate, selectedHour, selectedMinute) {
        ReminderDraftModel.combine(selectedDate, selectedHour, selectedMinute, zone)
    }
    val validation = ReminderDraftModel.validationFor(
        ReminderSchedulePolicy.checkTrigger(triggerAtUtcMs, nowUtcMs)
    )
    val errorText = when (validation) {
        ReminderDraftValidation.IN_PAST -> stringResource(R.string.reminders_error_past)
        ReminderDraftValidation.TOO_SOON -> stringResource(R.string.reminders_error_too_soon)
        ReminderDraftValidation.VALID -> null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ReminderDialogTheme {
            ReminderWindowDim()
            Surface(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .imePadding(),
                shape = tokens.cardShape,
                color = tokens.panel,
                border = BorderStroke(1.dp, tokens.border)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ReminderDialogHeader(
                        tokens = tokens,
                        title = title,
                        onDismiss = onDismiss
                    )

                    ReminderPickerRow(
                        tokens = tokens,
                        label = stringResource(R.string.reminders_date_label),
                        value = ReminderDraftModel.formatDate(selectedDate),
                        icon = Icons.Default.DateRange,
                        onClick = { showDatePicker = true }
                    )
                    ReminderPickerRow(
                        tokens = tokens,
                        label = stringResource(R.string.reminders_time_label),
                        value = ReminderDraftModel.formatTime(selectedHour, selectedMinute),
                        icon = Icons.Default.Schedule,
                        onClick = { showTimePicker = true }
                    )
                    ReminderContentField(
                        tokens = tokens,
                        content = content,
                        onContentChange = { content = it }
                    )

                    if (errorText != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = tokens.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = errorText,
                                color = tokens.error,
                                fontFamily = tokens.bodyFont,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Spacer(Modifier.weight(1f))
                        ReminderDialogButton(
                            tokens = tokens,
                            text = stringResource(R.string.reminders_dialog_cancel),
                            primary = false,
                            onClick = onDismiss
                        )
                        ReminderDialogButton(
                            tokens = tokens,
                            text = confirmLabel,
                            primary = true,
                            enabled = validation == ReminderDraftValidation.VALID,
                            onClick = {
                                onConfirm(
                                    ReminderDraft(
                                        triggerAtUtcMs = triggerAtUtcMs,
                                        content = content
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )
        ReminderDialogTheme {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                selectedDate = Instant.ofEpochMilli(millis)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                            }
                            showDatePicker = false
                        }
                    ) {
                        Text(stringResource(R.string.reminders_dialog_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text(stringResource(R.string.reminders_dialog_cancel))
                    }
                },
                shape = tokens.cardShape,
                tonalElevation = 0.dp
            ) {
                ReminderWindowDim()
                DatePicker(state = datePickerState)
            }
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = true
        )
        ReminderDialogTheme {
            ReminderWindowDim()
            Dialog(
                onDismissRequest = { showTimePicker = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                        .widthIn(max = 520.dp),
                    shape = tokens.cardShape,
                    color = tokens.panel,
                    border = BorderStroke(1.dp, tokens.border)
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.reminders_time_picker_hint),
                            color = tokens.text,
                            fontFamily = tokens.titleFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = if (tokens.isModern) 0.sp else 1.5.sp
                        )
                        TimePicker(
                            state = timePickerState,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Spacer(Modifier.weight(1f))
                            ReminderDialogButton(
                                tokens = tokens,
                                text = stringResource(R.string.reminders_dialog_cancel),
                                primary = false,
                                onClick = { showTimePicker = false }
                            )
                            ReminderDialogButton(
                                tokens = tokens,
                                text = stringResource(R.string.reminders_dialog_confirm),
                                primary = true,
                                onClick = {
                                    selectedHour = timePickerState.hour
                                    selectedMinute = timePickerState.minute
                                    showTimePicker = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Какие разрешения/настройки мешают напоминанию сработать. */
data class ReminderSetupIssues(
    val notificationsEnabled: Boolean,
    val exactAlarmsAllowed: Boolean,
    val batteryOptimizationIgnored: Boolean
) {
    val hasIssues: Boolean
        get() = !notificationsEnabled || !exactAlarmsAllowed || !batteryOptimizationIgnored
}

/**
 * Рекомендательный диалог после постановки напоминания: предлагает выдать уведомления,
 * разрешить точные будильники и отключить оптимизацию батареи, чтобы приложение
 * не выгружалось из фона. Закрывается по «Понятно».
 *
 * Блок про оптимизацию батареи показывается всегда: это обязательная рекомендация,
 * без неё система вправе выгрузить приложение и напоминание не сработает.
 */
@Composable
fun ReminderSetupDialog(
    issues: ReminderSetupIssues,
    onDismiss: () -> Unit,
    onFixNotifications: () -> Unit,
    onFixExactAlarms: () -> Unit,
    onFixBattery: () -> Unit
) {
    val tokens = reminderDialogTokens()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ReminderDialogTheme {
            ReminderWindowDim()
            Surface(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .imePadding(),
                shape = tokens.cardShape,
                color = tokens.panel,
                border = BorderStroke(1.dp, tokens.border)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ReminderDialogHeader(
                        tokens = tokens,
                        title = stringResource(R.string.reminders_dialog_title),
                        onDismiss = onDismiss
                    )

                    if (!issues.notificationsEnabled) {
                        ReminderSetupIssueBlock(
                            tokens = tokens,
                            icon = Icons.Default.NotificationsActive,
                            title = stringResource(R.string.reminders_perm_notifications_title),
                            message = stringResource(R.string.reminders_perm_notifications_message),
                            actionLabel = stringResource(R.string.reminders_perm_notifications_action),
                            onAction = onFixNotifications
                        )
                    }

                    if (!issues.exactAlarmsAllowed) {
                        ReminderSetupIssueBlock(
                            tokens = tokens,
                            icon = Icons.Default.Alarm,
                            title = stringResource(R.string.reminders_perm_exact_title),
                            message = stringResource(R.string.reminders_perm_exact_message),
                            actionLabel = stringResource(R.string.reminders_perm_exact_action),
                            onAction = onFixExactAlarms
                        )
                    }

                    if (issues.batteryOptimizationIgnored) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryAlert,
                                contentDescription = null,
                                tint = tokens.accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.reminders_perm_battery_done),
                                color = tokens.muted,
                                fontFamily = tokens.bodyFont,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    } else {
                        ReminderSetupIssueBlock(
                            tokens = tokens,
                            icon = Icons.Default.BatteryAlert,
                            title = stringResource(R.string.reminders_perm_battery_title),
                            message = stringResource(R.string.reminders_perm_battery_message),
                            actionLabel = stringResource(R.string.reminders_perm_battery_action),
                            onAction = onFixBattery
                        )
                    }

                    ReminderDialogButton(
                        tokens = tokens,
                        text = stringResource(R.string.reminders_perm_close),
                        primary = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

// --- Токены семейств ---

private data class ReminderDialogTokens(
    val isModern: Boolean,
    val panel: Color,
    val well: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val border: Color,
    val error: Color,
    val scrim: Color,
    val cardShape: Shape,
    val wellShape: Shape,
    val rowShape: Shape,
    val buttonShape: Shape,
    val titleFont: FontFamily,
    val bodyFont: FontFamily,
    val metaFont: FontFamily
)

@Composable
private fun reminderDialogTokens(): ReminderDialogTokens {
    val isModern = isNycModern
    return if (isModern) {
        ReminderDialogTokens(
            isModern = true,
            panel = Color(0xFF131D2B),
            well = Color(0xFF0C121B),
            text = Color(0xFFE2E8F0),
            muted = Color(0xFF8899AC),
            accent = NycCyan,
            border = Color(0xFF1F2E45),
            error = Color(0xFFFF6B81),
            scrim = Color(0xD9080C14),
            cardShape = RoundedCornerShape(NycShapes.card),
            wellShape = RoundedCornerShape(NycShapes.chip),
            rowShape = RoundedCornerShape(NycShapes.button),
            buttonShape = RoundedCornerShape(NycShapes.button),
            titleFont = NycSansFamily,
            bodyFont = NycSansFamily,
            metaFont = NycMonoFamily
        )
    } else {
        ReminderDialogTokens(
            isModern = false,
            panel = NierPanel,
            well = NierPanelAlt,
            text = NierDark,
            muted = NierDim,
            accent = selectionAccent(),
            border = NierBorder,
            error = NierRed,
            scrim = Color(0xC735322A),
            cardShape = RoundedCornerShape(0.dp),
            wellShape = RoundedCornerShape(0.dp),
            rowShape = RoundedCornerShape(0.dp),
            buttonShape = RoundedCornerShape(0.dp),
            titleFont = RajdhaniFamily,
            bodyFont = ShareTechMonoFamily,
            metaFont = ShareTechMonoFamily
        )
    }
}

/**
 * Материаловские пикеры наследуют цвета и формы из этой темы, поэтому DatePicker
 * и TimePicker автоматически попадают в палитру активного семейства.
 */
@Composable
private fun ReminderDialogTheme(content: @Composable () -> Unit) {
    val isModern = isNycModern
    val colorScheme = if (isModern) {
        darkColorScheme(
            primary = NycCyan,
            onPrimary = Color(0xFF04121A),
            primaryContainer = NycCyanDk,
            onPrimaryContainer = Color(0xFF04121A),
            secondary = NycCyan,
            onSecondary = Color(0xFF04121A),
            tertiary = Color(0xFFD946EF),
            background = Color(0xFF0A0F18),
            onBackground = Color(0xFFE2E8F0),
            surface = Color(0xFF131D2B),
            onSurface = Color(0xFFE2E8F0),
            surfaceVariant = Color(0xFF0C121B),
            onSurfaceVariant = Color(0xFF8899AC),
            outline = Color(0xFF1F2E45),
            outlineVariant = Color(0xFF1F2E45),
            error = Color(0xFFFF6B81),
            onError = Color(0xFF0A0F18)
        )
    } else {
        darkColorScheme(
            primary = selectionAccent(),
            onPrimary = NierBg,
            primaryContainer = NierPanel,
            onPrimaryContainer = NierDark,
            secondary = NierGreen,
            onSecondary = NierBg,
            background = NierBg,
            onBackground = NierDark,
            surface = NierPanel,
            onSurface = NierDark,
            surfaceVariant = NierPanelAlt,
            onSurfaceVariant = NierDim,
            outline = NierBorder,
            outlineVariant = NierBorderLight,
            error = NierRed,
            onError = NierBg
        )
    }
    val shapes = if (isModern) {
        Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(NycShapes.chip),
            medium = RoundedCornerShape(NycShapes.button),
            large = RoundedCornerShape(NycShapes.card),
            extraLarge = RoundedCornerShape(NycShapes.card)
        )
    } else {
        Shapes(
            extraSmall = RoundedCornerShape(0.dp),
            small = RoundedCornerShape(0.dp),
            medium = RoundedCornerShape(0.dp),
            large = RoundedCornerShape(0.dp),
            extraLarge = RoundedCornerShape(0.dp)
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = shapes,
        typography = if (isModern) NycTypography else NierTypography,
        content = content
    )
}

/**
 * Явное затемнение подложки для окна диалога.
 *
 * Диалог напоминания открывается поверх другого Compose-диалога (редактор заметки),
 * и вложенное окно не получает системного затемнения — фон остаётся ярким, а модальный
 * слой читается как «приклеенная карточка». Поэтому dimAmount выставляется вручную;
 * в списке напоминаний (обычное окно Activity) это лишь усиливает штатный скрим.
 */
@Composable
private fun ReminderWindowDim(alpha: Float = 0.62f) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.let {
                it.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                it.setDimAmount(alpha)
            }
        }
    }
}

// --- Внутренние блоки ---

@Composable
private fun ReminderDialogHeader(
    tokens: ReminderDialogTokens,
    title: String,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.NotificationsActive,
            contentDescription = null,
            tint = tokens.accent,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            color = tokens.text,
            fontFamily = tokens.titleFont,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = if (tokens.isModern) 0.sp else 1.5.sp,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.reminders_dialog_cancel),
                tint = tokens.muted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ReminderPickerRow(
    tokens: ReminderDialogTokens,
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = tokens.muted,
            fontFamily = tokens.metaFont,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 1.2.sp
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(tokens.rowShape)
                .background(tokens.well, tokens.rowShape)
                .border(1.dp, tokens.border, tokens.rowShape)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tokens.accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = value,
                color = tokens.text,
                fontFamily = tokens.titleFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = tokens.muted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ReminderContentField(
    tokens: ReminderDialogTokens,
    content: String,
    onContentChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.reminders_content_label),
            color = tokens.muted,
            fontFamily = tokens.metaFont,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 1.2.sp
        )
        BasicTextField(
            value = content,
            onValueChange = onContentChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .clip(tokens.wellShape)
                .background(tokens.well, tokens.wellShape)
                .border(1.dp, tokens.border, tokens.wellShape)
                .padding(12.dp),
            textStyle = TextStyle(
                color = tokens.text,
                fontFamily = tokens.bodyFont,
                fontSize = 13.sp,
                lineHeight = 18.sp
            ),
            cursorBrush = SolidColor(tokens.accent),
            decorationBox = { inner ->
                if (content.isEmpty()) {
                    Text(
                        text = stringResource(R.string.reminders_content_placeholder),
                        color = tokens.muted.copy(alpha = 0.65f),
                        fontFamily = tokens.bodyFont,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
                inner()
            }
        )
    }
}

@Composable
private fun ReminderSetupIssueBlock(
    tokens: ReminderDialogTokens,
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.wellShape)
            .background(tokens.well, tokens.wellShape)
            .border(1.dp, tokens.border, tokens.wellShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tokens.accent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                color = tokens.text,
                fontFamily = tokens.titleFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = if (tokens.isModern) 0.sp else 1.sp
            )
        }
        Text(
            text = message,
            color = tokens.muted,
            fontFamily = tokens.bodyFont,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        ReminderDialogButton(
            tokens = tokens,
            text = actionLabel,
            primary = false,
            modifier = Modifier.align(Alignment.End),
            onClick = onAction
        )
    }
}

@Composable
private fun ReminderDialogButton(
    tokens: ReminderDialogTokens,
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val background = when {
        !enabled -> tokens.muted.copy(alpha = 0.18f)
        primary -> tokens.accent
        else -> Color.Transparent
    }
    val foreground = when {
        !enabled -> tokens.muted.copy(alpha = 0.6f)
        primary -> if (tokens.isModern) Color(0xFF04121A) else NierSelectionText
        else -> tokens.accent
    }
    val borderColor = if (primary && enabled) Color.Transparent else tokens.border
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(tokens.buttonShape)
            .background(background, tokens.buttonShape)
            .border(1.dp, borderColor, tokens.buttonShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = foreground,
            fontFamily = tokens.titleFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = if (tokens.isModern) 0.4.sp else 1.sp
        )
    }
}
