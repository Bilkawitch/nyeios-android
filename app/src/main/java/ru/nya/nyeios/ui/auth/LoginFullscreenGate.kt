package ru.nya.nyeios.ui.auth

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nya.nyeios.data.model.AuthSession

/**
 * Full-screen first-launch login gate.
 * Displayed as an opaque overlay filling the entire screen content area
 * (below the TopAppBar/status bar, above the BottomBar) when the user is
 * not yet authenticated. The host is responsible for placing this composable
 * in the correct Z-order so that the top status bar / network-log sheet
 * remains reachable.
 */
@Composable
fun LoginFullscreenGate(
    authSession: AuthSession,
    isLoggingIn: Boolean,
    errorMessage: String?,
    onLogin: (String, String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var username by remember(authSession.username) { mutableStateOf(authSession.username) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ru.nya.nyeios.ui.theme.NierBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .border(1.dp, ru.nya.nyeios.ui.theme.NierBorder)
                .background(ru.nya.nyeios.ui.theme.NierPanelAlt)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(ru.nya.nyeios.ui.theme.NierDark)
                        .border(1.dp, ru.nya.nyeios.ui.theme.NierDark),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = ru.nya.nyeios.ui.theme.NierBg,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = "Вход в ЭИОС ГСГУ",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                        color = ru.nya.nyeios.ui.theme.NierDark
                    )
                    Text(
                        text = "Данные шифруются Android Keystore",
                        fontSize = 10.sp,
                        fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                        color = ru.nya.nyeios.ui.theme.NierDim
                    )
                }
            }

            // Error Banner
            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .background(ru.nya.nyeios.ui.theme.NierRed.copy(alpha = 0.1f))
                        .border(1.dp, ru.nya.nyeios.ui.theme.NierRed.copy(alpha = 0.4f))
                        .padding(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = ru.nya.nyeios.ui.theme.NierRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = errorMessage,
                            color = ru.nya.nyeios.ui.theme.NierRed,
                            fontSize = 11.sp,
                            fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily
                        )
                    }
                }
            }

            // Username field
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "ЛОГИН / E-MAIL",
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                    fontWeight = FontWeight.Bold,
                    color = ru.nya.nyeios.ui.theme.NierDim
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = { Text("ivan.petrov@gsgu.ru", color = ru.nya.nyeios.ui.theme.NierDim) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = ru.nya.nyeios.ui.theme.NierDim,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    shape = RoundedCornerShape(0.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ru.nya.nyeios.ui.theme.NierDark,
                        unfocusedBorderColor = ru.nya.nyeios.ui.theme.NierBorder,
                        focusedTextColor = ru.nya.nyeios.ui.theme.NierDark,
                        unfocusedTextColor = ru.nya.nyeios.ui.theme.NierDark,
                        focusedContainerColor = ru.nya.nyeios.ui.theme.NierPanelAlt,
                        unfocusedContainerColor = ru.nya.nyeios.ui.theme.NierPanelAlt
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(10.dp))

            // Password field
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "ПАРОЛЬ",
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                    fontWeight = FontWeight.Bold,
                    color = ru.nya.nyeios.ui.theme.NierDim
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("••••••••", color = ru.nya.nyeios.ui.theme.NierDim) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = ru.nya.nyeios.ui.theme.NierDim,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) "Скрыть пароль" else "Показать пароль",
                                tint = ru.nya.nyeios.ui.theme.NierDim,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    shape = RoundedCornerShape(0.dp),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            onLogin(username, password)
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ru.nya.nyeios.ui.theme.NierDark,
                        unfocusedBorderColor = ru.nya.nyeios.ui.theme.NierBorder,
                        focusedTextColor = ru.nya.nyeios.ui.theme.NierDark,
                        unfocusedTextColor = ru.nya.nyeios.ui.theme.NierDark,
                        focusedContainerColor = ru.nya.nyeios.ui.theme.NierPanelAlt,
                        unfocusedContainerColor = ru.nya.nyeios.ui.theme.NierPanelAlt
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(18.dp))

            // Action Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .background(ru.nya.nyeios.ui.theme.NierDark)
                    .clickable(enabled = !isLoggingIn && username.isNotBlank() && password.isNotBlank()) {
                        focusManager.clearFocus()
                        onLogin(username, password)
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isLoggingIn) {
                    CircularProgressIndicator(
                        color = ru.nya.nyeios.ui.theme.NierBg,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "▶  ВОЙТИ В ЭИОС",
                        fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = ru.nya.nyeios.ui.theme.NierBg,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
