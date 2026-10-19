package ru.nya.nyeios.ui.notes.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.getTextBeforeSelection
import androidx.compose.ui.text.style.TextDecoration
import java.util.regex.Pattern

/**
 * Конвертер между HTML-разметкой и Compose [AnnotatedString] со стилями:
 * - <b> / <strong> -> Bold
 * - <i> / <em> -> Italic
 * - <u> -> Underline
 * - <mark> -> Highlight (акцентный фон)
 */
object RichTextFormatter {

    val HighlightColor = Color(0xFFEAB308).copy(alpha = 0.35f)

    /**
     * Преобразует HTML-строку в [AnnotatedString] с соответствующими SpanStyle.
     */
    fun htmlToAnnotatedString(html: String, highlightColor: Color = HighlightColor): AnnotatedString {
        if (html.isEmpty()) return AnnotatedString("")

        // Упрощенный стековый парсер для тегов b, i, u, mark
        val clean = html.replace("<br>", "\n").replace("<br/>", "\n").replace("</p>", "\n\n").replace("<p>", "")
        val result = StringBuilder()
        val spanStack = mutableListOf<TagSpan>()
        val finishedSpans = mutableListOf<AnnotatedString.Range<SpanStyle>>()

        var i = 0
        while (i < clean.length) {
            if (clean[i] == '<') {
                val closeIdx = clean.indexOf('>', i)
                if (closeIdx != -1) {
                    val tagContent = clean.substring(i + 1, closeIdx).trim().lowercase()
                    val isClosing = tagContent.startsWith("/")
                    val tagName = if (isClosing) tagContent.removePrefix("/") else tagContent

                    if (!isClosing) {
                        spanStack.add(TagSpan(tagName, result.length))
                    } else {
                        val lastIdx = spanStack.indexOfLast { it.tagName == tagName }
                        if (lastIdx != -1) {
                            val opened = spanStack.removeAt(lastIdx)
                            val spanStyle = getSpanStyleForTag(tagName, highlightColor)
                            if (spanStyle != null && result.length > opened.start) {
                                finishedSpans.add(
                                    AnnotatedString.Range(
                                        item = spanStyle,
                                        start = opened.start,
                                        end = result.length
                                    )
                                )
                            }
                        }
                    }
                    i = closeIdx + 1
                    continue
                }
            }
            result.append(clean[i])
            i++
        }

        // Закрываем оставшиеся незакрытые теги до конца строки
        for (opened in spanStack) {
            val spanStyle = getSpanStyleForTag(opened.tagName, highlightColor)
            if (spanStyle != null && result.length > opened.start) {
                finishedSpans.add(
                    AnnotatedString.Range(
                        item = spanStyle,
                        start = opened.start,
                        end = result.length
                    )
                )
            }
        }

        val builder = AnnotatedString.Builder(result.toString())
        for (span in finishedSpans) {
            builder.addStyle(span.item, span.start, span.end)
        }
        return builder.toAnnotatedString()
    }

    private data class TagSpan(val tagName: String, val start: Int)

    private fun getSpanStyleForTag(tag: String, highlightColor: Color): SpanStyle? {
        return when (tag) {
            "b", "strong" -> SpanStyle(fontWeight = FontWeight.Bold)
            "i", "em" -> SpanStyle(fontStyle = FontStyle.Italic)
            "u" -> SpanStyle(textDecoration = TextDecoration.Underline)
            "mark" -> SpanStyle(background = highlightColor)
            else -> null
        }
    }

    /**
     * Преобразует [AnnotatedString] в HTML-разметку для сохранения.
     */
    fun annotatedStringToHtml(annotated: AnnotatedString): String {
        if (annotated.text.isEmpty()) return ""

        val text = annotated.text
        val spanStyles = annotated.spanStyles

        if (spanStyles.isEmpty()) {
            return text
        }

        // Собираем события открытия и закрытия стилей по индексам символов
        val openTags = mutableMapOf<Int, MutableList<String>>()
        val closeTags = mutableMapOf<Int, MutableList<String>>()

        for (range in spanStyles) {
            val tag = when {
                range.item.fontWeight == FontWeight.Bold -> "b"
                range.item.fontStyle == FontStyle.Italic -> "i"
                range.item.textDecoration == TextDecoration.Underline -> "u"
                range.item.background != Color.Unspecified && range.item.background.alpha > 0f -> "mark"
                else -> null
            } ?: continue

            openTags.getOrPut(range.start) { mutableListOf() }.add(tag)
            closeTags.getOrPut(range.end) { mutableListOf() }.add(tag)
        }

        val sb = StringBuilder()
        for (idx in 0..text.length) {
            closeTags[idx]?.forEach { tag ->
                sb.append("</$tag>")
            }
            openTags[idx]?.forEach { tag ->
                sb.append("<$tag>")
            }
            if (idx < text.length) {
                sb.append(text[idx])
            }
        }

        return sb.toString()
    }

    /**
     * Очищает HTML/Markdown теги и возвращает однострочный чистый текст для превью.
     */
    fun extractPlainSnippet(rawText: String, maxLength: Int = 120): String {
        if (rawText.isBlank()) return ""
        val clean = rawText
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("""^[•\-\*]\s*""", RegexOption.MULTILINE), "")
            .replace(Regex("""^\d+\.\s*""", RegexOption.MULTILINE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return if (clean.length > maxLength) clean.take(maxLength) + "…" else clean
    }
}
