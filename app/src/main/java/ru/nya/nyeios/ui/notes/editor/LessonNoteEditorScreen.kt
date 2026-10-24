package ru.nya.nyeios.ui.notes.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.nya.nyeios.R
import ru.nya.nyeios.data.notes.LessonNote
import ru.nya.nyeios.data.notes.LessonNotesRepository
import ru.nya.nyeios.data.notes.NotePhotoAttachment
import ru.nya.nyeios.data.notes.NotePhotoStore
import ru.nya.nyeios.data.notes.hasVisibleContent
import ru.nya.nyeios.ui.notes.common.LessonTagText
import ru.nya.nyeios.ui.notes.common.NotePhotoViewer
import ru.nya.nyeios.ui.notes.common.ThemedDropdownMenu
import ru.nya.nyeios.ui.theme.NierBg
import ru.nya.nyeios.ui.theme.NierBorderLight
import ru.nya.nyeios.ui.theme.NierDark
import ru.nya.nyeios.ui.theme.NierDim
import ru.nya.nyeios.ui.theme.NierPanel
import ru.nya.nyeios.ui.theme.NierPanelAlt
import ru.nya.nyeios.ui.theme.NierSelectionText
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.NycCyanDk
import ru.nya.nyeios.ui.theme.NycMonoFamily
import ru.nya.nyeios.ui.theme.NycSansFamily
import ru.nya.nyeios.ui.theme.NycShapes
import ru.nya.nyeios.ui.theme.RajdhaniFamily
import ru.nya.nyeios.ui.theme.ShareTechMonoFamily
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
    val photoStore = repository.photoStore
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val noteKey = remember(dateString, time, lessonNumber, subject) {
        repository.buildKey(dateString, time, lessonNumber, subject)
    }

    val existingNote = remember(noteKey) { repository.getNote(noteKey) }
    val initialRawText = remember(noteKey) { existingNote?.rawText ?: "" }
    var photoAttachments by remember(noteKey) {
        mutableStateOf(existingNote?.photoAttachments.orEmpty())
    }
    var viewerAttachment by remember { mutableStateOf<NotePhotoAttachment?>(null) }
    var attachMenuOpen by remember { mutableStateOf(false) }
    var pendingCameraFile by remember { mutableStateOf<java.io.File?>(null) }
    var saved by remember { mutableStateOf(false) }

    val editorHighlightColor = if (isNycModern) NycCyan.copy(alpha = 0.35f) else Color(0xFFEAB308).copy(alpha = 0.35f)
    val editorState = rememberRichTextEditorState(
        initialRawText = initialRawText,
        highlightColor = editorHighlightColor
    )
    val focusRequester = remember { FocusRequester() }

    fun saveCurrent() {
        if (saved) return
        saved = true
        val rawHtml = editorState.toRawHtml()
        val plainSnippet = editorState.toPlainSnippet()
        val note = LessonNote(
            key = noteKey,
            date = dateString,
            lessonNumber = lessonNumber,
            time = time,
            subject = subject,
            teacher = teacher,
            rawText = rawHtml,
            plainSnippet = plainSnippet,
            photoAttachments = photoAttachments,
            updatedAt = System.currentTimeMillis()
        )
        if (!note.hasVisibleContent) {
            repository.deleteNote(noteKey)
        } else {
            repository.saveNote(note)
        }
    }

    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val slots = NotePhotoStore.MAX_PHOTOS_PER_NOTE - photoAttachments.size
        if (slots <= 0) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.notes_photo_limit, NotePhotoStore.MAX_PHOTOS_PER_NOTE)
                )
            }
            return
        }
        scope.launch {
            val accepted = uris.take(slots)
            val imported = withContext(Dispatchers.IO) {
                accepted.mapNotNull { uri -> photoStore.importUri(noteKey, uri) }
            }
            if (imported.isNotEmpty()) {
                photoAttachments = photoAttachments + imported
            }
            if (imported.size < accepted.size) {
                snackbarHostState.showSnackbar(context.getString(R.string.notes_photo_import_failed))
            } else if (uris.size > slots) {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.notes_photo_limit, NotePhotoStore.MAX_PHOTOS_PER_NOTE)
                )
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(NotePhotoStore.MAX_PHOTOS_PER_NOTE)
    ) { uris -> addPhotos(uris) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        pendingCameraFile = null
        if (success && file != null) {
            scope.launch {
                val attachment = withContext(Dispatchers.IO) {
                    photoStore.attachmentForCamera(noteKey, file)
                }
                if (attachment != null) {
                    photoAttachments = photoAttachments + attachment
                } else {
                    snackbarHostState.showSnackbar(context.getString(R.string.notes_photo_import_failed))
                }
            }
        } else {
            file?.delete()
        }
    }

    fun launchCamera() {        if (photoAttachments.size >= NotePhotoStore.MAX_PHOTOS_PER_NOTE) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.notes_photo_limit, NotePhotoStore.MAX_PHOTOS_PER_NOTE)
                )
            }
            return
        }
        val pair = photoStore.createCameraFile(noteKey)
        if (pair == null) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.notes_photo_import_failed))
            }
            return
        }
        pendingCameraFile = pair.first
        cameraLauncher.launch(pair.second)
    }

    val saveRef = rememberUpdatedState(newValue = { saveCurrent() })
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    DisposableEffect(Unit) {
        onDispose { saveRef.value() }
    }
    BackHandler {
        if (imeVisible) {
            keyboardController?.hide()
        } else {
            saveCurrent()
            onDismiss()
        }
    }

    val isModern = isNycModern
    val bgCanvas = if (isModern) Color(0xFF0F172A) else NierBg
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
    val tag = remember(subject) { LessonTagTextTag(subject) }

    Dialog(
        onDismissRequest = {
            saveCurrent()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgCanvas)
                .statusBarsPadding()
                .imePadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
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
                                val pairLabel = if (lessonNumber.isNotEmpty()) {
                                    stringResource(R.string.notes_pair_fmt, lessonNumber)
                                } else {
                                    stringResource(R.string.notes_activity_label)
                                }
                                Text(
                                    text = "$pairLabel · $time",
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
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .heightIn(min = 44.dp)
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (isModern) 6.dp else 0.dp)
                        .clip(toolbarShape)
                        .background(panelBg.copy(alpha = 0.95f), toolbarShape)
                        .border(width = 1.dp, color = borderColor, shape = toolbarShape)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EditorToolbarButton(Icons.Default.FormatBold, stringResource(R.string.notes_toolbar_bold), editorState.isBoldSelected, accentColor, textColor) { editorState.toggleBold() }
                    EditorToolbarButton(Icons.Default.FormatItalic, stringResource(R.string.notes_toolbar_italic), editorState.isItalicSelected, accentColor, textColor) { editorState.toggleItalic() }
                    EditorToolbarButton(Icons.Default.FormatUnderlined, stringResource(R.string.notes_toolbar_underline), editorState.isUnderlineSelected, accentColor, textColor) { editorState.toggleUnderline() }
                    EditorToolbarButton(Icons.Default.FormatColorFill, stringResource(R.string.notes_toolbar_highlight), editorState.isHighlightSelected, accentColor, textColor) { editorState.toggleHighlight() }
                    ToolbarDivider(borderColor)
                    EditorToolbarButton(Icons.AutoMirrored.Filled.FormatListBulleted, stringResource(R.string.notes_toolbar_bullet), false, accentColor, textColor) { editorState.toggleBulletList() }
                    EditorToolbarButton(Icons.Default.FormatListNumbered, stringResource(R.string.notes_toolbar_numbered), false, accentColor, textColor) { editorState.toggleNumberedList() }
                    EditorToolbarButton(Icons.Default.FormatClear, stringResource(R.string.notes_toolbar_clear), false, accentColor, textColor) { editorState.clearFormatting() }
                    ToolbarDivider(borderColor)
                    EditorToolbarButton(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.notes_toolbar_undo), false, accentColor, textColor, editorState.canUndo) { editorState.undo() }
                    EditorToolbarButton(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.notes_toolbar_redo), false, accentColor, textColor, editorState.canRedo) { editorState.redo() }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "text") {
                        val cardShape = if (isModern) RoundedCornerShape(NycShapes.card) else RoundedCornerShape(0.dp)
                        val cardBg = if (isModern) Color(0xFF131D2A) else Color.Transparent
                        val cardBorder = if (isModern) borderColor.copy(alpha = 0.35f) else Color.Transparent
                        BasicTextField(
                            value = editorState.textFieldValue,
                            onValueChange = { editorState.onValueChange(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 190.dp)
                                .clip(cardShape)
                                .background(cardBg, cardShape)
                                .border(if (isModern) 1.dp else 0.dp, cardBorder, cardShape)
                                .padding(14.dp)
                                .focusRequester(focusRequester),
                            textStyle = TextStyle(
                                color = textColor,
                                fontSize = 14.sp,
                                fontFamily = titleFont,
                                lineHeight = 20.sp
                            ),
                            cursorBrush = SolidColor(accentColor),
                            decorationBox = { inner ->
                                if (editorState.textFieldValue.text.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.notes_editor_placeholder),
                                        fontSize = 14.sp,
                                        fontFamily = titleFont,
                                        color = mutedColor.copy(alpha = 0.6f),
                                        lineHeight = 20.sp
                                    )
                                }
                                inner()
                            }
                        )
                    }

                    if (photoAttachments.isNotEmpty()) {
                        items(photoAttachments, key = { it.id.ifBlank { it.relativePath } }) { attachment ->
                            EditablePhotoCard(
                                attachment = attachment,
                                store = photoStore,
                                textColor = textColor,
                                mutedColor = mutedColor,
                                accentColor = accentColor,
                                borderColor = borderColor,
                                onCaptionChange = { caption ->
                                    photoAttachments = photoAttachments.map {
                                        if (it.id == attachment.id) it.copy(caption = caption) else it
                                    }
                                },
                                onOpen = { viewerAttachment = attachment },
                                onRemove = {
                                    photoAttachments = photoAttachments.filterNot { it.id == attachment.id }
                                    scope.launch(Dispatchers.IO) { photoStore.deleteAttachment(attachment) }
                                }
                            )
                        }
                    }

                    item(key = "attach") {
                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .clip(if (isModern) RoundedCornerShape(11.dp) else RoundedCornerShape(0.dp))
                                    .border(
                                        1.dp,
                                        accentColor.copy(alpha = 0.45f),
                                        if (isModern) RoundedCornerShape(11.dp) else RoundedCornerShape(0.dp)
                                    )
                                    .clickable {
                                        attachMenuOpen = true
                                    }
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddAPhoto,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.notes_photo_add),
                                    color = accentColor,
                                    fontFamily = titleFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            ThemedDropdownMenu(
                                expanded = attachMenuOpen,
                                onDismissRequest = { attachMenuOpen = false },
                                maxWidth = 240.dp,
                                maxHeight = 200.dp,
                                alignEnd = false
                            ) {
                                MenuActionRow(
                                    text = stringResource(R.string.notes_photo_gallery),
                                    color = textColor
                                ) {
                                    attachMenuOpen = false
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                }
                                MenuActionRow(
                                    text = stringResource(R.string.notes_photo_camera),
                                    color = textColor
                                ) {
                                    attachMenuOpen = false
                                    launchCamera()
                                }
                            }
                        }
                    }

                    if (tag != null) {
                        item(key = "tag") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(borderColor.copy(alpha = 0.45f))
                                )
                                LessonTagText(
                                    tag = tag,
                                    accentColor = accentColor,
                                    modifier = Modifier.fillMaxWidth(),
                                    fontSize = 10
                                )
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )

            viewerAttachment?.let { attachment ->
                NotePhotoViewer(
                    attachment = attachment,
                    store = photoStore,
                    tag = tag,
                    onDismiss = { viewerAttachment = null }
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
    }
}

private fun LessonTagTextTag(subject: String): String? =
    ru.nya.nyeios.data.notes.LessonTag.fromSubject(subject)

@Composable
private fun MenuActionRow(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    val font = if (isNycModern) NycSansFamily else RajdhaniFamily
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = color,
            fontFamily = font,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ToolbarDivider(color: Color) {
    Box(
        modifier = Modifier
            .height(20.dp)
            .width(1.dp)
            .background(color)
    )
}

@Composable
private fun EditablePhotoCard(
    attachment: NotePhotoAttachment,
    store: NotePhotoStore,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    borderColor: Color,
    onCaptionChange: (String) -> Unit,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    val isModern = isNycModern
    val cardShape = if (isModern) RoundedCornerShape(NycShapes.card) else RoundedCornerShape(0.dp)
    val wellShape = if (isModern) RoundedCornerShape(NycShapes.well) else RoundedCornerShape(0.dp)
    val bodyFont = if (isModern) NycSansFamily else RajdhaniFamily
    val file = remember(attachment.relativePath) { store.resolve(attachment) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(if (isModern) Color(0xFF131D2A) else NierPanelAlt, cardShape)
            .border(1.dp, borderColor.copy(alpha = 0.6f), cardShape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(wellShape)
                .background(Color.Black.copy(alpha = 0.18f), wellShape)
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center
        ) {
            if (file != null && file.exists()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(file)
                        .crossfade(UiAnimationsEnabled)
                        .build(),
                    contentDescription = stringResource(R.string.notes_photo_open),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.BrokenImage,
                    contentDescription = stringResource(R.string.notes_photo_missing),
                    tint = mutedColor
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.notes_photo_remove),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        BasicTextField(
            value = attachment.caption,
            onValueChange = onCaptionChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 42.dp)
                .clip(if (isModern) RoundedCornerShape(8.dp) else RoundedCornerShape(0.dp))
                .background(if (isModern) Color(0xFF0F172A) else Color.Transparent, if (isModern) RoundedCornerShape(8.dp) else RoundedCornerShape(0.dp))
                .padding(horizontal = 10.dp, vertical = 9.dp),
            textStyle = TextStyle(
                color = textColor,
                fontFamily = bodyFont,
                fontSize = 12.sp,
                lineHeight = 16.sp
            ),
            cursorBrush = SolidColor(accentColor),
            maxLines = 4,
            decorationBox = { inner ->
                if (attachment.caption.isEmpty()) {
                    Text(
                        text = stringResource(R.string.notes_photo_caption),
                        color = mutedColor.copy(alpha = 0.65f),
                        fontFamily = bodyFont,
                        fontSize = 12.sp
                    )
                }
                inner()
            }
        )
    }
}

private val UiAnimationsEnabled: Boolean
    get() = ru.nya.nyeios.ui.theme.UiPreferencesManager.animationsEnabled

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
            .size(48.dp)
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
