package ru.nya.nyeios.ui.notes.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nya.nyeios.R
import ru.nya.nyeios.data.notes.LessonNotesRepository
import ru.nya.nyeios.data.notes.NotePhotoAttachment
import ru.nya.nyeios.data.notes.hasVisibleContent
import ru.nya.nyeios.ui.notes.common.LessonTagText
import ru.nya.nyeios.ui.notes.common.NotePhotoStrip
import ru.nya.nyeios.ui.notes.common.NotePhotoViewer
import ru.nya.nyeios.ui.notes.editor.RichTextFormatter
import ru.nya.nyeios.ui.theme.NierBorder
import ru.nya.nyeios.ui.theme.NierBorderLight
import ru.nya.nyeios.ui.theme.NierDark
import ru.nya.nyeios.ui.theme.NierDim
import ru.nya.nyeios.ui.theme.NierPanel
import ru.nya.nyeios.ui.theme.NierPanelAlt
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.NycCyanDk
import ru.nya.nyeios.ui.theme.NycMonoFamily
import ru.nya.nyeios.ui.theme.NycSansFamily
import ru.nya.nyeios.ui.theme.NycShapes
import ru.nya.nyeios.ui.theme.RajdhaniFamily
import ru.nya.nyeios.ui.theme.ShareTechMonoFamily
import ru.nya.nyeios.ui.theme.isNycModern
import ru.nya.nyeios.ui.theme.selectionAccent

/**
 * Всплывающее окошко предпросмотра заметки внизу экрана (долгое нажатие на значок пары).
 * В NyC-modern все углы скруглены согласно NycShapes.
 * В YoRHa сохраняются строгие прямоугольные формы.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonNotePreviewSheet(
    dateString: String,
    lessonNumber: String,
    time: String,
    subject: String,
    teacher: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val repository = remember { LessonNotesRepository.getInstance(context) }
    val notesMap by repository.notesFlow.collectAsState()

    val noteKey = remember(dateString, time, lessonNumber, subject) {
        repository.buildKey(dateString, time, lessonNumber, subject)
    }
    val note = notesMap[noteKey]
    val hasContent = note?.hasVisibleContent == true
    var viewerAttachment by remember { mutableStateOf<NotePhotoAttachment?>(null) }

    val isModern = isNycModern
    val bgCanvas = if (isModern) Color(0xFF0F172A) else NierPanel
    val sheetShape = if (isModern) RoundedCornerShape(topStart = NycShapes.sheetTop, topEnd = NycShapes.sheetTop) else RoundedCornerShape(0.dp)
    val infoCardShape = if (isModern) RoundedCornerShape(14.dp) else RoundedCornerShape(0.dp)
    val noteBoxShape = if (isModern) RoundedCornerShape(12.dp) else RoundedCornerShape(0.dp)
    val buttonShape = if (isModern) RoundedCornerShape(11.dp) else RoundedCornerShape(0.dp)

    val titleFont = if (isModern) NycSansFamily else RajdhaniFamily
    val metaFont = if (isModern) NycMonoFamily else ShareTechMonoFamily

    val textColor = if (isModern) Color(0xFFF1F5F9) else NierDark
    val mutedColor = if (isModern) Color(0xFF94A3B8) else NierDim
    val accentColor = if (isModern) NycCyan else selectionAccent()
    val borderColor = if (isModern) NycCyanDk.copy(alpha = 0.5f) else NierBorder

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = bgCanvas,
        shape = sheetShape,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NoteAlt,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.notes_preview_title),
                        fontFamily = titleFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = if (isModern) 0.5.sp else 1.sp,
                        color = textColor
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = mutedColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Lesson Details Info Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(infoCardShape)
                    .background(if (isModern) Color(0xFF1E293B) else NierPanelAlt, infoCardShape)
                    .border(1.dp, borderColor, infoCardShape)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Pair badge & Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val pairStr = if (lessonNumber.isNotEmpty()) {
                        stringResource(R.string.notes_pair_fmt, lessonNumber)
                    } else {
                        stringResource(R.string.notes_activity_label)
                    }
                    Text(
                        text = "$pairStr · $time",
                        fontFamily = metaFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = accentColor
                    )

                    if (dateString.isNotEmpty()) {
                        Text(
                            text = dateString,
                            fontFamily = metaFont,
                            fontSize = 9.sp,
                            color = mutedColor
                        )
                    }
                }

                // Subject name
                Text(
                    text = subject,
                    fontFamily = titleFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = textColor
                )

                if (teacher.isNotEmpty()) {
                    Text(
                        text = teacher,
                        fontFamily = metaFont,
                        fontSize = 9.sp,
                        color = mutedColor
                    )
                }
            }

            // Note Content Box
            if (hasContent) {
                if (note!!.rawText.isNotBlank()) {
                    val formatted = remember(note.rawText) {
                        RichTextFormatter.htmlToAnnotatedString(note.rawText)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp, max = 240.dp)
                            .clip(noteBoxShape)
                            .background(if (isModern) Color(0xFF131D2A) else textColor.copy(alpha = 0.04f), noteBoxShape)
                            .border(1.dp, accentColor.copy(alpha = 0.4f), noteBoxShape)
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = formatted,
                            fontFamily = titleFont,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = textColor
                        )
                    }
                }
                NotePhotoStrip(
                    attachments = note.photoAttachments.orEmpty(),
                    store = repository.photoStore,
                    textColor = textColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    onOpen = { viewerAttachment = it }
                )
                note.lessonTag?.let { tag ->
                    LessonTagText(
                        tag = tag,
                        accentColor = accentColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.notes_preview_empty),
                        fontFamily = metaFont,
                        fontSize = 11.sp,
                        color = mutedColor
                    )
                    Text(
                        text = stringResource(R.string.notes_preview_empty_hint),
                        fontFamily = metaFont,
                        fontSize = 9.sp,
                        color = mutedColor.copy(alpha = 0.7f)
                    )
                }
            }

            // Action Button: Open in Editor
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(buttonShape)
                    .background(accentColor, buttonShape)
                    // Callers read their pending lesson state inside onEdit before clearing it,
                    // so the sheet must not be dismissed here: onDismiss() would null the state
                    // and make the caller's own onEdit body throw KotlinNullPointerException.
                    .clickable { onEdit() }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = if (isModern) Color.Black else NierPanel,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.notes_preview_edit_btn),
                        fontFamily = titleFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = if (isModern) 0.5.sp else 1.sp,
                        color = if (isModern) Color.Black else NierPanel
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }

    viewerAttachment?.let { attachment ->
        NotePhotoViewer(
            attachment = attachment,
            store = repository.photoStore,
            tag = note?.lessonTag,
            onDismiss = { viewerAttachment = null }
        )
    }
}
