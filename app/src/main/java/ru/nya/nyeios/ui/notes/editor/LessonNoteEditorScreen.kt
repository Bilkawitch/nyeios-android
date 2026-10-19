package ru.nya.nyeios.ui.notes.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.nya.nyeios.R
import ru.nya.nyeios.data.notes.LessonNote
import ru.nya.nyeios.data.notes.LessonNotesRepository
import ru.nya.nyeios.ui.theme.NierBorder
import ru.nya.nyeios.ui.theme.NierBorderLight
import ru.nya.nyeios.ui.theme.NierDark
import ru.nya.nyeios.ui.theme.NierDim
import ru.nya.nyeios.ui.theme.NierPanel
import ru.nya.nyeios.ui.theme.NierPanelAlt
import ru.nya.nyeios.ui.theme.NierSelection
import ru.nya.nyeios.ui.theme.NierSelectionText
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.NycCyanDk
import ru.nya.nyeios.ui.theme.NycCyanHi
import ru.nya.nyeios.ui.theme.NycMonoFamily
import ru.nya.nyeios.ui.theme.NycSansFamily
import ru.nya.nyeios.ui.theme.NycShapes
import ru.nya.nyeios.ui.theme.RajdhaniFamily
import ru.nya.nyeios.ui.theme.ShareTechMonoFamily
import ru.nya.nyeios.ui.theme.UiPreferencesManager
import ru.nya.nyeios.ui.theme.isNycModern
import ru.nya.nyeios.ui.theme.selectionAccent

@Composable
fun LessonNoteEditorScreen(
    dateString: String,
    lessonNumber: String,
    time: String,
    subject: String,
    teacher: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { LessonNotesRepository.getInstance(context) }
    val noteKey = remember(dateString, time, lessonNumber, subject) {
        repository.buildKey(dateString, time, lessonNumber, subject)
    }

    val existingNote = remember(noteKey) { repository.getNote(noteKey) }
    val initialRawText = remember(noteKey) { existingNote?.rawText ?: "" }

    val editorHighlightColor = if (isNycModern) NycCyan.copy(alpha = 0.35f) else Color(0xFFEAB308).copy(alpha = 0.35f)
    val editorState = rememberRichTextEditorState(
        initialRawText = initialRawText,
        highlightColor = editorHighlightColor
    )

    val focusRequester = remember { FocusRequester() }

    // Auto-save logic
    fun saveCurrent() {
        val rawHtml = editorState.toRawHtml()
        val plainSnippet = editorState.toPlainSnippet()

        if (rawHtml.isBlank() && plainSnippet.isBlank()) {
            repository.deleteNote(noteKey)
        } else {
            val note = LessonNote(
                key = noteKey,
                date = dateString,
                lessonNumber = lessonNumber,
                time = time,
                subject = subject,
                teacher = teacher,
                rawText = rawHtml,
                plainSnippet = plainSnippet,
                updatedAt = System.currentTimeMillis()
            )
            repository.saveNote(note)
        }
    }

    // Auto-save on exit
    DisposableEffect(Unit) {
        onDispose {
            saveCurrent()
        }
    }

    BackHandler {
        saveCurrent()
        onDismiss()
    }

    // Colors according to theme
    val isModern = isNycModern
    val bgCanvas = if (isModern) Color(0xFF0F172A) else ru.nya.nyeios.ui.theme.NierBg
    val panelBg = if (isModern) Color(0xFF1E293B) else NierPanel
    val textColor = if (isModern) Color(0xFFF1F5F9) else NierDark
    val mutedColor = if (isModern) Color(0xFF94A3B8) else NierDim
    val accentColor = if (isModern) NycCyan else selectionAccent()
    val borderColor = if (isModern) NycCyanDk.copy(alpha = 0.5f) else NierBorderLight

    val headerShape = if (isModern) RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp) else RoundedCornerShape(0.dp)
    val doneShape = if (isModern) RoundedCornerShape(NycShapes.button) else RoundedCornerShape(0.dp)
    val toolbarShape = if (isModern) RoundedCornerShape(12.dp) else RoundedCornerShape(0.dp)
    val titleFont = if (isModern) NycSansFamily else RajdhaniFamily
    val metaFont = if (isModern) NycMonoFamily else ShareTechMonoFamily

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header TopBar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(headerShape)
                    .background(panelBg, headerShape)
                    .border(1.dp, borderColor, headerShape)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = {
                            saveCurrent()
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = textColor
                        )
                    }

                    Column(modifier = Modifier.padding(start = 4.dp, end = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val numStr = if (lessonNumber.isNotEmpty()) "ПАРА $lessonNumber" else "ЗАНЯТИЕ"
                            Text(
                                text = "$numStr · $time",
                                fontSize = 10.sp,
                                fontFamily = metaFont,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                            Text(
                                text = "· $dateString",
                                fontSize = 10.sp,
                                fontFamily = metaFont,
                                color = mutedColor
                            )
                        }
                        Text(
                            text = subject,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = titleFont,
                            color = textColor,
                            maxLines = 1
                        )
                    }
                }

                // Done Button
                Box(
                    modifier = Modifier
                        .clip(doneShape)
                        .background(accentColor, doneShape)
                        .clickable {
                            saveCurrent()
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (isModern) Color.Black else NierSelectionText,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.notes_editor_done),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = titleFont,
                            color = if (isModern) Color.Black else NierSelectionText,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Formatting Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isModern) 6.dp else 0.dp)
                    .clip(toolbarShape)
                    .background(panelBg.copy(alpha = 0.95f), toolbarShape)
                    .border(width = 1.dp, color = borderColor, shape = toolbarShape)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorToolbarButton(
                    icon = Icons.Default.FormatBold,
                    title = "Жирный",
                    isActive = editorState.isBoldSelected,
                    accentColor = accentColor,
                    textColor = textColor,
                    onClick = { editorState.toggleBold() }
                )
                EditorToolbarButton(
                    icon = Icons.Default.FormatItalic,
                    title = "Курсив",
                    isActive = editorState.isItalicSelected,
                    accentColor = accentColor,
                    textColor = textColor,
                    onClick = { editorState.toggleItalic() }
                )
                EditorToolbarButton(
                    icon = Icons.Default.FormatUnderlined,
                    title = "Подчеркнутый",
                    isActive = editorState.isUnderlineSelected,
                    accentColor = accentColor,
                    textColor = textColor,
                    onClick = { editorState.toggleUnderline() }
                )
                EditorToolbarButton(
                    icon = Icons.Default.FormatColorFill,
                    title = "Выделение",
                    isActive = editorState.isHighlightSelected,
                    accentColor = accentColor,
                    textColor = textColor,
                    onClick = { editorState.toggleHighlight() }
                )

                // Divider
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(borderColor)
                )

                EditorToolbarButton(
                    icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                    title = "Список",
                    isActive = false,
                    accentColor = accentColor,
                    textColor = textColor,
                    onClick = { editorState.toggleBulletList() }
                )
                EditorToolbarButton(
                    icon = Icons.Default.FormatListNumbered,
                    title = "Нумерация",
                    isActive = false,
                    accentColor = accentColor,
                    textColor = textColor,
                    onClick = { editorState.toggleNumberedList() }
                )
                EditorToolbarButton(
                    icon = Icons.Default.FormatClear,
                    title = "Очистить стиль",
                    isActive = false,
                    accentColor = accentColor,
                    textColor = textColor,
                    onClick = { editorState.clearFormatting() }
                )

                // Divider
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(borderColor)
                )

                EditorToolbarButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    title = "Отмена",
                    isActive = false,
                    enabled = editorState.canUndo,
                    accentColor = accentColor,
                    textColor = textColor,
                    onClick = { editorState.undo() }
                )
                EditorToolbarButton(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    title = "Повтор",
                    isActive = false,
                    enabled = editorState.canRedo,
                    accentColor = accentColor,
                    textColor = textColor,
                    onClick = { editorState.redo() }
                )
            }

            // Text Editing Field
            val editorCardShape = if (isModern) RoundedCornerShape(NycShapes.card) else RoundedCornerShape(0.dp)
            val editorCardBg = if (isModern) Color(0xFF131D2A) else Color.Transparent
            val editorCardBorder = if (isModern) borderColor.copy(alpha = 0.35f) else Color.Transparent

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
                    .clip(editorCardShape)
                    .background(editorCardBg, editorCardShape)
                    .border(if (isModern) 1.dp else 0.dp, editorCardBorder, editorCardShape)
                    .padding(if (isModern) 14.dp else 0.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (editorState.textFieldValue.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.notes_editor_placeholder),
                        fontSize = 14.sp,
                        fontFamily = titleFont,
                        color = mutedColor.copy(alpha = 0.6f),
                        lineHeight = 20.sp
                    )
                }

                BasicTextField(
                    value = editorState.textFieldValue,
                    onValueChange = { editorState.onValueChange(it) },
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        color = textColor,
                        fontSize = 14.sp,
                        fontFamily = titleFont,
                        lineHeight = 20.sp
                    ),
                    cursorBrush = SolidColor(accentColor)
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
    }
}

@Composable
private fun EditorToolbarButton(
    icon: ImageVector,
    title: String,
    isActive: Boolean,
    accentColor: Color,
    textColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val isModern = isNycModern
    val btnShape = if (isModern) RoundedCornerShape(8.dp) else RoundedCornerShape(0.dp)
    val bg = if (isActive) accentColor.copy(alpha = 0.25f) else Color.Transparent
    val border = if (isActive) accentColor else Color.Transparent
    val tint = if (!enabled) NierDim.copy(alpha = 0.3f) else if (isActive) accentColor else textColor

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(btnShape)
            .background(bg, btnShape)
            .border(1.dp, border, btnShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}
