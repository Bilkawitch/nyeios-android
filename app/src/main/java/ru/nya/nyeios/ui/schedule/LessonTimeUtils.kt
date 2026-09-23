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
    private val timeRegex = Regex("""(\d{2}:\d{2})\s*-\s*(\d{2}:\d{2})""")
    private val formatter = DateTimeFormatter.ofPattern("HH:mm")

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
        now: LocalTime = getNow()
    ): LessonProgressInfo? {
        val match = timeRegex.find(timeRangeStr) ?: return null
        val (startStr, endStr) = match.destructured

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
                    elapsedText = "0 с",
                    remainingText = formatLessonDuration(totalSeconds)
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
                        elapsedText = formatLessonDuration(elapsedSeconds),
                        remainingText = formatLessonDuration(remainingSeconds)
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
                        elapsedText = formatLessonDuration(totalSeconds),
                        remainingText = "Завершена"
                    )
                }
                isUpcoming -> {
                    val untilStartSeconds = Duration.between(now, start).seconds.coerceAtLeast(0)
                    val untilStartText = formatLessonDuration(untilStartSeconds)
                    LessonProgressInfo(
                        isOngoing = false,
                        isFinished = false,
                        isUpcoming = true,
                        progress = 0f,
                        elapsedSeconds = 0,
                        remainingSeconds = totalSeconds,
                        elapsedText = "0 с",
                        remainingText = "До начала: $untilStartText"
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

    fun formatLessonDuration(seconds: Long): String {
        val sec = seconds.coerceAtLeast(0)
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return when {
            h > 0 -> "${h} ч ${m} мин ${s} с"
            m > 0 -> "${m} мин ${s} с"
            else -> "${s} с"
        }
    }
}
