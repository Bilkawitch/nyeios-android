package ru.nya.nyeios.ui.notes.weekly

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nya.nyeios.R
import ru.nya.nyeios.data.model.DaySchedule
import ru.nya.nyeios.data.model.LessonItem
import ru.nya.nyeios.data.model.WeekSchedule
import ru.nya.nyeios.data.notes.LessonNotesRepository
import ru.nya.nyeios.ui.notes.editor.LessonNoteEditorScreen
import ru.nya.nyeios.ui.notes.editor.RichTextFormatter
import ru.nya.nyeios.ui.notes.preview.LessonNotePreviewSheet
import ru.nya.nyeios.ui.theme.NierBorder
import ru.nya.nyeios.ui.theme.NierBorderLight
import ru.nya.nyeios.ui.theme.NierDark
import ru.nya.nyeios.ui.theme.NierDim
import ru.nya.nyeios.ui.theme.NierPanel
import ru.nya.nyeios.ui.theme.NierPanelAlt
import ru.nya.nyeios.ui.theme.NierSelectionText
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.NycCyanDk
import ru.nya.nyeios.ui.theme.NycMonoFamily
import ru.nya.nyeios.ui.theme.NycSansFamily
import ru.nya.nyeios.ui.theme.NycShapes
import ru.nya.nyeios.ui.theme.RajdhaniFamily
import ru.nya.nyeios.ui.theme.ShareTechMonoFamily
import ru.nya.nyeios.ui.theme.isNycModern
import ru.nya.nyeios.ui.theme.selectionAccent

/**
 * Нижняя плашка (Скриншот 2): тройная полупрозрачная стрелочка и надпись.
 * Срабатывает СТРОГО по жесту свайпа вверх (drag gesture).
 */
@Composable
fun HomeworkPullUpHandle(
    modifier: Modifier = Modifier,
    onSwipeUp: () -> Unit
) {
    val isModern = isNycModern
    val arrowColor = if (isModern) NycCyan.copy(alpha = 0.5f) else ru.nya.nyeios.ui.theme.selectionAccent().copy(alpha = 0.45f)
    val textColor = if (isModern) NycCyan.copy(alpha = 0.7f) else NierDim
    val labelFont = if (isModern) NycMonoFamily else ShareTechMonoFamily

    var accumulatedDrag by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { accumulatedDrag = 0f },
                    onDragEnd = { accumulatedDrag = 0f },
                    onDragCancel = { accumulatedDrag = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDrag += dragAmount
                        // Порог свайпа вверх: отрицательное смещение более 40dp
                        if (accumulatedDrag < -100f) {
                            accumulatedDrag = 0f
                            onSwipeUp()
                        }
                    }
                )
            }
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Тройная полупрозрачная стрелочка (три шеврона вверх)
        TripleChevronUp(
            color = arrowColor,
            modifier = Modifier.size(width = 36.dp, height = 20.dp)
        )

        Text(
            text = stringResource(R.string.notes_pull_to_open_hw),
            fontSize = 9.sp,
            fontFamily = labelFont,
            color = textColor,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun TripleChevronUp(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()
        val spacing = h / 3f

        for (i in 0..2) {
            val yOffset = i * spacing
            val path = Path().apply {
                moveTo(w * 0.15f, yOffset + spacing * 0.9f)
                lineTo(w * 0.5f, yOffset + spacing * 0.2f)
                lineTo(w * 0.85f, yOffset + spacing * 0.9f)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

/**
 * Еженедельный блокнот с непрерывным вертикальным списком всех подгруженных недель.
 * Полная поддержка тем оформления:
 * - В NyC-modern: мягкие скругленные карточки (16dp), чипы (9dp), круглые маркеры, отсутствие острых 90° углов.
 * - В YoRHa: строгие прямоугольные рамки (0dp), ShareTechMono, ASCII разделители.
 */
@Composable
fun WeeklyHomeworkSheet(
    currentSchedule: WeekSchedule?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { LessonNotesRepository.getInstance(context) }
    val notesMap by repository.notesFlow.collectAsState()

    val allWeeks = remember(currentSchedule, notesMap) {
        repository.loadAllCachedWeeks(currentSchedule)
    }

    // Состояние аккордеона: по умолчанию развернута строго нынешняя неделя (offsetWeeks == 0)
    var expandedWeeks by remember(allWeeks) {
        mutableStateOf(
            allWeeks.filter { it.offsetWeeks == 0 }.map { it.startDate }.toSet()
                .ifEmpty { setOfNotNull(currentSchedule?.startDate) }
        )
    }

    var activeEditorParams by remember { mutableStateOf<EditorLaunchParams?>(null) }
    var activePreviewParams by remember { mutableStateOf<EditorLaunchParams?>(null) }

    val listState = rememberLazyListState()
    var hasScrolledToInitial by remember { mutableStateOf(false) }

    // Скролл прямо на выбранную неделю при первичном открытии
    LaunchedEffect(allWeeks, currentSchedule) {
        if (!hasScrolledToInitial && allWeeks.isNotEmpty()) {
            val targetStartDate = currentSchedule?.startDate?.ifEmpty { null }
                ?: allWeeks.find { it.offsetWeeks == 0 }?.startDate
                ?: allWeeks.first().startDate

            // Если выбранная неделя отличается от текущей, также гарантируем её разворачивание
            if (targetStartDate !in expandedWeeks) {
                expandedWeeks = expandedWeeks + targetStartDate
            }

            val targetIndex = repository.calculateWeekItemIndex(targetStartDate, allWeeks, expandedWeeks)
            listState.scrollToItem(targetIndex)
            hasScrolledToInitial = true
        }
    }

    BackHandler {
        onDismiss()
    }

    val isModern = isNycModern
    val bgCanvas = if (isModern) Color(0xFF0F172A) else ru.nya.nyeios.ui.theme.NierBg
    val panelBg = if (isModern) Color(0xFF1E293B) else NierPanel
    val textColor = if (isModern) Color(0xFFF1F5F9) else NierDark
    val mutedColor = if (isModern) Color(0xFF94A3B8) else NierDim
    val accentColor = if (isModern) NycCyan else selectionAccent()
    val borderColor = if (isModern) NycCyanDk.copy(alpha = 0.5f) else NierBorderLight

    val headerShape = if (isModern) RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp) else RoundedCornerShape(0.dp)
    val titleFont = if (isModern) NycSansFamily else RajdhaniFamily
    val subtitleFont = if (isModern) NycMonoFamily else ShareTechMonoFamily

    val totalNotesCount = remember(notesMap) {
        notesMap.values.count { it.rawText.isNotBlank() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
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
                        imageVector = Icons.Default.EditNote,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.notes_title_weekly),
                            fontFamily = titleFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = if (isModern) 0.5.sp else 1.sp,
                            color = textColor
                        )
                        Text(
                            text = stringResource(R.string.notes_count_fmt, totalNotesCount),
                            fontFamily = subtitleFont,
                            fontSize = 10.sp,
                            color = mutedColor
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Continuous vertical list of all loaded weeks
            if (allWeeks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.notes_no_weeks_loaded),
                        fontFamily = subtitleFont,
                        fontSize = 12.sp,
                        color = mutedColor
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    allWeeks.forEach { week ->
                        val isExpanded = week.startDate in expandedWeeks
                        val isCurrent = (week.offsetWeeks == 0)

                        // Week Separator Header with adaptive borders and accordion toggle
                        item(key = "week_${week.startDate}") {
                            WeekDividerCard(
                                week = week,
                                isCurrent = isCurrent,
                                isExpanded = isExpanded,
                                accentColor = accentColor,
                                textColor = textColor,
                                panelBg = panelBg,
                                borderColor = borderColor,
                                notesMap = notesMap,
                                repository = repository,
                                onToggle = {
                                    expandedWeeks = if (isExpanded) {
                                        expandedWeeks - week.startDate
                                    } else {
                                        expandedWeeks + week.startDate
                                    }
                                }
                            )
                        }

                        if (isExpanded) {
                            week.days.forEach { day ->
                                if (day.lessons.isNotEmpty()) {
                                    // Day Header
                                    item(key = "day_${week.startDate}_${day.dayTitle}") {
                                        DayHeaderItem(
                                            day = day,
                                            mutedColor = mutedColor,
                                            accentColor = accentColor
                                        )
                                    }

                                    items(
                                        items = day.lessons,
                                        key = { lesson ->
                                            "${week.startDate}_${day.dateString}_${lesson.time}_${lesson.subject}_${lesson.lessonNumber}"
                                        }
                                    ) { lesson ->
                                        val noteKey = repository.buildKey(
                                            dateString = day.dateString.ifEmpty { week.startDate },
                                            time = lesson.time,
                                            lessonNumber = lesson.lessonNumber,
                                            subject = lesson.subject
                                        )
                                        val note = notesMap[noteKey]

                                        WeeklyLessonNoteCard(
                                            day = day,
                                            lesson = lesson,
                                            note = note,
                                            textColor = textColor,
                                            mutedColor = mutedColor,
                                            accentColor = accentColor,
                                            borderColor = borderColor,
                                            panelBg = if (isModern) Color(0xFF1E293B) else NierPanelAlt,
                                            onClick = {
                                                activeEditorParams = EditorLaunchParams(
                                                    dateString = day.dateString.ifEmpty { week.startDate },
                                                    lessonNumber = lesson.lessonNumber,
                                                    time = lesson.time,
                                                    subject = lesson.subject,
                                                    teacher = lesson.teacher
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }

        // Preview Bottom Sheet overlay
        if (activePreviewParams != null) {
            val params = activePreviewParams!!
            LessonNotePreviewSheet(
                dateString = params.dateString,
                lessonNumber = params.lessonNumber,
                time = params.time,
                subject = params.subject,
                teacher = params.teacher,
                onDismiss = { activePreviewParams = null },
                onEdit = {
                    val p = activePreviewParams!!
                    activePreviewParams = null
                    activeEditorParams = p
                }
            )
        }

        // Editor Screen overlay
        if (activeEditorParams != null) {
            val params = activeEditorParams!!
            LessonNoteEditorScreen(
                dateString = params.dateString,
                lessonNumber = params.lessonNumber,
                time = params.time,
                subject = params.subject,
                teacher = params.teacher,
                onDismiss = { activeEditorParams = null }
            )
        }
    }
}

private data class EditorLaunchParams(
    val dateString: String,
    val lessonNumber: String,
    val time: String,
    val subject: String,
    val teacher: String
)

@Composable
private fun WeekDividerCard(
    week: WeekSchedule,
    isCurrent: Boolean,
    isExpanded: Boolean,
    accentColor: Color,
    textColor: Color,
    panelBg: Color,
    borderColor: Color,
    notesMap: Map<String, ru.nya.nyeios.data.notes.LessonNote>,
    repository: LessonNotesRepository,
    onToggle: () -> Unit
) {
    val isModern = isNycModern
    val cardShape = if (isModern) RoundedCornerShape(NycShapes.card) else RoundedCornerShape(0.dp)
    val chipShape = if (isModern) RoundedCornerShape(NycShapes.chip) else RoundedCornerShape(0.dp)
    val titleFont = if (isModern) NycSansFamily else ShareTechMonoFamily
    val metaFont = if (isModern) NycMonoFamily else ShareTechMonoFamily

    val totalLessons = remember(week) {
        week.days.sumOf { it.lessons.size }
    }
    val totalNotesInWeek = remember(week, notesMap) {
        week.days.flatMap { it.lessons }.count { lesson ->
            val key = repository.buildKey(
                dateString = week.days.firstOrNull { d -> d.lessons.contains(lesson) }?.dateString?.ifEmpty { week.startDate } ?: week.startDate,
                time = lesson.time,
                lessonNumber = lesson.lessonNumber,
                subject = lesson.subject
            )
            notesMap[key]?.rawText?.isNotBlank() == true
        }
    }

    val currentTag = stringResource(R.string.notes_week_current_tag)
    val weekLabel = when {
        isCurrent -> "НЕДЕЛЯ 0 ($currentTag)"
        week.offsetWeeks > 0 -> "НЕДЕЛЯ +${week.offsetWeeks}"
        else -> "НЕДЕЛЯ ${week.offsetWeeks}"
    }
    val dateLabel = if (week.startDate.isNotEmpty()) "${week.startDate} — ${week.endDate}" else ""

    val borderThickness = if (isModern) (if (isCurrent) 1.5.dp else 1.dp) else 2.5.dp
    val activeBorderColor = if (isCurrent) accentColor.copy(alpha = 0.9f) else borderColor
    val cardBg = if (isCurrent) {
        if (isModern) Color(0xFF1E2D42) else panelBg.copy(alpha = 0.95f)
    } else {
        panelBg
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
            .clip(cardShape)
            .background(cardBg, cardShape)
            .border(borderThickness, activeBorderColor, cardShape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                // Аккордеон стрелка
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (isCurrent) accentColor else textColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )

                val headerTitle = if (isModern) {
                    "$weekLabel  ·  $dateLabel"
                } else {
                    "══  $weekLabel  ·  $dateLabel  ══"
                }

                Text(
                    text = headerTitle,
                    fontFamily = titleFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (isCurrent) accentColor else textColor,
                    letterSpacing = if (isModern) 0.5.sp else 0.8.sp
                )
            }

            // Счетчик для свернутого состояния в аккуратном чипе
            if (!isExpanded && totalLessons > 0) {
                Box(
                    modifier = Modifier
                        .clip(chipShape)
                        .background(accentColor.copy(alpha = 0.12f), chipShape)
                        .border(1.dp, accentColor.copy(alpha = 0.35f), chipShape)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = stringResource(R.string.notes_collapsed_summary, totalLessons, totalNotesInWeek),
                        fontFamily = metaFont,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeaderItem(
    day: DaySchedule,
    mutedColor: Color,
    accentColor: Color
) {
    val isModern = isNycModern
    val font = if (isModern) NycMonoFamily else ShareTechMonoFamily

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (isModern) 5.dp else 4.dp)
                .background(accentColor, if (isModern) CircleShape else RoundedCornerShape(0.dp))
        )
        Text(
            text = day.dayTitle.uppercase(),
            fontFamily = font,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = mutedColor
        )
    }
}

@Composable
private fun WeeklyLessonNoteCard(
    day: DaySchedule,
    lesson: LessonItem,
    note: ru.nya.nyeios.data.notes.LessonNote?,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    borderColor: Color,
    panelBg: Color,
    onClick: () -> Unit
) {
    val isModern = isNycModern
    val cardShape = if (isModern) RoundedCornerShape(NycShapes.card) else RoundedCornerShape(0.dp)
    val wellShape = if (isModern) RoundedCornerShape(NycShapes.well) else RoundedCornerShape(0.dp)
    val titleFont = if (isModern) NycSansFamily else RajdhaniFamily
    val metaFont = if (isModern) NycMonoFamily else ShareTechMonoFamily

    val hasNote = note != null && note.rawText.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(panelBg, cardShape)
            .border(
                width = if (hasNote) 1.5.dp else 1.dp,
                color = if (hasNote) accentColor.copy(alpha = 0.8f) else borderColor,
                shape = cardShape
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            // Pair header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val pairStr = if (lesson.lessonNumber.isNotEmpty()) "ПАРА ${lesson.lessonNumber}" else "ЗАНЯТИЕ"
                    Text(
                        text = "$pairStr · ${lesson.time}",
                        fontFamily = metaFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = accentColor
                    )
                }

                if (hasNote) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(accentColor, if (isModern) CircleShape else RoundedCornerShape(0.dp))
                        )
                        Text(
                            text = stringResource(R.string.notes_has_entry),
                            fontFamily = metaFont,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                }
            }

            // Subject Title
            Text(
                text = lesson.subject,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = titleFont,
                color = textColor
            )

            if (lesson.teacher.isNotEmpty()) {
                Text(
                    text = lesson.teacher,
                    fontSize = 9.sp,
                    fontFamily = metaFont,
                    color = mutedColor
                )
            }

            // Note Content or placeholder
            if (hasNote) {
                val formatted = remember(note!!.rawText) {
                    RichTextFormatter.htmlToAnnotatedString(note.rawText)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(wellShape)
                        .background(
                            if (isModern) Color(0xFF131D2A) else textColor.copy(alpha = 0.04f),
                            wellShape
                        )
                        .border(1.dp, accentColor.copy(alpha = 0.3f), wellShape)
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = formatted,
                        fontSize = 11.sp,
                        fontFamily = titleFont,
                        color = textColor,
                        lineHeight = 15.sp,
                        maxLines = 6
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.notes_empty_tap_to_add),
                    fontSize = 10.sp,
                    fontFamily = metaFont,
                    color = mutedColor.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
