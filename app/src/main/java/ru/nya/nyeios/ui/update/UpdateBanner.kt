package ru.nya.nyeios.ui.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nya.nyeios.data.model.UpdateUiState
import ru.nya.nyeios.ui.theme.NierBlue
import ru.nya.nyeios.ui.theme.NierBorder
import ru.nya.nyeios.ui.theme.NierBorderLight
import ru.nya.nyeios.ui.theme.NierDark
import ru.nya.nyeios.ui.theme.NierDarkSecondary
import ru.nya.nyeios.ui.theme.NierDim
import ru.nya.nyeios.ui.theme.NierGreen
import ru.nya.nyeios.ui.theme.NierPanel
import ru.nya.nyeios.ui.theme.NierRed
import ru.nya.nyeios.ui.theme.RajdhaniFamily
import ru.nya.nyeios.ui.theme.ShareTechMonoFamily

@Composable
fun UpdateBanner(
    state: UpdateUiState,
    viewModel: UpdateViewModel,
    modifier: Modifier = Modifier
) {
    val visible = state is UpdateUiState.UpdateAvailable
        || state is UpdateUiState.Downloading
        || state is UpdateUiState.ReadyToInstall
        || state is UpdateUiState.Error

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .background(NierPanel)
                .border(1.dp, NierBorderLight)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            when (state) {
                is UpdateUiState.UpdateAvailable -> BannerAvailable(state, viewModel)
                is UpdateUiState.Downloading -> BannerDownloading(state, viewModel)
                is UpdateUiState.ReadyToInstall -> BannerReadyToInstall(viewModel)
                is UpdateUiState.Error -> BannerError(state, viewModel)
                else -> Unit
            }
        }
    }
}

// ─── UpdateAvailable ──────────────────────────────────────────────────────────

@Composable
private fun BannerAvailable(state: UpdateUiState.UpdateAvailable, viewModel: UpdateViewModel) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = NierBlue,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "ДОСТУПНО ОБНОВЛЕНИЕ v${state.info.version}",
                    fontFamily = RajdhaniFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp,
                    color = NierDark
                )
            }

            if (state.info.changelog.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.info.changelog,
                    fontFamily = ShareTechMonoFamily,
                    fontSize = 10.sp,
                    color = NierDim,
                    lineHeight = 14.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .background(NierBlue)
                    .clickable { viewModel.downloadApk(state.info) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "СКАЧАТЬ",
                        fontFamily = RajdhaniFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = Color.White
                    )
                }
            }
        }

        // Кнопка закрытия
        Box(
            modifier = Modifier
                .size(24.dp)
                .clickable { viewModel.dismiss() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Закрыть",
                tint = NierDim,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

// ─── Downloading ──────────────────────────────────────────────────────────────

@Composable
private fun BannerDownloading(state: UpdateUiState.Downloading, viewModel: UpdateViewModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "СКАЧИВАНИЕ v${state.info.version}…",
                fontFamily = RajdhaniFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp,
                color = NierDark
            )
            val downloaded = formatSize(state.bytesNow)
            val total = if (state.bytesTotal > 0) formatSize(state.bytesTotal) else "?"
            Text(
                text = "$downloaded / $total",
                fontFamily = ShareTechMonoFamily,
                fontSize = 10.sp,
                color = NierDim
            )
        }

        Spacer(Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { if (state.progress > 0f) state.progress else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = NierBlue,
            trackColor = NierBlue.copy(alpha = 0.2f)
        )

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .border(1.dp, NierBorder)
                .background(Color.Transparent)
                .clickable { viewModel.cancelDownload() }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ОТМЕНА",
                fontFamily = ShareTechMonoFamily,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
                color = NierDark
            )
        }
    }
}

// ─── ReadyToInstall ───────────────────────────────────────────────────────────

@Composable
private fun BannerReadyToInstall(viewModel: UpdateViewModel) {
    val context = LocalContext.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Text(
                text = "ОБНОВЛЕНИЕ ЗАГРУЖЕНО",
                fontFamily = RajdhaniFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp,
                color = NierDark
            )
            Text(
                text = "Нажмите, чтобы установить APK",
                fontFamily = ShareTechMonoFamily,
                fontSize = 10.sp,
                color = NierDim
            )
        }

        Box(
            modifier = Modifier
                .background(NierGreen)
                .clickable { viewModel.installApk(context) }
                .padding(horizontal = 14.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "УСТАНОВИТЬ",
                fontFamily = RajdhaniFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = Color.White
            )
        }
    }
}

// ─── Error ────────────────────────────────────────────────────────────────────

@Composable
private fun BannerError(state: UpdateUiState.Error, viewModel: UpdateViewModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "ОШИБКА ОБНОВЛЕНИЯ",
                fontFamily = RajdhaniFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp,
                color = NierRed
            )
            Text(
                text = state.message,
                fontFamily = ShareTechMonoFamily,
                fontSize = 10.sp,
                color = NierDim
            )
        }

        Box(
            modifier = Modifier
                .size(24.dp)
                .clickable { viewModel.dismiss() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Закрыть",
                tint = NierDim,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 Б"
    val mb = bytes / 1_048_576.0
    val kb = bytes / 1024.0
    return when {
        mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f МБ", mb)
        kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f КБ", kb)
        else -> "$bytes Б"
    }
}
