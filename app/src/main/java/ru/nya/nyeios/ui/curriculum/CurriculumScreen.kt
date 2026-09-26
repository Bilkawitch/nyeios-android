package ru.nya.nyeios.ui.curriculum

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import ru.nya.nyeios.ui.theme.LabAmber
import ru.nya.nyeios.ui.theme.LabAmberBg
import ru.nya.nyeios.ui.theme.LectureBlue
import ru.nya.nyeios.ui.theme.LectureBlueBg
import ru.nya.nyeios.ui.theme.LiveBadgeColor
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

            CurriculumUiState.NotLoggedIn -> {
                // Content is obscured by LoginFullscreenGate in MainActivity
                Box(modifier = Modifier.fillMaxSize())
            }

            is CurriculumUiState.Error -> {
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
            .background(ru.nya.nyeios.ui.theme.NierPanel)
            .border(1.dp, ru.nya.nyeios.ui.theme.NierBorderLight)
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(terms) { term ->
            val isSelected = term.termNum == selectedTermNum
            Box(
                modifier = Modifier
                    .background(if (isSelected) ru.nya.nyeios.ui.theme.NierBlue else ru.nya.nyeios.ui.theme.NierPanelAlt)
                    .border(
                        1.dp,
                        if (isSelected) ru.nya.nyeios.ui.theme.NierBlue else ru.nya.nyeios.ui.theme.NierBorder
                    )
                    .clickable { onSelectTerm(term.termNum) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = term.termTitle,
                    color = if (isSelected) Color.White else ru.nya.nyeios.ui.theme.NierDim,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                    fontSize = 11.sp
                )
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
            .background(ru.nya.nyeios.ui.theme.NierPanel)
            .border(1.dp, ru.nya.nyeios.ui.theme.NierBorderLight)
            .padding(vertical = 6.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            title = "Предметов",
            count = totalSubjects.toString(),
            color = ru.nya.nyeios.ui.theme.NierDark,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Зачётов",
            count = passedCount.toString(),
            color = ru.nya.nyeios.ui.theme.NierGreen,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Экзаменов",
            count = examsCount.toString(),
            color = ru.nya.nyeios.ui.theme.NierRed,
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
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = count,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                color = color
            )
            Text(
                text = title.uppercase(),
                fontSize = 8.sp,
                letterSpacing = 0.5.sp,
                fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                color = ru.nya.nyeios.ui.theme.NierDim
            )
        }
    }
}

@Composable
fun CurriculumSubjectCard(subject: CurriculumSubject) {
    var isExpanded by remember { mutableStateOf(false) }

    val (ctrlColor, ctrlBg) = when (subject.controlCategory) {
        CurriculumControlType.EXAM -> Pair(ru.nya.nyeios.ui.theme.NierRed, ru.nya.nyeios.ui.theme.NierRed.copy(alpha = 0.15f))
        CurriculumControlType.GRADED_TEST -> Pair(ru.nya.nyeios.ui.theme.NierAmber, ru.nya.nyeios.ui.theme.NierAmber.copy(alpha = 0.15f))
        CurriculumControlType.TEST -> Pair(ru.nya.nyeios.ui.theme.NierGreen, ru.nya.nyeios.ui.theme.NierGreen.copy(alpha = 0.15f))
        CurriculumControlType.COURSEWORK -> Pair(ru.nya.nyeios.ui.theme.NierPurple, ru.nya.nyeios.ui.theme.NierPurple.copy(alpha = 0.15f))
        CurriculumControlType.OTHER -> Pair(ru.nya.nyeios.ui.theme.NierBlue, ru.nya.nyeios.ui.theme.NierBlue.copy(alpha = 0.15f))
    }

    val gradeText = subject.grade.trim()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ru.nya.nyeios.ui.theme.NierPanelAlt)
            .border(1.dp, ru.nya.nyeios.ui.theme.NierBorderLight)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: Control type badge, Subject name, Score and Arrow
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Control Type Badge
                Box(
                    modifier = Modifier
                        .background(ctrlBg)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = subject.controlType,
                        color = ctrlColor,
                        fontSize = 9.sp,
                        fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Subject Name
                Text(
                    text = subject.subject,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                    color = ru.nya.nyeios.ui.theme.NierDark,
                    modifier = Modifier.weight(1f)
                )

                // Score / Grade display
                val scorePrimary = when {
                    subject.finalRating.isNotBlank() && subject.finalRating != "0" -> subject.finalRating
                    subject.currentScore.isNotBlank() && subject.currentScore != "0" -> subject.currentScore
                    subject.currentRating.isNotBlank() && subject.currentRating != "0" -> subject.currentRating
                    subject.termRating.isNotBlank() && subject.termRating != "0" -> subject.termRating
                    else -> null
                }

                val scoreLabel = when {
                    subject.finalRating.isNotBlank() && subject.finalRating != "0" -> "/100"
                    subject.currentScore.isNotBlank() && subject.currentScore != "0" -> " б."
                    else -> ""
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (gradeText.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(ctrlColor.copy(alpha = 0.12f))
                                .border(0.5.dp, ctrlColor)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = gradeText,
                                color = ctrlColor,
                                fontSize = 9.sp,
                                fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (scorePrimary != null) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = scorePrimary,
                                color = ru.nya.nyeios.ui.theme.NierDark,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily
                            )
                            if (scoreLabel.isNotEmpty()) {
                                Text(
                                    text = scoreLabel,
                                    color = ru.nya.nyeios.ui.theme.NierDim,
                                    fontSize = 8.sp,
                                    fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                                    modifier = Modifier.padding(bottom = 1.dp)
                                )
                            }
                        }
                    } else if (gradeText.isEmpty()) {
                        Text(
                            text = "—",
                            color = ru.nya.nyeios.ui.theme.NierDim,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily
                        )
                    }

                    Text(
                        text = if (isExpanded) "▲" else "▼",
                        color = ru.nya.nyeios.ui.theme.NierDim,
                        fontSize = 9.sp
                    )
                }
            }

            // Expanded details block
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ru.nya.nyeios.ui.theme.NierPanel)
                        .border(1.dp, ru.nya.nyeios.ui.theme.NierBorderLight)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BrsDetailRow(label = "Текущий контроль (балл):", value = subject.currentScore.ifEmpty { "0" })
                    if (subject.currentRating.isNotBlank()) {
                        BrsDetailRow(label = "Рейтинг текущей успеваемости:", value = subject.currentRating)
                    }
                    if (subject.termRating.isNotBlank()) {
                        BrsDetailRow(label = "Семестровый рейтинг:", value = subject.termRating)
                    }
                    BrsDetailRow(label = "Баллы на экзамене / зачёте:", value = subject.examScore.ifEmpty { "0" })
                    BrsDetailRow(label = "Итоговый рейтинг:", value = if (subject.finalRating.isNotBlank() && subject.finalRating != "0") subject.finalRating else "—", isHighlight = true)
                    if (gradeText.isNotEmpty()) {
                        BrsDetailRow(label = "Итоговая оценка:", value = gradeText, isHighlight = true)
                    }
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
