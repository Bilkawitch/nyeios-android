package ru.nya.nyeios.ui.curriculum

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nya.nyeios.data.model.CurriculumControlType
import ru.nya.nyeios.data.model.CurriculumSubject
import ru.nya.nyeios.data.model.CurriculumTerm
import ru.nya.nyeios.data.model.CurriculumUiState
import ru.nya.nyeios.ui.theme.ExamRed
import ru.nya.nyeios.ui.theme.ExamRedBg
import ru.nya.nyeios.ui.theme.LabAmber
import ru.nya.nyeios.ui.theme.LabAmberBg
import ru.nya.nyeios.ui.theme.LectureBlue
import ru.nya.nyeios.ui.theme.LectureBlueBg
import ru.nya.nyeios.ui.theme.LiveBadgeColor
import ru.nya.nyeios.ui.theme.ObsidianBorder
import ru.nya.nyeios.ui.theme.ObsidianCard
import ru.nya.nyeios.ui.theme.ObsidianSurface
import ru.nya.nyeios.ui.theme.OtherPurple
import ru.nya.nyeios.ui.theme.OtherPurpleBg
import ru.nya.nyeios.ui.theme.PracticeGreen
import ru.nya.nyeios.ui.theme.PracticeGreenBg
import ru.nya.nyeios.ui.theme.TextMuted
import ru.nya.nyeios.ui.theme.TextPrimary
import ru.nya.nyeios.ui.theme.TextSecondary

@Composable
fun CurriculumScreen(
    uiState: CurriculumUiState,
    onSelectTerm: (Int) -> Unit,
    onRefresh: () -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is CurriculumUiState.Loading -> {
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
                            text = "Загрузка успеваемости (БРС)...",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            is CurriculumUiState.Error -> {
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
                            modifier = Modifier.padding(horizontal = 16.dp)
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

            is CurriculumUiState.Success -> {
                if (uiState.terms.isEmpty()) {
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
                            Text(text = "🎓", fontSize = 40.sp)
                            Text(
                                text = "Данные об успеваемости отсутствуют",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Не удалось найти записи в учебном плане. Попробуйте синхронизировать.",
                                color = TextMuted,
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    val activeTerm = uiState.terms.firstOrNull { it.termNum == uiState.selectedTermNum }
                        ?: uiState.terms.first()

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Semester selector tabs row
                        CurriculumTermsBar(
                            terms = uiState.terms,
                            selectedTermNum = activeTerm.termNum,
                            onSelectTerm = onSelectTerm
                        )

                        // Statistics summary card
                        CurriculumStatsHeader(term = activeTerm)

                        // Subjects list
                        if (activeTerm.subjects.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "В этом семестре нет зарегистрированных дисциплин.",
                                    color = TextMuted,
                                    fontSize = 14.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(activeTerm.subjects) { subject ->
                                    CurriculumSubjectCard(subject = subject)
                                }
                                item {
                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CurriculumTermsBar(
    terms: List<CurriculumTerm>,
    selectedTermNum: Int,
    onSelectTerm: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(terms) { term ->
            val isSelected = term.termNum == selectedTermNum
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) LectureBlue else ObsidianCard)
                    .border(
                        1.dp,
                        if (isSelected) LectureBlue else ObsidianBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectTerm(term.termNum) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = term.termTitle,
                        color = if (isSelected) Color.White else TextPrimary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    )

                    if (term.isCurrent) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) Color.White.copy(alpha = 0.25f) else LiveBadgeColor.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "Текущий",
                                color = if (isSelected) Color.White else LiveBadgeColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CurriculumStatsHeader(term: CurriculumTerm) {
    val totalSubjects = term.subjects.size
    val examsCount = term.subjects.count { it.controlCategory == CurriculumControlType.EXAM }
    val passedCount = totalSubjects - examsCount

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard(
            title = "Дисциплин",
            count = totalSubjects.toString(),
            color = LectureBlue,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Экзаменов",
            count = examsCount.toString(),
            color = ExamRed,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Зачётов",
            count = passedCount.toString(),
            color = PracticeGreen,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun StatCard(
    title: String,
    count: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = count,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
fun CurriculumSubjectCard(subject: CurriculumSubject) {
    var isExpanded by remember { mutableStateOf(false) }

    val (ctrlColor, ctrlBg) = when (subject.controlCategory) {
        CurriculumControlType.EXAM -> Pair(ExamRed, ExamRedBg)
        CurriculumControlType.GRADED_TEST -> Pair(LabAmber, LabAmberBg)
        CurriculumControlType.TEST -> Pair(PracticeGreen, PracticeGreenBg)
        CurriculumControlType.COURSEWORK -> Pair(OtherPurple, OtherPurpleBg)
        CurriculumControlType.OTHER -> Pair(LectureBlue, LectureBlueBg)
    }

    val gradeText = subject.grade.trim()
    val (gradeColor, gradeBg) = when {
        gradeText.contains("отлич", ignoreCase = true) -> Pair(LiveBadgeColor, LiveBadgeColor.copy(alpha = 0.15f))
        gradeText.contains("хорош", ignoreCase = true) -> Pair(LectureBlue, LectureBlueBg)
        gradeText.contains("зачт", ignoreCase = true) -> Pair(PracticeGreen, PracticeGreenBg)
        gradeText.contains("удовл", ignoreCase = true) -> Pair(LabAmber, LabAmberBg)
        gradeText.contains("незач", ignoreCase = true) || gradeText.contains("неуд", ignoreCase = true) -> Pair(ExamRed, ExamRedBg)
        else -> Pair(TextMuted, ObsidianSurface)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianCard)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
            .clickable { isExpanded = !isExpanded }
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row: Control badge and Grade badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Control type badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ctrlBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = subject.controlType,
                        color = ctrlColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Current Score Badge (if available)
                    if (subject.currentScore.isNotBlank() && subject.currentScore != "0") {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ObsidianSurface)
                                .border(1.dp, ObsidianBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "КТ: ${subject.currentScore}",
                                color = LiveBadgeColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Final Grade Badge
                    if (gradeText.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(gradeBg)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = gradeText,
                                color = gradeColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            text = "—",
                            color = TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Subject Title
            Text(
                text = subject.subject,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                lineHeight = 20.sp
            )

            // Primary Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${subject.hours} ч.${if (subject.zet.isNotBlank() && subject.zet != "0") " • ${subject.zet} ЗЕТ" else ""}",
                    color = TextMuted,
                    fontSize = 12.sp
                )

                if (subject.finalRating.isNotBlank() && subject.finalRating != "0") {
                    Text(
                        text = "Итог: ${subject.finalRating}",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 12.sp
                    )
                }
            }

            // Expanded BRS details accordion
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianSurface)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BrsDetailRow(label = "Текущий контроль (балл)", value = subject.currentScore.ifEmpty { "0" })
                    if (subject.currentRating.isNotBlank()) {
                        BrsDetailRow(label = "Рейтинг по текущей успеваемости", value = subject.currentRating)
                    }
                    if (subject.termRating.isNotBlank()) {
                        BrsDetailRow(label = "Семестровый рейтинг", value = subject.termRating)
                    }
                    BrsDetailRow(label = "Баллы на экзамене / зачёте", value = subject.examScore.ifEmpty { "0" })
                    BrsDetailRow(label = "Итоговый рейтинг", value = subject.finalRating.ifEmpty { "0" }, isHighlight = true)
                }
            }
        }
    }
}

@Composable
fun BrsDetailRow(
    label: String,
    value: String,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (isHighlight) TextPrimary else TextMuted
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isHighlight) LectureBlue else TextPrimary
        )
    }
}
