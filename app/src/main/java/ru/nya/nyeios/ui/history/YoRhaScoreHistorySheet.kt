package ru.nya.nyeios.ui.history

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import ru.nya.nyeios.ui.theme.NierBg
import ru.nya.nyeios.ui.theme.NierBorderLight
import ru.nya.nyeios.ui.theme.NierDark
import ru.nya.nyeios.ui.theme.NierDim
import ru.nya.nyeios.ui.theme.NierGreen
import ru.nya.nyeios.ui.theme.NierPanel
import ru.nya.nyeios.ui.theme.NierPanelAlt
import ru.nya.nyeios.ui.theme.NierRed
import ru.nya.nyeios.ui.theme.RajdhaniFamily
import ru.nya.nyeios.ui.theme.ShareTechMonoFamily

/**
 * Данные экрана истории, собранные в одном IO-проходе.
 *
 * Ключи [candlesBySubject] и [pointsBySubject] — составной id серии
 * `subject + SEPARATOR + controlType`: в одном семестре у предмета может быть
 * несколько серий (зачёт/экзамен), и они не должны перетирать друг друга.
 */
private data class HistoryUiData(
    val lines: List<TrendLine>,
    val candlesBySubject: Map<String, List<OhlcCandle>>,
    val pointsBySubject: Map<String, Int>
)

private const val SERIES_ID_SEPARATOR = '\u0001'

private fun seriesId(key: ScoreSeriesKey): String =
    key.subject + SERIES_ID_SEPARATOR + key.controlType

private fun seriesSubject(id: String): String = id.substringBefore(SERIES_ID_SEPARATOR)

/** Число для подписи: целые без хвоста `.0`, дробные — один знак, точка как разделитель. */
private fun formatScore(value: Double): String {
    val rounded = value.roundToInt()
    return if (abs(value - rounded) < 0.05) {
        rounded.toString()
    } else {
        String.format(Locale.ROOT, "%.1f", value)
    }
}

/**
 * Собирает данные для UI строго на IO-потоке: серии -> точки -> числовые сэмплы ->
 * линии тренда и дневные свечи. Серии без числовых баллов остаются в списке
 * предметов (с пометкой «недостаточно данных»), но не попадают в график.
 */
private fun buildHistoryData(
    repository: ScoreHistoryRepository,
    termNum: Int?
): HistoryUiData {
    val zone = ZoneId.systemDefault()
    var keys = repository.seriesKeys().filter { termNum == null || it.termNum == termNum }
    // Как и в NyC-версии: если у запомненной группы есть свои серии — показываем только их,
    // иначе (группа неизвестна/импорт из другой группы) — все серии, чтобы история не пропадала.
    val rememberedGroup = repository.exportPayload().group
    if (rememberedGroup.isNotBlank()) {
        val ownKeys = keys.filter { it.group == rememberedGroup }
        if (ownKeys.isNotEmpty()) keys = ownKeys
    }
    val lines = ArrayList<TrendLine>(keys.size)
    val candlesBySubject = LinkedHashMap<String, List<OhlcCandle>>()
    val pointsBySubject = LinkedHashMap<String, Int>()

    for (key in keys) {
        val samples = repository.pointsFor(key)
            .mapNotNull { point ->
                ScoreHistoryValues.primaryValue(point.scores)
                    ?.let { value -> ScoreSample(atMs = point.recordedAtMs, value = value) }
            }
            .sortedBy { it.atMs }

        val id = seriesId(key)
        pointsBySubject[id] = samples.size
        if (samples.isEmpty()) continue

        lines += TrendLine(
            key = key,
            label = key.subject,
            samples = samples,
            rising = samples.last().value >= samples.first().value
        )
        candlesBySubject[id] = ChartMath.candles(samples, zone)
    }

    return HistoryUiData(
        lines = lines,
        candlesBySubject = candlesBySubject,
        pointsBySubject = pointsBySubject
    )
}

/**
 * Полноэкранная история баллов БРС в семействе YoRHa (0dp, Rajdhani / ShareTechMono).
 *
 * Два состояния: обзор (линии тренда всех серий выбранного семестра + список предметов)
 * и детально (свечной график предмета с OHLC выбранной свечи). Все цвета свечей —
 * строго из [HistoryChartPalette] (зелёный рост / красный падение, тема их не меняет).
 */
@Composable
fun YoRhaScoreHistorySheet(
    onDismiss: () -> Unit,
    initialTermNum: Int? = null
) {
    val context = LocalContext.current
    val repository = remember(context) { ScoreHistoryRepository.getInstance(context) }
    val revision by repository.revision.collectAsState()
    val zone = remember { ZoneId.systemDefault() }

    val uiData by produceState<HistoryUiData?>(
        initialValue = null,
        key1 = revision,
        key2 = initialTermNum
    ) {
        value = withContext(Dispatchers.IO) {
            // Сбой чтения SQLite не должен ронять экран: показываем пустое состояние.
            runCatching { buildHistoryData(repository, initialTermNum) }
                .getOrElse { HistoryUiData(emptyList(), emptyMap(), emptyMap()) }
        }
    }

    var selectedSeriesId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCandleIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    // Системный «назад» из детального вида возвращает к обзору, а не закрывает экран.
    // Обработчик Dialog срабатывает, даже если BackHandler внутри окна не получает
    // событие (окно диалога может перехватить его раньше activity-диспетчера).
    val handleBack: () -> Unit = {
        if (selectedSeriesId != null) {
            selectedSeriesId = null
            selectedCandleIndex = null
        } else {
            onDismiss()
        }
    }

    BackHandler { handleBack() }

    val data = uiData
    val detailId = selectedSeriesId?.takeIf { data?.pointsBySubject?.containsKey(it) == true }
    val group = data?.lines?.firstOrNull()?.key?.group.orEmpty()
    val totalPoints = data?.pointsBySubject?.values?.sum() ?: 0
    val subtitle = historySubtitle(group = group, termNum = initialTermNum)

    Dialog(
        onDismissRequest = handleBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NierBg)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                HistoryHeader(
                    subtitle = subtitle,
                    totalPoints = totalPoints,
                    onDismiss = onDismiss
                )

                // Контент забирает остаток высоты: LazyColumn/скролл должны получить
                // ограниченные размеры, иначе на маленьких экранах низ уедет за границу.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when {
                        data == null -> HistoryLoading()
                        data.pointsBySubject.isEmpty() -> HistoryEmptyState()
                        detailId == null -> HistoryOverview(
                            data = data,
                            onOpenSeries = { id ->
                                selectedSeriesId = id
                                selectedCandleIndex = null
                            }
                        )
                        else -> HistoryDetail(
                            seriesId = detailId,
                            candles = data.candlesBySubject[detailId].orEmpty(),
                            // Точек может быть больше, чем свечей: несколько замеров в один день.
                            pointsCount = data.pointsBySubject[detailId] ?: 0,
                            zone = zone,
                            selectedCandleIndex = selectedCandleIndex,
                            onSelectCandle = { selectedCandleIndex = it },
                            onBack = {
                                selectedSeriesId = null
                                selectedCandleIndex = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun historySubtitle(group: String, termNum: Int?): String {
    val allSubjects = stringResource(R.string.history_all_subjects)
    return if (termNum != null) {
        stringResource(
            R.string.history_group_term_fmt,
            group.ifBlank { allSubjects },
            termNum
        )
    } else {
        allSubjects
    }
}

@Composable
private fun HistoryHeader(
    subtitle: String,
    totalPoints: Int,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NierPanel)
            .border(1.dp, NierBorderLight)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(NierGreen)
                )
                Text(
                    text = stringResource(R.string.history_title),
                    fontFamily = RajdhaniFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.4.sp,
                    color = NierDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = subtitle,
                    fontFamily = ShareTechMonoFamily,
                    fontSize = 9.sp,
                    color = NierDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (totalPoints > 0) {
                    Text(
                        text = stringResource(R.string.history_points_fmt, totalPoints),
                        fontFamily = ShareTechMonoFamily,
                        fontSize = 9.sp,
                        color = NierDim,
                        maxLines = 1
                    )
                }
            }
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.history_close_cd),
                tint = NierDark,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun HistoryLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = NierDim)
    }
}

@Composable
private fun HistoryEmptyState() {
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
                    .size(40.dp)
                    .background(NierPanelAlt)
                    .border(1.dp, NierBorderLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Timeline,
                    contentDescription = null,
                    tint = NierDim,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = stringResource(R.string.history_empty_title),
                fontFamily = RajdhaniFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 0.5.sp,
                color = NierDark,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.history_empty_desc),
                fontFamily = RajdhaniFamily,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = NierDim,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HistoryOverview(
    data: HistoryUiData,
    onOpenSeries: (String) -> Unit
) {
    val seriesIds = remember(data) { data.pointsBySubject.keys.toList() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            HistoryTrendPanel(lines = data.lines)
        }
        item {
            Text(
                text = stringResource(R.string.history_hint),
                fontFamily = RajdhaniFamily,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
                color = NierDim
            )
        }
        items(seriesIds, key = { it }) { id ->
            HistorySubjectRow(
                id = id,
                candles = data.candlesBySubject[id].orEmpty(),
                onClick = { onOpenSeries(id) }
            )
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HistoryTrendPanel(lines: List<TrendLine>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NierPanelAlt)
            .border(1.dp, NierBorderLight)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (lines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.history_no_data_for_subject),
                    fontFamily = RajdhaniFamily,
                    fontSize = 11.sp,
                    color = NierDim,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            TrendLinesCanvas(
                lines = lines,
                lineColorFor = { line ->
                    if (line.rising) HistoryChartPalette.Rising else HistoryChartPalette.Falling
                },
                gridColor = NierBorderLight,
                height = 180.dp,
                labelStyle = TextStyle(
                    fontFamily = ShareTechMonoFamily,
                    fontSize = 8.sp,
                    color = NierDim
                )
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TrendLegendItem(
                    color = HistoryChartPalette.Rising,
                    label = stringResource(R.string.history_trend_up)
                )
                TrendLegendItem(
                    color = HistoryChartPalette.Falling,
                    label = stringResource(R.string.history_trend_down)
                )
            }
        }
    }
}

@Composable
private fun TrendLegendItem(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(color)
        )
        Text(
            text = label,
            fontFamily = RajdhaniFamily,
            fontSize = 8.sp,
            letterSpacing = 0.5.sp,
            color = NierDim
        )
    }
}

@Composable
private fun HistorySubjectRow(
    id: String,
    candles: List<OhlcCandle>,
    onClick: () -> Unit
) {
    val hasTrend = candles.size >= 2
    val isRising = hasTrend && candles.last().close >= candles.first().open
    val trendColor = when {
        !hasTrend -> NierDim
        isRising -> NierGreen
        else -> NierRed
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NierPanelAlt)
            .border(1.dp, NierBorderLight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(trendColor)
        )
        Text(
            text = seriesSubject(id),
            fontFamily = RajdhaniFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = NierDark,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (hasTrend) {
            val firstValue = candles.first().open
            val lastValue = candles.last().close
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = stringResource(R.string.history_value_fmt, formatScore(lastValue)),
                    fontFamily = ShareTechMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = NierDark
                )
                Text(
                    text = stringResource(R.string.history_value_fmt, formatScore(firstValue)) +
                        " → " +
                        stringResource(R.string.history_value_fmt, formatScore(lastValue)),
                    fontFamily = ShareTechMonoFamily,
                    fontSize = 9.sp,
                    color = NierDim,
                    maxLines = 1
                )
            }
        } else {
            Text(
                text = stringResource(R.string.history_no_data_for_subject),
                fontFamily = RajdhaniFamily,
                fontSize = 8.sp,
                color = NierDim,
                maxLines = 3,
                lineHeight = 10.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 88.dp)
            )
        }
    }
}

@Composable
private fun HistoryDetail(
    seriesId: String,
    candles: List<OhlcCandle>,
    pointsCount: Int,
    zone: ZoneId,
    selectedCandleIndex: Int?,
    onSelectCandle: (Int?) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val backLabel = stringResource(R.string.history_back)
            Row(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .background(NierPanelAlt)
                    .border(1.dp, NierBorderLight)
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = NierDark,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = backLabel,
                    fontFamily = RajdhaniFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = NierDark,
                    maxLines = 1
                )
            }
            Text(
                text = seriesSubject(seriesId),
                fontFamily = RajdhaniFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = NierDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        if (candles.size < 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NierPanelAlt)
                    .border(1.dp, NierBorderLight)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.history_no_data_for_subject),
                    fontFamily = RajdhaniFamily,
                    fontSize = 12.sp,
                    color = NierDim,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val safeSelected = selectedCandleIndex?.takeIf { it in candles.indices }
            val displayCandle = candles[safeSelected ?: candles.lastIndex]
            val displayDate = ChartMath.axisDate(displayCandle.dayStartMs, zone)
            val candleDescription = stringResource(
                R.string.history_candle_cd,
                displayDate,
                formatScore(displayCandle.open),
                formatScore(displayCandle.high),
                formatScore(displayCandle.low),
                formatScore(displayCandle.close)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NierPanelAlt)
                    .border(1.dp, NierBorderLight)
                    .padding(8.dp)
            ) {
                CandlestickCanvas(
                    candles = candles,
                    axisColor = NierBorderLight,
                    labelStyle = TextStyle(
                        fontFamily = ShareTechMonoFamily,
                        fontSize = 9.sp,
                        color = NierDim
                    ),
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = candleDescription
                    },
                    height = 260.dp,
                    selectedIndex = safeSelected,
                    onSelectIndex = onSelectCandle
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = displayDate,
                    fontFamily = ShareTechMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = NierDark,
                    maxLines = 1
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OhlcCell(
                        text = stringResource(
                            R.string.history_ohlc_open_fmt,
                            formatScore(displayCandle.open)
                        )
                    )
                    OhlcCell(
                        text = stringResource(
                            R.string.history_ohlc_high_fmt,
                            formatScore(displayCandle.high)
                        )
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OhlcCell(
                        text = stringResource(
                            R.string.history_ohlc_low_fmt,
                            formatScore(displayCandle.low)
                        )
                    )
                    OhlcCell(
                        text = stringResource(
                            R.string.history_ohlc_close_fmt,
                            formatScore(displayCandle.close)
                        )
                    )
                }
                Text(
                    text = stringResource(
                        R.string.history_points_fmt,
                        if (pointsCount > 0) pointsCount else candles.size
                    ),
                    fontFamily = ShareTechMonoFamily,
                    fontSize = 9.sp,
                    color = NierDim,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun OhlcCell(text: String) {
    Text(
        text = text,
        fontFamily = ShareTechMonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        color = NierDark,
        maxLines = 1
    )
}
