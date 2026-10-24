package ru.nya.nyeios.ui.notes.weekly

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nya.nyeios.R
import ru.nya.nyeios.ui.notes.common.PingPongMarqueeText
import ru.nya.nyeios.ui.notes.common.ThemedDropdownMenu
import ru.nya.nyeios.ui.theme.NierBorder
import ru.nya.nyeios.ui.theme.NierDark
import ru.nya.nyeios.ui.theme.NierDim
import ru.nya.nyeios.ui.theme.NierPanel
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.NycCyanDk
import ru.nya.nyeios.ui.theme.NycMonoFamily
import ru.nya.nyeios.ui.theme.NycSansFamily
import ru.nya.nyeios.ui.theme.RajdhaniFamily
import ru.nya.nyeios.ui.theme.ShareTechMonoFamily
import ru.nya.nyeios.ui.theme.isNycModern
import ru.nya.nyeios.ui.theme.selectionAccent

@Composable
fun WeeklyNotesSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    tags: List<String>,
    selectedTag: String?,
    onTagSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val isModern = isNycModern
    val textColor = if (isModern) Color(0xFFF1F5F9) else NierDark
    val mutedColor = if (isModern) Color(0xFF94A3B8) else NierDim
    val accentColor = if (isModern) NycCyan else selectionAccent()
    val borderColor = if (isModern) NycCyanDk.copy(alpha = 0.5f) else NierBorder
    val panelBg = if (isModern) Color(0xFF131D2A) else NierPanel
    val shape = if (isModern) RoundedCornerShape(11.dp) else RoundedCornerShape(0.dp)
    val chipShape = if (isModern) RoundedCornerShape(9.dp) else RoundedCornerShape(0.dp)
    val bodyFont = if (isModern) NycSansFamily else RajdhaniFamily
    val metaFont = if (isModern) NycMonoFamily else ShareTechMonoFamily
    val keyboard = LocalSoftwareKeyboardController.current
    var tagMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(panelBg, shape)
                .border(1.dp, borderColor, shape)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = mutedColor,
                modifier = Modifier.size(18.dp)
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 9.dp, vertical = 13.dp),
                singleLine = true,
                textStyle = TextStyle(
                    color = textColor,
                    fontFamily = bodyFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(accentColor),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Search
                ),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.notes_search_placeholder),
                                color = mutedColor.copy(alpha = 0.7f),
                                fontFamily = bodyFont,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                        inner()
                    }
                }
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.notes_search_clear),
                        tint = mutedColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Box {
                IconButton(
                    onClick = {
                        keyboard?.hide()
                        tagMenuOpen = true
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sell,
                        contentDescription = stringResource(R.string.notes_filter_tag),
                        tint = if (selectedTag == null) mutedColor else accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                ThemedDropdownMenu(
                    expanded = tagMenuOpen,
                    onDismissRequest = { tagMenuOpen = false }
                ) {
                    TagMenuRow(
                        label = stringResource(R.string.notes_all_tags),
                        selected = selectedTag == null,
                        accentColor = accentColor,
                        textColor = textColor,
                        fontFamily = metaFont
                    ) {
                        onTagSelected(null)
                        tagMenuOpen = false
                    }
                    tags.forEach { tag ->
                        TagMenuRow(
                            label = tag,
                            selected = tag == selectedTag,
                            accentColor = accentColor,
                            textColor = textColor,
                            fontFamily = metaFont
                        ) {
                            onTagSelected(tag)
                            tagMenuOpen = false
                        }
                    }
                }
            }
        }

        if (selectedTag != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(chipShape)
                    .background(accentColor.copy(alpha = 0.10f), chipShape)
                    .border(1.dp, accentColor.copy(alpha = 0.45f), chipShape)
                    .padding(start = 10.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PingPongMarqueeText(
                    text = selectedTag,
                    color = accentColor,
                    fontFamily = metaFont,
                    fontSize = 11,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onTagSelected(null) },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.notes_selected_tag_clear),
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TagMenuRow(
    label: String,
    selected: Boolean,
    accentColor: Color,
    textColor: Color,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .background(if (selected) accentColor.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
        }
        PingPongMarqueeText(
            text = label,
            color = if (selected) accentColor else textColor,
            fontFamily = fontFamily,
            fontSize = 11,
            modifier = Modifier.weight(1f)
        )
    }
}
