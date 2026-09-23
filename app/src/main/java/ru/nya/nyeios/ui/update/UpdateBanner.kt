package ru.nya.nyeios.ui.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nya.nyeios.data.model.UpdateUiState
import ru.nya.nyeios.ui.theme.ExamRed
import ru.nya.nyeios.ui.theme.ExamRedBg
import ru.nya.nyeios.ui.theme.LectureBlue
import ru.nya.nyeios.ui.theme.ObsidianBorder
import ru.nya.nyeios.ui.theme.ObsidianCard
import ru.nya.nyeios.ui.theme.PracticeGreen
import ru.nya.nyeios.ui.theme.TextMuted
import ru.nya.nyeios.ui.theme.TextPrimary
import ru.nya.nyeios.ui.theme.TextSecondary

@Composable
fun UpdateBanner(
    state: UpdateUiState,
    viewModel: UpdateViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ObsidianCard)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
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
    Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = LectureBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Доступно обновление ${state.info.version}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextPrimary
                )
            }

            if (state.info.changelog.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.info.changelog,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = { viewModel.downloadApk(state.info) },
                colors = ButtonDefaults.buttonColors(containerColor = LectureBlue),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Скачать",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Кнопка закрытия
        IconButton(
            onClick = { viewModel.dismiss() },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Закрыть",
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ─── Downloading ──────────────────────────────────────────────────────────────

@Composable
private fun BannerDownloading(state: UpdateUiState.Downloading, viewModel: UpdateViewModel) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Скачивание ${state.info.version}…",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TextPrimary
            )
            val downloaded = formatSize(state.bytesNow)
            val total = if (state.bytesTotal > 0) formatSize(state.bytesTotal) else "?"
            Text(
                text = "$downloaded / $total",
                fontSize = 11.sp,
                color = TextMuted
            )
        }

        Spacer(Modifier.height(8.dp))

        if (state.progress > 0f) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = LectureBlue,
                trackColor = ObsidianBorder
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = LectureBlue,
                trackColor = ObsidianBorder
            )
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = { viewModel.cancelDownload() },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(34.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
        ) {
            Text("Отмена", fontSize = 12.sp)
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
                text = "Обновление загружено",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TextPrimary
            )
            Text(
                text = "Нажмите, чтобы установить",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }

        Button(
            onClick = { viewModel.installApk(context) },
            colors = ButtonDefaults.buttonColors(containerColor = PracticeGreen),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Text(
                text = "Установить",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
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
                text = "Ошибка обновления",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = ExamRed
            )
            Text(
                text = state.message,
                fontSize = 11.sp,
                color = TextMuted
            )
        }

        IconButton(
            onClick = { viewModel.dismiss() },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Закрыть",
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
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
