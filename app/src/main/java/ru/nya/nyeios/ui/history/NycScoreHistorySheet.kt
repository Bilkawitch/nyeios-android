package ru.nya.nyeios.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.nya.nyeios.R
import ru.nya.nyeios.data.history.ScoreHistoryRepository
import ru.nya.nyeios.data.history.ScoreHistoryValues
import ru.nya.nyeios.data.history.ScoreSeriesKey
import ru.nya.nyeios.ui.history.chart.CandlestickCanvas
import ru.nya.nyeios.ui.history.chart.ChartMath
import ru.nya.nyeios.ui.history.chart.HistoryChartPalette
import ru.nya.nyeios.ui.history.chart.OhlcCandle
import ru.nya.nyeios.ui.history.chart.ScoreSample
import ru.nya.nyeios.ui.history.chart.TrendLine
import ru.nya.nyeios.ui.history.chart.TrendLinesCanvas
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.NycLed
import ru.nya.nyeios.ui.theme.NycMonoFamily
import ru.nya.nyeios.ui.theme.NycSansFamily
import ru.nya.nyeios.ui.theme.nycCard
import ru.nya.nyeios.ui.theme.nycRaised
import ru.nya.nyeios.ui.theme.nycWell

// NyC-modern (Night Curator) full-screen score history: overview of all subject
// trend lines + per-subject candlestick drill-down. Designed after the NyC token
// hierarchy (15dp cards / 12dp wells / 9dp small wells) and the NyC palette
// (obsidian canvas 0xFF0A0F18, cyan accent). Candlestick colours are NEVER taken
// from the theme: they come strictly from HistoryChartPalette (green up / red down).

private val nycHistoryBg = Color(0xFF0A0F18)
private val nycHistoryHeaderBg = Color(0xFF0C121B)
private val nycHistoryText = Color(0xFFDBE4F0)
private val nycHistoryMuted = Color(0xFF8794A7)
private val nycHistoryFaint = Color(0xFF5B6A7E)

/** Цвет осей/сетки графиков (по контракту §4); свечи берут цвет из HistoryChartPalette. */
private val nycHistoryChartAxis = Color(0xFF1F2E45)

private const val NYC_SERIES_SEPARATOR = '\u0001'

/**
 * История одной серии (предмет + тип контроля) со всеми точками и дневными свечами.
 *
 * Серия без числовых баллов остаётся в списке (с пометкой `history_no_data_for_subject`),
 * но в график не попадает: null-сэмплы отфильтровываются в [nycLoadHistorySeries].
 */
private data class NycHistorySeries(
    val key: ScoreSeriesKey,
    val samples: List<ScoreSample>,
    val candles: List<OhlcCandle>
) {
    /**
     * Уникальный id строки: группа + семестр + предмет + тип контроля.
     * Семестр и группа обязательны в ключе — иначе при показе всех семестров
     * у одного предмета совпал бы ключ LazyColumn.
     */
    val id: String
        get() = key.group + NYC_SERIES_SEPARATOR +
            key.termNum + NYC_SERIES_SEPARATOR +
            key.subject + NYC_SERIES_SEPARATOR +
            key.controlType
    val firstValue: Double? get() = samples.firstOrNull()?.value
    val lastValue: Double? get() = samples.lastOrNull()?.value
    val rising: Boolean get() = (lastValue ?: 0.0) >= (firstValue ?: 0.0)

    /** Линия обзорного графика; null — если числовых точек нет. */
    val trendLine: TrendLine?
        get() = lastValue?.let { value ->
            TrendLine(
                key = key,
                label = key.subject,
                samples = samples,
                rising = (value >= (firstValue ?: value))
            )
        }
}

/** Число для подписи: целые без хвоста `.0`, дробные — один знак, точка как разделитель. */
private fun nycFormatScore(value: Double): String {
    val rounded = value.roundToInt()
    return if (abs(value - rounded) < 0.05) {
        rounded.toString()
    } else {
        String.format(Locale.ROOT, "%.1f", value)
    }
}

/**
 * Собирает серии для UI строго на IO-потоке.
 *
 * Берём серии текущей запомненной группы ([ScoreHistoryRepository.seriesKeys] с
 * `allGroups = false`); если группа ещё не запомнена/истории по ней нет — падаем
 * обратно на все группы. Затем фильтруем по выбранному семестру; если в этом
 * семестре истории нет, показываем всё, что есть (иначе экран пуст при живых данных).
 */
private fun nycLoadHistorySeries(
    repository: ScoreHistoryRepository,
    termNum: Int?,
    zone: ZoneId
): List<NycHistorySeries> = runCatching {
    val currentGroupKeys = repository.seriesKeys(allGroups = false)
    val baseKeys = if (currentGroupKeys.isNotEmpty()) currentGroupKeys else repository.seriesKeys()
    val termKeys = if (termNum == null) baseKeys else baseKeys.filter { it.termNum == termNum }
    val keys = if (termKeys.isNotEmpty()) termKeys else baseKeys

    keys.map { key ->
        val samples = repository.pointsFor(key)
            .mapNotNull { point ->
                ScoreHistoryValues.primaryValue(point.scores)?.let { value ->
                    ScoreSample(atMs = point.recordedAtMs, value = value)
                }
            }
            .sortedBy { it.atMs }
        NycHistorySeries(
            key = key,
            samples = samples,
            candles = ChartMath.candles(samples, zone)
        )
    }
}.getOrDefault(emptyList())

/**
 * Полноэкранная история баллов БРС в семействе NyC Modern.
 *
 * Два состояния: обзор (линии тренда всех серий + список предметов) и детально
 * (свечной график предмета с OHLC выбранной свечи). Данные перечитываются по
 * [ScoreHistoryRepository.revision]; чтение БД — на `Dispatchers.IO` в `produceState`.
 *
 * @param initialTermNum выбранный на экране БРС семестр (null — все предметы).
 */
@Composable
fun NycScoreHistorySheet(
    onDismiss: () -> Unit,
    initialTermNum: Int? = null
) {
    val context = LocalContext.current
    val repository = remember(context) { ScoreHistoryRepository.getInstance(context) }
    val revision by repository.revision.collectAsState()
    val zone = remember { ZoneId.systemDefault() }

    val loadedSeries by produceState<List<NycHistorySeries>?>(
        initialValue = null,
        key1 = revision,
        key2 = initialTermNum
    ) {
        value = withContext(Dispatchers.IO) {
            nycLoadHistorySeries(repository = repository, termNum = initialTermNum, zone = zone)
        }
    }

    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCandleIndex by rememberSaveable { mutableStateOf(-1) }

    val series = loadedSeries.orEmpty()
    val detail = detailId?.let { id -> series.firstOrNull { it.id == id } }

    val title = if (detail != null) detail.key.subject else stringResource(R.string.history_title)
    val subtitle = if (detail != null) {
        nycDetailSubtitle(detail)
    } else {
        nycOverviewSubtitle(series)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(nycHistoryBg)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                NycHistoryHeader(
                    title = title,
                    subtitle = subtitle,
                    showBack = detail != null,
                    onBack = {
                        detailId = null
                        selectedCandleIndex = -1
                    },
                    onClose = onDismiss
                )

                when {
                    loadedSeries == null -> NycHistoryLoading()

                    series.isEmpty() -> NycHistoryEmpty()

                    detail != null -> NycHistoryDetail(
                        series = detail,
                        zone = zone,
                        selectedCandleIndex = selectedCandleIndex,
                        onSelectCandle = { index -> selectedCandleIndex = index }
                    )

                    else -> NycHistoryOverview(
                        series = series,
                        onOpenSeries = { opened ->
                            detailId = opened.id
                            selectedCandleIndex = -1
                        }
                    )
                }
            }
        }
    }
}

// ---------- Header ----------

@Composable
private fun NycHistoryHeader(
    title: String,
    subtitle: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(nycHistoryHeaderBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showBack) {
                NycHistoryBackChip(onBack = onBack)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    fontFamily = NycSansFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    color = nycHistoryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 8.5.sp,
                    letterSpacing = 1.sp,
                    color = nycHistoryFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            NycHistoryCloseButton(onClose = onClose)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(nycHistoryChartAxis.copy(alpha = 0.75f))
        )
    }
}

@Composable
private fun NycHistoryBackChip(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .height(44.dp)
            .nycWell(12.dp)
            .clickable(role = Role.Button, onClick = onBack)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = NycCyan,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = stringResource(R.string.history_back),
            fontFamily = NycMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 0.8.sp,
            color = NycCyan,
            maxLines = 1
        )
    }
}

@Composable
private fun NycHistoryCloseButton(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .nycRaised(12.dp)
            .clickable(role = Role.Button, onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.history_close_cd),
            tint = nycHistoryMuted,
            modifier = Modifier.size(19.dp)
        )
    }
}

// ---------- Overview ----------

@Composable
private fun nycOverviewSubtitle(series: List<NycHistorySeries>): String {
    val allSubjects = stringResource(R.string.history_all_subjects)
    if (series.isEmpty()) return allSubjects
    val group = series.map { it.key.group }.filter { it.isNotBlank() }.distinct().singleOrNull()
    val term = series.map { it.key.termNum }.distinct().singleOrNull()
    return if (group != null && term != null) {
        stringResource(R.string.history_group_term_fmt, group, term)
    } else {
        allSubjects
    }
}

@Composable
private fun nycDetailSubtitle(series: NycHistorySeries): String {
    val group = series.key.group
    return if (group.isNotBlank()) {
        stringResource(R.string.history_group_term_fmt, group, series.key.termNum)
    } else {
        stringResource(R.string.history_all_subjects)
    }
}

@Composable
private fun NycHistoryLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = NycCyan, strokeWidth = 2.dp)
    }
}

@Composable
private fun NycHistoryEmpty() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .nycWell(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Timeline,
                    contentDescription = null,
                    tint = nycHistoryFaint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = stringResource(R.string.history_empty_title),
                fontFamily = NycSansFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = nycHistoryText,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.history_empty_desc),
                fontFamily = NycSansFamily,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = nycHistoryMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NycHistoryOverview(
    series: List<NycHistorySeries>,
    onOpenSeries: (NycHistorySeries) -> Unit
) {
    val lines = remember(series) { series.mapNotNull { it.trendLine } }
    val totalPoints = series.sumOf { it.samples.size }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "trend") {
            NycHistoryTrendCard(lines = lines, totalPoints = totalPoints)
        }
        item(key = "hint") {
            Text(
                text = stringResource(R.string.history_hint),
                fontFamily = NycSansFamily,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                color = nycHistoryFaint
            )
        }
        items(series, key = { item -> item.id }) { item ->
            NycHistorySeriesRow(
                series = item,
                onClick = { onOpenSeries(item) }
            )
        }
    }
}

@Composable
private fun NycHistoryTrendCard(
    lines: List<TrendLine>,
    totalPoints: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .nycCard(15.dp)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.history_points_fmt, totalPoints),
                fontFamily = NycMonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 8.5.sp,
                letterSpacing = 1.sp,
                color = nycHistoryFaint,
                maxLines = 1
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                NycHistoryLegend(
                    color = HistoryChartPalette.Rising,
                    label = stringResource(R.string.history_trend_up)
                )
                NycHistoryLegend(
                    color = HistoryChartPalette.Falling,
                    label = stringResource(R.string.history_trend_down)
                )
            }
        }

        if (lines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.history_no_data_for_subject),
                    fontFamily = NycSansFamily,
                    fontSize = 11.sp,
                    color = nycHistoryFaint,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            TrendLinesCanvas(
                lines = lines,
                lineColorFor = { line ->
                    // Строго фиксированная палитра: рост зелёный, падение красное.
                    if (line.rising) HistoryChartPalette.Rising else HistoryChartPalette.Falling
                },
                gridColor = nycHistoryChartAxis,
                height = 168.dp,
                labelStyle = TextStyle(
                    fontFamily = NycMonoFamily,
                    fontSize = 8.sp,
                    color = nycHistoryMuted
                )
            )
        }
    }
}

@Composable
private fun NycHistoryLegend(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        NycLed(color = color, diameter = 6.dp)
        Text(
            text = label.uppercase(),
            fontFamily = NycMonoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 7.5.sp,
            letterSpacing = 1.sp,
            color = nycHistoryFaint,
            maxLines = 1
        )
    }
}

@Composable
private fun NycHistorySeriesRow(
    series: NycHistorySeries,
    onClick: () -> Unit
) {
    val first = series.firstValue
    val last = series.lastValue
    val trendColor = when {
        first == null || last == null -> nycHistoryFaint
        series.rising -> HistoryChartPalette.Rising
        else -> HistoryChartPalette.Falling
    }
    val trendWord = stringResource(
        if (series.rising) R.string.history_trend_up else R.string.history_trend_down
    )
    val description = if (first != null && last != null) {
        series.key.subject + ", " +
            stringResource(R.string.history_value_fmt, nycFormatScore(last)) + ", " +
            trendWord
    } else {
        series.key.subject + ", " + stringResource(R.string.history_no_data_for_subject)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .nycWell(12.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        NycLed(color = trendColor, diameter = 8.dp)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = series.key.subject,
                fontFamily = NycSansFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                color = nycHistoryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            // Тип контроля — данные портала (Экзамен/Зачёт), как в карточке БРС.
            Text(
                text = series.key.controlType.uppercase(),
                fontFamily = NycMonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 7.5.sp,
                letterSpacing = 1.sp,
                color = nycHistoryFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (first != null && last != null) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.history_value_fmt, nycFormatScore(last)),
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = nycHistoryText,
                    maxLines = 1
                )
                Text(
                    text = stringResource(R.string.history_value_fmt, nycFormatScore(first)) +
                        " → " +
                        stringResource(R.string.history_value_fmt, nycFormatScore(last)),
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 8.5.sp,
                    color = trendColor,
                    maxLines = 1
                )
            }
        } else {
            Text(
                text = stringResource(R.string.history_no_data_for_subject),
                fontFamily = NycSansFamily,
                fontSize = 8.5.sp,
                lineHeight = 11.sp,
                color = nycHistoryFaint,
                textAlign = TextAlign.End,
                maxLines = 2,
                modifier = Modifier.widthIn(max = 78.dp)
            )
        }
    }
}

// ---------- Detail (candlesticks) ----------

@Composable
private fun NycHistoryDetail(
    series: NycHistorySeries,
    zone: ZoneId,
    selectedCandleIndex: Int,
    onSelectCandle: (Int) -> Unit
) {
    val candles = series.candles

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "meta") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = series.key.controlType.uppercase(),
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 8.5.sp,
                    letterSpacing = 1.sp,
                    color = nycHistoryFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = stringResource(R.string.history_points_fmt, series.samples.size),
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 8.5.sp,
                    letterSpacing = 1.sp,
                    color = nycHistoryFaint,
                    maxLines = 1
                )
            }
        }

        if (series.samples.size < 2 || candles.isEmpty()) {
            item(key = "no-data") {
                NycHistoryNoDataCard()
            }
        } else {
            item(key = "chart") {
                NycHistoryCandleCard(
                    series = series,
                    candles = candles,
                    zone = zone,
                    selectedCandleIndex = selectedCandleIndex,
                    onSelectCandle = onSelectCandle
                )
            }
        }
    }
}

@Composable
private fun NycHistoryNoDataCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .nycWell(12.dp)
            .padding(horizontal = 18.dp, vertical = 26.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.history_no_data_for_subject),
            fontFamily = NycSansFamily,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = nycHistoryMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NycHistoryCandleCard(
    series: NycHistorySeries,
    candles: List<OhlcCandle>,
    zone: ZoneId,
    selectedCandleIndex: Int,
    onSelectCandle: (Int) -> Unit
) {
    val safeSelectedIndex = selectedCandleIndex.takeIf { it in candles.indices }
    val displayCandle = candles[safeSelectedIndex ?: candles.lastIndex]
    val displayDate = ChartMath.axisDate(displayCandle.dayStartMs, zone)
    val trendColor = if (displayCandle.isRising) HistoryChartPalette.Rising else HistoryChartPalette.Falling
    val trendWord = stringResource(
        if (displayCandle.isRising) R.string.history_trend_up else R.string.history_trend_down
    )
    val candleDescription = stringResource(
        R.string.history_candle_cd,
        displayDate,
        nycFormatScore(displayCandle.open),
        nycFormatScore(displayCandle.high),
        nycFormatScore(displayCandle.low),
        nycFormatScore(displayCandle.close)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .nycCard(15.dp)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = displayDate,
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = nycHistoryText,
                    maxLines = 1
                )
                Text(
                    text = trendWord.uppercase(),
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 8.sp,
                    letterSpacing = 1.sp,
                    color = trendColor,
                    maxLines = 1
                )
            }
        }

        CandlestickCanvas(
            candles = candles,
            axisColor = nycHistoryChartAxis,
            labelStyle = TextStyle(
                fontFamily = NycMonoFamily,
                fontSize = 8.sp,
                color = nycHistoryMuted
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = candleDescription },
            height = 280.dp,
            selectedIndex = safeSelectedIndex,
            onSelectIndex = { index -> if (index != null) onSelectCandle(index) }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(nycHistoryChartAxis.copy(alpha = 0.55f))
        )

        // OHLC выбранной свечи (или последней, если выбор пуст).
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NycHistoryOhlcCell(
                    text = stringResource(R.string.history_ohlc_open_fmt, nycFormatScore(displayCandle.open)),
                    modifier = Modifier.weight(1f)
                )
                NycHistoryOhlcCell(
                    text = stringResource(R.string.history_ohlc_high_fmt, nycFormatScore(displayCandle.high)),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NycHistoryOhlcCell(
                    text = stringResource(R.string.history_ohlc_low_fmt, nycFormatScore(displayCandle.low)),
                    modifier = Modifier.weight(1f)
                )
                NycHistoryOhlcCell(
                    text = stringResource(R.string.history_ohlc_close_fmt, nycFormatScore(displayCandle.close)),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NycHistoryOhlcCell(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(38.dp)
            .nycWell(9.dp)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontFamily = NycMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = nycHistoryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
