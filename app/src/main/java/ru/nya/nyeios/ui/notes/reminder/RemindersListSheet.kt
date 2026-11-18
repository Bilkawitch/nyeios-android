package ru.nya.nyeios.ui.notes.reminder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.nya.nyeios.R
import ru.nya.nyeios.data.reminders.Reminder
import ru.nya.nyeios.data.reminders.ReminderFormatter
import ru.nya.nyeios.data.reminders.ReminderScheduleNotice
import ru.nya.nyeios.data.reminders.ReminderSchedulePolicy
import ru.nya.nyeios.data.reminders.RemindersRepository
import ru.nya.nyeios.ui.schedule.LessonTimeUtils
import ru.nya.nyeios.ui.theme.NierBg
import ru.nya.nyeios.ui.theme.NierBorderLight
import ru.nya.nyeios.ui.theme.NierDark
import ru.nya.nyeios.ui.theme.NierDim
import ru.nya.nyeios.ui.theme.NierPanel
import ru.nya.nyeios.ui.theme.NierPanelAlt
import ru.nya.nyeios.ui.theme.NierRed
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.NycCyanDk
import ru.nya.nyeios.ui.theme.NycMonoFamily
import ru.nya.nyeios.ui.theme.NycSansFamily
import ru.nya.nyeios.ui.theme.NycShapes
import ru.nya.nyeios.ui.theme.RajdhaniFamily
import ru.nya.nyeios.ui.theme.ShareTechMonoFamily
import ru.nya.nyeios.ui.theme.isNycModern
import ru.nya.nyeios.ui.theme.nycCard
import ru.nya.nyeios.ui.theme.selectionAccent

/**
 * Список установленных напоминаний: содержание, сколько осталось до срабатывания,
 * правка и удаление. Открывается кнопкой-таймером на месте воскресенья в ряду дней.
 *
 * Шторка сама читает [RemindersRepository], поэтому внешнему слою достаточно
 * передать [onDismiss]. Встроена в область контента экрана расписания, как и
 * [ru.nya.nyeios.ui.notes.weekly.WeeklyHomeworkSheet], поэтому при
 * [applyWindowInsets] = false системные отступы уже обработаны Scaffold-ом.
 */
@Composable
fun RemindersListSheet(
    onDismiss: () -> Unit,
    applyWindowInsets: Boolean = true
) {
    val context = LocalContext.current
    val repository = remember { RemindersRepository.getInstance(context) }
    val reminders by repository.remindersFlow.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Обратный отсчёт обновляется раз в 30 секунд: чаще в списке не нужно, а
    // тикающая раз в секунду строка в LazyColumn излишне нагружает композицию.
    val nowUtcMs by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(COUNTDOWN_REFRESH_MS)
        }
    }

    var editingReminder by remember { mutableStateOf<Reminder?>(null) }
    var deletingReminder by remember { mutableStateOf<Reminder?>(null) }

    BackHandler { onDismiss() }

    val isModern = isNycModern
    val bgCanvas = if (isModern) Color(0xFF0F172A) else NierBg
    val panelBg = if (isModern) Color(0xFF1E293B) else NierPanel
    val textColor = if (isModern) Color(0xFFF1F5F9) else NierDark
    val mutedColor = if (isModern) Color(0xFF94A3B8) else NierDim
    val accentColor = if (isModern) NycCyan else selectionAccent()
    val borderColor = if (isModern) NycCyanDk.copy(alpha = 0.5f) else NierBorderLight

    val headerShape =
        if (isModern) RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
        else RoundedCornerShape(0.dp)
    val titleFont = if (isModern) NycSansFamily else RajdhaniFamily
    val monoFont = if (isModern) NycMonoFamily else ShareTechMonoFamily

    val windowInsetsModifier = if (applyWindowInsets) {
        Modifier
            .statusBarsPadding()
            .navigationBarsPadding()
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgCanvas)
            .then(windowInsetsModifier)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Заголовок шторки: иконка-будильник, название и кнопка закрытия.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(headerShape)
                    .background(panelBg, headerShape)
                    .border(1.dp, borderColor, headerShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.reminders_list_title),
                        fontFamily = titleFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = if (isModern) 0.5.sp else 1.sp,
                        color = textColor
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            val sortedReminders = remember(reminders, nowUtcMs) {
                ReminderSchedulePolicy.sortForList(reminders, nowUtcMs)
            }

            if (sortedReminders.isEmpty()) {
                RemindersEmptyState(
                    textColor = textColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    titleFont = titleFont,
                    monoFont = monoFont
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sortedReminders) { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            nowUtcMs = nowUtcMs,
                            isModern = isModern,
                            textColor = textColor,
                            mutedColor = mutedColor,
                            accentColor = accentColor,
                            borderColor = borderColor,
                            titleFont = titleFont,
                            monoFont = monoFont,
                            onEdit = { editingReminder = reminder },
                            onDelete = { deletingReminder = reminder }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    editingReminder?.let { reminder ->
        ReminderDraftDialog(
            title = stringResource(R.string.reminders_dialog_edit_title),
            confirmLabel = stringResource(R.string.reminders_dialog_save),
            initialTriggerAtUtcMs = reminder.triggerAtUtcMs,
            initialContent = reminder.noteText,
            onDismiss = { editingReminder = null },
            onConfirm = { draft ->
                editingReminder = null
                scope.launch {
                    // Запись на диск и постановка будильника — не на главном потоке.
                    val result = withContext(Dispatchers.IO) {
                        repository.upsert(
                            reminder.copy(
                                triggerAtUtcMs = draft.triggerAtUtcMs,
                                noteText = ReminderFormatter.normalizeNoteText(draft.content)
                            )
                        )
                    }
                    val triggerLabel = ReminderFormatter.formatTrigger(
                        result.reminder.triggerAtUtcMs,
                        LessonTimeUtils.moscowZone
                    )
                    snackbarHostState.showSnackbar(
                        when (result.notice) {
                            ReminderScheduleNotice.EXACT ->
                                context.getString(R.string.reminders_updated_fmt, triggerLabel)

                            ReminderScheduleNotice.INEXACT ->
                                context.getString(R.string.reminders_scheduled_inexact)

                            ReminderScheduleNotice.FAILED ->
                                context.getString(R.string.reminders_scheduled_failed)
                        }
                    )
                }
            }
        )
    }

    deletingReminder?.let { reminder ->
        ReminderDeleteDialog(
            isModern = isModern,
            textColor = textColor,
            mutedColor = mutedColor,
            titleFont = titleFont,
            monoFont = monoFont,
            onDismiss = { deletingReminder = null },
            onConfirm = {
                deletingReminder = null
                scope.launch {
                    withContext(Dispatchers.IO) { repository.delete(reminder.id) }
                    snackbarHostState.showSnackbar(context.getString(R.string.reminders_deleted))
                }
            }
        )
    }
}

private const val COUNTDOWN_REFRESH_MS = 30_000L

@Composable
private fun ReminderCard(
    reminder: Reminder,
    nowUtcMs: Long,
    isModern: Boolean,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    borderColor: Color,
    titleFont: FontFamily,
    monoFont: FontFamily,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val cardShape = if (isModern) RoundedCornerShape(NycShapes.card) else RoundedCornerShape(0.dp)
    val wellShape = if (isModern) RoundedCornerShape(NycShapes.well) else RoundedCornerShape(0.dp)
    val actionShape = if (isModern) RoundedCornerShape(NycShapes.chip) else RoundedCornerShape(0.dp)

    val remainingMs = reminder.triggerAtUtcMs - nowUtcMs
    val isOverdue = remainingMs <= 0L
    val countdownText = if (isOverdue) {
        stringResource(R.string.reminders_overdue)
    } else {
        stringResource(
            R.string.reminders_remaining_fmt,
            ReminderFormatter.formatCountdown(remainingMs)
        )
    }
    val countdownColor = if (isOverdue) NierRed else accentColor

    // Без remember: форматтеры читают текущую локаль приложения, поэтому строка
    // должна пересчитываться при смене языка без перезапуска процесса (AGENTS.md §12.1).
    val triggerText = ReminderFormatter.formatTrigger(reminder.triggerAtUtcMs, LessonTimeUtils.moscowZone)
    val title = ReminderFormatter.listTitle(reminder)
    val content = ReminderFormatter.listContent(reminder)

    val surfaceModifier = if (isModern) {
        Modifier
            .fillMaxWidth()
            .nycCard()
    } else {
        Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(NierPanelAlt, cardShape)
            .border(1.dp, borderColor, cardShape)
    }

    Box(
        modifier = surfaceModifier.padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontFamily = titleFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = textColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = triggerText,
                        fontFamily = monoFont,
                        fontSize = 9.5.sp,
                        letterSpacing = 0.4.sp,
                        color = mutedColor,
                        maxLines = 1
                    )
                }

                // Обратный отсчёт: «через …» либо «время истекло».
                Box(
                    modifier = Modifier
                        .clip(actionShape)
                        .background(countdownColor.copy(alpha = 0.10f), actionShape)
                        .border(1.dp, countdownColor.copy(alpha = 0.34f), actionShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = countdownText,
                        fontFamily = monoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        letterSpacing = 0.4.sp,
                        color = countdownColor,
                        maxLines = 1
                    )
                }
            }

            if (content.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(wellShape)
                        .background(
                            if (isModern) Color(0xFF0C121B) else textColor.copy(alpha = 0.04f),
                            wellShape
                        )
                        .border(1.dp, accentColor.copy(alpha = 0.22f), wellShape)
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = content,
                        fontFamily = if (isModern) NycSansFamily else RajdhaniFamily,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = textColor,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReminderActionButton(
                    label = stringResource(R.string.reminders_action_edit),
                    color = accentColor,
                    shape = actionShape,
                    font = monoFont,
                    modifier = Modifier.weight(1f),
                    onClick = onEdit
                )
                ReminderActionButton(
                    label = stringResource(R.string.reminders_action_delete),
                    color = NierRed,
                    shape = actionShape,
                    font = monoFont,
                    modifier = Modifier.weight(1f),
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun ReminderActionButton(
    label: String,
    color: Color,
    shape: Shape,
    font: FontFamily,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(color.copy(alpha = 0.10f), shape)
            .border(1.dp, color.copy(alpha = 0.38f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = font,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = color,
            maxLines = 1
        )
    }
}

@Composable
private fun RemindersEmptyState(
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    titleFont: FontFamily,
    monoFont: FontFamily
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Alarm,
            contentDescription = null,
            tint = accentColor.copy(alpha = 0.6f),
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.reminders_list_empty_title),
            fontFamily = titleFont,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = textColor,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.reminders_list_empty_hint),
            fontFamily = monoFont,
            fontSize = 10.sp,
            lineHeight = 15.sp,
            color = mutedColor,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ReminderDeleteDialog(
    isModern: Boolean,
    textColor: Color,
    mutedColor: Color,
    titleFont: FontFamily,
    monoFont: FontFamily,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isModern) Color(0xFF1E293B) else NierPanel,
        titleContentColor = textColor,
        textContentColor = mutedColor,
        shape = if (isModern) RoundedCornerShape(16.dp) else RoundedCornerShape(0.dp),
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = NierRed,
                modifier = Modifier.size(26.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.reminders_delete_confirm_title),
                fontFamily = titleFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = textColor
            )
        },
        text = {
            Text(
                text = stringResource(R.string.reminders_delete_confirm_message),
                fontFamily = monoFont,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = mutedColor
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(
                    text = stringResource(R.string.reminders_action_delete),
                    fontFamily = monoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = NierRed
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(
                    text = stringResource(R.string.reminders_dialog_cancel),
                    fontFamily = monoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = mutedColor
                )
            }
        }
    )
}
