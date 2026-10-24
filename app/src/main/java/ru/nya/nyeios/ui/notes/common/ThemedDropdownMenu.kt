package ru.nya.nyeios.ui.notes.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import ru.nya.nyeios.ui.theme.NierBorder
import ru.nya.nyeios.ui.theme.NierPanel
import ru.nya.nyeios.ui.theme.NycCyanDk
import ru.nya.nyeios.ui.theme.isNycModern

/**
 * Dropdown that follows the app's two shape systems instead of Material's default menu corners.
 * It opens below the anchor and flips above it when there is not enough room.
 */
@Composable
fun ThemedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 360.dp,
    maxHeight: Dp = 420.dp,
    alignEnd: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded) return
    val isModern = isNycModern
    val shape = if (isModern) RoundedCornerShape(12.dp) else RoundedCornerShape(0.dp)
    val bg = if (isModern) Color(0xFF131D2A) else NierPanel
    val border = if (isModern) NycCyanDk.copy(alpha = 0.7f) else NierBorder
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val width = minOf(maxWidth, screenWidth - 16.dp)
    val marginPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val gapPx = with(LocalDensity.current) { 4.dp.roundToPx() }

    Popup(
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val contentWidth = minOf(
                    popupContentSize.width,
                    windowSize.width - marginPx * 2
                )
                val x = if (alignEnd) {
                    anchorBounds.right - contentWidth
                } else {
                    anchorBounds.left
                }.coerceIn(marginPx, (windowSize.width - marginPx - contentWidth).coerceAtLeast(marginPx))
                val below = anchorBounds.bottom + gapPx
                val y = if (below + popupContentSize.height <= windowSize.height - marginPx) {
                    below
                } else {
                    (anchorBounds.top - gapPx - popupContentSize.height)
                        .coerceIn(marginPx, (windowSize.height - marginPx - popupContentSize.height).coerceAtLeast(marginPx))
                }
                return IntOffset(x, y)
            }
        },
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = modifier
                .width(width)
                .heightIn(max = maxHeight)
                .clip(shape)
                .background(bg, shape)
                .border(1.dp, border, shape)
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                content = content
            )
        }
    }
}
