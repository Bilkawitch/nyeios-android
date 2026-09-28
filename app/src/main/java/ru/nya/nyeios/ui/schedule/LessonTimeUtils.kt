package ru.nya.nyeios.ui.schedule

import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class LessonProgressInfo(
    val isOngoing: Boolean,
    val isFinished: Boolean,
    val isUpcoming: Boolean,
    val progress: Float,
    val elapsedSeconds: Long,
    val remainingSeconds: Long,
    val elapsedText: String,
    val remainingText: String
)

object LessonTimeUtils {
    private val timeRegex = Regex("""(\d{2}:\d{2})\s*[-—–]\s*(\d{2}:\d{2})""")
    private val formatter = DateTimeFormatter.ofPattern("HH:mm")

    fun parseTimeRange(timeRangeStr: String): Pair<LocalTime, LocalTime>? {
        val match = timeRegex.find(timeRangeStr) ?: return null
        val (startStr, endStr) = match.destructured
        return try {
            val start = LocalTime.parse(startStr, formatter)
            val end = LocalTime.parse(endStr, formatter)
            Pair(start, end)
        } catch (_: Exception) {
            null
        }
    }

    fun computeBreakMinutes(previousTimeStr: String, nextTimeStr: String): Long? {
        val prevRange = parseTimeRange(previousTimeStr) ?: return null
        val nextRange = parseTimeRange(nextTimeStr) ?: return null
        val minutes = Duration.between(prevRange.second, nextRange.first).toMinutes()
        return if (minutes > 0) minutes else null
    }

    fun formatLessonCount(count: Int, locale: java.util.Locale = java.util.Locale.getDefault()): String {
        if (locale.language.equals("en", ignoreCase = true)) {
            val word = if (count == 1) "LESSON" else "LESSONS"
            return "$count $word"
        }
        val rem10 = count % 10
        val rem100 = count % 100
        val word = when {
            rem100 in 11..19 -> "ПАР"
            rem10 == 1 -> "ПАРА"
            rem10 in 2..4 -> "ПАРЫ"
            else -> "ПАР"
        }
        return "$count $word"
    }

    fun computeDayTimeRange(lessons: List<ru.nya.nyeios.data.model.LessonItem>): String {
        if (lessons.isEmpty()) return ""
        val firstStart = parseTimeRange(lessons.first().time)?.first?.format(formatter) ?: ""
        val lastEnd = parseTimeRange(lessons.last().time)?.second?.format(formatter) ?: ""
        return if (firstStart.isNotEmpty() && lastEnd.isNotEmpty()) {
            "$firstStart—$lastEnd"
        } else {
            ""
        }
    }


    val moscowZone: ZoneId by lazy {
        try {
            ZoneId.of("Europe/Moscow")
        } catch (e: Exception) {
            ZoneId.systemDefault()
        }
    }

    fun getNow(): LocalTime = LocalTime.now(moscowZone)

    fun computeLessonProgress(
        timeRangeStr: String,
        isToday: Boolean = true,
        now: LocalTime = getNow(),
        locale: java.util.Locale = java.util.Locale.getDefault()
    ): LessonProgressInfo? {
        val match = timeRegex.find(timeRangeStr) ?: return null
        val (startStr, endStr) = match.destructured
        val isEn = locale.language.equals("en", ignoreCase = true)
        val zeroSecText = if (isEn) "0 s" else "0 с"

        return try {
            val start = LocalTime.parse(startStr, formatter)
            val end = LocalTime.parse(endStr, formatter)
            val totalSeconds = Duration.between(start, end).seconds.coerceAtLeast(1)

            if (!isToday) {
                return LessonProgressInfo(
                    isOngoing = false,
                    isFinished = false,
                    isUpcoming = false,
                    progress = 0f,
                    elapsedSeconds = 0,
                    remainingSeconds = totalSeconds,
                    elapsedText = zeroSecText,
                    remainingText = formatLessonDuration(totalSeconds, locale)
                )
            }

            val isOngoing = !now.isBefore(start) && now.isBefore(end)
            val isFinished = !now.isBefore(end)
            val isUpcoming = now.isBefore(start)

            when {
                isOngoing -> {
                    val elapsedSeconds = Duration.between(start, now).seconds.coerceAtLeast(0)
                    val remainingSeconds = Duration.between(now, end).seconds.coerceAtLeast(0)
                    val progress = (elapsedSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)

                    LessonProgressInfo(
                        isOngoing = true,
                        isFinished = false,
                        isUpcoming = false,
                        progress = progress,
                        elapsedSeconds = elapsedSeconds,
                        remainingSeconds = remainingSeconds,
                        elapsedText = formatLessonDuration(elapsedSeconds, locale),
                        remainingText = formatLessonDuration(remainingSeconds, locale)
                    )
                }
                isFinished -> {
                    LessonProgressInfo(
                        isOngoing = false,
                        isFinished = true,
                        isUpcoming = false,
                        progress = 1f,
                        elapsedSeconds = totalSeconds,
                        remainingSeconds = 0,
                        elapsedText = formatLessonDuration(totalSeconds, locale),
                        remainingText = if (isEn) "Finished" else "Завершена"
                    )
                }
                isUpcoming -> {
                    val untilStartSeconds = Duration.between(now, start).seconds.coerceAtLeast(0)
                    val untilStartText = formatLessonDuration(untilStartSeconds, locale)
                    LessonProgressInfo(
                        isOngoing = false,
                        isFinished = false,
                        isUpcoming = true,
                        progress = 0f,
                        elapsedSeconds = 0,
                        remainingSeconds = totalSeconds,
                        elapsedText = zeroSecText,
                        remainingText = if (isEn) "Starts in: $untilStartText" else "До начала: $untilStartText"
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun checkIfOngoing(
        timeRangeStr: String,
        isToday: Boolean = true,
        now: LocalTime = getNow()
    ): Boolean {
        return computeLessonProgress(timeRangeStr, isToday, now)?.isOngoing == true
    }

    fun formatLessonDuration(seconds: Long, locale: java.util.Locale = java.util.Locale.getDefault()): String {
        val sec = seconds.coerceAtLeast(0)
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        val isEn = locale.language.equals("en", ignoreCase = true)
        return if (isEn) {
            when {
                h > 0 -> "${h} h ${m} m ${s} s"
                m > 0 -> "${m} m ${s} s"
                else -> "${s} s"
            }
        } else {
            when {
                h > 0 -> "${h} ч ${m} мин ${s} с"
                m > 0 -> "${m} мин ${s} с"
                else -> "${s} с"
            }
        }
    }
}
