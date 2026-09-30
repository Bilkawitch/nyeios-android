package ru.nya.nyeios.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.nya.nyeios.R
import ru.nya.nyeios.data.model.AuthSession
import ru.nya.nyeios.data.net.NetworkMetricsTracker
import ru.nya.nyeios.data.net.PingResult
import ru.nya.nyeios.ui.common.localizeErrorMessage
import ru.nya.nyeios.ui.theme.NierRed
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.NycMonoFamily
import ru.nya.nyeios.ui.theme.NycSansFamily
import ru.nya.nyeios.ui.theme.ShareTechMonoFamily

private val nycFaint = Color(0xFF5B6A7E)
private val nycMuted = Color(0xFF8794A7)
private val nycText = Color(0xFFDBE4F0)
private val cardBg = Color(0xFF131D2B)
private val cardBorder = Color(150, 185, 235, alpha = 26)
private val wellBg = Color(0xFF0C121B)
private val wellBorder = Color(150, 185, 235, alpha = 18)

/**
 * Modern Night-Skeuomorph popup for user authentication and connection diagnostics
 * in the NyC theme. Follows the exact redesign mockup with rounded cards, pills,
 * cyan glowing accents, and telemetry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NycLoginBottomSheet(
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
        containerColor = Color(0xFF0F1622),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        scrimColor = Color(0xCC04070B),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 10.dp)
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(150, 185, 235, alpha = 46))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Card 1: [ 01 ] АВТОРИЗАЦИЯ ПОРТАЛА ─────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Header: [ 01 ] АВТОРИЗАЦИЯ ПОРТАЛА + Person icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.login_portal_auth_section),
                            fontFamily = ShareTechMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            letterSpacing = 1.2.sp,
                            color = NycCyan
                        )
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = NycCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (authSession.isLoggedIn) {
                        // Soft green status pill: "Вы авторизованы: maksim.grechki@yandex.ru"
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF9FE8AB))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF145428),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(
                                        R.string.login_logged_in_fmt,
                                        authSession.username.ifEmpty { stringResource(R.string.login_active_session) }
                                    ),
                                    fontFamily = ShareTechMonoFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    color = Color(0xFF0F381B)
                                )
                            }
                        }

                        // Soft red logout button: "ВЫЙТИ ИЗ АККАУНТА"
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFE47B7B))
                                .clickable { onLogout() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.login_action_logout),
                                fontFamily = NycSansFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 1.2.sp,
                                color = Color(0xFF3D1212)
                            )
                        }
                    }

                    // Username field: ЛОГИН ЭИОС
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.login_username_label),
                            fontFamily = ShareTechMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 1.2.sp,
                            color = nycFaint,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.login_placeholder_user),
                                    fontFamily = ShareTechMonoFamily,
                                    fontSize = 12.5.sp,
                                    color = nycFaint
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = nycFaint,
                                    modifier = Modifier.size(17.dp)
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NycCyan,
                                unfocusedBorderColor = wellBorder,
                                focusedTextColor = nycText,
                                unfocusedTextColor = nycText,
                                focusedContainerColor = wellBg,
                                unfocusedContainerColor = wellBg,
                                cursorColor = NycCyan
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = ShareTechMonoFamily,
                                fontSize = 12.5.sp
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Password field: ПАРОЛЬ
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.login_password_label),
                            fontFamily = ShareTechMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 1.2.sp,
                            color = nycFaint,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            placeholder = {
                                Text(
                                    text = "••••••••••",
                                    fontFamily = ShareTechMonoFamily,
                                    fontSize = 12.5.sp,
                                    color = nycFaint
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = nycFaint,
                                    modifier = Modifier.size(17.dp)
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (passwordVisible) stringResource(R.string.login_hide_password) else stringResource(R.string.login_show_password),
                                        tint = nycFaint,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
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
                                focusedBorderColor = NycCyan,
                                unfocusedBorderColor = wellBorder,
                                focusedTextColor = nycText,
                                unfocusedTextColor = nycText,
                                focusedContainerColor = wellBg,
                                unfocusedContainerColor = wellBg,
                                cursorColor = NycCyan
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = ShareTechMonoFamily,
                                fontSize = 12.5.sp
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Action button: ОБНОВИТЬ СЕССИЮ / ВОЙТИ В ЭИОС
                    val canSubmit = !isLoggingIn && username.isNotBlank() && password.isNotBlank()
                    val buttonBrush = if (canSubmit) {
                        Brush.horizontalGradient(listOf(Color(0xFF67ECE9), Color(0xFF4EE2DF)))
                    } else {
                        Brush.horizontalGradient(listOf(Color(0xFF5CE8E6).copy(alpha = 0.85f), Color(0xFF38D5D5).copy(alpha = 0.85f)))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(16.dp),
                                ambientColor = Color(0xFF4ADEDE).copy(alpha = 0.5f),
                                spotColor = Color(0xFF4ADEDE).copy(alpha = 0.5f)
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .background(buttonBrush)
                            .clickable(enabled = canSubmit) {
                                focusManager.clearFocus()
                                onLogin(username, password)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoggingIn) {
                            CircularProgressIndicator(
                                color = Color(0xFF04211F),
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "›",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF04211F)
                                )
                                Text(
                                    text = if (authSession.isLoggedIn) stringResource(R.string.login_refresh_session) else stringResource(R.string.login_submit),
                                    fontFamily = NycSansFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.5.sp,
                                    letterSpacing = 1.sp,
                                    color = Color(0xFF04211F)
                                )
                            }
                        }
                    }

                    if (errorMessage != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = NierRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = localizeErrorMessage(errorMessage),
                                color = NierRed,
                                fontSize = 11.sp,
                                fontFamily = ShareTechMonoFamily
                            )
                        }
                    }

                    // Footnote: БЕЗОПАСНОЕ ХРАНЕНИЕ СЕССИИ
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = Color(0xFF46E08C),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.login_keystore_stored),
                            fontFamily = ShareTechMonoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 8.5.sp,
                            letterSpacing = 1.5.sp,
                            color = nycFaint
                        )
                    }
                }
            }

            // ── Card 2: [ 02 ] ПРОВЕРКА СОЕДИНЕНИЯ ─────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
                    .clickable {
                        scope.launch { tracker.measureEiosPing() }
                    }
            ) {
                Column {
                    // Mint/cyan header banner with [ 02 ] ПРОВЕРКА СОЕДИНЕНИЯ + Dns icon
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                            .background(Color(0xFF8CEEE9))
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.login_conn_test_section),
                            fontFamily = ShareTechMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            letterSpacing = 1.2.sp,
                            color = Color(0xFF052A28)
                        )
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = Color(0xFF052A28),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Diagnostics body
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Server hostname + ONLINE badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "eios.gukolomna.ru",
                                fontFamily = ShareTechMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = nycText
                            )

                            val (statusText, statusBg) = when (pingResult) {
                                is PingResult.Success -> "ONLINE" to Color(0xFF46E08C)
                                is PingResult.Measuring -> "CHECKING" to ru.nya.nyeios.ui.theme.NierAmber
                                is PingResult.Error -> "OFFLINE" to NierRed
                                is PingResult.Idle -> "ONLINE" to Color(0xFF46E08C)
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(statusBg)
                                    .padding(horizontal = 7.dp, vertical = 2.5.dp)
                            ) {
                                Text(
                                    text = statusText,
                                    fontFamily = ShareTechMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    color = Color(0xFF052A16)
                                )
                            }
                        }

                        // Pill-style segmented meter with ambient glow
                        val (meterProgress, meterColor) = when (val p = pingResult) {
                            is PingResult.Success -> {
                                val ratio = (1.0f - (p.latencyMs / 500f)).coerceIn(0.2f, 0.95f)
                                ratio to Color(0xFF46E08C)
                            }
                            is PingResult.Measuring -> 0.5f to ru.nya.nyeios.ui.theme.NierAmber
                            is PingResult.Error -> 0.15f to NierRed
                            is PingResult.Idle -> 0.88f to Color(0xFF46E08C)
                        }

                        NycPillSegmentedMeter(
                            progress = meterProgress,
                            activeColor = meterColor,
                            inactiveColor = Color(0xFF223042),
                            segments = 26,
                            height = 7.dp
                        )

                        // Telemetry details: TCP RTT | TLS 1.3 | VPN
                        val msUnit = stringResource(R.string.unit_ms_caps)
                        val rttText = when (val p = pingResult) {
                            is PingResult.Success -> "TCP RTT ${p.latencyMs} $msUnit"
                            is PingResult.Measuring -> "TCP RTT ... $msUnit"
                            is PingResult.Error -> "TCP RTT ERR"
                            is PingResult.Idle -> "TCP RTT 988 $msUnit"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = rttText,
                                fontFamily = ShareTechMonoFamily,
                                fontSize = 9.5.sp,
                                letterSpacing = 0.5.sp,
                                color = nycFaint
                            )
                            Text(
                                text = "TLS 1.3",
                                fontFamily = ShareTechMonoFamily,
                                fontSize = 9.5.sp,
                                letterSpacing = 0.5.sp,
                                color = nycFaint
                            )
                            Text(
                                text = "VPN: ${if (isVpn) "ON" else "OFF"}",
                                fontFamily = ShareTechMonoFamily,
                                fontSize = 9.5.sp,
                                letterSpacing = 0.5.sp,
                                color = nycFaint
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Segmented level meter with rounded pill segments and soft glow matching the mockup.
 */
@Composable
private fun NycPillSegmentedMeter(
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    segments: Int = 26,
    height: Dp = 7.dp,
    spacing: Dp = 2.dp
) {
    val clamped = progress.coerceIn(0f, 1f)
    val activeCount = kotlin.math.round(clamped * segments).toInt().coerceIn(0, segments)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(3.dp),
                ambientColor = activeColor.copy(alpha = 0.45f),
                spotColor = activeColor.copy(alpha = 0.45f)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            for (i in 0 until segments) {
                val isActive = i < activeCount
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(if (isActive) activeColor else inactiveColor)
                )
            }
        }
    }
}
