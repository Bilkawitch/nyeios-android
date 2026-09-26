package ru.nya.nyeios.ui.auth

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import ru.nya.nyeios.ui.theme.appRectShape
import ru.nya.nyeios.ui.theme.appSheetShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.nya.nyeios.data.model.AuthSession
import ru.nya.nyeios.data.net.NetworkMetricsTracker
import ru.nya.nyeios.data.net.PingResult
import ru.nya.nyeios.ui.common.SegmentedMeter
import ru.nya.nyeios.ui.theme.UiPreferencesManager
import ru.nya.nyeios.ui.theme.NierBg
import ru.nya.nyeios.ui.theme.NierBlue
import ru.nya.nyeios.ui.theme.NierBorder
import ru.nya.nyeios.ui.theme.NierDark
import ru.nya.nyeios.ui.theme.NierDim
import ru.nya.nyeios.ui.theme.NierGreen
import ru.nya.nyeios.ui.theme.NierPanel
import ru.nya.nyeios.ui.theme.NierPanelAlt
import ru.nya.nyeios.ui.theme.NierRed
import ru.nya.nyeios.ui.theme.NierSelectionText
import ru.nya.nyeios.ui.theme.RajdhaniFamily
import ru.nya.nyeios.ui.theme.ShareTechMonoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginBottomSheet(
    authSession: AuthSession,
    isLoggingIn: Boolean,
    errorMessage: String?,
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val tracker = remember { NetworkMetricsTracker.getInstance(context) }
    val pingResult by tracker.pingResult.collectAsState()
    val isVpn = remember { tracker.isVpnActive() }

    var username by remember(authSession.username) { mutableStateOf(authSession.username) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        tracker.measureEiosPing()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NierPanel,
        shape = appSheetShape(),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 3.dp)
                    .background(NierBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Panel [ 01 ] АВТОРИЗАЦИЯ
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(UiPreferencesManager.borderWidth, NierBorder)
                    .background(NierPanelAlt)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NierDark)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "[ 01 ]  АВТОРИЗАЦИЯ ПОРТАЛА",
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.2.sp,
                            color = NierSelectionText
                        )
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = NierSelectionText,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (authSession.isLoggedIn) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(NierGreen.copy(alpha = 0.1f))
                                    .border(1.dp, NierGreen)
                                    .padding(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = NierGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Вы авторизованы: ${authSession.username.ifEmpty { "Активная сессия" }}",
                                        fontFamily = ShareTechMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = NierDark
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, NierRed)
                                    .clickable { onLogout() }
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "ВЫЙТИ ИЗ АККАУНТА",
                                    color = NierRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = RajdhaniFamily,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        // Username field
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "ЛОГИН ЭИОС",
                                fontSize = 9.sp,
                                letterSpacing = 1.sp,
                                fontFamily = ShareTechMonoFamily,
                                fontWeight = FontWeight.Bold,
                                color = NierDim
                            )
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                placeholder = { Text("23an041", color = NierDim) },
                                shape = appRectShape(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NierBlue,
                                    unfocusedBorderColor = NierBorder,
                                    focusedTextColor = NierDark,
                                    unfocusedTextColor = NierDark,
                                    focusedContainerColor = NierPanel,
                                    unfocusedContainerColor = NierPanel
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = ShareTechMonoFamily,
                                    fontSize = 13.sp
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Password field
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "ПАРОЛЬ",
                                fontSize = 9.sp,
                                letterSpacing = 1.sp,
                                fontFamily = ShareTechMonoFamily,
                                fontWeight = FontWeight.Bold,
                                color = NierDim
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                placeholder = { Text("••••••••••", color = NierDim) },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (passwordVisible) "Скрыть пароль" else "Показать пароль",
                                            tint = NierDim,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                shape = appRectShape(),
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        if (username.isNotBlank() && password.isNotBlank()) {
                                            onLogin(username, password)
                                        }
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NierBlue,
                                    unfocusedBorderColor = NierBorder,
                                    focusedTextColor = NierDark,
                                    unfocusedTextColor = NierDark,
                                    focusedContainerColor = NierPanel,
                                    unfocusedContainerColor = NierPanel
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = ShareTechMonoFamily,
                                    fontSize = 13.sp
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Action Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .background(NierBlue)
                                .clickable(enabled = !isLoggingIn && username.isNotBlank() && password.isNotBlank()) {
                                    focusManager.clearFocus()
                                    onLogin(username, password)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoggingIn) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = if (authSession.isLoggedIn) "ОБНОВИТЬ СЕССИЮ" else "ВОЙТИ В ЭИОС",
                                    fontFamily = RajdhaniFamily,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage,
                                color = NierRed,
                                fontSize = 11.sp,
                                fontFamily = ShareTechMonoFamily
                            )
                        }
                    }
                }
            }

            // Panel [ 02 ] ПРОВЕРКА СОЕДИНЕНИЯ
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(UiPreferencesManager.borderWidth, NierBorder)
                    .background(NierPanelAlt)
                    .clickable {
                        scope.launch { tracker.measureEiosPing() }
                    }
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NierDark)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "[ 02 ]  ПРОВЕРКА СОЕДИНЕНИЯ",
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.2.sp,
                            color = NierSelectionText
                        )
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = NierSelectionText,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "eios.gukolomna.ru",
                                fontFamily = ShareTechMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = NierDark
                            )

                            val (statusText, statusBg) = when (pingResult) {
                                is PingResult.Success -> "ONLINE" to NierGreen
                                is PingResult.Measuring -> "CHECKING" to ru.nya.nyeios.ui.theme.NierAmber
                                is PingResult.Error -> "OFFLINE" to NierRed
                                is PingResult.Idle -> "ONLINE" to NierGreen
                            }

                            Box(
                                modifier = Modifier
                                    .background(statusBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = statusText,
                                    fontFamily = ShareTechMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    color = Color.White
                                )
                            }
                        }

                        val (meterProgress, meterColor) = when (val p = pingResult) {
                            is PingResult.Success -> {
                                val ratio = (1.0f - (p.latencyMs / 500f)).coerceIn(0.2f, 0.95f)
                                ratio to NierGreen
                            }
                            is PingResult.Measuring -> 0.5f to ru.nya.nyeios.ui.theme.NierAmber
                            is PingResult.Error -> 0.15f to NierRed
                            is PingResult.Idle -> 0.88f to NierGreen
                        }

                        SegmentedMeter(
                            progress = meterProgress,
                            activeColor = meterColor,
                            segments = 28,
                            height = 6.dp
                        )

                        val rttText = when (val p = pingResult) {
                            is PingResult.Success -> "TCP RTT ${p.latencyMs} МС"
                            is PingResult.Measuring -> "TCP RTT ... МС"
                            is PingResult.Error -> "TCP RTT ERR"
                            is PingResult.Idle -> "TCP RTT 98 МС"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = rttText,
                                fontFamily = ShareTechMonoFamily,
                                fontSize = 9.sp,
                                letterSpacing = 1.sp,
                                color = NierDim
                            )
                            Text(
                                text = "TLS 1.3",
                                fontFamily = ShareTechMonoFamily,
                                fontSize = 9.sp,
                                letterSpacing = 1.sp,
                                color = NierDim
                            )
                            Text(
                                text = "VPN: ${if (isVpn) "ON" else "OFF"}",
                                fontFamily = ShareTechMonoFamily,
                                fontSize = 9.sp,
                                letterSpacing = 1.sp,
                                color = NierDim
                            )
                        }
                    }
                }
            }
        }
    }
}
