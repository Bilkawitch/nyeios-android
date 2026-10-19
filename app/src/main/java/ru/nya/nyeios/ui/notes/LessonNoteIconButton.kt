package ru.nya.nyeios.ui.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.nya.nyeios.R
import ru.nya.nyeios.ui.theme.NierBorderLight
import ru.nya.nyeios.ui.theme.NierDim
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.isNycModern
import ru.nya.nyeios.ui.theme.selectionAccent

/**
 * Кнопка-значок заметки в карточке пары (Скриншот 1).
 * Краткий клик (onClick) -> открывает редактор заметки.
 * Долгий клик (onLongClick) -> открывает предпросмотр содержимого внизу экрана.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LessonNoteIconButton(
    hasNote: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = if (isNycModern) NycCyan else selectionAccent(),
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val isModern = isNycModern
    val shape = if (isModern) RoundedCornerShape(7.dp) else RoundedCornerShape(0.dp)

    val bg = if (hasNote) {
        accentColor.copy(alpha = 0.22f)
    } else {
        Color.Transparent
    }

    val border = if (hasNote) {
        accentColor
    } else {
        if (isModern) NycCyan.copy(alpha = 0.25f) else NierBorderLight.copy(alpha = 0.5f)
    }

    val iconTint = if (hasNote) {
        accentColor
    } else {
        if (isModern) NycCyan.copy(alpha = 0.45f) else NierDim.copy(alpha = 0.5f)
    }

    Box(
        modifier = modifier
            .size(26.dp)
            .background(bg, shape)
            .border(1.dp, border, shape)
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.EditNote,
            contentDescription = stringResource(R.string.notes_editor_title),
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )

        // Индикатор наличия сохраненной записи
        if (hasNote) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
        }
    }
}
