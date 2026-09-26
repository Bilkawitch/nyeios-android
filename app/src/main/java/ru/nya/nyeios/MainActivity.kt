package ru.nya.nyeios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import ru.nya.nyeios.ui.theme.appPillShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.nya.nyeios.data.update.AppVersionProvider
import ru.nya.nyeios.data.model.CurriculumUiState
import ru.nya.nyeios.data.model.FeedUiState
import ru.nya.nyeios.data.model.ScheduleUiState
import ru.nya.nyeios.ui.auth.LoginBottomSheet
import ru.nya.nyeios.ui.auth.NycLoginGate
import ru.nya.nyeios.ui.auth.LoginFullscreenGate
import ru.nya.nyeios.ui.debug.NetworkLogsBottomSheet
import ru.nya.nyeios.ui.debug.NycLogsSheet
import ru.nya.nyeios.ui.curriculum.CurriculumScreen
import ru.nya.nyeios.ui.curriculum.NycCurriculumScreen
import ru.nya.nyeios.ui.curriculum.CurriculumViewModel
import ru.nya.nyeios.ui.feed.FeedScreen
import ru.nya.nyeios.ui.feed.NycFeedScreen
import ru.nya.nyeios.ui.feed.FeedViewModel
import ru.nya.nyeios.ui.common.NycBottomNav
import ru.nya.nyeios.ui.common.NycTopBar
import ru.nya.nyeios.ui.schedule.ScheduleScreen
import ru.nya.nyeios.ui.schedule.NycScheduleScreen
import ru.nya.nyeios.ui.schedule.ScheduleViewModel
import ru.nya.nyeios.ui.settings.SettingsScreen
import ru.nya.nyeios.ui.settings.NycSettingsScreen
import ru.nya.nyeios.ui.settings.SettingsViewModel
import ru.nya.nyeios.ui.theme.LectureBlue
import ru.nya.nyeios.ui.theme.NyEIOSTheme
import ru.nya.nyeios.ui.theme.ObsidianBg
import ru.nya.nyeios.ui.theme.ObsidianBorder
import ru.nya.nyeios.ui.theme.ObsidianSurface
import ru.nya.nyeios.ui.theme.PracticeGreen
import ru.nya.nyeios.ui.theme.TextMuted
import ru.nya.nyeios.ui.theme.ExamRed
import ru.nya.nyeios.ui.theme.TextPrimary
import ru.nya.nyeios.ui.theme.TextSecondary
import ru.nya.nyeios.ui.update.UpdateBanner
import ru.nya.nyeios.ui.update.UpdateViewModel
import ru.nya.nyeios.data.model.UpdateUiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ru.nya.nyeios.ui.theme.ThemeManager.init(this)
        enableEdgeToEdge()

        setContent {
            NyEIOSTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObsidianBg
                ) {
                    val scheduleViewModel: ScheduleViewModel = viewModel()
                    val feedViewModel: FeedViewModel = viewModel()
                    val curriculumViewModel: CurriculumViewModel = viewModel()
                    val updateViewModel: UpdateViewModel = viewModel()
                    val settingsViewModel: SettingsViewModel = viewModel()

                    val context = LocalContext.current
                    val currentAppVersion = remember(context) { AppVersionProvider.getVersionName(context) }

                    var currentTab by rememberSaveable { mutableIntStateOf(0) }

                    val scheduleUiState by scheduleViewModel.uiState.collectAsState()
                    val weekOffset by scheduleViewModel.weekOffset.collectAsState()
                    val selectedDayIndex by scheduleViewModel.selectedDayIndex.collectAsState()

                    val feedUiState by feedViewModel.uiState.collectAsState()
                    val feedSyncProgress by feedViewModel.feedSyncProgress.collectAsState()
                    val curriculumUiState by curriculumViewModel.uiState.collectAsState()
                    val settingsUiState by settingsViewModel.uiState.collectAsState()

                    val authSession by scheduleViewModel.authSession.collectAsState()
                    val isLoginSheetVisible by scheduleViewModel.isLoginSheetVisible.collectAsState()
                    val isLoggingIn by scheduleViewModel.isLoggingIn.collectAsState()
                    val loginError by scheduleViewModel.loginError.collectAsState()
                    val lastSyncTime by scheduleViewModel.lastSyncTime.collectAsState()
                    var isNetworkLogsSheetVisible by rememberSaveable { mutableStateOf(false) }

                    val updateUiState by updateViewModel.uiState.collectAsState()
                    val hasPendingUpdate = updateUiState is UpdateUiState.Dismissed
                    val isNycModern = ru.nya.nyeios.ui.theme.ThemeManager.currentTheme ==
                        ru.nya.nyeios.ui.theme.NierThemeMode.NYC_MODERN

                    LaunchedEffect(Unit) {
                        updateViewModel.checkForUpdate()
                    }

                    val isRefreshing = when (currentTab) {
                        0 -> (scheduleUiState as? ScheduleUiState.Success)?.isRefreshing == true || scheduleUiState is ScheduleUiState.Loading
                        1 -> feedSyncProgress.isSyncing || (feedUiState as? FeedUiState.Success)?.isRefreshing == true || feedUiState is FeedUiState.Loading
                        2 -> (curriculumUiState as? CurriculumUiState.Success)?.isRefreshing == true || curriculumUiState is CurriculumUiState.Loading
                        else -> false
                    }

                    val currentTimeMs by produceState(initialValue = System.currentTimeMillis()) {
                        while (true) {
                            kotlinx.coroutines.delay(10_000L)
                            value = System.currentTimeMillis()
                        }
                    }

                    val syncState = ru.nya.nyeios.ui.common.SyncStatusUtils.getSyncState(lastSyncTime, currentTimeMs)
                    val isRefreshBlinking = syncState == ru.nya.nyeios.ui.common.SyncState.STALE && !isRefreshing

                    LaunchedEffect(currentTab, authSession.isLoggedIn) {
                        if (!authSession.isLoggedIn) return@LaunchedEffect
                        when (currentTab) {
                            1 -> if (feedUiState !is FeedUiState.Success) feedViewModel.loadFeed(forceNetwork = false)
                            2 -> if (curriculumUiState !is CurriculumUiState.Success) curriculumViewModel.loadCurriculum(forceNetwork = false)
                        }
                    }

                    Scaffold(
                        containerColor = ru.nya.nyeios.ui.theme.NierBg,
                        topBar = {
                            if (isNycModern) {
                                // Parallel NyC-modern chrome: same state, mockup rendering.
                                val nycTitle = when (currentTab) {
                                    0 -> "Расписание"
                                    1 -> "Лента"
                                    2 -> "Успеваемость"
                                    3 -> "Настройки"
                                    else -> "NyEIOS"
                                }
                                val nycBase = remember(lastSyncTime) { formatSyncTime(lastSyncTime) }
                                val nycFresh = remember(nycBase, currentTab, feedUiState) {
                                    if (nycBase.isEmpty()) "" else {
                                        var t = nycBase
                                        if (currentTab == 1) {
                                            val n = (feedUiState as? ru.nya.nyeios.data.model.FeedUiState.Success)?.posts?.size ?: 0
                                            if (n > 0) t += " | $n ЗАПИСЕЙ"
                                        }
                                        t
                                    }
                                }
                                val nycFreshColor = when (syncState) {
                                    ru.nya.nyeios.ui.common.SyncState.FRESH -> ru.nya.nyeios.ui.theme.NierGreen
                                    ru.nya.nyeios.ui.common.SyncState.AGED -> ru.nya.nyeios.ui.theme.NierAmber
                                    ru.nya.nyeios.ui.common.SyncState.STALE -> ru.nya.nyeios.ui.theme.NierRed
                                }
                                val nycGroup =
                                    (scheduleUiState as? ScheduleUiState.Success)?.schedule?.group
                                val nycDownloads =
                                    if (currentTab == 1) feedViewModel.downloadedFiles.collectAsState().value else emptyList()
                                NycTopBar(
                                    title = nycTitle,
                                    freshText = nycFresh,
                                    freshColor = nycFreshColor,
                                    isSyncingFeed = feedSyncProgress.isSyncing,
                                    version = if (currentTab == 0) "v$currentAppVersion" else "",
                                    group = if (currentTab == 0) nycGroup else null,
                                    showDownloads = currentTab == 1,
                                    downloadCount = nycDownloads.size,
                                    onDownloads = { feedViewModel.showDownloadsSheet() },
                                    profileEnabled = authSession.isLoggedIn,
                                    isLoggedIn = authSession.isLoggedIn,
                                    onProfile = { scheduleViewModel.showLoginSheet() },
                                    showRefresh = currentTab != 3,
                                    isRefreshing = isRefreshing,
                                    isStale = syncState == ru.nya.nyeios.ui.common.SyncState.STALE,
                                    onRefresh = {
                                        if (feedSyncProgress.isSyncing) {
                                            android.widget.Toast.makeText(
                                                this@MainActivity,
                                                "Идёт синхронизация Живой ленты. Расписание и успеваемость работают из кэша.",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                            return@NycTopBar
                                        }
                                        when (currentTab) {
                                            0 -> scheduleViewModel.refresh()
                                            1 -> feedViewModel.requestSyncFeed()
                                            2 -> curriculumViewModel.refresh()
                                        }
                                    },
                                    hasPendingUpdate = hasPendingUpdate,
                                    onLogoClick = {
                                        if (hasPendingUpdate) {
                                            updateViewModel.restoreBanner()
                                        } else {
                                            isNetworkLogsSheetVisible = true
                                        }
                                    }
                                )
                            } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ru.nya.nyeios.ui.theme.NierBg)
                            ) {
                                // Status Bar Simulation / Dot Row
                                ru.nya.nyeios.ui.common.NierDotRow()

                                TopAppBar(
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = ru.nya.nyeios.ui.theme.NierBg
                                    ),
                                    modifier = Modifier.drawBehind {
                                        // Thick bottom border characteristic of NieR UI
                                        drawLine(
                                            color = ru.nya.nyeios.ui.theme.NierDark,
                                            start = Offset(0f, size.height),
                                            end = Offset(size.width, size.height),
                                            strokeWidth = 2.dp.toPx()
                                        )
                                    },
                                    title = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier
                                                .clickable {
                                                    if (hasPendingUpdate) {
                                                        updateViewModel.restoreBanner()
                                                    } else {
                                                        isNetworkLogsSheetVisible = true
                                                    }
                                                }
                                                .padding(vertical = 2.dp)
                                        ) {
                                            // Square NieR Logo box
                                            val pulseTransition = rememberInfiniteTransition(label = "update_pulse")
                                            val pulseAlpha by pulseTransition.animateFloat(
                                                initialValue = 0.20f,
                                                targetValue = 0.85f,
                                                animationSpec = infiniteRepeatable(
                                                    animation = tween(1000, easing = FastOutSlowInEasing),
                                                    repeatMode = RepeatMode.Reverse
                                                ),
                                                label = "pulse_alpha"
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .background(ru.nya.nyeios.ui.theme.NierDark)
                                                    .border(
                                                        width = 1.5.dp,
                                                        color = if (hasPendingUpdate) ru.nya.nyeios.ui.theme.NierRed else ru.nya.nyeios.ui.theme.NierDark
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Ny",
                                                    color = ru.nya.nyeios.ui.theme.NierBg,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily
                                                )
                                            }

                                            Column {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = when (currentTab) {
                                                            0 -> "NyEIOS"
                                                            1 -> "Живая лента"
                                                            2 -> "Успеваемость"
                                                            3 -> "Настройки"
                                                            else -> "NyEIOS"
                                                        },
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 18.sp,
                                                        fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                                        color = ru.nya.nyeios.ui.theme.NierDark,
                                                        letterSpacing = 0.5.sp
                                                    )

                                                    if (currentTab == 0) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(appPillShape())
                                                                .background(ru.nya.nyeios.ui.theme.NierDark.copy(alpha = 0.12f))
                                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                                        ) {
                                                            Text(
                                                                text = "v$currentAppVersion",
                                                                color = ru.nya.nyeios.ui.theme.NierDim,
                                                                fontSize = 9.sp,
                                                                fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                        val group = (scheduleUiState as? ScheduleUiState.Success)?.schedule?.group
                                                        if (group != null) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(appPillShape())
                                                                    .background(ru.nya.nyeios.ui.theme.NierBlue.copy(alpha = 0.15f))
                                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                                            ) {
                                                                Text(
                                                                    text = group,
                                                                    color = ru.nya.nyeios.ui.theme.NierBlue,
                                                                    fontSize = 9.sp,
                                                                    fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                if (lastSyncTime > 0L) {
                                                    val syncText = remember(lastSyncTime) { formatSyncTime(lastSyncTime) }
                                                    if (syncText.isNotEmpty()) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                            modifier = Modifier.padding(top = 1.dp)
                                                        ) {
                                                            if (feedSyncProgress.isSyncing) {
                                                                CircularProgressIndicator(
                                                                    modifier = Modifier.size(9.dp),
                                                                    color = ru.nya.nyeios.ui.theme.NierGreen,
                                                                    strokeWidth = 1.5.dp
                                                                )
                                                                Text(
                                                                    text = "Синхр. ленты...",
                                                                    color = ru.nya.nyeios.ui.theme.NierGreen,
                                                                    fontSize = 9.sp,
                                                                    fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily
                                                                )
                                                            } else {
                                                                val triangleColor = when (syncState) {
                                                                    ru.nya.nyeios.ui.common.SyncState.FRESH -> ru.nya.nyeios.ui.theme.NierGreen
                                                                    ru.nya.nyeios.ui.common.SyncState.AGED -> ru.nya.nyeios.ui.theme.NierAmber
                                                                    ru.nya.nyeios.ui.common.SyncState.STALE -> ru.nya.nyeios.ui.theme.NierRed
                                                                }
                                                                Text(
                                                                    text = buildAnnotatedString {
                                                                        withStyle(SpanStyle(color = triangleColor)) {
                                                                            append("▲ ")
                                                                        }
                                                                        withStyle(SpanStyle(color = ru.nya.nyeios.ui.theme.NierDim)) {
                                                                            append(syncText)
                                                                            if (currentTab == 1) {
                                                                                val postsCount = (feedUiState as? ru.nya.nyeios.data.model.FeedUiState.Success)?.posts?.size ?: 0
                                                                                if (postsCount > 0) {
                                                                                    append(" | $postsCount ЗАПИСЕЙ")
                                                                                }
                                                                            }
                                                                        }
                                                                    },
                                                                    fontSize = 9.sp,
                                                                    fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    actions = {
                                        // Downloads button (Feed tab)
                                        if (currentTab == 1) {
                                            val downloadedFiles by feedViewModel.downloadedFiles.collectAsState()
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clickable { feedViewModel.showDownloadsSheet() },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.FileDownload,
                                                    contentDescription = "Загрузки",
                                                    tint = if (downloadedFiles.isNotEmpty()) ru.nya.nyeios.ui.theme.NierBlue else ru.nya.nyeios.ui.theme.NierDarkSecondary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                if (downloadedFiles.isNotEmpty()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(top = 4.dp, end = 2.dp)
                                                            .background(ru.nya.nyeios.ui.theme.NierBlue)
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            text = "${downloadedFiles.size}",
                                                            color = Color.White,
                                                            fontSize = 9.sp,
                                                            fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                                            fontWeight = FontWeight.Bold,
                                                            lineHeight = 10.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Profile button — disabled when not authenticated
                                        IconButton(
                                            onClick = { scheduleViewModel.showLoginSheet() },
                                            enabled = authSession.isLoggedIn
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccountCircle,
                                                contentDescription = "Профиль",
                                                tint = if (authSession.isLoggedIn) ru.nya.nyeios.ui.theme.NierGreen else ru.nya.nyeios.ui.theme.NierDarkSecondary
                                            )
                                        }

                                        // Refresh button — hidden on the settings tab
                                        if (currentTab != 3) {
                                        IconButton(
                                            onClick = {
                                                if (feedSyncProgress.isSyncing) {
                                                    android.widget.Toast.makeText(
                                                        this@MainActivity,
                                                        "Идёт синхронизация Живой ленты. Расписание и успеваемость работают из кэша.",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                    return@IconButton
                                                }
                                                when (currentTab) {
                                                    0 -> scheduleViewModel.refresh()
                                                    1 -> feedViewModel.requestSyncFeed()
                                                    2 -> curriculumViewModel.refresh()
                                                }
                                            }
                                        ) {
                                            val rotation = if (isRefreshing) {
                                                val infiniteTransition = rememberInfiniteTransition(label = "spin")
                                                val rot by infiniteTransition.animateFloat(
                                                    initialValue = 0f,
                                                    targetValue = 360f,
                                                    animationSpec = infiniteRepeatable(
                                                        animation = tween(1000),
                                                        repeatMode = RepeatMode.Restart
                                                    ),
                                                    label = "rotation"
                                                )
                                                rot
                                            } else 0f

                                            val blinkPhase = if (isRefreshBlinking) {
                                                val infiniteTransition = rememberInfiniteTransition(label = "refresh_blink")
                                                val phase by infiniteTransition.animateFloat(
                                                    initialValue = 0f,
                                                    targetValue = 1f,
                                                    animationSpec = infiniteRepeatable(
                                                        animation = tween(1000, easing = androidx.compose.animation.core.LinearEasing),
                                                        repeatMode = RepeatMode.Restart
                                                    ),
                                                    label = "blink_phase"
                                                )
                                                phase
                                            } else 0f

                                            val isPhaseDark = blinkPhase < 0.5f

                                            val themeMode = ru.nya.nyeios.ui.theme.ThemeManager.currentTheme
                                            val solidDark = when (themeMode) {
                                                ru.nya.nyeios.ui.theme.NierThemeMode.BLACK -> Color(0xFF1A1A18)
                                                ru.nya.nyeios.ui.theme.NierThemeMode.NIGHT -> Color(0xFF1E1A15)
                                                ru.nya.nyeios.ui.theme.NierThemeMode.REGULAR -> Color(0xFF3A342B)
                                                ru.nya.nyeios.ui.theme.NierThemeMode.RETRO -> Color(0xFF2E2C25)
                                                ru.nya.nyeios.ui.theme.NierThemeMode.NYC_MODERN -> Color(0xFF0A0F16)
                                            }
                                            val solidLight = when (themeMode) {
                                                ru.nya.nyeios.ui.theme.NierThemeMode.BLACK -> Color(0xFFCAC6A8)
                                                ru.nya.nyeios.ui.theme.NierThemeMode.NIGHT -> Color(0xFFCAC6A8)
                                                ru.nya.nyeios.ui.theme.NierThemeMode.REGULAR -> Color(0xFFEDEAD8)
                                                ru.nya.nyeios.ui.theme.NierThemeMode.RETRO -> Color(0xFFE7E4D0)
                                                ru.nya.nyeios.ui.theme.NierThemeMode.NYC_MODERN -> Color(0xFF4ADEDE)
                                            }
                                            val solidBorder = when (themeMode) {
                                                ru.nya.nyeios.ui.theme.NierThemeMode.BLACK -> Color(0xFFCAC6A8)
                                                ru.nya.nyeios.ui.theme.NierThemeMode.NIGHT -> Color(0xFFCAC6A8)
                                                ru.nya.nyeios.ui.theme.NierThemeMode.REGULAR -> Color(0xFF3A342B)
                                                ru.nya.nyeios.ui.theme.NierThemeMode.RETRO -> Color(0xFF4A4740)
                                                ru.nya.nyeios.ui.theme.NierThemeMode.NYC_MODERN -> Color(0xFF4ADEDE)
                                            }

                                            val squareBg = if (isRefreshBlinking) {
                                                if (isPhaseDark) solidDark else solidLight
                                            } else {
                                                Color.Transparent
                                            }

                                            val iconTint = if (isRefreshBlinking) {
                                                if (isPhaseDark) solidLight else solidDark
                                            } else if (isRefreshing) {
                                                ru.nya.nyeios.ui.theme.NierBlue
                                            } else {
                                                ru.nya.nyeios.ui.theme.NierDarkSecondary
                                            }

                                            val squareBorder = if (isRefreshBlinking) solidBorder else Color.Transparent

                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .background(squareBg)
                                                    .border(if (isRefreshBlinking) 1.dp else 0.dp, squareBorder),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "Обновить",
                                                    tint = iconTint,
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .rotate(rotation)
                                                )
                                            }
                                        }
                                        } // end if (currentTab != 3)
                                    }
                                )
                            }
                            } // end legacy topBar Column
                        },
                        bottomBar = {
                            if (isNycModern) {
                                NycBottomNav(
                                    currentTab = currentTab,
                                    onSelect = { currentTab = it }
                                )
                            } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ru.nya.nyeios.ui.theme.NierPanel)
                            ) {
                                NavigationBar(
                                    containerColor = ru.nya.nyeios.ui.theme.NierPanel,
                                    tonalElevation = 0.dp,
                                    modifier = Modifier.drawBehind {
                                        // Thick top border of the NieR navigation bar
                                        drawLine(
                                            color = ru.nya.nyeios.ui.theme.NierDark,
                                            start = Offset(0f, 0f),
                                            end = Offset(size.width, 0f),
                                            strokeWidth = 2.dp.toPx()
                                        )
                                    }
                                ) {
                                    val tabs = listOf(
                                        Triple(0, Icons.Default.CalendarToday, "Расписание"),
                                        Triple(1, Icons.Default.DynamicFeed, "Лента"),
                                        Triple(2, Icons.Default.School, "БРС"),
                                        Triple(3, Icons.Default.Settings, "Настройки")
                                    )

                                    tabs.forEach { (index, icon, title) ->
                                        val isSelected = currentTab == index
                                        NavigationBarItem(
                                            selected = isSelected,
                                            onClick = { currentTab = index },
                                            icon = {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = title
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = title.uppercase(),
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                                    letterSpacing = 0.5.sp
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = ru.nya.nyeios.ui.theme.selectionAccent(),
                                                selectedTextColor = ru.nya.nyeios.ui.theme.selectionAccent(),
                                                unselectedIconColor = ru.nya.nyeios.ui.theme.NierDim,
                                                unselectedTextColor = ru.nya.nyeios.ui.theme.NierDim,
                                                indicatorColor = Color.Transparent
                                            ),
                                            modifier = Modifier.drawBehind {
                                                if (isSelected) {
                                                    val barWidth = size.width * 0.45f
                                                    val startX = (size.width - barWidth) / 2f
                                                    drawRect(
                                                        color = ru.nya.nyeios.ui.theme.selectionAccent(),
                                                        topLeft = Offset(startX, 0f),
                                                        size = androidx.compose.ui.geometry.Size(barWidth, 2.dp.toPx())
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }

                                ru.nya.nyeios.ui.common.NierDotRow()
                            }
                            } // end legacy bottomBar Column
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            ru.nya.nyeios.ui.common.NierBackground {
                                Crossfade(
                                    targetState = currentTab,
                                    label = "tab_transition",
                                    modifier = Modifier.fillMaxSize()
                                ) { tab ->
                                    when (tab) {
                                        0 -> if (isNycModern) {
                                            NycScheduleScreen(
                                                uiState = scheduleUiState,
                                                weekOffset = weekOffset,
                                                selectedDayIndex = selectedDayIndex,
                                                onPrevWeek = { scheduleViewModel.prevWeek() },
                                                onNextWeek = { scheduleViewModel.nextWeek() },
                                                onCurrentWeek = { scheduleViewModel.currentWeek() },
                                                onSelectDay = { scheduleViewModel.selectDay(it) },
                                                onRefresh = { scheduleViewModel.refresh() },
                                                onOpenLogin = { scheduleViewModel.showLoginSheet() }
                                            )
                                        } else {
                                            ScheduleScreen(
                                                uiState = scheduleUiState,
                                                weekOffset = weekOffset,
                                                selectedDayIndex = selectedDayIndex,
                                                onPrevWeek = { scheduleViewModel.prevWeek() },
                                                onNextWeek = { scheduleViewModel.nextWeek() },
                                                onCurrentWeek = { scheduleViewModel.currentWeek() },
                                                onSelectDay = { scheduleViewModel.selectDay(it) },
                                                onRefresh = { scheduleViewModel.refresh() },
                                                onOpenLogin = { scheduleViewModel.showLoginSheet() }
                                            )
                                        }
                                        1 -> if (isNycModern) {
                                            NycFeedScreen(
                                                uiState = feedUiState,
                                                feedViewModel = feedViewModel,
                                                onRefresh = { feedViewModel.refresh() },
                                                onOpenLogin = { scheduleViewModel.showLoginSheet() }
                                            )
                                        } else {
                                            FeedScreen(
                                                uiState = feedUiState,
                                                feedViewModel = feedViewModel,
                                                onRefresh = { feedViewModel.refresh() },
                                                onOpenLogin = { scheduleViewModel.showLoginSheet() }
                                            )
                                        }
                                        2 -> if (isNycModern) {
                                            NycCurriculumScreen(
                                                uiState = curriculumUiState,
                                                onSelectTerm = { curriculumViewModel.selectTerm(it) },
                                                onRefresh = { curriculumViewModel.refresh() },
                                                onOpenLogin = { scheduleViewModel.showLoginSheet() }
                                            )
                                        } else {
                                            CurriculumScreen(
                                                uiState = curriculumUiState,
                                                onSelectTerm = { curriculumViewModel.selectTerm(it) },
                                                onRefresh = { curriculumViewModel.refresh() },
                                                onOpenLogin = { scheduleViewModel.showLoginSheet() }
                                            )
                                        }
                                        3 -> if (isNycModern) {
                                            NycSettingsScreen(
                                                viewModel = settingsViewModel,
                                                updateViewModel = updateViewModel
                                            )
                                        } else {
                                            SettingsScreen(
                                                viewModel = settingsViewModel,
                                                updateViewModel = updateViewModel
                                            )
                                        }
                                    }
                                }
                            }

                            // Update banner — floating above content, pinned to bottom
                            UpdateBanner(
                                state = updateUiState,
                                viewModel = updateViewModel,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 8.dp)
                            )

                            // Fullscreen login gate — shown when user is not authenticated.
                            // Covers the entire content area (below TopAppBar, above BottomBar).
                            // NetworkLogsBottomSheet is still accessible via the title tap.
                            if (!authSession.isLoggedIn) {
                                if (isNycModern) {
                                    NycLoginGate(
                                        authSession = authSession,
                                        isLoggingIn = isLoggingIn,
                                        errorMessage = loginError,
                                        onLogin = { u, p ->
                                            scheduleViewModel.performLogin(u, p)
                                        }
                                    )
                                } else {
                                    LoginFullscreenGate(
                                        authSession = authSession,
                                        isLoggingIn = isLoggingIn,
                                        errorMessage = loginError,
                                        onLogin = { u, p ->
                                            scheduleViewModel.performLogin(u, p)
                                        }
                                    )
                                }
                            }

                            if (isLoginSheetVisible) {
                                LoginBottomSheet(
                                    authSession = authSession,
                                    isLoggingIn = isLoggingIn,
                                    errorMessage = loginError,
                                    onLogin = { u, p ->
                                        scheduleViewModel.performLogin(u, p)
                                        feedViewModel.refresh()
                                        curriculumViewModel.refresh()
                                    },
                                    onLogout = {
                                        scheduleViewModel.logout()
                                        feedViewModel.refresh()
                                        curriculumViewModel.refresh()
                                    },
                                    onDismiss = { scheduleViewModel.hideLoginSheet() }
                                )
                            }

                            if (isNetworkLogsSheetVisible) {
                                if (isNycModern) {
                                    NycLogsSheet(
                                        onDismiss = { isNetworkLogsSheetVisible = false },
                                        updateViewModel = updateViewModel
                                    )
                                } else {
                                    NetworkLogsBottomSheet(
                                        onDismiss = { isNetworkLogsSheetVisible = false },
                                        updateViewModel = updateViewModel
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSyncTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return try {
        val syncDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        val timeStr = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(timeFmt)

        when {
            syncDate == today -> timeStr
            syncDate == today.minusDays(1) -> "вчера $timeStr"
            else -> {
                val dateFmt = DateTimeFormatter.ofPattern("dd.MM HH:mm", Locale.getDefault())
                Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(dateFmt)
            }
        }
    } catch (e: Exception) {
        ""
    }
}
