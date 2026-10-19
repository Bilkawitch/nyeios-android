package ru.nya.nyeios.ui.notes.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration

class RichTextEditorState(
    initialRawText: String = "",
    val highlightColor: Color = RichTextFormatter.HighlightColor
) {
    var textFieldValue by mutableStateOf(
        TextFieldValue(
            annotatedString = RichTextFormatter.htmlToAnnotatedString(initialRawText, highlightColor),
            selection = TextRange(initialRawText.length)
        )
    )

    private val undoStack = mutableListOf<TextFieldValue>()
    private val redoStack = mutableListOf<TextFieldValue>()

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    // Status indicators for the formatting toolbar
    val isBoldSelected: Boolean
        get() = hasSpanInSelection { it.fontWeight == FontWeight.Bold }

    val isItalicSelected: Boolean
        get() = hasSpanInSelection { it.fontStyle == FontStyle.Italic }

    val isUnderlineSelected: Boolean
        get() = hasSpanInSelection { it.textDecoration == TextDecoration.Underline }

    val isHighlightSelected: Boolean
        get() = hasSpanInSelection { it.background != Color.Unspecified && it.background.alpha > 0f }

    private fun pushUndo(current: TextFieldValue) {
        if (undoStack.size >= 50) {
            undoStack.removeAt(0)
        }
        undoStack.add(current)
        redoStack.clear()
        canUndo = undoStack.isNotEmpty()
        canRedo = false
    }

    fun onValueChange(newValue: TextFieldValue) {
        if (newValue.text != textFieldValue.text) {
            pushUndo(textFieldValue)
        }
        textFieldValue = newValue
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(textFieldValue)
            textFieldValue = prev
            canUndo = undoStack.isNotEmpty()
            canRedo = redoStack.isNotEmpty()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(textFieldValue)
            textFieldValue = next
            canUndo = undoStack.isNotEmpty()
            canRedo = redoStack.isNotEmpty()
        }
    }

    fun toggleBold() {
        toggleSpan(
            style = SpanStyle(fontWeight = FontWeight.Bold),
            predicate = { it.fontWeight == FontWeight.Bold }
        )
    }

    fun toggleItalic() {
        toggleSpan(
            style = SpanStyle(fontStyle = FontStyle.Italic),
            predicate = { it.fontStyle == FontStyle.Italic }
        )
    }

    fun toggleUnderline() {
        toggleSpan(
            style = SpanStyle(textDecoration = TextDecoration.Underline),
            predicate = { it.textDecoration == TextDecoration.Underline }
        )
    }

    fun toggleHighlight() {
        toggleSpan(
            style = SpanStyle(background = highlightColor),
            predicate = { it.background != Color.Unspecified && it.background.alpha > 0f }
        )
    }

    private fun hasSpanInSelection(predicate: (SpanStyle) -> Boolean): Boolean {
        val sel = textFieldValue.selection
        if (sel.collapsed) {
            val pos = sel.start
            return textFieldValue.annotatedString.spanStyles.any {
                pos in it.start..it.end && predicate(it.item)
            }
        }
        val selStart = minOf(sel.start, sel.end)
        val selEnd = maxOf(sel.start, sel.end)
        return textFieldValue.annotatedString.spanStyles.any {
            it.start < selEnd && it.end > selStart && predicate(it.item)
        }
    }

    private fun toggleSpan(style: SpanStyle, predicate: (SpanStyle) -> Boolean) {
        val current = textFieldValue
        val sel = current.selection
        val text = current.text
        if (text.isEmpty()) return

        pushUndo(current)

        val selStart = if (sel.collapsed) 0 else minOf(sel.start, sel.end)
        val selEnd = if (sel.collapsed) text.length else maxOf(sel.start, sel.end)

        val alreadyApplied = current.annotatedString.spanStyles.any {
            it.start <= selStart && it.end >= selEnd && predicate(it.item)
        }

        val newSpans = mutableListOf<AnnotatedString.Range<SpanStyle>>()

        for (range in current.annotatedString.spanStyles) {
            if (predicate(range.item)) {
                // If it intersects with selection and already applied, we remove or split it
                if (alreadyApplied) {
                    if (range.start < selStart) {
                        newSpans.add(AnnotatedString.Range(range.item, range.start, selStart))
                    }
                    if (range.end > selEnd) {
                        newSpans.add(AnnotatedString.Range(range.item, selEnd, range.end))
                    }
                } else {
                    newSpans.add(range)
                }
            } else {
                newSpans.add(range)
            }
        }

        if (!alreadyApplied) {
            newSpans.add(AnnotatedString.Range(style, selStart, selEnd))
        }

        val builder = AnnotatedString.Builder(text)
        for (range in newSpans) {
            builder.addStyle(range.item, range.start, range.end)
        }

        textFieldValue = current.copy(annotatedString = builder.toAnnotatedString())
    }

    fun clearFormatting() {
        val current = textFieldValue
        val text = current.text
        if (text.isEmpty()) return

        pushUndo(current)

        val sel = current.selection
        val selStart = if (sel.collapsed) 0 else minOf(sel.start, sel.end)
        val selEnd = if (sel.collapsed) text.length else maxOf(sel.start, sel.end)

        val newSpans = mutableListOf<AnnotatedString.Range<SpanStyle>>()
        for (range in current.annotatedString.spanStyles) {
            if (range.end <= selStart || range.start >= selEnd) {
                newSpans.add(range)
            } else {
                if (range.start < selStart) {
                    newSpans.add(AnnotatedString.Range(range.item, range.start, selStart))
                }
                if (range.end > selEnd) {
                    newSpans.add(AnnotatedString.Range(range.item, selEnd, range.end))
                }
            }
        }

        val builder = AnnotatedString.Builder(text)
        for (range in newSpans) {
            builder.addStyle(range.item, range.start, range.end)
        }
        textFieldValue = current.copy(annotatedString = builder.toAnnotatedString())
    }

    fun toggleBulletList() {
        modifyLines { line ->
            if (line.startsWith("• ")) {
                line.removePrefix("• ")
            } else {
                "• $line"
            }
        }
    }

    fun toggleNumberedList() {
        var num = 1
        val numRegex = Regex("""^\d+\.\s*""")
        val isAlreadyNumbered = getSelectedLines().any { it.contains(numRegex) }

        modifyLines { line ->
            if (isAlreadyNumbered) {
                line.replace(numRegex, "")
            } else {
                val clean = line.replace(Regex("""^[•\-\*]\s*"""), "")
                "${num++}. $clean"
            }
        }
    }

    private fun getSelectedLines(): List<String> {
        val text = textFieldValue.text
        if (text.isEmpty()) return emptyList()
        val sel = textFieldValue.selection
        val selStart = minOf(sel.start, sel.end).coerceIn(0, text.length)
        val selEnd = maxOf(sel.start, sel.end).coerceIn(0, text.length)

        val lineStart = text.lastIndexOf('\n', (selStart - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', selEnd).let { if (it == -1) text.length else it }

        val block = text.substring(lineStart, lineEnd)
        return block.split('\n')
    }

    private fun modifyLines(transform: (String) -> String) {
        val current = textFieldValue
        val text = current.text
        pushUndo(current)

        val sel = current.selection
        val selStart = minOf(sel.start, sel.end).coerceIn(0, text.length)
        val selEnd = maxOf(sel.start, sel.end).coerceIn(0, text.length)

        val lineStart = text.lastIndexOf('\n', (selStart - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', selEnd).let { if (it == -1) text.length else it }

        val prefix = text.substring(0, lineStart)
        val targetBlock = text.substring(lineStart, lineEnd)
        val suffix = text.substring(lineEnd)

        val lines = targetBlock.split('\n')
        val transformedLines = lines.map(transform)
        val newBlock = transformedLines.joinToString("\n")

        val newText = prefix + newBlock + suffix
        // Re-parse or map spans
        val newAnnotated = AnnotatedString(newText)

        val newCursor = (lineStart + newBlock.length).coerceIn(0, newText.length)
        textFieldValue = TextFieldValue(
            annotatedString = newAnnotated,
            selection = TextRange(newCursor)
        )
    }

    fun toRawHtml(): String {
        return RichTextFormatter.annotatedStringToHtml(textFieldValue.annotatedString)
    }

    fun toPlainSnippet(): String {
        return RichTextFormatter.extractPlainSnippet(textFieldValue.text)
    }
}

@Composable
fun rememberRichTextEditorState(initialRawText: String = "", highlightColor: Color = RichTextFormatter.HighlightColor): RichTextEditorState {
    return remember(initialRawText, highlightColor) {
        RichTextEditorState(initialRawText, highlightColor)
    }
}
