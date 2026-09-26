package ru.nya.nyeios.ui.schedule

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nya.nyeios.R
import ru.nya.nyeios.data.model.AuthSession
import ru.nya.nyeios.data.model.DaySchedule
import ru.nya.nyeios.data.model.LessonItem
import ru.nya.nyeios.data.model.LessonType
import ru.nya.nyeios.data.model.ScheduleUiState
import ru.nya.nyeios.ui.theme.ExamRed
import ru.nya.nyeios.ui.theme.ExamRedBg
import ru.nya.nyeios.ui.theme.LabAmber
import ru.nya.nyeios.ui.theme.LabAmberBg
import ru.nya.nyeios.ui.theme.LectureBlue
import ru.nya.nyeios.ui.theme.LectureBlueBg
import ru.nya.nyeios.ui.theme.LiveBadgeColor
import ru.nya.nyeios.ui.theme.LiveGlowBorder
import ru.nya.nyeios.ui.theme.ObsidianBg
import ru.nya.nyeios.ui.theme.ObsidianBorder
import ru.nya.nyeios.ui.theme.ObsidianBorderActive
import ru.nya.nyeios.ui.theme.ObsidianCard
import ru.nya.nyeios.ui.theme.ObsidianCardSelected
import ru.nya.nyeios.ui.theme.ObsidianSurface
import ru.nya.nyeios.ui.theme.OtherPurple
import ru.nya.nyeios.ui.theme.OtherPurpleBg
import ru.nya.nyeios.ui.theme.PracticeGreen
import ru.nya.nyeios.ui.theme.PracticeGreenBg
import ru.nya.nyeios.ui.theme.TextMuted
import ru.nya.nyeios.ui.theme.TextPrimary
import ru.nya.nyeios.ui.theme.TextSecondary
import ru.nya.nyeios.ui.floormap.FloorMapBottomSheet
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    uiState: ScheduleUiState,
    weekOffset: Int,
    selectedDayIndex: Int,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    onSelectDay: (Int) -> Unit,
    onRefresh: () -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    var floorMapTargetRoom by rememberSaveable { mutableStateOf<String?>(null) }
    var floorMapFromRoom by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Week Navigator Bar
            WeekNavigator(
                weekOffset = weekOffset,
                uiState = uiState,
                onPrev = onPrevWeek,
                onNext = onNextWeek,
                onToday = onCurrentWeek
            )

            // Day Selector Tabs
            val daysList = (uiState as? ScheduleUiState.Success)?.schedule?.days ?: emptyList()
            if (daysList.isNotEmpty()) {
                DaySelectorRow(
                    days = daysList,
                    selectedIndex = selectedDayIndex.coerceIn(0, (daysList.size - 1).coerceAtLeast(0)),
                    onSelectDay = onSelectDay
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Content Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (uiState) {
                    is ScheduleUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = ru.nya.nyeios.ui.theme.NierBlue,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "ЗАГРУЗКА РАСПИСАНИЯ...",
                                    fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = ru.nya.nyeios.ui.theme.NierDim,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }

                    is ScheduleUiState.NotLoggedIn -> {
                        // Content is obscured by LoginFullscreenGate in MainActivity
                        Box(modifier = Modifier.fillMaxSize())
                    }

                    is ScheduleUiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp, vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, ru.nya.nyeios.ui.theme.NierBorder)
                                    .background(ru.nya.nyeios.ui.theme.NierPanelAlt)
                                    .padding(20.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(ru.nya.nyeios.ui.theme.NierRed)
                                    )
                                    Text(
                                        text = "СИСТЕМНОЕ ОПОВЕЩЕНИЕ",
                                        fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.sp,
                                        color = ru.nya.nyeios.ui.theme.NierRed
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(ru.nya.nyeios.ui.theme.NierRed.copy(alpha = 0.12f))
                                        .border(1.dp, ru.nya.nyeios.ui.theme.NierRed),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = ru.nya.nyeios.ui.theme.NierRed,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Spacer(Modifier.height(14.dp))

                                Text(
                                    text = uiState.message,
                                    color = ru.nya.nyeios.ui.theme.NierDark,
                                    fontSize = 13.sp,
                                    fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 18.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                )

                                Spacer(Modifier.height(20.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .height(38.dp)
                                            .border(1.dp, ru.nya.nyeios.ui.theme.NierBorder)
                                            .background(ru.nya.nyeios.ui.theme.NierPanel)
                                            .clickable { onRefresh() }
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "ПОВТОРИТЬ",
                                            fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            letterSpacing = 1.sp,
                                            color = ru.nya.nyeios.ui.theme.NierDark
                                        )
                                    }

                                    val isAuthIssue = uiState.message.contains("авториз", ignoreCase = true) ||
                                            uiState.message.contains("сесси", ignoreCase = true) ||
                                            uiState.message.contains("аккаунт", ignoreCase = true)

                                    if (isAuthIssue) {
                                        Box(
                                            modifier = Modifier
                                                .height(38.dp)
                                                .border(1.dp, ru.nya.nyeios.ui.theme.NierSelection)
                                                .background(ru.nya.nyeios.ui.theme.NierSelection)
                                                .clickable { onOpenLogin() }
                                                .padding(horizontal = 14.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "ВОЙТИ В АККАУНТ",
                                                fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                letterSpacing = 1.sp,
                                                color = ru.nya.nyeios.ui.theme.NierSelectionText
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is ScheduleUiState.Success -> {
                        val safeDayIndex = selectedDayIndex.coerceIn(0, (uiState.schedule.days.size - 1).coerceAtLeast(0))

                        AnimatedContent(
                            targetState = Pair(uiState.schedule.offsetWeeks, safeDayIndex),
                            transitionSpec = {
                                val isForward = if (targetState.first != initialState.first) {
                                    targetState.first > initialState.first
                                } else {
                                    targetState.second > initialState.second
                                }
                                val slideFraction = 0.28f
                                val animDuration = 240
                                if (isForward) {
                                    (slideInHorizontally(
                                        animationSpec = tween(animDuration, easing = FastOutSlowInEasing),
                                        initialOffsetX = { fullWidth -> (fullWidth * slideFraction).toInt() }
                                    ) + fadeIn(
                                        animationSpec = tween(animDuration, easing = LinearEasing)
                                    )).togetherWith(
                                        slideOutHorizontally(
                                            animationSpec = tween(animDuration, easing = FastOutSlowInEasing),
                                            targetOffsetX = { fullWidth -> -(fullWidth * slideFraction).toInt() }
                                        ) + fadeOut(
                                            animationSpec = tween((animDuration * 0.75f).toInt(), easing = LinearEasing)
                                        )
                                    )
                                } else {
                                    (slideInHorizontally(
                                        animationSpec = tween(animDuration, easing = FastOutSlowInEasing),
                                        initialOffsetX = { fullWidth -> -(fullWidth * slideFraction).toInt() }
                                    ) + fadeIn(
                                        animationSpec = tween(animDuration, easing = LinearEasing)
                                    )).togetherWith(
                                        slideOutHorizontally(
                                            animationSpec = tween(animDuration, easing = FastOutSlowInEasing),
                                            targetOffsetX = { fullWidth -> (fullWidth * slideFraction).toInt() }
                                        ) + fadeOut(
                                            animationSpec = tween((animDuration * 0.75f).toInt(), easing = LinearEasing)
                                        )
                                    )
                                }.using(SizeTransform(clip = false))
                            },
                            label = "day_schedule_transition",
                            modifier = Modifier.fillMaxSize()
                        ) { (_, dayIdx) ->
                            val currentDay = uiState.schedule.days.getOrNull(dayIdx)

                            if (currentDay == null || currentDay.lessons.isEmpty()) {
                                val allDaysEmpty = uiState.schedule.days.isNotEmpty() && uiState.schedule.days.all { it.lessons.isEmpty() }
                                // NieR Empty state slot
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, ru.nya.nyeios.ui.theme.NierBorderLight)
                                            .background(ru.nya.nyeios.ui.theme.NierPanelAlt.copy(alpha = 0.5f))
                                            .padding(vertical = 20.dp, horizontal = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (allDaysEmpty) "— НА ЭТОЙ НЕДЕЛЕ ЗАНЯТИЙ НЕТ —" else "— ЗАНЯТИЙ НЕТ · СВОБОДНЫЙ ДЕНЬ —",
                                                fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                letterSpacing = 1.sp,
                                                color = ru.nya.nyeios.ui.theme.NierDim
                                            )
                                            if (allDaysEmpty) {
                                                Text(
                                                    text = "В расписании университета нет запланированных пар",
                                                    fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 12.sp,
                                                    color = ru.nya.nyeios.ui.theme.NierDim.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                val todayDayMonth = remember {
                                    try {
                                        java.time.LocalDate.now(LessonTimeUtils.moscowZone).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM"))
                                    } catch (e: Exception) {
                                        ""
                                    }
                                }
                                val isDayToday = currentDay.isToday || (
                                    uiState.schedule.offsetWeeks == 0 &&
                                    todayDayMonth.isNotEmpty() &&
                                    (currentDay.dateString.contains(todayDayMonth) || currentDay.dayTitle.contains(todayDayMonth))
                                )

                                val lessons = currentDay.lessons
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(lessons) { index, lesson ->
                                        val previousRoom = if (index > 0) {
                                            lessons.subList(0, index).lastOrNull { it.room.isNotBlank() }?.room
                                        } else {
                                            null
                                        }

                                        LessonCard(
                                            lesson = lesson,
                                            isToday = isDayToday,
                                            onRoomClick = { room ->
                                                floorMapTargetRoom = room
                                                floorMapFromRoom = previousRoom
                                            }
                                        )
                                    }
                                    item {
                                        Spacer(modifier = Modifier.height(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (floorMapTargetRoom != null) {
            FloorMapBottomSheet(
                targetRoomQuery = floorMapTargetRoom!!,
                fromRoomQuery = floorMapFromRoom,
                onDismiss = {
                    floorMapTargetRoom = null
                    floorMapFromRoom = null
                }
            )
        }
    }

@Composable
fun WeekNavigator(
    weekOffset: Int,
    uiState: ScheduleUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    val schedule = (uiState as? ScheduleUiState.Success)?.schedule
    val dateSubtitle = if (schedule != null && schedule.startDate.isNotEmpty()) {
        "${schedule.startDate} – ${schedule.endDate} · Неделя $weekOffset"
    } else {
        "Неделя $weekOffset"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ru.nya.nyeios.ui.theme.NierPanel)
            .border(width = 1.dp, color = ru.nya.nyeios.ui.theme.NierBorderLight)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Prev button (square NieR button)
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(ru.nya.nyeios.ui.theme.NierPanel)
                .border(1.dp, ru.nya.nyeios.ui.theme.NierDark)
                .clickable { onPrev() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "◀",
                fontSize = 11.sp,
                color = ru.nya.nyeios.ui.theme.NierDark,
                fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { onToday() }
        ) {
            Text(
                text = when (weekOffset) {
                    0 -> "Текущая неделя"
                    1 -> "Следующая неделя"
                    -1 -> "Предыдущая неделя"
                    else -> if (weekOffset > 0) "+$weekOffset нед." else "$weekOffset нед."
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                color = ru.nya.nyeios.ui.theme.NierDark,
                letterSpacing = 0.5.sp
            )
            Text(
                text = dateSubtitle,
                fontSize = 10.sp,
                fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                color = ru.nya.nyeios.ui.theme.NierDim
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Next button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(ru.nya.nyeios.ui.theme.NierPanel)
                    .border(1.dp, ru.nya.nyeios.ui.theme.NierDark)
                    .clickable { onNext() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "▶",
                    fontSize = 11.sp,
                    color = ru.nya.nyeios.ui.theme.NierDark,
                    fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily
                )
            }
        }
    }
}

@Composable
fun DaySelectorRow(
    days: List<DaySchedule>,
    selectedIndex: Int,
    onSelectDay: (Int) -> Unit
) {
    if (days.isEmpty()) return

    val dayCount = days.size
    val safeSelectedIndex = selectedIndex.coerceIn(0, dayCount - 1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ru.nya.nyeios.ui.theme.NierPanel)
            .border(width = 1.dp, color = ru.nya.nyeios.ui.theme.NierDark)
    ) {
        days.forEachIndexed { index, day ->
            val isSelected = index == safeSelectedIndex
            val cleanDayDate = Regex("""(\d{1,2}\.\d{1,2})""").find(day.dateString.ifEmpty { day.dayTitle })?.value
                ?: day.dateString.take(5)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectDay(index) }
                    .padding(vertical = 6.dp, horizontal = 2.dp)
                    .drawBehind {
                        // Vertical separator between tabs
                        if (index < dayCount - 1) {
                            drawLine(
                                color = ru.nya.nyeios.ui.theme.NierBorderLight,
                                start = Offset(size.width, 4f),
                                end = Offset(size.width, size.height - 4f),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        // Active bottom indicator bar
                        if (isSelected) {
                            drawRect(
                                color = ru.nya.nyeios.ui.theme.NierBlue,
                                topLeft = Offset(0f, size.height - 2.5.dp.toPx()),
                                size = androidx.compose.ui.geometry.Size(size.width, 2.5.dp.toPx())
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = day.dayName.uppercase(),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp,
                        color = if (isSelected) ru.nya.nyeios.ui.theme.NierDark else ru.nya.nyeios.ui.theme.NierDim
                    )
                    if (cleanDayDate.isNotEmpty()) {
                        Text(
                            text = cleanDayDate,
                            fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                            fontSize = 9.sp,
                            color = if (isSelected) ru.nya.nyeios.ui.theme.NierBlue else ru.nya.nyeios.ui.theme.NierDim
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LessonCard(
    lesson: LessonItem,
    isToday: Boolean,
    onRoomClick: (String) -> Unit = {}
) {
    val progressInfo by produceState<LessonProgressInfo?>(
        initialValue = LessonTimeUtils.computeLessonProgress(lesson.time, isToday),
        key1 = lesson.time,
        key2 = isToday
    ) {
        while (true) {
            value = LessonTimeUtils.computeLessonProgress(lesson.time, isToday)
            delay(1000L)
        }
    }

    val isOngoing = progressInfo?.isOngoing == true

    val animatedProgress by animateFloatAsState(
        targetValue = progressInfo?.progress ?: 0f,
        animationSpec = tween(durationMillis = 500),
        label = "lesson_progress"
    )

    val dotColor = when (lesson.type) {
        LessonType.LECTURE -> ru.nya.nyeios.ui.theme.NierBlue
        LessonType.SEMINAR, LessonType.PRACTICE -> ru.nya.nyeios.ui.theme.NierGreen
        LessonType.LAB -> ru.nya.nyeios.ui.theme.NierAmber
        LessonType.EXAM -> ru.nya.nyeios.ui.theme.NierRed
        LessonType.OTHER -> ru.nya.nyeios.ui.theme.NierPurple
    }

    // Split time into slot or bounds
    val timeParts = lesson.time.split("-").map { it.trim() }
    val startTime = timeParts.getOrNull(0) ?: lesson.time
    val endTime = timeParts.getOrNull(1) ?: ""

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ru.nya.nyeios.ui.theme.NierPanelAlt)
            .border(
                width = if (isOngoing) 1.5.dp else 1.dp,
                color = if (isOngoing) ru.nya.nyeios.ui.theme.NierGreen else ru.nya.nyeios.ui.theme.NierBorderLight
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: Time & Slot Info with right divider
                Box(
                    modifier = Modifier
                        .width(62.dp)
                        .drawBehind {
                            drawLine(
                                color = ru.nya.nyeios.ui.theme.NierBorderLight,
                                start = Offset(size.width, 0f),
                                end = Offset(size.width, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = startTime,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                            color = ru.nya.nyeios.ui.theme.NierDark
                        )
                        if (endTime.isNotEmpty()) {
                            Text(
                                text = "–",
                                fontSize = 8.sp,
                                fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                                color = ru.nya.nyeios.ui.theme.NierDim
                            )
                            Text(
                                text = endTime,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                                color = ru.nya.nyeios.ui.theme.NierDark
                            )
                        }
                    }
                }

                // Right Column: Lesson Details
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Type Row + Live Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(dotColor)
                            )
                            Text(
                                text = lesson.type.title.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                color = ru.nya.nyeios.ui.theme.NierDim,
                                letterSpacing = 0.8.sp
                            )
                            if (lesson.subgroup.isNotEmpty()) {
                                Text(
                                    text = "· ${lesson.subgroup}",
                                    fontSize = 9.sp,
                                    fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                                    color = ru.nya.nyeios.ui.theme.NierDim
                                )
                            }
                        }

                        if (isOngoing) {
                            Row(
                                modifier = Modifier
                                    .background(ru.nya.nyeios.ui.theme.NierGreen.copy(alpha = 0.18f))
                                    .border(1.dp, ru.nya.nyeios.ui.theme.NierGreen)
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(ru.nya.nyeios.ui.theme.NierGreen)
                                )
                                Text(
                                    text = "СЕЙЧАС",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                    color = ru.nya.nyeios.ui.theme.NierGreen,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    // Subject Name
                    Text(
                        text = lesson.subject,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                        color = ru.nya.nyeios.ui.theme.NierDark,
                        lineHeight = 16.sp
                    )

                    // Meta: Teacher & Room Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = lesson.teacher.ifEmpty { "Преподаватель не указан" },
                            fontSize = 10.sp,
                            fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                            color = ru.nya.nyeios.ui.theme.NierDim,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1
                        )

                        if (lesson.room.isNotEmpty()) {
                            val isDotRoom = lesson.room.contains("ДО", ignoreCase = true) ||
                                    lesson.room.contains("ДОТ", ignoreCase = true) ||
                                    lesson.room.contains("ЭОР", ignoreCase = true)

                            Box(
                                modifier = Modifier
                                    .background(if (isDotRoom) ru.nya.nyeios.ui.theme.NierHighlight else ru.nya.nyeios.ui.theme.NierDark)
                                    .clickable { onRoomClick(lesson.room) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "📍 ${lesson.room}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                    color = ru.nya.nyeios.ui.theme.NierSelectionText
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Progress bar for ongoing lesson
            if (isOngoing && animatedProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(ru.nya.nyeios.ui.theme.NierGreen.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(ru.nya.nyeios.ui.theme.NierGreen)
                    )
                }
            }
        }
    }
}

private fun checkIfOngoing(timeRangeStr: String): Boolean {
    return LessonTimeUtils.checkIfOngoing(timeRangeStr)
}
