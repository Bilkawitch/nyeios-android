package ru.nya.nyeios.ui.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.nya.nyeios.R
import ru.nya.nyeios.data.history.ScoreHistoryRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Исход операции экспорта/импорта истории баллов.
 *
 * Текст статуса намеренно не хранится здесь: `stringResource` доступен только из
 * композиции, поэтому строку резолвит [transferResultText] в UI-слое, а обе темы
 * (YoRHa и NyC) лишь выбирают цвет по [isFailure].
 */
sealed interface TransferResult {
    /** История записана в выбранный файл; [points] — число экспортированных точек. */
    data class Exported(val points: Int) : TransferResult

    /** Файл прочитан и слит с локальной историей. */
    data class Imported(val added: Int, val duplicates: Int) : TransferResult

    /** Экспорт не начат: в истории нет ни одной точки, файл не создавался. */
    data object Empty : TransferResult

    data object ExportFailed : TransferResult

    data object ImportFailed : TransferResult
}

/** true для неуспешных исходов — UI красит статусную строку красным. */
val TransferResult.isFailure: Boolean
    get() = this is TransferResult.ExportFailed || this is TransferResult.ImportFailed

/** Обработчики кнопок «ЭКСПОРТ»/«ИМПОРТ» и признак занятости SAF-сессией/файловым IO. */
class ScoreHistoryTransferHandles(
    val export: () -> Unit,
    val import: () -> Unit,
    val isBusy: Boolean
)

/** MIME-фильтр выбора файла импорта: JSON плюс «сырые» варианты, которые отдают чужие провайдеры. */
private val OPEN_DOCUMENT_MIME_TYPES = arrayOf(
    "application/json",
    "text/plain",
    "application/octet-stream"
)

private const val EXPORT_FILE_PREFIX = "nyeios-score-history"
private const val EXPORT_FILE_EXT = ".json"

/** Имя файла экспорта, его число точек — всё, что нужно до запуска SAF-лончера. */
private data class PreparedExport(val fileName: String, val points: Int)

/**
 * Общий для обеих тем помощник экспорта/импорта истории баллов через Storage Access Framework.
 *
 * Экспорт:
 * 1. на [Dispatchers.IO] читает `stats()` и, если точек нет, возвращает [TransferResult.Empty]
 *    **до** запуска лончера (файл не создаётся);
 * 2. иначе запускает `CreateDocument("application/json")` с именем
 *    `nyeios-score-history-<group>-<yyyyMMdd>.json` (пустая группа — без `-<group>`);
 * 3. в колбэке пишет `exportJson()` в поток URI и отдаёт [TransferResult.Exported].
 *
 * Импорт: `OpenDocument` с MIME-фильтром, чтение потока и `importJson()` на [Dispatchers.IO],
 * результат — [TransferResult.Imported] / [TransferResult.ImportFailed].
 *
 * Все результаты приходят на главный поток; [ScoreHistoryTransferHandles.isBusy] взводится
 * до запуска лончера и снимается в колбэке (включая отмену выбора файла), что исключает
 * повторный запуск SAF поверх уже открытого системного выбора.
 */
@Composable
fun rememberScoreHistoryTransfer(
    onResult: (TransferResult) -> Unit
): ScoreHistoryTransferHandles {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { ScoreHistoryRepository.getInstance(context) }

    var isBusy by remember { mutableStateOf(false) }
    // Число точек фиксируем перед открытием SAF, чтобы статус не зависел от гонок с записью БРС.
    var exportPoints by remember { mutableStateOf(0) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) {
            // Пользователь отменил выбор файла — тихо снимаем блокировку, статус не трогаем.
            isBusy = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val points = exportPoints
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    val json = repository.exportJson()
                    // "wt" просит провайдера усечь существующий файл (перезапись тем же именем),
                    // но не все провайдеры знают этот режим — откатываемся на стандартный "w".
                    val stream = runCatching {
                        context.contentResolver.openOutputStream(uri, "wt")
                    }.getOrNull()
                        ?: context.contentResolver.openOutputStream(uri)
                        ?: error("no output stream for export uri")
                    stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }.isSuccess
            }
            isBusy = false
            onResult(
                if (written) TransferResult.Exported(points) else TransferResult.ExportFailed
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            isBusy = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: error("no input stream for import uri")
                    val json = stream.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    }.removePrefix("\uFEFF")
                    repository.importJson(json)
                }
            }
            isBusy = false
            parsed.onSuccess { result ->
                // Пустой/чужой JSON не даёт ни добавленных, ни дубликатов — это не успешный импорт.
                if (result.added == 0 && result.duplicates == 0 && result.invalid > 0) {
                    onResult(TransferResult.ImportFailed)
                } else {
                    onResult(TransferResult.Imported(result.added, result.duplicates))
                }
            }.onFailure {
                onResult(TransferResult.ImportFailed)
            }
        }
    }

    fun beginExport() {
        if (isBusy) return
        isBusy = true
        scope.launch {
            val prepared = withContext(Dispatchers.IO) {
                runCatching {
                    val stats = repository.stats()
                    if (stats.totalPoints == 0) {
                        null
                    } else {
                        PreparedExport(
                            fileName = exportFileName(
                                group = repository.exportPayload().group,
                                nowMs = System.currentTimeMillis()
                            ),
                            points = stats.totalPoints
                        )
                    }
                }
            }
            prepared.onSuccess { plan ->
                if (plan == null) {
                    isBusy = false
                    onResult(TransferResult.Empty)
                } else {
                    exportPoints = plan.points
                    runCatching { exportLauncher.launch(plan.fileName) }.onFailure {
                        isBusy = false
                        onResult(TransferResult.ExportFailed)
                    }
                }
            }.onFailure {
                isBusy = false
                onResult(TransferResult.ExportFailed)
            }
        }
    }

    fun beginImport() {
        if (isBusy) return
        isBusy = true
        runCatching { importLauncher.launch(OPEN_DOCUMENT_MIME_TYPES) }.onFailure {
            isBusy = false
            onResult(TransferResult.ImportFailed)
        }
    }

    return ScoreHistoryTransferHandles(
        export = ::beginExport,
        import = ::beginImport,
        isBusy = isBusy
    )
}

/** Локализованный текст статуса; null — если показывать нечего. */
@Composable
fun transferResultText(result: TransferResult): String? = when (result) {
    is TransferResult.Exported ->
        stringResource(R.string.settings_history_export_done_fmt, result.points)
    is TransferResult.Imported ->
        stringResource(R.string.settings_history_import_done_fmt, result.added, result.duplicates)
    TransferResult.Empty -> stringResource(R.string.settings_history_empty)
    TransferResult.ExportFailed -> stringResource(R.string.settings_history_export_failed)
    TransferResult.ImportFailed -> stringResource(R.string.settings_history_import_failed)
}

/**
 * `nyeios-score-history-<group>-<yyyyMMdd>.json`; пустая группа не оставляет двойного дефиса.
 * Символы, недопустимые в имени документа SAF, заменяются на `_`.
 */
private fun exportFileName(group: String, nowMs: Long): String {
    val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(nowMs))
    val safeGroup = group.trim()
        .replace(Regex("[^\\p{L}\\p{N}_-]+"), "_")
        .trim('_')
    return if (safeGroup.isEmpty()) {
        "$EXPORT_FILE_PREFIX-$date$EXPORT_FILE_EXT"
    } else {
        "$EXPORT_FILE_PREFIX-$safeGroup-$date$EXPORT_FILE_EXT"
    }
}
