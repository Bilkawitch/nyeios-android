package ru.nya.nyeios.ui.floormap

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nya.nyeios.R
import ru.nya.nyeios.data.floormap.FloorLevel
import ru.nya.nyeios.data.floormap.FloorMapRepository
import ru.nya.nyeios.data.floormap.FloorRoom
import ru.nya.nyeios.data.floormap.FloorRoute
import ru.nya.nyeios.data.floormap.RoomType
import ru.nya.nyeios.ui.theme.LectureBlue
import ru.nya.nyeios.ui.theme.LiveBadgeColor
import ru.nya.nyeios.ui.theme.ObsidianBg
import ru.nya.nyeios.ui.theme.ObsidianBorder
import ru.nya.nyeios.ui.theme.ObsidianCard
import ru.nya.nyeios.ui.theme.ObsidianSurface
import ru.nya.nyeios.ui.theme.TextMuted
import ru.nya.nyeios.ui.theme.TextPrimary
import ru.nya.nyeios.ui.theme.TextSecondary
import kotlin.math.hypot

enum class RouteEditTarget {
    START,       // Selection applies to "Предыдущая"
    DESTINATION  // Selection applies to "Текущая"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloorMapBottomSheet(
    targetRoomQuery: String,
    fromRoomQuery: String? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val targetFloor = remember(targetRoomQuery) {
        FloorMapRepository.getFloorForRoom(targetRoomQuery) ?: 4
    }
    val fromFloor = remember(fromRoomQuery) {
        if (!fromRoomQuery.isNullOrBlank()) FloorMapRepository.getFloorForRoom(fromRoomQuery) else null
    }

    // Dynamic start and destination rooms
    val initialDest = remember(targetRoomQuery) {
        FloorMapRepository.findRoom(targetRoomQuery)
    }
    val initialStart = remember(fromRoomQuery) {
        if (!fromRoomQuery.isNullOrBlank()) FloorMapRepository.findRoom(fromRoomQuery) else null
    }

    var startRoom by remember(fromRoomQuery) { mutableStateOf(initialStart) }
    var destRoom by remember(targetRoomQuery) { mutableStateOf(initialDest) }

    // Mode for which point on map is being changed: Start vs Destination
    var editTarget by remember { mutableStateOf(RouteEditTarget.DESTINATION) }

    // Recompute route dynamically whenever startRoom or destRoom changes
    val route = remember(startRoom, destRoom) {
        if (startRoom != null && destRoom != null) {
            FloorMapRepository.buildRoute(startRoom?.room, destRoom!!.room)
        } else {
            null
        }
    }

    val isCrossFloorActive = route?.isCrossFloor == true || (fromFloor != null && targetFloor != fromFloor)
    val initialSelectedFloor = remember(isCrossFloorActive, targetFloor) {
        if (isCrossFloorActive) 0 else targetFloor
    }
    var selectedFloor by remember { mutableIntStateOf(initialSelectedFloor) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        scrimColor = Color.Black.copy(alpha = 0.70f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ObsidianBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (route != null && (route.points.isNotEmpty() || route.isCrossFloor)) {
                                    if (route.isCrossFloor) Color(0xFFD946EF).copy(alpha = 0.2f) else LectureBlue.copy(alpha = 0.2f)
                                } else {
                                    LiveBadgeColor.copy(alpha = 0.15f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (route != null && (route.points.isNotEmpty() || route.isCrossFloor)) {
                                Icons.Default.Route
                            } else {
                                Icons.Default.LocationOn
                            },
                            contentDescription = null,
                            tint = if (route != null && (route.points.isNotEmpty() || route.isCrossFloor)) {
                                if (route.isCrossFloor) Color(0xFFD946EF) else LectureBlue
                            } else {
                                LiveBadgeColor
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = if (route != null && route.isCrossFloor) {
                                "Межэтажный маршрут"
                            } else if (route != null && route.points.isNotEmpty()) {
                                "Маршрут между парами"
                            } else {
                                "Навигация по корпусу"
                            },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "ГОУ ВО МО «ГСГУ»",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Floor Selector Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 3rd Floor Tab
                val is3Selected = selectedFloor == 3
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (is3Selected) LectureBlue else ObsidianCard)
                        .border(1.dp, if (is3Selected) LectureBlue else ObsidianBorder, RoundedCornerShape(10.dp))
                        .clickable {
                            selectedFloor = 3
                            if (destRoom == null || destRoom?.floor != 3) {
                                destRoom = FloorMapRepository.findRoom("301")
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "3 этаж",
                        fontSize = 13.sp,
                        fontWeight = if (is3Selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (is3Selected) Color.White else TextPrimary
                    )
                }

                // 4th Floor Tab
                val is4Selected = selectedFloor == 4
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (is4Selected) LectureBlue else ObsidianCard)
                        .border(1.dp, if (is4Selected) LectureBlue else ObsidianBorder, RoundedCornerShape(10.dp))
                        .clickable {
                            selectedFloor = 4
                            if (destRoom == null || destRoom?.floor != 4) {
                                destRoom = FloorMapRepository.findRoom("421")
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "4 этаж",
                        fontSize = 13.sp,
                        fontWeight = if (is4Selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (is4Selected) Color.White else TextPrimary
                    )
                }

                // Multi-floor Dual View Tab ("3 ↔ 4 Связка")
                val isMultiSelected = selectedFloor == 0
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isMultiSelected) Color(0xFFD946EF) else ObsidianCard)
                        .border(1.dp, if (isMultiSelected) Color(0xFFD946EF) else ObsidianBorder, RoundedCornerShape(10.dp))
                        .clickable { selectedFloor = 0 }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "3 ↔ 4 Связка",
                            fontSize = 13.sp,
                            fontWeight = if (isMultiSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isMultiSelected) Color.White else TextPrimary
                        )
                        if (route?.isCrossFloor == true) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.22f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "путь",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Floor Content: Multi-Floor (0) OR Single Floor (3 or 4) OR Stub
            when (selectedFloor) {
                0 -> {
                    MultiFloorInteractiveView(
                        startRoom = startRoom,
                        destRoom = destRoom,
                        route = route,
                        onRoomSelected = { clickedRoom ->
                            if (startRoom == null) {
                                destRoom = clickedRoom
                            } else {
                                when (editTarget) {
                                    RouteEditTarget.DESTINATION -> destRoom = clickedRoom
                                    RouteEditTarget.START -> startRoom = clickedRoom
                                }
                            }
                        }
                    )
                }
                3, 4 -> {
                    SingleFloorInteractiveView(
                        floor = selectedFloor,
                        startRoom = startRoom,
                        destRoom = destRoom,
                        route = route,
                        onRoomSelected = { clickedRoom ->
                            if (startRoom == null) {
                                destRoom = clickedRoom
                            } else {
                                when (editTarget) {
                                    RouteEditTarget.DESTINATION -> destRoom = clickedRoom
                                    RouteEditTarget.START -> startRoom = clickedRoom
                                }
                            }
                        }
                    )
                }
                else -> {
                    FloorStubView(
                        floor = selectedFloor,
                        onSwitchToFloor4 = { selectedFloor = 4 }
                    )
                }
            }

            // Bottom Info Card (Route Navigation or Single Room details)
            if (selectedFloor in listOf(0, 3, 4)) {
                Spacer(modifier = Modifier.height(8.dp))
                if (startRoom != null && destRoom != null) {
                    RouteInfoCard(
                        startRoom = startRoom,
                        destRoom = destRoom!!,
                        route = route,
                        editTarget = editTarget,
                        onSelectEditTarget = { editTarget = it },
                        onClearRoute = { startRoom = null }
                    )
                } else if (destRoom != null) {
                    RoomInfoCard(
                        room = destRoom!!
                    )
                }
            }
        }
    }
}


@Composable
private fun SingleFloorInteractiveView(
    floor: Int,
    startRoom: FloorRoom?,
    destRoom: FloorRoom?,
    route: FloorRoute?,
    onRoomSelected: (FloorRoom) -> Unit
) {
    val floorImageBitmap = ImageBitmap.imageResource(
        id = if (floor == 3) R.drawable.floor_3 else R.drawable.floor_4
    )
    val mapWidth = if (floor == 3) FloorMapRepository.MAP_WIDTH_3 else FloorMapRepository.MAP_WIDTH_4
    val mapHeight = if (floor == 3) FloorMapRepository.MAP_HEIGHT_3 else FloorMapRepository.MAP_HEIGHT_4
    val rooms = remember(floor) { FloorMapRepository.getRoomsForFloor(floor) }

    // Pulsing highlight animation for destination room
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_animation")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.50f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val pulseStrokeWidth by infiniteTransition.animateFloat(
        initialValue = 2.2f,
        targetValue = 4.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_stroke"
    )

    // Marching animation for route chevrons
    val chevronAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 32f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "chevron_anim"
    )

    val currentFloorRoutePoints = remember(route, floor) {
        if (route == null) emptyList()
        else if (route.isCrossFloor) {
            if (route.fromFloor == floor) route.fromFloorPoints
            else if (route.toFloor == floor) route.toFloorPoints
            else emptyList()
        } else if (route.toFloor == floor) {
            route.points
        } else {
            emptyList()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianBg)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
    ) {
        val viewWidthPx = constraints.maxWidth.toFloat()
        val viewHeightPx = constraints.maxHeight.toFloat()

        val fitScale = remember(viewWidthPx, viewHeightPx, mapWidth, mapHeight) {
            val sx = (viewWidthPx - 16f) / mapWidth
            val sy = (viewHeightPx - 16f) / mapHeight
            minOf(sx, sy).coerceIn(0.4f, 1.2f)
        }

        var scale by remember(floor) { mutableFloatStateOf(fitScale) }
        var offset by remember(floor) {
            mutableStateOf(
                Offset(
                    (viewWidthPx - mapWidth * fitScale) / 2f,
                    (viewHeightPx - mapHeight * fitScale) / 2f
                )
            )
        }

        // Automatic framing when route or room selection changes
        LaunchedEffect(startRoom, destRoom, currentFloorRoutePoints, viewWidthPx, viewHeightPx) {
            if (viewWidthPx <= 0f || viewHeightPx <= 0f) return@LaunchedEffect

            if (currentFloorRoutePoints.size >= 2) {
                var minX = Float.MAX_VALUE
                var maxX = Float.MIN_VALUE
                var minY = Float.MAX_VALUE
                var maxY = Float.MIN_VALUE

                currentFloorRoutePoints.forEach { pt ->
                    minX = minOf(minX, pt.x)
                    maxX = maxOf(maxX, pt.x)
                    minY = minOf(minY, pt.y)
                    maxY = maxOf(maxY, pt.y)
                }

                if (startRoom?.floor == floor) {
                    minX = minOf(minX, startRoom.x)
                    maxX = maxOf(maxX, startRoom.x + startRoom.width)
                    minY = minOf(minY, startRoom.y)
                    maxY = maxOf(maxY, startRoom.y + startRoom.height)
                }

                if (destRoom?.floor == floor) {
                    minX = minOf(minX, destRoom.x)
                    maxX = maxOf(maxX, destRoom.x + destRoom.width)
                    minY = minOf(minY, destRoom.y)
                    maxY = maxOf(maxY, destRoom.y + destRoom.height)
                }

                val bboxWidth = (maxX - minX).coerceAtLeast(100f)
                val bboxHeight = (maxY - minY).coerceAtLeast(100f)

                val routeScaleX = (viewWidthPx - 64f) / bboxWidth
                val routeScaleY = (viewHeightPx - 64f) / bboxHeight
                val targetScale = minOf(routeScaleX, routeScaleY).coerceIn(0.85f, 2.2f)

                val bboxCenterX = (minX + maxX) / 2f
                val bboxCenterY = (minY + maxY) / 2f

                scale = targetScale
                offset = Offset(
                    viewWidthPx / 2f - bboxCenterX * targetScale,
                    viewHeightPx / 2f - bboxCenterY * targetScale
                )
            } else if (destRoom != null && destRoom.floor == floor) {
                val focusScale = (fitScale * 1.85f).coerceIn(1.2f, 3.0f)
                val targetOffsetX = viewWidthPx / 2f - destRoom.centerX * focusScale
                val targetOffsetY = viewHeightPx / 2f - destRoom.centerY * focusScale
                scale = focusScale
                offset = Offset(targetOffsetX, targetOffsetY)
            }
        }

        val textPaint = remember {
            Paint().apply {
                isAntiAlias = true
                textSize = 22f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(rooms, scale, offset) {
                    detectTapGestures { tapOffset ->
                        val mapX = (tapOffset.x - offset.x) / scale
                        val mapY = (tapOffset.y - offset.y) / scale
                        val clicked = rooms.find { it.containsPoint(mapX, mapY) }
                        if (clicked != null) {
                            onRoomSelected(clicked)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(fitScale * 0.7f, 3.8f)
                        offset += pan
                    }
                }
        ) {
            withTransform({
                translate(offset.x, offset.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                // 1. Draw Transparent Floor Plan
                drawImage(floorImageBitmap)

                // 2. Draw Floor Watermark / Badge
                drawRoundRect(
                    color = ObsidianSurface.copy(alpha = 0.85f),
                    topLeft = Offset(16f, 16f),
                    size = Size(100f, 32f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                )
                textPaint.color = android.graphics.Color.WHITE
                textPaint.textSize = 16f
                drawContext.canvas.nativeCanvas.drawText("$floor ЭТАЖ", 66f, 38f, textPaint)

                // 3. Draw Room Outlines
                drawRoomsList(
                    rooms = rooms,
                    startRoom = startRoom,
                    destRoom = destRoom,
                    pulseAlpha = pulseAlpha,
                    pulseStrokeWidth = pulseStrokeWidth,
                    textPaint = textPaint,
                    yOffset = 0f
                )

                // 4. Draw Route Path on Current Floor
                if (currentFloorRoutePoints.size >= 2) {
                    drawRouteSegments(
                        pts = currentFloorRoutePoints,
                        chevronAnim = chevronAnim,
                        baseColor = if (route?.isCrossFloor == true) Color(0xFFD946EF) else LectureBlue,
                        yOffset = 0f
                    )

                    val startPt = currentFloorRoutePoints.first()
                    val endPt = currentFloorRoutePoints.last()

                    if (route?.isCrossFloor == true) {
                        if (route.fromFloor == floor) {
                            // Starts at room door, ends at stairs
                            drawDoorMarker(startPt, isStart = true)
                            // Beacon at stairs
                            drawCircle(color = Color(0xFFD946EF).copy(alpha = pulseAlpha), radius = 14f, center = endPt)
                            drawCircle(color = Color(0xFFD946EF), radius = 6.5f, center = endPt)
                            drawCircle(color = Color.White, radius = 3f, center = endPt)
                        } else if (route.toFloor == floor) {
                            // Starts at stairs, ends at room door
                            drawCircle(color = Color(0xFFD946EF).copy(alpha = pulseAlpha), radius = 14f, center = startPt)
                            drawCircle(color = Color(0xFFD946EF), radius = 6.5f, center = startPt)
                            drawCircle(color = Color.White, radius = 3f, center = startPt)
                            drawDoorMarker(endPt, isStart = false)
                        }
                    } else {
                        drawDoorMarker(startPt, isStart = true)
                        drawDoorMarker(endPt, isStart = false)
                    }
                }
            }
        }

        // Floating Zoom / Fit Controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianSurface.copy(alpha = 0.88f))
                .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { scale = (scale * 1.25f).coerceAtMost(3.8f) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Приблизить",
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = { scale = (scale / 1.25f).coerceAtLeast(fitScale * 0.7f) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Отдалить",
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = {
                    scale = fitScale
                    offset = Offset(
                        (viewWidthPx - mapWidth * fitScale) / 2f,
                        (viewHeightPx - mapHeight * fitScale) / 2f
                    )
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FitScreen,
                    contentDescription = "Вписать в экран",
                    tint = LectureBlue,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun MultiFloorInteractiveView(
    startRoom: FloorRoom?,
    destRoom: FloorRoom?,
    route: FloorRoute?,
    onRoomSelected: (FloorRoom) -> Unit
) {
    val floor4ImageBitmap = ImageBitmap.imageResource(id = R.drawable.floor_4)
    val floor3ImageBitmap = ImageBitmap.imageResource(id = R.drawable.floor_3)

    val rooms4 = remember { FloorMapRepository.getRoomsForFloor(4) }
    val rooms3 = remember { FloorMapRepository.getRoomsForFloor(3) }

    val floorGap = 160f
    val floor3Y = FloorMapRepository.MAP_HEIGHT_4 + floorGap // 605 + 160 = 765f
    val combinedWidth = 1024f
    val combinedHeight = floor3Y + FloorMapRepository.MAP_HEIGHT_3 // 765 + 603 = 1368f

    val infiniteTransition = rememberInfiniteTransition(label = "multi_floor_anim")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.50f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val pulseStrokeWidth by infiniteTransition.animateFloat(
        initialValue = 2.2f,
        targetValue = 4.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_stroke"
    )
    val chevronAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 32f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "chevron_anim"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(410.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianBg)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
    ) {
        val viewWidthPx = constraints.maxWidth.toFloat()
        val viewHeightPx = constraints.maxHeight.toFloat()

        val fitScale = remember(viewWidthPx, viewHeightPx) {
            val sx = (viewWidthPx - 16f) / combinedWidth
            val sy = (viewHeightPx - 16f) / combinedHeight
            minOf(sx, sy).coerceIn(0.25f, 1.0f)
        }

        var scale by remember { mutableFloatStateOf(fitScale) }
        var offset by remember {
            mutableStateOf(
                Offset(
                    (viewWidthPx - combinedWidth * fitScale) / 2f,
                    (viewHeightPx - combinedHeight * fitScale) / 2f
                )
            )
        }

        // Automatic framing when route or room selection changes
        LaunchedEffect(startRoom, destRoom, route, viewWidthPx, viewHeightPx) {
            if (viewWidthPx <= 0f || viewHeightPx <= 0f) return@LaunchedEffect

            if (route != null && route.isCrossFloor) {
                var minX = Float.MAX_VALUE
                var maxX = Float.MIN_VALUE
                var minY = Float.MAX_VALUE
                var maxY = Float.MIN_VALUE

                val pts4 = if (route.fromFloor == 4) route.fromFloorPoints else route.toFloorPoints
                pts4.forEach { pt ->
                    minX = minOf(minX, pt.x)
                    maxX = maxOf(maxX, pt.x)
                    minY = minOf(minY, pt.y)
                    maxY = maxOf(maxY, pt.y)
                }

                val pts3 = if (route.fromFloor == 3) route.fromFloorPoints else route.toFloorPoints
                pts3.forEach { pt ->
                    minX = minOf(minX, pt.x)
                    maxX = maxOf(maxX, pt.x)
                    minY = minOf(minY, pt.y + floor3Y)
                    maxY = maxOf(maxY, pt.y + floor3Y)
                }

                val bboxWidth = (maxX - minX).coerceAtLeast(180f)
                val bboxHeight = (maxY - minY).coerceAtLeast(250f)

                val routeScaleX = (viewWidthPx - 48f) / bboxWidth
                val routeScaleY = (viewHeightPx - 48f) / bboxHeight
                val targetScale = minOf(routeScaleX, routeScaleY).coerceIn(0.40f, 1.4f)

                val bboxCenterX = (minX + maxX) / 2f
                val bboxCenterY = (minY + maxY) / 2f

                scale = targetScale
                offset = Offset(
                    viewWidthPx / 2f - bboxCenterX * targetScale,
                    viewHeightPx / 2f - bboxCenterY * targetScale
                )
            } else {
                scale = fitScale
                offset = Offset(
                    (viewWidthPx - combinedWidth * fitScale) / 2f,
                    (viewHeightPx - combinedHeight * fitScale) / 2f
                )
            }
        }

        val textPaint = remember {
            Paint().apply {
                isAntiAlias = true
                textSize = 22f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
        }

        val badgePaint = remember {
            Paint().apply {
                isAntiAlias = true
                textSize = 15f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(rooms4, rooms3, scale, offset) {
                    detectTapGestures { tapOffset ->
                        val mapX = (tapOffset.x - offset.x) / scale
                        val mapY = (tapOffset.y - offset.y) / scale
                        val clicked = if (mapY < FloorMapRepository.MAP_HEIGHT_4 + floorGap / 2f) {
                            rooms4.find { it.containsPoint(mapX, mapY) }
                        } else {
                            rooms3.find { it.containsPoint(mapX, mapY - floor3Y) }
                        }
                        if (clicked != null) {
                            onRoomSelected(clicked)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(fitScale * 0.6f, 3.5f)
                        offset += pan
                    }
                }
        ) {
            withTransform({
                translate(offset.x, offset.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                // 1. Draw Floor 4 (Top Plan)
                drawImage(floor4ImageBitmap, topLeft = Offset.Zero)
                drawRoundRect(
                    color = ObsidianSurface.copy(alpha = 0.85f),
                    topLeft = Offset(16f, 16f),
                    size = Size(100f, 32f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                )
                textPaint.color = android.graphics.Color.WHITE
                textPaint.textSize = 16f
                drawContext.canvas.nativeCanvas.drawText("4 ЭТАЖ", 66f, 38f, textPaint)
                drawRoomsList(rooms4, startRoom, destRoom, pulseAlpha, pulseStrokeWidth, textPaint, yOffset = 0f)

                // 2. Draw Floor 3 (Bottom Plan)
                drawImage(floor3ImageBitmap, topLeft = Offset(0f, floor3Y))
                drawRoundRect(
                    color = ObsidianSurface.copy(alpha = 0.85f),
                    topLeft = Offset(16f, floor3Y + 16f),
                    size = Size(100f, 32f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                )
                textPaint.color = android.graphics.Color.WHITE
                textPaint.textSize = 16f
                drawContext.canvas.nativeCanvas.drawText("3 ЭТАЖ", 66f, floor3Y + 38f, textPaint)
                drawRoomsList(rooms3, startRoom, destRoom, pulseAlpha, pulseStrokeWidth, textPaint, yOffset = floor3Y)

                // 3. Draw Route
                if (route != null && route.isCrossFloor) {
                    val pts4 = if (route.fromFloor == 4) route.fromFloorPoints else route.toFloorPoints
                    val pts3 = if (route.fromFloor == 3) route.fromFloorPoints else route.toFloorPoints

                    // Floor 4 leg
                    drawRouteSegments(pts4, chevronAnim, LectureBlue, yOffset = 0f)
                    // Floor 3 leg
                    drawRouteSegments(pts3, chevronAnim, LectureBlue, yOffset = floor3Y)

                    // Connecting Stairs Dashed Line (Floor 4 Stairs <---> Floor 3 Stairs)
                    val st4 = if (route.fromFloor == 4) route.fromStairsPoint else route.toStairsPoint
                    val st3 = if (route.fromFloor == 3) route.fromStairsPoint else route.toStairsPoint

                    if (st4 != null && st3 != null) {
                        val p4 = st4
                        val p3 = Offset(st3.x, st3.y + floor3Y)

                        // Magenta Glow Line
                        drawLine(
                            color = Color(0xFFD946EF).copy(alpha = 0.35f),
                            start = p4,
                            end = p3,
                            strokeWidth = 9.0f,
                            cap = StrokeCap.Round
                        )

                        // Animated Dashed Line
                        drawLine(
                            color = Color(0xFFD946EF),
                            start = p4,
                            end = p3,
                            strokeWidth = 4.0f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), phase = -chevronAnim),
                            cap = StrokeCap.Round
                        )

                        // Glowing Beacons at stairs ends
                        drawCircle(color = Color(0xFFD946EF).copy(alpha = pulseAlpha), radius = 14f, center = p4)
                        drawCircle(color = Color(0xFFD946EF), radius = 6.5f, center = p4)
                        drawCircle(color = Color.White, radius = 3f, center = p4)

                        drawCircle(color = Color(0xFFD946EF).copy(alpha = pulseAlpha), radius = 14f, center = p3)
                        drawCircle(color = Color(0xFFD946EF), radius = 6.5f, center = p3)
                        drawCircle(color = Color.White, radius = 3f, center = p3)

                        // Badge in the inter-floor gap
                        val midX = (p4.x + p3.x) / 2f
                        val midY = (p4.y + p3.y) / 2f

                        drawRoundRect(
                            color = ObsidianSurface.copy(alpha = 0.90f),
                            topLeft = Offset(midX - 120f, midY - 18f),
                            size = Size(240f, 36f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
                        )
                        drawRoundRect(
                            color = Color(0xFFD946EF),
                            topLeft = Offset(midX - 120f, midY - 18f),
                            size = Size(240f, 36f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f),
                            style = Stroke(width = 1.5f)
                        )

                        badgePaint.color = android.graphics.Color.WHITE
                        drawContext.canvas.nativeCanvas.drawText("↕ ${route.transitionStairsName}", midX, midY + 6f, badgePaint)
                    }

                    // Start marker
                    val startPt = if (route.fromFloor == 4) {
                        route.fromFloorPoints.firstOrNull()
                    } else {
                        route.fromFloorPoints.firstOrNull()?.let { Offset(it.x, it.y + floor3Y) }
                    }
                    if (startPt != null) drawDoorMarker(startPt, isStart = true)

                    // Destination marker
                    val destPt = if (route.toFloor == 4) {
                        route.toFloorPoints.lastOrNull()
                    } else {
                        route.toFloorPoints.lastOrNull()?.let { Offset(it.x, it.y + floor3Y) }
                    }
                    if (destPt != null) drawDoorMarker(destPt, isStart = false)
                } else if (route != null && !route.isCrossFloor && route.points.size >= 2) {
                    val yOff = if (route.toFloor == 3) floor3Y else 0f
                    drawRouteSegments(route.points, chevronAnim, LectureBlue, yOffset = yOff)
                    drawDoorMarker(Offset(route.points.first().x, route.points.first().y + yOff), isStart = true)
                    drawDoorMarker(Offset(route.points.last().x, route.points.last().y + yOff), isStart = false)
                }
            }
        }

        // Floating Zoom / Fit Controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianSurface.copy(alpha = 0.88f))
                .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { scale = (scale * 1.25f).coerceAtMost(3.5f) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Приблизить",
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = { scale = (scale / 1.25f).coerceAtLeast(fitScale * 0.6f) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Отдалить",
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = {
                    scale = fitScale
                    offset = Offset(
                        (viewWidthPx - combinedWidth * fitScale) / 2f,
                        (viewHeightPx - combinedHeight * fitScale) / 2f
                    )
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FitScreen,
                    contentDescription = "Вписать оба этажа",
                    tint = Color(0xFFD946EF),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun DrawScope.drawRoomsList(
    rooms: List<FloorRoom>,
    startRoom: FloorRoom?,
    destRoom: FloorRoom?,
    pulseAlpha: Float,
    pulseStrokeWidth: Float,
    textPaint: Paint,
    yOffset: Float = 0f
) {
    for (r in rooms) {
        val isStart = startRoom?.room == r.room && startRoom.floor == r.floor
        val isDest = destRoom?.room == r.room && destRoom.floor == r.floor

        val rTopLeft = Offset(r.x, r.y + yOffset)
        val rSize = Size(r.width, r.height)
        val rCenterX = r.centerX
        val rCenterY = r.centerY + yOffset

        when {
            isDest -> {
                drawRect(
                    color = LiveBadgeColor.copy(alpha = pulseAlpha),
                    topLeft = rTopLeft,
                    size = rSize
                )
                drawRect(
                    color = LiveBadgeColor,
                    topLeft = rTopLeft,
                    size = rSize,
                    style = Stroke(width = pulseStrokeWidth)
                )
                textPaint.color = android.graphics.Color.WHITE
                textPaint.textSize = if (r.width > 50) 24f else 18f
                drawContext.canvas.nativeCanvas.drawText(
                    r.room,
                    rCenterX,
                    rCenterY + 8f,
                    textPaint
                )
            }
            isStart -> {
                drawRect(
                    color = LectureBlue.copy(alpha = 0.28f),
                    topLeft = rTopLeft,
                    size = rSize
                )
                drawRect(
                    color = LectureBlue,
                    topLeft = rTopLeft,
                    size = rSize,
                    style = Stroke(width = 2.5f)
                )
                textPaint.color = android.graphics.Color.WHITE
                textPaint.textSize = if (r.width > 50) 22f else 16f
                drawContext.canvas.nativeCanvas.drawText(
                    r.room,
                    rCenterX,
                    rCenterY + 8f,
                    textPaint
                )
            }
            else -> {
                drawRect(
                    color = Color.White.copy(alpha = 0.08f),
                    topLeft = rTopLeft,
                    size = rSize,
                    style = Stroke(width = 0.8f)
                )
            }
        }
    }
}

private fun DrawScope.drawRouteSegments(
    pts: List<Offset>,
    chevronAnim: Float,
    baseColor: Color = LectureBlue,
    yOffset: Float = 0f
) {
    if (pts.size < 2) return
    val mappedPts = if (yOffset == 0f) pts else pts.map { it.copy(y = it.y + yOffset) }

    for (i in 0 until mappedPts.size - 1) {
        drawLine(
            color = baseColor.copy(alpha = 0.45f),
            start = mappedPts[i],
            end = mappedPts[i + 1],
            strokeWidth = 9.0f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = if (baseColor == Color(0xFFD946EF)) Color(0xFFF0ABFC) else Color(0xFF60A5FA),
            start = mappedPts[i],
            end = mappedPts[i + 1],
            strokeWidth = 4.5f,
            cap = StrokeCap.Round
        )
    }

    val chevronSpacing = 32f
    for (i in 0 until mappedPts.size - 1) {
        val p1 = mappedPts[i]
        val p2 = mappedPts[i + 1]
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val dist = hypot(dx, dy)
        if (dist < 18f) continue

        val ux = dx / dist
        val uy = dy / dist
        val nx = -uy
        val ny = ux

        var d = (chevronAnim % chevronSpacing) + 8f
        while (d < dist - 10f) {
            val cx = p1.x + ux * d
            val cy = p1.y + uy * d

            val tip = Offset(cx + ux * 5f, cy + uy * 5f)
            val w1 = Offset(cx - ux * 3.5f + nx * 4.5f, cy - uy * 3.5f + ny * 4.5f)
            val w2 = Offset(cx - ux * 3.5f - nx * 4.5f, cy - uy * 3.5f - ny * 4.5f)

            drawLine(
                color = Color.White,
                start = w1,
                end = tip,
                strokeWidth = 2.0f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = w2,
                end = tip,
                strokeWidth = 2.0f,
                cap = StrokeCap.Round
            )

            d += chevronSpacing
        }
    }
}

private fun DrawScope.drawDoorMarker(center: Offset, isStart: Boolean) {
    val color = if (isStart) LectureBlue else LiveBadgeColor
    drawCircle(
        color = color.copy(alpha = 0.35f),
        radius = 12f,
        center = center
    )
    drawCircle(
        color = color,
        radius = 6.5f,
        center = center
    )
    drawCircle(
        color = Color.White,
        radius = 3f,
        center = center
    )
}

@Composable
private fun RouteInfoCard(
    startRoom: FloorRoom?,
    destRoom: FloorRoom,
    route: FloorRoute?,
    editTarget: RouteEditTarget,
    onSelectEditTarget: (RouteEditTarget) -> Unit,
    onClearRoute: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianCard)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Interactive Selectable Steps Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Start Room Pill (Selectable for edit)
                val isEditingStart = editTarget == RouteEditTarget.START
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isEditingStart) LectureBlue.copy(alpha = 0.22f) else ObsidianSurface
                        )
                        .border(
                            if (isEditingStart) 2.dp else 1.dp,
                            if (isEditingStart) LectureBlue else ObsidianBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectEditTarget(RouteEditTarget.START) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "ПРЕДЫДУЩАЯ",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isEditingStart) LectureBlue else TextMuted
                            )
                            if (isEditingStart) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(LectureBlue)
                                )
                            }
                        }
                        Text(
                            text = if (startRoom != null) "Каб. ${startRoom.room} (${startRoom.floor} эт.)" else "Выбрать",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isEditingStart) Color.White else TextPrimary
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )

                // Destination Room Pill (Selectable for edit)
                val isEditingDest = editTarget == RouteEditTarget.DESTINATION
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isEditingDest) LiveBadgeColor.copy(alpha = 0.22f) else ObsidianSurface
                        )
                        .border(
                            if (isEditingDest) 2.dp else 1.dp,
                            if (isEditingDest) LiveBadgeColor else ObsidianBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectEditTarget(RouteEditTarget.DESTINATION) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "ТЕКУЩАЯ",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isEditingDest) LiveBadgeColor else TextMuted
                            )
                            if (isEditingDest) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(LiveBadgeColor)
                                )
                            }
                        }
                        Text(
                            text = "Каб. ${destRoom.room} (${destRoom.floor} эт.)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isEditingDest) Color.White else LiveBadgeColor
                        )
                    }
                }
            }

            // Description Line
            if (route != null) {
                Text(
                    text = route.description,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
            }

            // Cross-floor stairs notification
            if (route != null && route.isCrossFloor && route.transitionStairsName.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFD946EF).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFFD946EF).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Route,
                            contentDescription = null,
                            tint = Color(0xFFD946EF),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Переход по лестнице: ${route.transitionStairsName} (${route.fromFloor} ↔ ${route.toFloor} этаж)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF0ABFC)
                        )
                    }
                }
            }

            // Active edit mode hint
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.NearMe,
                    contentDescription = null,
                    tint = if (editTarget == RouteEditTarget.START) LectureBlue else LiveBadgeColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (editTarget == RouteEditTarget.START) {
                        "Кликните на кабинет на карте, чтобы сменить начало пути (Предыдущая)"
                    } else {
                        "Кликните на кабинет на карте, чтобы сменить цель (Текущая)"
                    },
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            // Destination details
            if (destRoom.type == RoomType.DEAN_OFFICE) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFD29922),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Кабинет 421 — Деканат факультета",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFD29922)
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomInfoCard(
    room: FloorRoom
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianCard)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Кабинет ${room.room}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = LiveBadgeColor
                    )

                    if (room.type == RoomType.DEAN_OFFICE) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFD29922).copy(alpha = 0.2f))
                                .border(1.dp, Color(0xFFD29922).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFD29922),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Деканат",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD29922)
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ObsidianSurface)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = room.wing.title,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            // Info Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Тип:",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Text(
                        text = room.type.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Лестница:",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Text(
                        text = room.nearestStairs,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LectureBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun FloorStubView(
    floor: Int,
    onSwitchToFloor4: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianBg)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(ObsidianCard)
                    .border(1.dp, ObsidianBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = LectureBlue,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = "Схема $floor этажа в разработке",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = "План аудиторий $floor этажа будет добавлен в одном из следующих обновлений. Сейчас доступна интерактивная карта 4 этажа.",
                fontSize = 13.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Button(
                onClick = onSwitchToFloor4,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LectureBlue,
                    contentColor = Color.White
                ),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = "Открыть 4 этаж",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
