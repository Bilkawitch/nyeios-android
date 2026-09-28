package ru.nya.nyeios.ui.curriculum

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import ru.nya.nyeios.R
import ru.nya.nyeios.ui.common.localizeErrorMessage
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nya.nyeios.data.model.CurriculumControlType
import ru.nya.nyeios.data.model.CurriculumSubject
import ru.nya.nyeios.data.model.CurriculumTerm
import ru.nya.nyeios.data.model.CurriculumUiState
import ru.nya.nyeios.ui.theme.NierAmber
import ru.nya.nyeios.ui.theme.NierBlue
import ru.nya.nyeios.ui.theme.NierGreen
import ru.nya.nyeios.ui.theme.NierPurple
import ru.nya.nyeios.ui.theme.NierRed
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.NycLed
import ru.nya.nyeios.ui.theme.NycMonoFamily
import ru.nya.nyeios.ui.theme.NycSansFamily
import ru.nya.nyeios.ui.theme.nycBadgeBg
import ru.nya.nyeios.ui.theme.nycBadgeShape
import ru.nya.nyeios.ui.theme.nycCard
import ru.nya.nyeios.ui.theme.nycRaised
import ru.nya.nyeios.ui.theme.UiAnimatedVisibility
import ru.nya.nyeios.ui.theme.UiPreferencesManager
import ru.nya.nyeios.ui.theme.nycWell

// Parallel NyC-modern performance (БРС) interface from mockup
// nyeios_redesign.html (screen 5) and reference 5.png. Same state, same term
// selection, same score rules as CurriculumScreen; only rendering differs.
// Legacy CurriculumScreen is untouched.
//
// Mockup notes: department lines are absent from portal data, so the meta line
// shows hours/zet when present; cards stay expandable (tap) to preserve the
// score drill-down, rendered in Nyc well style.

// Mockup fixed tokens.
private val nycText = Color(0xFFDBE4F0)
private val nycMuted = Color(0xFF8794A7)
private val nycFaint = Color(0xFF5B6A7E)

@Composable
fun NycCurriculumScreen(
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
                        CircularProgressIndicator(color = NycCyan)
                        Text(
                            text = stringResource(R.string.curriculum_loading),
                            color = nycMuted,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            CurriculumUiState.NotLoggedIn -> {
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
                        Text(
                            text = localizeErrorMessage(uiState.message),
                            color = nycText,
                            fontSize = 13.sp,
                            fontFamily = NycSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 14.dp)
                        )
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
                            Text(
                                text = stringResource(R.string.curriculum_empty_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                fontFamily = NycSansFamily,
                                color = nycText
                            )
                            Text(
                                text = stringResource(R.string.curriculum_empty_desc),
                                color = nycMuted,
                                fontSize = 14.sp,
                                fontFamily = NycSansFamily,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    val activeTerm = uiState.terms.firstOrNull { it.termNum == uiState.selectedTermNum }
                        ?: uiState.terms.first()

                    Column(modifier = Modifier.fillMaxSize()) {
                        NycTermsRow(
                            terms = uiState.terms,
                            selectedTermNum = activeTerm.termNum,
                            onSelectTerm = onSelectTerm
                        )

                        NycSummaryPanel(term = activeTerm)

                        if (activeTerm.subjects.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.curriculum_empty_term),
                                    color = nycMuted,
                                    fontSize = 14.sp,
                                    fontFamily = NycSansFamily,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = UiPreferencesManager.listSpace(8.dp)),
                                verticalArrangement = Arrangement.spacedBy(UiPreferencesManager.listSpace(8.dp))
                            ) {
                                items(activeTerm.subjects) { subject ->
                                    NycSubjectCard(subject = subject)
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
}

// ---------- Semester row with right fade ----------

@Composable
private fun NycTermsRow(
    terms: List<CurriculumTerm>,
    selectedTermNum: Int,
    onSelectTerm: (Int) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(terms) { term ->
                val isSelected = term.termNum == selectedTermNum
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .then(if (isSelected) Modifier.nycWell(10.dp) else Modifier.nycRaised(10.dp))
                        .then(
                            if (isSelected) Modifier.border(
                                1.dp,
                                NycCyan.copy(alpha = 0.3f),
                                RoundedCornerShape(10.dp)
                            ) else Modifier
                        )
                        .clickable { onSelectTerm(term.termNum) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.semester_fmt, term.termNum).uppercase(),
                        color = if (isSelected) NycCyan else nycMuted,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = NycMonoFamily,
                        fontSize = 9.5.sp,
                        letterSpacing = 1.sp,
                        maxLines = 1
                    )
                }
            }
        }
        // Right fade (mockup semrow::after).
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(34.dp)
                .height(42.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF0B1119).copy(alpha = 0.95f))
                    )
                )
        )
    }
}

// ---------- Summary panel ----------

@Composable
private fun NycSummaryPanel(term: CurriculumTerm) {
    val totalSubjects = term.subjects.size
    val examsCount = term.subjects.count { it.controlCategory == CurriculumControlType.EXAM }
    val passedCount = totalSubjects - examsCount

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp)
            .nycCard()
            .padding(vertical = 13.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NycSum(value = "$totalSubjects", label = stringResource(R.string.curriculum_stat_subjects).uppercase(), color = nycText, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(34.dp)
                .background(Color(150, 185, 235, alpha = 36))
        )
        NycSum(value = "$passedCount", label = stringResource(R.string.curriculum_stat_tests).uppercase(), color = NierGreen, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(34.dp)
                .background(Color(150, 185, 235, alpha = 36))
        )
        NycSum(value = "$examsCount", label = stringResource(R.string.curriculum_stat_exams).uppercase(), color = NierRed, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun NycSum(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontFamily = NycMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 21.sp,
            color = color
        )
        Text(
            text = label,
            fontFamily = NycMonoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 7.5.sp,
            letterSpacing = 1.5.sp,
            color = nycFaint,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// ---------- Subject card ----------

@Composable
private fun NycSubjectCard(subject: CurriculumSubject) {
    var isExpanded by remember { mutableStateOf(false) }

    val ctrlColor = when (subject.controlCategory) {
        CurriculumControlType.EXAM -> NierRed
        CurriculumControlType.GRADED_TEST -> NierAmber
        CurriculumControlType.TEST -> NierGreen
        CurriculumControlType.COURSEWORK -> NierPurple
        CurriculumControlType.OTHER -> NierBlue
    }

    // Same primary-score priority as the legacy card.
    val scorePrimary = when {
        subject.finalRating.isNotBlank() && subject.finalRating != "0" -> subject.finalRating
        subject.currentScore.isNotBlank() && subject.currentScore != "0" -> subject.currentScore
        subject.currentRating.isNotBlank() && subject.currentRating != "0" -> subject.currentRating
        subject.termRating.isNotBlank() && subject.termRating != "0" -> subject.termRating
        else -> null
    }
    val scorePointsStr = stringResource(R.string.curriculum_score_points)
    val scoreLabel = when {
        subject.finalRating.isNotBlank() && subject.finalRating != "0" -> "/ 100"
        subject.currentScore.isNotBlank() && subject.currentScore != "0" -> scorePointsStr
        else -> ""
    }
    val scoreNum = scorePrimary?.toFloatOrNull()
    val barFill = when {
        scoreNum == null -> NierAmber
        scoreNum >= 75f -> NierGreen
        scoreNum >= 55f -> NierAmber
        else -> NierRed
    }
    val (markText, markColor) = shortMark(subject.grade)

    val hoursFmt = stringResource(R.string.curriculum_hours_fmt, subject.hours)
    val zetFmt = stringResource(R.string.curriculum_zet_fmt, subject.zet)
    val metaLine = buildString {
        if (subject.hours.isNotBlank()) append(hoursFmt)
        if (subject.hours.isNotBlank() && subject.zet.isNotBlank()) append(" · ")
        if (subject.zet.isNotBlank()) append(zetFmt)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .nycCard(14.dp)
            .clickable { isExpanded = !isExpanded }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Badge.
            Row(
                modifier = Modifier
                    .clip(nycBadgeShape())
                    .background(ctrlColor.copy(alpha = 0.10f))
                    .border(1.dp, ctrlColor.copy(alpha = 0.25f), nycBadgeShape())
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                NycLed(color = ctrlColor, diameter = 5.dp)
                Text(
                    text = subject.controlType.uppercase(),
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 7.8.sp,
                    letterSpacing = 1.sp,
                    color = ctrlColor,
                    maxLines = 1
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subject.subject,
                    fontFamily = NycSansFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = nycText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (metaLine.isNotEmpty()) {
                    Text(
                        text = metaLine,
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 8.sp,
                        letterSpacing = 1.sp,
                        color = nycFaint,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = scorePrimary ?: "—",
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = nycText
                    )
                    if (scoreLabel.isNotEmpty()) {
                        Text(
                            text = " $scoreLabel",
                            fontFamily = NycMonoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp,
                            color = nycFaint,
                            modifier = Modifier.padding(bottom = 1.dp)
                        )
                    }
                }
                // Progress bar only for /100 finals (mockup semantics); current
                // points use an unknown scale, so text alone is shown for them.
                if (scoreLabel == "/ 100" && scoreNum != null) {
                    Box(
                        modifier = Modifier
                            .width(58.dp)
                            .height(5.dp)
                            .padding(top = 0.dp)
                            .nycWell(3.dp)
                            .clip(RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((scoreNum / 100f).coerceIn(0f, 1f))
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(barFill)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(markColor.copy(alpha = 0.10f))
                    .border(1.dp, markColor.copy(alpha = 0.25f), RoundedCornerShape(7.dp))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = markText,
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.5.sp,
                    letterSpacing = 0.5.sp,
                    color = markColor
                )
            }
        }

        // Drill-down preserved from the legacy expandable card.
        UiAnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .nycWell(10.dp)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NycDetailRow(label = stringResource(R.string.curriculum_detail_current_score), value = subject.currentScore.ifEmpty { "0" })
                if (subject.currentRating.isNotBlank()) {
                    NycDetailRow(label = stringResource(R.string.curriculum_detail_current_rating), value = subject.currentRating)
                }
                if (subject.termRating.isNotBlank()) {
                    NycDetailRow(label = stringResource(R.string.curriculum_detail_term_rating), value = subject.termRating)
                }
                NycDetailRow(label = stringResource(R.string.curriculum_detail_exam_score), value = subject.examScore.ifEmpty { "0" })
                NycDetailRow(
                    label = stringResource(R.string.curriculum_detail_final_rating),
                    value = if (subject.finalRating.isNotBlank() && subject.finalRating != "0") subject.finalRating else "—",
                    highlight = true
                )
                if (subject.grade.trim().isNotEmpty()) {
                    NycDetailRow(label = stringResource(R.string.curriculum_detail_final_grade), value = subject.grade.trim(), highlight = true)
                }
            }
        }
    }
}

@Composable
private fun shortMark(grade: String): Pair<String, Color> {
    val g = grade.trim().lowercase()
    return when {
        g.isEmpty() -> "—" to nycFaint
        g.contains("отл") || g == "5" -> stringResource(R.string.curriculum_mark_excellent) to NierGreen
        g.contains("хор") || g == "4" -> stringResource(R.string.curriculum_mark_good) to NierAmber
        g.contains("удовл") || g == "3" -> stringResource(R.string.curriculum_mark_satisfactory) to NierAmber
        g.contains("зачт") -> stringResource(R.string.curriculum_mark_passed) to NierGreen
        g.contains("незач") || g.contains("неуд") || g == "2" -> stringResource(R.string.curriculum_mark_fail) to NierRed
        else -> grade.trim().uppercase().take(5) to nycFaint
    }
}

@Composable
private fun NycDetailRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = NycSansFamily,
            fontSize = 12.sp,
            color = nycMuted
        )
        Text(
            text = value,
            fontFamily = NycSansFamily,
            fontSize = 12.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold,
            color = if (highlight) NycCyan else nycText
        )
    }
}
