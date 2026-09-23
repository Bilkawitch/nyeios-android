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
import androidx.compose.ui.draw.rotate
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
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = LectureBlue)
                                Text(
                                    text = "Загрузка расписания...",
                                    color = TextMuted,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    is ScheduleUiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(ExamRedBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = ExamRed,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Text(
                                    text = uiState.message,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 22.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = onRefresh,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, ObsidianBorder),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Text("Повторить", fontWeight = FontWeight.SemiBold)
                                    }

                                    Button(
                                        onClick = onOpenLogin,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = LectureBlue,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Text("Войти в аккаунт", fontWeight = FontWeight.Bold)
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
                                // Empty state
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "✨",
                                            fontSize = 40.sp
                                        )
                                        Text(
                                            text = "Пар нет!",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "На этот день занятия не запланированы. Можно отдыхать!",
                                            color = TextMuted,
                                            fontSize = 14.sp
                                        )
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
        "${schedule.startDate} — ${schedule.endDate}"
    } else {
        "Расписание недели"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = onPrev,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Предыдущая неделя",
                tint = TextPrimary,
                modifier = Modifier.size(18.dp)
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
                color = if (weekOffset == 0) LectureBlue else TextPrimary
            )
            Text(
                text = dateSubtitle,
                fontSize = 11.sp,
                color = TextMuted
            )
        }

        IconButton(
            onClick = onNext,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Следующая неделя",
                tint = TextPrimary,
                modifier = Modifier.size(18.dp)
            )
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

    val tabSpacing = 6.dp
    val dayCount = days.size
    val safeSelectedIndex = selectedIndex.coerceIn(0, dayCount - 1)
    val density = LocalDensity.current
    var rowHeightDp by remember { mutableStateOf(52.dp) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        val tabWidth = (maxWidth - tabSpacing * (dayCount - 1)) / dayCount
        val indicatorOffset by animateDpAsState(
            targetValue = (tabWidth + tabSpacing) * safeSelectedIndex,
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = 380f
            ),
            label = "day_tab_indicator_offset"
        )

        // 1. Inactive background slots
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tabSpacing)
        ) {
            days.forEach { _ ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(rowHeightDp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianCard)
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                )
            }
        }

        // 2. Sliding active indicator pill
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .height(rowHeightDp)
                .clip(RoundedCornerShape(12.dp))
                .background(LectureBlue)
                .border(1.dp, LectureBlue, RoundedCornerShape(12.dp))
        )

        // 3. Foreground interactive tabs with smooth cross-fading colors and tactile scale pop
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    if (size.height > 0) {
                        rowHeightDp = with(density) { size.height.toDp() }
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(tabSpacing)
        ) {
            days.forEachIndexed { index, day ->
                val isSelected = index == safeSelectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else TextPrimary,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    label = "tab_text_color"
                )
                val dateColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White.copy(alpha = 0.85f) else TextMuted,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    label = "tab_date_color"
                )
                val dotColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else LiveBadgeColor,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    label = "tab_dot_color"
                )
                val tabScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.0f else 0.96f,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                    label = "tab_scale"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            scaleX = tabScale
                            scaleY = tabScale
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onSelectDay(index)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = day.dayName,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = textColor
                            )
                            if (day.isToday) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                            }
                        }
                        val cleanDayDate = Regex("""(\d{1,2}\.\d{1,2})""").find(day.dateString.ifEmpty { day.dayTitle })?.value ?: day.dateString.take(5)
                        if (cleanDayDate.isNotEmpty()) {
                            Text(
                                text = cleanDayDate,
                                fontSize = 10.sp,
                                color = dateColor
                            )
                        }
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

    val (badgeColor, badgeBg) = when (lesson.type) {
        LessonType.LECTURE -> Pair(LectureBlue, LectureBlueBg)
        LessonType.SEMINAR, LessonType.PRACTICE -> Pair(PracticeGreen, PracticeGreenBg)
        LessonType.LAB -> Pair(LabAmber, LabAmberBg)
        LessonType.EXAM -> Pair(ExamRed, ExamRedBg)
        LessonType.OTHER -> Pair(OtherPurple, OtherPurpleBg)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isOngoing) ObsidianCardSelected else ObsidianCard)
            .border(
                1.dp,
                if (isOngoing) LiveGlowBorder else ObsidianBorder,
                RoundedCornerShape(14.dp)
            )
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row: Time & Type Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ObsidianSurface)
                            .border(1.dp, ObsidianBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = lesson.time,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }

                    if (isOngoing) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(LiveBadgeColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "СЕЙЧАС ИДЁТ",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = LiveBadgeColor
                            )
                        }
                    }
                }

                // Lesson Type
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = lesson.type.title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }

            // Subject Title
            Text(
                text = lesson.subject,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                lineHeight = 20.sp
            )

            // Details: Teacher and Room
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (lesson.teacher.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = lesson.teacher,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                if (lesson.room.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ObsidianSurface)
                            .border(1.dp, ObsidianBorder, RoundedCornerShape(6.dp))
                            .clickable { onRoomClick(lesson.room) }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Показать кабинет на карте",
                            tint = LectureBlue,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = lesson.room,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
            }

            if (lesson.subgroup.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ObsidianSurface)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = lesson.subgroup,
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            // Real-time loading bar for lesson
            val currentProgress = progressInfo
            if (currentProgress != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Time stats: Elapsed & Remaining / Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Прошло:",
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = currentProgress.elapsedText,
                                fontSize = 11.sp,
                                color = if (currentProgress.progress > 0f) PracticeGreen else TextMuted,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (currentProgress.isOngoing) {
                                Text(
                                    text = "Осталось:",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = currentProgress.remainingText,
                                fontSize = 11.sp,
                                color = when {
                                    currentProgress.isOngoing -> TextPrimary
                                    currentProgress.isUpcoming -> LectureBlue
                                    currentProgress.isFinished -> PracticeGreen
                                    else -> TextSecondary
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Loading / Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(ObsidianBg)
                            .border(0.5.dp, ObsidianBorder, RoundedCornerShape(3.dp))
                    ) {
                        if (animatedProgress > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(PracticeGreen)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun checkIfOngoing(timeRangeStr: String): Boolean {
    return LessonTimeUtils.checkIfOngoing(timeRangeStr)
}
