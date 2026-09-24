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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nya.nyeios.data.model.AuthSession
import ru.nya.nyeios.ui.theme.ExamRed
import ru.nya.nyeios.ui.theme.ExamRedBg
import ru.nya.nyeios.ui.theme.LectureBlue
import ru.nya.nyeios.ui.theme.ObsidianBorder
import ru.nya.nyeios.ui.theme.ObsidianCard
import ru.nya.nyeios.ui.theme.ObsidianSurface
import ru.nya.nyeios.ui.theme.PracticeGreen
import ru.nya.nyeios.ui.theme.TextMuted
import ru.nya.nyeios.ui.theme.TextPrimary
import ru.nya.nyeios.ui.theme.TextSecondary

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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current

    var username by remember(authSession.username) { mutableStateOf(authSession.username) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ru.nya.nyeios.ui.theme.NierPanel,
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 3.dp)
                    .background(ru.nya.nyeios.ui.theme.NierBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
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

            Spacer(modifier = Modifier.height(16.dp))

            if (authSession.isLoggedIn) {
                // Already Logged in card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ru.nya.nyeios.ui.theme.NierPanelAlt)
                        .border(1.dp, ru.nya.nyeios.ui.theme.NierGreen)
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ru.nya.nyeios.ui.theme.NierGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Вы успешно авторизованы",
                                fontWeight = FontWeight.Bold,
                                fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                                color = ru.nya.nyeios.ui.theme.NierDark,
                                fontSize = 13.sp
                            )
                        }
                        Text(
                            text = "Логин: ${authSession.username.ifEmpty { "Сохранённая сессия" }}",
                            color = ru.nya.nyeios.ui.theme.NierDim,
                            fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ru.nya.nyeios.ui.theme.NierRed)
                        .clickable { onLogout() }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ВЫЙТИ ИЗ АККАУНТА",
                        color = ru.nya.nyeios.ui.theme.NierRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ru.nya.nyeios.ui.theme.RajdhaniFamily,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Или введите новые данные для смены пользователя:",
                    color = ru.nya.nyeios.ui.theme.NierDim,
                    fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Error Banner
            AnimatedVisibility(visible = errorMessage != null) {
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
                            text = errorMessage ?: "",
                            color = ru.nya.nyeios.ui.theme.NierRed,
                            fontSize = 11.sp,
                            fontFamily = ru.nya.nyeios.ui.theme.ShareTechMonoFamily
                        )
                    }
                }
            }

            // Input Fields
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Username field
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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

                // Password field
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            }

            Spacer(modifier = Modifier.height(18.dp))

            // NieR Action Button
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
                        text = if (authSession.isLoggedIn) "▶  ОБНОВИТЬ АВТОРИЗАЦИЮ" else "▶  ВОЙТИ В ЭИОС",
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
