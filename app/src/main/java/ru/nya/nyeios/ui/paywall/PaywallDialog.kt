package ru.nya.nyeios.ui.paywall

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.nya.nyeios.ui.theme.LabAmber
import ru.nya.nyeios.ui.theme.LectureBlue
import ru.nya.nyeios.ui.theme.ObsidianBorder
import ru.nya.nyeios.ui.theme.ObsidianCard
import ru.nya.nyeios.ui.theme.ObsidianCardSelected
import ru.nya.nyeios.ui.theme.ObsidianSurface
import ru.nya.nyeios.ui.theme.OtherPurple
import ru.nya.nyeios.ui.theme.TextMuted
import ru.nya.nyeios.ui.theme.TextPrimary
import ru.nya.nyeios.ui.theme.TextSecondary

data class PlanItem(
    val title: String,
    val badge: String?,
    val price: String,
    val period: String,
    val features: List<String>,
    val accentColor: Color
)

@Composable
fun PaywallDialog(
    onDismiss: () -> Unit
) {
    val plans = remember {
        listOf(
            PlanItem(
                title = "Pro План",
                badge = null,
                price = "499 ₽",
                period = "в месяц",
                features = listOf(
                    "Доступ к расписанию по понедельникам",
                    "Отображение только чётных пар",
                    "Без рекламы студенческой столовой"
                ),
                accentColor = LectureBlue
            ),
            PlanItem(
                title = "Ultra План",
                badge = "ХИТ 🔥",
                price = "1 499 ₽",
                period = "в месяц",
                features = listOf(
                    "Расписание на ВСЕ дни недели!",
                    "Автопоиск свободных диванов в вузе",
                    "Телепортация на первую пару",
                    "Преподаватель не спрашивает домашку"
                ),
                accentColor = LabAmber
            ),
            PlanItem(
                title = "Enterprise План",
                badge = "VIP 👑",
                price = "4 999 ₽",
                period = "разово",
                features = listOf(
                    "Отмена любых пар одним свайпом",
                    "Автоматическая сдача сессии на «отлично»",
                    "Большое спасибо от разработчика, его мамы, папы и всех дальних родственников"
                ),
                accentColor = OtherPurple
            )
        )
    }

    var selectedPlanIndex by remember { mutableIntStateOf(1) } // Ultra selected by default

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(24.dp))
                    .padding(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // VIP Badge Icon
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(LabAmber.copy(alpha = 0.3f), OtherPurple.copy(alpha = 0.3f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = LabAmber,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "NyEIOS Ultra Premium",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Чтобы начать смотреть расписание пар, оплатите подписку:",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Plans List
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        plans.forEachIndexed { index, plan ->
                            val isSelected = index == selectedPlanIndex
                            PlanCard(
                                plan = plan,
                                isSelected = isSelected,
                                onClick = { selectedPlanIndex = index }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Bottom Buttons: Two "Шутка" buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextSecondary
                            )
                        ) {
                            Text(
                                text = "Шутка",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LectureBlue,
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = "Шутка",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlanCard(
    plan: PlanItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) ObsidianCardSelected else ObsidianCard)
            .border(
                1.5.dp,
                if (isSelected) plan.accentColor else ObsidianBorder,
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row: Title & Badge on Left, Price on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = plan.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary,
                        maxLines = 1
                    )

                    if (plan.badge != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(plan.accentColor.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = plan.badge,
                                color = plan.accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Price & Period block on Right - strictly never wrapped
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = plan.price,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = if (isSelected) plan.accentColor else TextPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = plan.period,
                        fontSize = 11.sp,
                        color = TextMuted,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }
            }

            // Subtle divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(ObsidianBorder.copy(alpha = 0.7f))
            )

            // Features list
            Column(
                modifier = Modifier.padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                plan.features.forEach { feature ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 3.dp)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) plan.accentColor.copy(alpha = 0.25f) else ObsidianSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (isSelected) plan.accentColor else TextMuted,
                                modifier = Modifier.size(9.dp)
                            )
                        }
                        Text(
                            text = feature,
                            fontSize = 12.sp,
                            color = if (isSelected) TextSecondary else TextMuted,
                            lineHeight = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
