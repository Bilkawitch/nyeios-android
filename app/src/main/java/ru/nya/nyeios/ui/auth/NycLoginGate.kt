package ru.nya.nyeios.ui.auth

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nya.nyeios.data.model.AuthSession
import ru.nya.nyeios.data.net.NetworkMetricsTracker
import ru.nya.nyeios.data.update.AppVersionProvider
import ru.nya.nyeios.ui.theme.NierRed
import ru.nya.nyeios.ui.theme.NierSurface
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.NycMonoFamily
import ru.nya.nyeios.ui.theme.NycSansFamily
import ru.nya.nyeios.ui.theme.nycCard
import ru.nya.nyeios.ui.theme.nycRaised

// Parallel NyC-modern login gate from mockup nyeios_redesign.html (screen 1) and
// reference 1.png. Same props and login behavior as LoginFullscreenGate; only
// rendering differs. Legacy gate is untouched.
//
// Note: the legacy connection-diagnostics panel has no mockup counterpart, so it
// is not rendered here; the shared ping measurement is still triggered to keep
// behavior identical.

// Mockup fixed tokens.
private val nycText = Color(0xFFDBE4F0)
private val nycMuted = Color(0xFF8794A7)
private val nycFaint = Color(0xFF5B6A7E)

@Composable
fun NycLoginGate(
    authSession: AuthSession,
    isLoggingIn: Boolean,
    errorMessage: String?,
    onLogin: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var username by remember(authSession.username) { mutableStateOf(authSession.username) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val appVersion = remember(context) { AppVersionProvider.getVersionName(context) }

    // Same shared measurement the legacy gate triggers; UI stays mockup-faithful.
    LaunchedEffect(Unit) {
        try {
            NetworkMetricsTracker.getInstance(context).measureEiosPing()
        } catch (_: Exception) {
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF04060A))
            .drawBehind {
                // Radial cyan glow, 420px circle centered at top (CSS .login::before).
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NycCyan.copy(alpha = 0.14f),
                            NycCyan.copy(alpha = 0.045f),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2f, 70.dp.toPx()),
                        radius = 210.dp.toPx()
                    )
                )
            }
    ) {
        // Dot grid overlay (CSS .login::after, mask approximated by low alpha).
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 22.dp.toPx()
            val dotColor = Color(150, 185, 235, alpha = 19)
            var y = 0f
            while (y < size.height) {
                var x = 0f
                while (x < size.width) {
                    drawCircle(color = dotColor, radius = 1.dp.toPx(), center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(64.dp))

            // Big logo 78px with halo ring.
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .shadow(
                        17.dp,
                        RoundedCornerShape(24.dp),
                        ambientColor = NycCyan.copy(alpha = 0.16f),
                        spotColor = NycCyan.copy(alpha = 0.16f)
                    )
                    .nycRaised(24.dp)
                    .border(1.dp, NycCyan.copy(alpha = 0.4f), RoundedCornerShape(29.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Ny",
                    fontFamily = NycSansFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    color = NycCyan
                )
            }

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = nycText)) { append("Ny") }
                    withStyle(SpanStyle(color = NycCyan)) { append("EIOS") }
                },
                fontFamily = NycSansFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 25.sp,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.padding(top = 18.dp)
            )
            Text(
                text = "КЛИЕНТ ПОРТАЛА ЭИОС · НАВИГАТОР КОРПУСА",
                fontFamily = NycMonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 8.5.sp,
                letterSpacing = 2.sp,
                color = nycFaint,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Panel.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 30.dp)
                    .nycCard(20.dp)
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                NycFieldLabel("ЛОГИН")
                NycLoginField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = "учётная запись портала",
                    leadingIcon = Icons.Default.Person,
                    isPassword = false,
                    passwordVisible = false,
                    onTogglePassword = {},
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    onDone = {
                        focusManager.clearFocus()
                        if (username.isNotBlank() && password.isNotBlank()) onLogin(username, password)
                    }
                )

                NycFieldLabel("ПАРОЛЬ", modifier = Modifier.padding(top = 14.dp))
                NycLoginField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "••••••••••••",
                    leadingIcon = Icons.Default.Lock,
                    isPassword = true,
                    passwordVisible = passwordVisible,
                    onTogglePassword = { passwordVisible = !passwordVisible },
                    onNext = {},
                    onDone = {
                        focusManager.clearFocus()
                        if (username.isNotBlank() && password.isNotBlank()) onLogin(username, password)
                    }
                )

                // ВОЙТИ button 52px, cyan gradient.
                val canLogin = !isLoggingIn && username.isNotBlank() && password.isNotBlank()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF63EBE7), Color(0xFF2FB4B6))
                            )
                        )
                        .clickable(enabled = canLogin) {
                            focusManager.clearFocus()
                            onLogin(username, password)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(
                            color = Color(0xFF04211F),
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "›", fontSize = 16.sp, color = Color(0xFF04211F))
                            Text(
                                text = "ВОЙТИ",
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
                    Text(
                        text = errorMessage,
                        fontFamily = NycMonoFamily,
                        fontSize = 11.sp,
                        color = NierRed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 13.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = Color(0xFF46E08C),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.size(7.dp))
                    Text(
                        text = "ПАРОЛЬ ХРАНИТСЯ В ANDROID KEYSTORE",
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 8.5.sp,
                        letterSpacing = 1.sp,
                        color = nycFaint
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.padding(bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                NycStaticChip("OFFLINE-FIRST", active = false)
                NycStaticChip("SWR-КЭШ", active = false)
                NycStaticChip("v$appVersion", active = true)
            }
        }
    }
}

@Composable
private fun NycFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = NycMonoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 7.5.sp,
        letterSpacing = 2.sp,
        color = nycFaint,
        modifier = modifier.padding(start = 2.dp, bottom = 6.dp, top = 2.dp)
    )
}

@Composable
private fun NycLoginField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    isPassword: Boolean,
    passwordVisible: Boolean,
    onTogglePassword: () -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                fontFamily = NycMonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = nycFaint
            )
        },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = nycFaint,
                modifier = Modifier.size(15.dp)
            )
        },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "Скрыть пароль" else "Показать пароль",
                        tint = nycMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        } else null,
        shape = RoundedCornerShape(13.dp),
        singleLine = true,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
            imeAction = if (isPassword) ImeAction.Done else ImeAction.Next
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext() },
            onDone = { onDone() }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NycCyan,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = nycText,
            unfocusedTextColor = nycText,
            focusedContainerColor = NierSurface,
            unfocusedContainerColor = NierSurface,
            cursorColor = NycCyan
        ),
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = NycMonoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    )
}

@Composable
private fun NycStaticChip(text: String, active: Boolean) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .then(
                if (active) {
                    Modifier
                        .background(NycCyan.copy(alpha = 0.12f))
                        .border(1.dp, NycCyan.copy(alpha = 0.34f), RoundedCornerShape(9.dp))
                } else {
                    Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF0A0F16), Color(0xFF0E1622))
                            )
                        )
                        .border(1.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(9.dp))
                }
            )
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = NycMonoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.5.sp,
            letterSpacing = 1.sp,
            color = if (active) NycCyan else nycMuted
        )
    }
}
