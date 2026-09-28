package ru.nya.nyeios.ui.floormap

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.res.stringResource
import ru.nya.nyeios.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nya.nyeios.data.floormap.FloorMapRepository
import ru.nya.nyeios.ui.common.localizedRoomType
import ru.nya.nyeios.ui.common.localizedRoomWing
import ru.nya.nyeios.ui.common.localizedStairsName
import ru.nya.nyeios.ui.theme.NycCyan
import ru.nya.nyeios.ui.theme.NycLed
import ru.nya.nyeios.ui.theme.NycMonoFamily
import ru.nya.nyeios.ui.theme.NycSansFamily
import ru.nya.nyeios.ui.theme.NycShapes
import ru.nya.nyeios.ui.theme.appSheetShape
import ru.nya.nyeios.ui.theme.nycRaised
import ru.nya.nyeios.ui.theme.nycWell
import kotlin.math.roundToInt

// Parallel NyC-modern map sheet from mockup nyeios_redesign.html (screen 3) and
// reference 3.png. Same state, same repository rules as FloorMapBottomSheet; only
// the chrome differs. The map engine itself (SingleFloorInteractiveView) is reused
// untouched, so routing geometry and tap handling are identical.
//
// Mockup notes:
// - seg holds 3|4 only (no 3<->4 связка tab); cross-floor routes render their
//   destination-floor segment with the transition summarized in the info card.
// - room taps always retarget the destination (no ОТ/ДО switcher in the mockup);
//   the start point arrives from the schedule pair flow via fromRoomQuery.

// Mockup fixed tokens.
private val nycText = Color(0xFFDBE4F0)
private val nycMuted = Color(0xFF8794A7)
private val nycFaint = Color(0xFF5B6A7E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NycMapSheet(
    targetRoomQuery: String,
    fromRoomQuery: String? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Same resolution rules as the legacy sheet.
    val targetFloor = remember(targetRoomQuery) {
        FloorMapRepository.getFloorForRoom(targetRoomQuery) ?: 4
    }
    val initialDest = remember(targetRoomQuery) {
        FloorMapRepository.findRoom(targetRoomQuery)
    }
    val initialStart = remember(fromRoomQuery) {
        if (!fromRoomQuery.isNullOrBlank()) FloorMapRepository.findRoom(fromRoomQuery) else null
    }

    var startRoom by remember(fromRoomQuery) { mutableStateOf(initialStart) }
    var destRoom by remember(targetRoomQuery) { mutableStateOf(initialDest) }

    val route = remember(startRoom, destRoom) {
        if (startRoom != null && destRoom != null) {
            FloorMapRepository.buildRoute(startRoom?.room, destRoom!!.room)
        } else {
            null
        }
    }

    var selectedFloor by remember { mutableIntStateOf(targetFloor) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141D2B),
        shape = appSheetShape(),
        scrimColor = Color(0xCC04070B),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 9.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(150, 185, 235, alpha = 46))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            // Header: collapse | Навигатор + КОРПУС 1 · ЭТАЖ N | locate.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .nycRaised(13.dp)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.map_collapse),
                        tint = nycMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.map_navigator_title),
                        fontFamily = NycSansFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.5.sp,
                        color = nycText
                    )
                    Text(
                        text = stringResource(R.string.map_campus_floor_fmt, selectedFloor),
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 8.5.sp,
                        letterSpacing = 1.5.sp,
                        color = nycFaint,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .nycRaised(13.dp)
                        .clickable {
                            // Jump back to the destination floor (view recenters).
                            selectedFloor = destRoom?.floor ?: targetFloor
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = stringResource(R.string.map_to_target),
                        tint = nycMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Map bar: seg 3|4 + legend chips.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .nycWell(12.dp)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NycFloorSeg(
                        label = "3",
                        selected = selectedFloor == 3,
                        onClick = {
                            selectedFloor = 3
                            if (destRoom == null || destRoom?.floor != 3) {
                                destRoom = FloorMapRepository.findRoom("301")
                            }
                        }
                    )
                    NycFloorSeg(
                        label = "4",
                        selected = selectedFloor == 4,
                        onClick = {
                            selectedFloor = 4
                            if (destRoom == null || destRoom?.floor != 4) {
                                destRoom = FloorMapRepository.findRoom("421")
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                NycLegendChip(
                    text = stringResource(R.string.map_route_label),
                    dot = true,
                    active = route != null && (route.points.isNotEmpty() || route.isCrossFloor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                NycLegendChip(text = stringResource(R.string.map_door_to_door), dot = false, active = false)
            }

            // Map engine (shared with the legacy sheet).
            SingleFloorInteractiveView(
                floor = selectedFloor,
                startRoom = startRoom,
                destRoom = destRoom,
                route = route,
                onRoomSelected = { clickedRoom ->
                    destRoom = clickedRoom
                }
            )

            // Info card (mockup mapinfo).
            if (destRoom != null) {
                val distPx = if (route != null) computeRouteDistance(route) else 0f
                val meters = (distPx * 0.11f).toInt()
                val minutes = maxOf(1, (meters / 70.0).roundToInt())
                val floorsLine = if (route?.isCrossFloor == true) {
                    val from = startRoom?.floor ?: selectedFloor
                    stringResource(R.string.schedule_floor_transfer_fmt, from, destRoom!!.floor)
                } else {
                    stringResource(R.string.schedule_floor_fmt, destRoom!!.floor)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 10.dp)
                        .height(88.dp)
                        .shadow(
                            16.dp,
                            RoundedCornerShape(18.dp),
                            ambientColor = Color.Black.copy(alpha = 0.55f),
                            spotColor = Color.Black.copy(alpha = 0.55f)
                        )
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF1C2838), Color(0xFF131C2A))
                            ),
                            RoundedCornerShape(18.dp)
                        )
                        .border(
                            1.dp,
                            Color(150, 185, 235, alpha = 33),
                            RoundedCornerShape(18.dp)
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .nycWell(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MeetingRoom,
                                contentDescription = null,
                                tint = NycCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.map_target_label),
                                fontFamily = NycMonoFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 8.sp,
                                letterSpacing = 1.5.sp,
                                color = nycFaint
                            )
                            Text(
                                text = stringResource(R.string.lesson_room_prefix, destRoom!!.room),
                                fontFamily = NycMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = NycCyan,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                text = "${localizedRoomType(destRoom!!.type)} · ${localizedRoomWing(destRoom!!.wing)}"
                                    .uppercase(),
                                fontFamily = NycMonoFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 9.sp,
                                color = nycMuted,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(56.dp)
                                .background(Color(150, 185, 235, alpha = 41))
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (route != null) stringResource(R.string.map_time_min_fmt, minutes) else "—",
                                fontFamily = NycMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = nycText
                            )
                            Text(
                                text = if (route != null) stringResource(R.string.schedule_dist_fmt, meters) else "",
                                fontFamily = NycMonoFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 8.sp,
                                letterSpacing = 1.sp,
                                color = nycFaint,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                text = floorsLine,
                                fontFamily = NycMonoFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 8.sp,
                                letterSpacing = 1.sp,
                                color = nycFaint,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            // Route transition note (mockup has no ОТ/ДО pills; stairs shown here).
            if (route?.isCrossFloor == true) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NycLed(color = NycCyan, diameter = 7.dp)
                    Text(
                        text = stringResource(R.string.map_transition_fmt, localizedStairsName(route.transitionStairsName)).uppercase(),
                        fontFamily = NycMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 8.5.sp,
                        letterSpacing = 1.sp,
                        color = NycCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun NycFloorSeg(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .height(32.dp)
            .then(if (selected) Modifier.nycRaised(10.dp) else Modifier)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = NycMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = if (selected) NycCyan else nycFaint
        )
    }
}

@Composable
private fun NycLegendChip(text: String, dot: Boolean, active: Boolean) {
    Row(
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (dot) {
            NycLed(color = NycCyan, diameter = 7.dp)
        }
        Text(
            text = text,
            fontFamily = NycMonoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.5.sp,
            letterSpacing = 1.sp,
            color = if (active) NycCyan else nycMuted,
            maxLines = 1
        )
    }
}
