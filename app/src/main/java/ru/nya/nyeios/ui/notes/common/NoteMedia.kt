package ru.nya.nyeios.ui.notes.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import ru.nya.nyeios.R
import ru.nya.nyeios.data.notes.NotePhotoAttachment
import ru.nya.nyeios.data.notes.NotePhotoStore
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.NycMonoFamily
import ru.nya.nyeios.ui.theme.NycSansFamily
import ru.nya.nyeios.ui.theme.RajdhaniFamily
import ru.nya.nyeios.ui.theme.ShareTechMonoFamily
import ru.nya.nyeios.ui.theme.UiPreferencesManager
import ru.nya.nyeios.ui.theme.isNycModern
import ru.nya.nyeios.ui.theme.selectionAccent
import kotlin.math.roundToInt

@Composable
fun LessonTagText(
    tag: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    marquee: Boolean = false,
    fontSize: Int = 10
) {
    val font = if (isNycModern) NycMonoFamily else ShareTechMonoFamily
    if (marquee) {
        PingPongMarqueeText(
            text = tag,
            color = accentColor,
            fontFamily = font,
            fontSize = fontSize,
            modifier = modifier
        )
    } else {
        Text(
            text = tag,
            modifier = modifier,
            color = accentColor,
            fontFamily = font,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Ping-pong marquee with a pause on both edges. Compose's basicMarquee only wraps one way,
 * while long lesson tags need to be read in both directions.
 */
@Composable
fun PingPongMarqueeText(
    text: String,
    color: Color,
    fontFamily: FontFamily,
    fontSize: Int,
    modifier: Modifier = Modifier
) {
    val offset = remember { Animatable(0f) }
    var contentWidth by remember { mutableIntStateOf(0) }
    var containerWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { containerWidth = it.width }
    ) {
        Text(
            text = text,
            color = color,
            fontFamily = fontFamily,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { layout -> contentWidth = layout.getLineRight(0).roundToInt() },
            modifier = Modifier.graphicsLayer {
                translationX = -offset.value
                clip = false
            }
        )
    }

    val overflow = (contentWidth - containerWidth).coerceAtLeast(0)
    LaunchedEffect(text, overflow, UiPreferencesManager.animationsEnabled) {
        offset.snapTo(0f)
        if (overflow <= 0 || !UiPreferencesManager.animationsEnabled) return@LaunchedEffect
        val duration = ((overflow / density.density) / 24f * 1000f).roundToInt().coerceAtLeast(500)
        while (true) {
            delay(900)
            offset.animateTo(overflow.toFloat(), tween(duration, easing = LinearEasing))
            delay(1100)
            offset.animateTo(0f, tween(duration, easing = LinearEasing))
        }
    }
}

@Composable
fun NotePhotoStrip(
    attachments: List<NotePhotoAttachment>,
    store: NotePhotoStore,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onOpen: (NotePhotoAttachment) -> Unit,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return
    val isModern = isNycModern
    val cardShape = if (isModern) RoundedCornerShape(12.dp) else RoundedCornerShape(0.dp)
    val wellShape = if (isModern) RoundedCornerShape(9.dp) else RoundedCornerShape(0.dp)
    val titleFont = if (isModern) NycSansFamily else RajdhaniFamily

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(attachments, key = { it.id.ifBlank { it.relativePath } }) { attachment ->
            val file = remember(attachment.relativePath) { store.resolve(attachment) }
            Column(
                modifier = Modifier
                    .width(168.dp)
                    .clip(cardShape)
                    .background(
                        if (isModern) Color(0xFF131D2A) else textColor.copy(alpha = 0.04f),
                        cardShape
                    )
                    .border(1.dp, accentColor.copy(alpha = 0.25f), cardShape)
                    .clickable { onOpen(attachment) }
                    .padding(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(wellShape)
                        .background(Color.Black.copy(alpha = 0.18f), wellShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (file != null && file.exists()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(file)
                                .crossfade(UiPreferencesManager.animationsEnabled)
                                .build(),
                            contentDescription = stringResource(R.string.notes_photo_open),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.BrokenImage,
                            contentDescription = stringResource(R.string.notes_photo_missing),
                            tint = mutedColor
                        )
                    }
                }
                if (attachment.caption.isNotBlank()) {
                    Text(
                        text = attachment.caption,
                        color = textColor,
                        fontFamily = titleFont,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun NotePhotoViewer(
    attachment: NotePhotoAttachment,
    store: NotePhotoStore,
    tag: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val file = remember(attachment.relativePath) { store.resolve(attachment) }
    val textColor = if (isNycModern) Color(0xFFE2E8F0) else Color(0xFFE4DFCC)
    val mutedColor = if (isNycModern) Color(0xFF94A3B8) else Color(0xFFB8B29D)
    val accentColor = if (isNycModern) NycCyan else selectionAccent()
    val titleFont = if (isNycModern) NycSansFamily else RajdhaniFamily
    val metaFont = if (isNycModern) NycMonoFamily else ShareTechMonoFamily

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isNycModern) Color(0xFF070B12) else Color(0xFF14130F))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = textColor
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (file != null && file.exists()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(file)
                                .crossfade(UiPreferencesManager.animationsEnabled)
                                .build(),
                            contentDescription = attachment.caption.ifBlank {
                                stringResource(R.string.notes_photo_open)
                            },
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.BrokenImage,
                                contentDescription = null,
                                tint = mutedColor,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = stringResource(R.string.notes_photo_missing),
                                color = mutedColor,
                                fontFamily = metaFont,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (attachment.caption.isNotBlank() || tag != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (attachment.caption.isNotBlank()) {
                            Text(
                                text = attachment.caption,
                                color = textColor,
                                fontFamily = titleFont,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                        if (tag != null) {
                            LessonTagText(
                                tag = tag,
                                accentColor = accentColor,
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = 11
                            )
                        }
                    }
                }
            }
        }
    }
}
