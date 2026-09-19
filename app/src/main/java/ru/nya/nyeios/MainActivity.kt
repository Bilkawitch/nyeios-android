package ru.nya.nyeios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.nya.nyeios.ui.auth.LoginBottomSheet
import ru.nya.nyeios.ui.paywall.PaywallDialog
import ru.nya.nyeios.ui.schedule.ScheduleScreen
import ru.nya.nyeios.ui.schedule.ScheduleViewModel
import ru.nya.nyeios.ui.theme.NyEIOSTheme
import ru.nya.nyeios.ui.theme.ObsidianBg

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NyEIOSTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObsidianBg
                ) {
                    val viewModel: ScheduleViewModel = viewModel()
                    val uiState by viewModel.uiState.collectAsState()
                    val weekOffset by viewModel.weekOffset.collectAsState()
                    val selectedDayIndex by viewModel.selectedDayIndex.collectAsState()
                    val authSession by viewModel.authSession.collectAsState()
                    val isLoginSheetVisible by viewModel.isLoginSheetVisible.collectAsState()
                    val isLoggingIn by viewModel.isLoggingIn.collectAsState()
                    val loginError by viewModel.loginError.collectAsState()

                    // Joke paywall modal on app open
                    var showPaywall by rememberSaveable { mutableStateOf(true) }

                    ScheduleScreen(
                        uiState = uiState,
                        weekOffset = weekOffset,
                        selectedDayIndex = selectedDayIndex,
                        authSession = authSession,
                        onPrevWeek = { viewModel.prevWeek() },
                        onNextWeek = { viewModel.nextWeek() },
                        onCurrentWeek = { viewModel.currentWeek() },
                        onSelectDay = { viewModel.selectDay(it) },
                        onRefresh = { viewModel.refresh() },
                        onOpenLogin = { viewModel.showLoginSheet() }
                    )

                    if (showPaywall) {
                        PaywallDialog(
                            onDismiss = { showPaywall = false }
                        )
                    }

                    if (isLoginSheetVisible) {
                        LoginBottomSheet(
                            authSession = authSession,
                            isLoggingIn = isLoggingIn,
                            errorMessage = loginError,
                            onLogin = { u, p -> viewModel.performLogin(u, p) },
                            onLogout = { viewModel.logout() },
                            onDismiss = { viewModel.hideLoginSheet() }
                        )
                    }
                }
            }
        }
    }
}
