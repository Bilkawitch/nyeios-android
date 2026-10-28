package ru.nya.nyeios.data.notes

import ru.nya.nyeios.data.parser.ScheduleParser
import java.util.Locale

/**
 * Канонический ключ заметки к паре. Единственная точка нормализации для вкладки
 * расписания, недельной ведомости ДЗ, предпросмотра и редактора заметки.
 *
 * Ключ обязан совпадать для одной и той же пары, даже если данные пришли из разных
 * источников: боевое (уже санитизированное) расписание и сырой JSON дискового кэша.
 * Поэтому:
 * - дата приводится к виду dd.MM (год отбрасывается: и день недели в кэше, и ключи
 *   предыдущих версий приложения хранят только день и месяц);
 * - номер пары канонизируется ровно так же, как в `EiosRepository.sanitizeCachedSchedule`:
 *   корректный номер 1..8 сохраняется, устаревший/битый (например "10", "12", "0")
 *   пересчитывается из времени по сетке пар;
 * - время приводится к виду "HH:mm-HH:mm" (пробелы вокруг тире убираются, тире
 *   нормализуется, пробелы заменяются на "_");
 * - название дисциплины приводится к нижнему регистру, "ё" складывается в "е",
 *   любые последовательности не-букв/не-цифр заменяются одним "_", хвостовые "_"
 *   обрезаются, длина ограничена 40 символами.
 */
object LessonNoteKey {

    private const val SUBJECT_MAX_LENGTH = 40

    private val DD_MM_PATTERN = Regex("""(\d{1,2})\s*[.\-/]\s*(\d{1,2})""")
    private val ISO_DATE_PATTERN = Regex("""(\d{4})[-./](\d{1,2})[-./](\d{1,2})""")
    private val NON_ALPHANUMERIC = Regex("""[^a-zа-я0-9]+""")
    private val SPACES_AROUND_DASH = Regex("""\s*-\s*""")
    private val WHITESPACE = Regex("""\s+""")

    fun buildKey(
        dateString: String,
        time: String,
        lessonNumber: String,
        subject: String
    ): String {
        val date = canonicalDate(dateString)
        val number = canonicalLessonNumber(lessonNumber, time)
        val cleanTime = canonicalTime(time)
        val cleanSubject = canonicalSubject(subject)
        return "${date}_${number}_${cleanTime}_${cleanSubject}"
    }

    /** Приводит дату к dd.MM. Неразборчивая строка возвращается без изменений (trim). */
    fun canonicalDate(dateString: String): String {
        val trimmed = dateString.trim()
        if (trimmed.isEmpty()) return ""

        val iso = ISO_DATE_PATTERN.find(trimmed)
        if (iso != null) {
            val year = iso.groupValues[1].toIntOrNull()
            val month = iso.groupValues[2].toIntOrNull()
            val day = iso.groupValues[3].toIntOrNull()
            if (year != null && month != null && day != null && isPlausibleDate(month, day)) {
                return formatDayMonth(day, month)
            }
        }

        val dayMonth = DD_MM_PATTERN.find(trimmed)
        if (dayMonth != null) {
            val day = dayMonth.groupValues[1].toIntOrNull()
            val month = dayMonth.groupValues[2].toIntOrNull()
            if (day != null && month != null && isPlausibleDate(month, day)) {
                return formatDayMonth(day, month)
            }
        }

        return trimmed
    }

    /**
     * Канонический номер пары. Повторяет логику `sanitizeCachedSchedule`, чтобы ключ
     * одной и той же пары совпадал для боевого расписания и для сырого кэша.
     */
    fun canonicalLessonNumber(lessonNumber: String, time: String): String {
        val trimmed = lessonNumber.trim()
        val numeric = trimmed.toIntOrNull()
        if (numeric != null && numeric in 1..8 && !trimmed.startsWith("0")) {
            return numeric.toString()
        }
        val inferred = ScheduleParser.inferLessonNumber(time).trim()
        if (inferred.isNotEmpty()) return inferred
        return if (trimmed.isEmpty()) "0" else trimmed
    }

    /** Приводит время к "HH:mm-HH:mm" с заменой пробелов на "_". */
    fun canonicalTime(time: String): String {
        return time.trim()
            .replace('\u00A0', ' ')
            .replace('\u2013', '-')
            .replace('\u2014', '-')
            .replace('\u2212', '-')
            .replace(SPACES_AROUND_DASH, "-")
            .replace(WHITESPACE, " ")
            .replace(' ', '_')
    }

    /** Приводит дисциплину к устойчивому фрагменту ключа (нижний регистр, ё -> е). */
    fun canonicalSubject(subject: String): String {
        return subject.trim()
            .lowercase(Locale.ROOT)
            .replace('ё', 'е')
            .replace(NON_ALPHANUMERIC, "_")
            .trim('_')
            .take(SUBJECT_MAX_LENGTH)
    }

    private fun isPlausibleDate(month: Int, day: Int): Boolean =
        month in 1..12 && day in 1..31

    private fun formatDayMonth(day: Int, month: Int): String =
        "%02d.%02d".format(Locale.ROOT, day, month)
}
