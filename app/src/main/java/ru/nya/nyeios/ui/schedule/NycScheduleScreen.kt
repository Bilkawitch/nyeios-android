package ru.nya.nyeios.ui.schedule

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import ru.nya.nyeios.R
import ru.nya.nyeios.ui.common.isAuthRelatedMessage
import ru.nya.nyeios.ui.common.localizeErrorMessage
import ru.nya.nyeios.ui.common.localizedLessonType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.nya.nyeios.data.floormap.FloorMapRepository
import ru.nya.nyeios.data.model.DaySchedule
import ru.nya.nyeios.data.model.LessonItem
import ru.nya.nyeios.data.model.LessonType
import ru.nya.nyeios.data.model.ScheduleUiState
import ru.nya.nyeios.data.model.WeekSchedule
import ru.nya.nyeios.ui.floormap.FloorMapBottomSheet
import ru.nya.nyeios.ui.floormap.NycMapSheet
import ru.nya.nyeios.ui.theme.isNycModern
import ru.nya.nyeios.ui.theme.NierAmber
import ru.nya.nyeios.ui.theme.NierBlue
import ru.nya.nyeios.ui.theme.NierBorderLight
import ru.nya.nyeios.ui.theme.NierDim
import ru.nya.nyeios.ui.theme.NierGreen
import ru.nya.nyeios.ui.theme.NierPurple
import ru.nya.nyeios.ui.theme.NierRed
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.NycCyanDk
import ru.nya.nyeios.ui.theme.NycCyanHi
import ru.nya.nyeios.ui.theme.NycLed
import ru.nya.nyeios.ui.theme.NycMonoFamily
import ru.nya.nyeios.ui.theme.NycSansFamily
import ru.nya.nyeios.ui.theme.NycShapes
import ru.nya.nyeios.ui.theme.UiPreferencesManager
import ru.nya.nyeios.ui.theme.nycBadgeBg
import ru.nya.nyeios.ui.theme.nycBadgeShape
import ru.nya.nyeios.ui.theme.nycCard
import ru.nya.nyeios.ui.theme.nycRaised
import ru.nya.nyeios.ui.theme.rememberUiInfiniteTransition
import ru.nya.nyeios.ui.theme.nycWell

// Parallel NyC-modern schedule interface from mockup nyeios_redesign.html (screen 2).
// Same state, same callbacks, same business rules as ScheduleScreen; only rendering
// differs. Legacy ScheduleScreen is untouched.

// Mockup fixed tokens (match CSS px at 360dp width).
private val nycText = Color(0xFFDBE4F0)
private val nycMuted = Color(0xFF8794A7)
private val nycFaint = Color(0xFF5B6A7E)

@Composable
fun NycScheduleScreen(
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

    Column(modifier = modifier.fillMaxSize()) {
        val schedule = (uiState as? ScheduleUiState.Success)?.schedule

        NycWeekRow(
            weekOffset = weekOffset,
            schedule = schedule,
            onPrev = onPrevWeek,
            onNext = onNextWeek,
            onToday = onCurrentWeek
        )

        val daysList = schedule?.days ?: emptyList()
        if (daysList.isNotEmpty()) {
            NycDayRow(
                days = daysList,
                selectedIndex = selectedDayIndex.coerceIn(0, (daysList.size - 1).coerceAtLeast(0)),
                onSelectDay = onSelectDay
            )
        }

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
                                color = NycCyan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = stringResource(R.string.schedule_loading),
                                fontFamily = NycMonoFamily,
                                fontWeight = FontWeight.Bold,
                                color = nycMuted,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                is ScheduleUiState.NotLoggedIn -> {
                    Box(modifier = Modifier.fillMaxSize())
                }

                is ScheduleUiState.Error -> {
                    NycScheduleError(
                        message = uiState.message,
                        onRefresh = onRefresh,
                        onOpenLogin = onOpenLogin
                    )
                }

                is ScheduleUiState.Success -> {
                    val safeDayIndex =
                        selectedDayIndex.coerceIn(0, (uiState.schedule.days.size - 1).coerceAtLeast(0))

                    // Same day/week slide as the YoRHa screen so "Анимации переходов" has an
                    // effect in the NyC theme too; gated() collapses it to an instant swap.
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
                                    animationSpec = UiPreferencesManager.gated(tween(animDuration, easing = FastOutSlowInEasing)),
                                    initialOffsetX = { fullWidth -> (fullWidth * slideFraction).toInt() }
                                ) + fadeIn(
                                    animationSpec = UiPreferencesManager.gated(tween(animDuration, easing = LinearEasing))
                                )).togetherWith(
                                    slideOutHorizontally(
                                        animationSpec = UiPreferencesManager.gated(tween(animDuration, easing = FastOutSlowInEasing)),
                                        targetOffsetX = { fullWidth -> -(fullWidth * slideFraction).toInt() }
                                    ) + fadeOut(
                                        animationSpec = UiPreferencesManager.gated(tween((animDuration * 0.75f).toInt(), easing = LinearEasing))
                                    )
                                )
                            } else {
                                (slideInHorizontally(
                                    animationSpec = UiPreferencesManager.gated(tween(animDuration, easing = FastOutSlowInEasing)),
                                    initialOffsetX = { fullWidth -> -(fullWidth * slideFraction).toInt() }
                                ) + fadeIn(
                                    animationSpec = UiPreferencesManager.gated(tween(animDuration, easing = LinearEasing))
                                )).togetherWith(
                                    slideOutHorizontally(
                                        animationSpec = UiPreferencesManager.gated(tween(animDuration, easing = FastOutSlowInEasing)),
                                        targetOffsetX = { fullWidth -> (fullWidth * slideFraction).toInt() }
                                    ) + fadeOut(
                                        animationSpec = UiPreferencesManager.gated(tween((animDuration * 0.75f).toInt(), easing = LinearEasing))
                                    )
                                )
                            }.using(SizeTransform(clip = false))
                        },
                        label = "nyc_day_schedule_transition",
                        modifier = Modifier.fillMaxSize()
                    ) { (_, dayIdx) ->
                        val currentDay = uiState.schedule.days.getOrNull(dayIdx)

                        if (currentDay == null || currentDay.lessons.isEmpty()) {
                            val allDaysEmpty = uiState.schedule.days.isNotEmpty() &&
                                uiState.schedule.days.all { it.lessons.isEmpty() }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .nycCard()
                                        .padding(vertical = 20.dp, horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (allDaysEmpty) stringResource(R.string.schedule_empty_week_banner)
                                        else stringResource(R.string.schedule_empty_day_banner),
                                        fontFamily = NycMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.sp,
                                        color = nycMuted,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            val todayDayMonth = remember {
                                try {
                                    java.time.LocalDate.now(LessonTimeUtils.moscowZone)
                                        .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM"))
                                } catch (e: Exception) {
                                    ""
                                }
                            }
                            val isDayToday = currentDay.isToday || (
                                uiState.schedule.offsetWeeks == 0 &&
                                    todayDayMonth.isNotEmpty() &&
                                    (currentDay.dateString.contains(todayDayMonth) ||
                                        currentDay.dayTitle.contains(todayDayMonth))
                                )

                            val lessons = currentDay.lessons
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = UiPreferencesManager.listSpace(10.dp)),
                                verticalArrangement = Arrangement.spacedBy(UiPreferencesManager.listSpace(10.dp))
                            ) {
                                // Slim day summary (app-only element, kept minimal per Operate).
                                item {
                                    val dayNameUpper =
                                        localizedDayName(currentDay.dayTitle.substringBefore(' '))
                                    val cacheStr = if (uiState.schedule.isCached) stringResource(R.string.schedule_cache_badge) else stringResource(R.string.schedule_network_badge)
                                    val summary =
                                        "$dayNameUpper · ${LessonTimeUtils.formatLessonCount(lessons.size)}" +
                                            " · $cacheStr"
                                    Text(
                                        text = summary,
                                        fontFamily = NycMonoFamily,
                                        fontSize = 9.sp,
                                        letterSpacing = 1.2.sp,
                                        color = nycFaint,
                                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                                    )
                                }

                                itemsIndexed(lessons) { index, lesson ->
                                    if (index > 0) {
                                        ScheduleTransferItem(
                                            prevLesson = lessons[index - 1],
                                            nextLesson = lesson
                                        )
                                    }

                                    val previousRoom = if (index > 0) {
                                        lessons.subList(0, index)
                                            .lastOrNull { it.room.isNotBlank() }?.room
                                    } else {
                                        null
                                    }

                                    NycLessonCard(
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

        if (floorMapTargetRoom != null) {
            if (isNycModern) {
                NycMapSheet(
                    targetRoomQuery = floorMapTargetRoom!!,
                    fromRoomQuery = floorMapFromRoom,
                    onDismiss = {
                        floorMapTargetRoom = null
                        floorMapFromRoom = null
                    }
                )
            } else {
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
    }
}

@Composable
private fun NycScheduleError(
    message: String,
    onRefresh: () -> Unit,
    onOpenLogin: () -> Unit
) {
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
                .nycCard()
                .padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.system_alert_caps),
                fontFamily = NycSansFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = NierRed
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = localizeErrorMessage(message),
                color = nycText,
                fontSize = 13.sp,
                fontFamily = NycSansFamily,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .nycRaised(11.dp)
                        .clickable { onRefresh() }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.action_retry_caps),
                        fontFamily = NycSansFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        color = nycText
                    )
                }

                val isAuthIssue = isAuthRelatedMessage(message)
                if (isAuthIssue) {
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(NycCyan.copy(alpha = 0.12f))
                            .border(1.dp, NycCyan.copy(alpha = 0.34f), RoundedCornerShape(11.dp))
                            .clickable { onOpenLogin() }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.action_login_account),
                            fontFamily = NycSansFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp,
                            color = NycCyan
                        )
                    }
                }
            }
        }
    }
}

// ---------- Week row: weeksel 48px + today button ----------

@Composable
private fun NycWeekRow(
    weekOffset: Int,
    schedule: WeekSchedule?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    fun short(date: String): String {
        val m = Regex("""(\d{1,2}\.\d{1,2})""").find(date)
        return m?.value ?: date.take(5)
    }
    val range = if (schedule != null && schedule.startDate.isNotEmpty()) {
        "${short(schedule.startDate)} — ${short(schedule.endDate)}"
    } else {
        ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .nycCard(14.dp)
                .padding(start = 13.dp, end = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .nycWell(11.dp)
                        .clickable { onPrev() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "‹", fontSize = 16.sp, color = nycMuted)
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .nycWell(11.dp)
                        .clickable { onNext() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "›", fontSize = 16.sp, color = nycMuted)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (weekOffset == 0) stringResource(R.string.schedule_current_week).uppercase() else stringResource(R.string.schedule_week_fmt, weekOffset).uppercase(),
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.5.sp,
                    letterSpacing = 1.5.sp,
                    color = nycFaint
                )
                Text(
                    text = range,
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = nycText,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(NycCyan.copy(alpha = 0.12f))
                .nycWell(14.dp)
                .border(1.dp, NycCyan.copy(alpha = 0.34f), RoundedCornerShape(14.dp))
                .clickable { onToday() }
                .padding(horizontal = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.schedule_today),
                fontFamily = NycMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 8.5.sp,
                letterSpacing = 1.sp,
                color = NycCyan
            )
        }
    }
}

// ---------- Day row: 7 cells, 56px ----------

@Composable
private fun NycDayRow(
    days: List<DaySchedule>,
    selectedIndex: Int,
    onSelectDay: (Int) -> Unit
) {
    if (days.isEmpty()) return
    val safeSelectedIndex = selectedIndex.coerceIn(0, days.size - 1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        days.forEachIndexed { index, day ->
            val isSelected = index == safeSelectedIndex
            val cleanDayDate =
                Regex("""(\d{1,2}\.\d{1,2})""").find(day.dateString.ifEmpty { day.dayTitle })?.value
                    ?: day.dateString.take(5)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .then(if (isSelected) Modifier.nycWell(12.dp) else Modifier)
                    .then(
                        if (isSelected) Modifier.border(
                            1.dp,
                            NycCyan.copy(alpha = 0.22f),
                            RoundedCornerShape(12.dp)
                        ) else Modifier
                    )
                    // Clip before clickable so the press ripple matches the 12dp chip corners
                    // instead of spilling out as a sharp-edged rectangle.
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectDay(index) }
                    .drawBehind {
                        if (day.isToday) {
                            drawRect(
                                color = NycCyanDk,
                                topLeft = Offset((size.width - 22.dp.toPx()) / 2f, 3.dp.toPx()),
                                size = Size(22.dp.toPx(), 2.dp.toPx())
                            )
                        }
                        if (isSelected) {
                            drawRect(
                                color = NycCyan,
                                topLeft = Offset(
                                    (size.width - 22.dp.toPx()) / 2f,
                                    size.height - 5.dp.toPx()
                                ),
                                size = Size(22.dp.toPx(), 2.dp.toPx())
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = localizedDayName(day.dayName),
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        letterSpacing = 1.sp,
                        color = when {
                            isSelected -> NycCyan
                            day.isToday -> nycMuted
                            else -> nycFaint
                        }
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = cleanDayDate,
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.5.sp,
                        color = if (isSelected) nycText else nycMuted
                    )
                }
            }
        }
    }
}

// ---------- Lesson card ----------

@Composable
private fun NycLessonCard(
    lesson: LessonItem,
    isToday: Boolean,
    onRoomClick: (String) -> Unit = {}
) {
    val fontDelta = UiPreferencesManager.scheduleFontDeltaSp

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
    val isFinished = progressInfo?.isFinished == true

    val animatedProgress by animateFloatAsState(
        targetValue = progressInfo?.progress ?: 0f,
        animationSpec = UiPreferencesManager.gated(tween(durationMillis = 500)),
        label = "nyc_lesson_progress"
    )

    val typeColor = when (lesson.type) {
        LessonType.LECTURE -> NierBlue
        LessonType.SEMINAR, LessonType.PRACTICE -> NierGreen
        LessonType.LAB -> NierAmber
        LessonType.EXAM -> NierRed
        LessonType.OTHER -> NierPurple
    }

    val timeParts = lesson.time.split("-").map { it.trim() }
    val startTime = timeParts.getOrNull(0) ?: lesson.time
    val endTime = timeParts.getOrNull(1) ?: ""

    val remainingLabel = progressInfo?.let {
        val s = it.remainingSeconds.coerceAtLeast(0)
        if (s >= 60) stringResource(R.string.lesson_left_min_fmt, s / 60) else stringResource(R.string.lesson_left_sec_fmt, s)
    } ?: ""

    val blink = rememberUiInfiniteTransition("nyc_led")
    val ledAlpha = blink?.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Reverse),
        label = "led"
    )?.value ?: 1f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isFinished && !isOngoing) 0.48f else 1f)
            .then(
                if (isOngoing) Modifier.shadow(
                    12.dp,
                    RoundedCornerShape(NycShapes.card),
                    ambientColor = NycCyan.copy(alpha = 0.13f),
                    spotColor = NycCyan.copy(alpha = 0.13f)
                ) else Modifier
            )
            .nycCard()
            .then(
                if (isOngoing) Modifier.border(
                    1.dp,
                    NycCyan.copy(alpha = 0.45f),
                    RoundedCornerShape(NycShapes.card)
                ) else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Time column 58dp
            Column(
                modifier = Modifier.width(58.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = startTime,
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = nycText,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (endTime.isNotEmpty()) {
                    Text(
                        text = endTime,
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        color = nycFaint,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                val displayLessonNumber = if (lesson.lessonNumber.isNotEmpty() && lesson.lessonNumber.toIntOrNull() in 1..8 && !lesson.lessonNumber.startsWith("0")) {
                    lesson.lessonNumber
                } else {
                    ru.nya.nyeios.data.parser.ScheduleParser.inferLessonNumber(lesson.time)
                }
                if (displayLessonNumber.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.lesson_pair_fmt, displayLessonNumber),
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 7.5.sp,
                        letterSpacing = 1.sp,
                        color = nycFaint,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            // Main column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (isOngoing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            NycLed(color = NycCyan.copy(alpha = ledAlpha), diameter = 6.dp)
                            Text(
                                text = stringResource(R.string.lesson_ongoing_badge),
                                fontFamily = NycMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                                letterSpacing = 1.5.sp,
                                color = NycCyan
                            )
                        }
                        Text(
                            text = remainingLabel,
                            fontFamily = NycMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 8.5.sp,
                            letterSpacing = 1.sp,
                            color = nycMuted
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NycTypeBadge(text = localizedLessonType(lesson.type).uppercase(), color = typeColor)
                    if (lesson.subgroup.isNotEmpty()) {
                        NycTypeBadge(text = lesson.subgroup.uppercase(), color = NierAmber)
                    }
                }

                Text(
                    text = lesson.subject,
                    fontFamily = NycSansFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = (13 + fontDelta).sp,
                    lineHeight = (17 + fontDelta).sp,
                    color = if (isOngoing) Color.White else nycText,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = lesson.teacher.ifEmpty { stringResource(R.string.lesson_no_teacher) },
                        fontFamily = NycSansFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        color = nycMuted,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (lesson.room.isNotEmpty()) {
                        val isDotRoom = lesson.room.contains("ДО", ignoreCase = true) ||
                            lesson.room.contains("ДОТ", ignoreCase = true) ||
                            lesson.room.contains("ЭОР", ignoreCase = true)
                        val roomObj = remember(lesson.room) { FloorMapRepository.findRoom(lesson.room) }
                        // Mockup chip format: "ауд. 438". Legacy keeps "438 / 4Э".
                        val roomBadgeText = if (roomObj != null && !isDotRoom) {
                            stringResource(R.string.lesson_room_prefix, lesson.room)
                        } else {
                            lesson.room
                        }
                        val chipActive = isOngoing && !isDotRoom
                        Box(
                            modifier = Modifier
                                .height(30.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    if (chipActive) {
                                        Modifier
                                            .background(NycCyan.copy(alpha = 0.12f))
                                            .border(
                                                1.dp,
                                                NycCyan.copy(alpha = 0.34f),
                                                RoundedCornerShape(10.dp)
                                            )
                                    } else {
                                        Modifier
                                            .background(Color(150, 185, 235, alpha = 14))
                                            .border(
                                                1.dp,
                                                Color(150, 185, 235, alpha = 19),
                                                RoundedCornerShape(10.dp)
                                            )
                                    }
                                )
                                .clickable { onRoomClick(lesson.room) }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (chipActive) NycCyan else nycMuted,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = roomBadgeText,
                                    fontFamily = NycMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (chipActive) NycCyan else nycMuted,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                if (isOngoing && animatedProgress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(5.dp)
                            .nycWell(3.dp)
                            .clip(RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(NycCyanDk, NycCyanHi)
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NycTypeBadge(text: String, color: Color) {
    Row(
        modifier = Modifier
            .height(20.dp)
            .clip(nycBadgeShape())
            .background(nycBadgeBg(color))
            .border(1.dp, color.copy(alpha = 0.25f), nycBadgeShape())
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        NycLed(color = color, diameter = 5.dp)
        Text(
            text = text,
            fontFamily = NycMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 7.8.sp,
            letterSpacing = 1.sp,
            color = color,
            maxLines = 1
        )
    }
}
