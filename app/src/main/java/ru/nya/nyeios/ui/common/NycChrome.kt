package ru.nya.nyeios.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.NycMonoFamily
import ru.nya.nyeios.ui.theme.NycSansFamily
import ru.nya.nyeios.ui.theme.NycShapes
import ru.nya.nyeios.ui.theme.nycRaised

// Parallel NyC-modern chrome from mockup nyeios_redesign.html (appbar 64px, bnav 76px).
// Render-only: all state and callbacks are computed by MainActivity exactly as for
// the legacy chrome, so business logic is shared and untouched.

// ---------- Top app bar ----------

@Composable
fun NycTopBar(
    title: String,
    freshText: String,
    freshColor: Color,
    isSyncingFeed: Boolean,
    version: String,
    group: String?,
    showDownloads: Boolean,
    downloadCount: Int,
    onDownloads: () -> Unit,
    profileEnabled: Boolean,
    isLoggedIn: Boolean,
    onProfile: () -> Unit,
    showRefresh: Boolean,
    isRefreshing: Boolean,
    isStale: Boolean,
    onRefresh: () -> Unit,
    hasPendingUpdate: Boolean,
    onLogoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val text = Color(0xFFDBE4F0)
    val muted = Color(0xFF8794A7)
    val faint = Color(0xFF5B6A7E)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF121A26))
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        // Logo 42dp, halo pulse when an update is pending.
        val halo = rememberInfiniteTransition(label = "nyc_halo")
        val haloAlpha by halo.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "halo_alpha"
        )
        Box(
            modifier = Modifier
                .size(42.dp)
                .then(
                    if (hasPendingUpdate) {
                        Modifier.shadow(
                            12.dp,
                            RoundedCornerShape(NycShapes.logo),
                            ambientColor = NycCyan.copy(alpha = haloAlpha * 0.5f),
                            spotColor = NycCyan.copy(alpha = haloAlpha * 0.5f)
                        )
                    } else Modifier
                )
                .nycRaised(NycShapes.logo)
                .clickable { onLogoClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Ny",
                fontFamily = NycSansFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = NycCyan,
                // textShadow approximated with drawBehind glow is skipped; solid cyan per mockup
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onLogoClick() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    fontFamily = NycSansFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.5.sp,
                    color = text,
                    maxLines = 1
                )
                if (version.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(150, 185, 235, alpha = 31))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = version,
                            fontFamily = NycMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = muted
                        )
                    }
                }
                if (!group.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(111, 168, 255, alpha = 38))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = group,
                            fontFamily = NycMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = Color(0xFF6FA8FF)
                        )
                    }
                }
            }
            if (isSyncingFeed) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(top = 3.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(9.dp),
                        strokeWidth = 1.5.dp,
                        color = Color(0xFF46E08C)
                    )
                    Text(
                        text = "Синхр. ленты...",
                        fontFamily = NycMonoFamily,
                        fontSize = 9.5.sp,
                        color = Color(0xFF46E08C)
                    )
                }
            } else if (freshText.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(top = 3.dp)
                ) {
                    Text(text = "▲", fontSize = 8.sp, color = freshColor)
                    Text(
                        text = freshText,
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.5.sp,
                        color = muted
                    )
                }
            }
        }

        if (showDownloads) {
            NycIconButton(
                onClick = onDownloads,
                badgeCount = downloadCount,
                contentDescription = "Загрузки"
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    tint = if (downloadCount > 0) NycCyan else muted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Profile
        Box(
            modifier = Modifier
                .size(42.dp)
                .nycRaised(13.dp)
                .clickable(enabled = profileEnabled) { onProfile() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Профиль",
                tint = if (isLoggedIn) Color(0xFF46E08C) else muted,
                modifier = Modifier.size(20.dp)
            )
        }

        if (showRefresh) {
            val blink = rememberInfiniteTransition(label = "nyc_btnblink")
            val blinkPhase by blink.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
                label = "blink"
            )
            val blinking = isStale && !isRefreshing
            val spin = rememberInfiniteTransition(label = "nyc_spin")
            val rot by spin.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
                label = "rot"
            )
            // faint is read to keep the mockup token set referenced; tint below uses muted
            @Suppress("UNUSED_EXPRESSION")
            faint
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .nycRaised(13.dp)
                    .then(
                        if (blinking) {
                            Modifier.border(
                                1.dp,
                                NycCyan.copy(alpha = if (blinkPhase < 0.5f) 0.2f else 0.6f),
                                RoundedCornerShape(13.dp)
                            )
                        } else Modifier
                    )
                    .clickable { onRefresh() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Обновить",
                    tint = if (isRefreshing) NycCyan else muted,
                    modifier = Modifier
                        .size(20.dp)
                        .then(if (isRefreshing) Modifier.rotate(rot) else Modifier)
                )
            }
        }
    }
}

@Composable
private fun NycIconButton(
    onClick: () -> Unit,
    badgeCount: Int,
    contentDescription: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .nycRaised(13.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        content()
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2FB4B6))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "$badgeCount",
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = Color(0xFF03211F),
                    lineHeight = 10.sp
                )
            }
        }
    }
}

// ---------- Bottom navigation ----------

private data class NycTab(val index: Int, val icon: ImageVector, val title: String)

@Composable
fun NycBottomNav(
    currentTab: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val faint = Color(0xFF5B6A7E)
    val tabs = listOf(
        NycTab(0, Icons.Default.CalendarToday, "Расписание"),
        NycTab(1, Icons.Default.DynamicFeed, "Лента"),
        NycTab(2, Icons.Default.School, "БРС"),
        NycTab(3, Icons.Default.Settings, "Настройки")
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(colors = listOf(Color(0xFF0E141E), Color(0xFF090E15)))
            )
            .drawBehind {
                drawLine(
                    color = Color(165, 200, 245, alpha = 14),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEach { tab ->
            val selected = tab.index == currentTab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(66.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(tab.index) }
                    .drawBehind {
                        if (selected) {
                            val w = 26.dp.toPx()
                            drawRect(
                                color = NycCyan,
                                topLeft = Offset((size.width - w) / 2f, 0f),
                                size = androidx.compose.ui.geometry.Size(w, 2.dp.toPx())
                            )
                        }
                    }
                    .padding(top = 6.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(46.dp)
                        .height(34.dp)
                        .then(
                            if (selected) Modifier.nycRaised(12.dp)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        tint = if (selected) NycCyan else faint,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tab.title.uppercase(),
                    fontFamily = NycMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 7.8.sp,
                    letterSpacing = 1.sp,
                    color = if (selected) NycCyan else faint
                )
            }
        }
    }
}
