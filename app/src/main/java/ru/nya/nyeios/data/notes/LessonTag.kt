package ru.nya.nyeios.data.notes

import java.text.Normalizer
import java.util.Locale

/**
 * Deterministic hashtag projected from a lesson subject. The tag is metadata:
 * it is never written into the note body and never counts as user content.
 */
object LessonTag {

    fun fromSubject(subject: String): String? {
        val slug = slug(subject) ?: return null
        return "#$slug"
    }

    fun slug(subject: String): String? {
        val normalized = Normalizer.normalize(subject, Normalizer.Form.NFC)
            .lowercase(Locale.ROOT)
        val out = StringBuilder()
        var sawLetter = false
        var separatorPending = false

        normalized.codePoints().forEach { cp ->
            when {
                Character.isLetter(cp) || Character.getType(cp) == Character.NON_SPACING_MARK.toInt() -> {
                    if (sawLetter && separatorPending && out.isNotEmpty() && out.last() != '-') {
                        out.append('-')
                    }
                    out.appendCodePoint(cp)
                    sawLetter = true
                    separatorPending = false
                }

                Character.isDigit(cp) || Character.isWhitespace(cp) || Character.isISOControl(cp) -> {
                    if (sawLetter) separatorPending = true
                }

                else -> {
                    // Punctuation and symbols separate words but never enter the tag.
                    if (sawLetter) separatorPending = true
                }
            }
        }

        return out.toString().trim('-').ifEmpty { null }
    }
}
