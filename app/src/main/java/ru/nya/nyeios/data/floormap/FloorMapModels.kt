package ru.nya.nyeios.data.floormap
 
import androidx.compose.ui.geometry.Offset

enum class RoomWing(val title: String) {
    TOP("Северный ряд (Верх)"),
    BOTTOM("Южный ряд (Низ)"),
    VERTICAL("Восточное крыло (Право)"),
    LEFT("Западное крыло (Лево)")
}

enum class RoomType(val title: String) {
    CLASSROOM("Учебная аудитория"),
    DEAN_OFFICE("Деканат"),
    STAIRS("Лестница"),
    RESTROOM("Санузел"),
    OTHER("Служебное помещение")
}

data class FloorRoom(
    val room: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val wing: RoomWing,
    val type: RoomType = RoomType.CLASSROOM,
    val label: String = room,
    val description: String = "",
    val nearestStairs: String = "",
    val doorX: Float = x + width / 2f,
    val doorY: Float = y,
    val accessWaypoints: List<Offset> = emptyList(),
    val floor: Int = 4
) {
    val doorPoint: Offset get() = Offset(doorX, doorY)
    val centerX: Float get() = x + width / 2f
    val centerY: Float get() = y + height / 2f

    fun containsPoint(px: Float, py: Float): Boolean {
        return px in x..(x + width) && py in y..(y + height)
    }
}

/** Corridor axes of one floor, as authored in the map JSON. */
data class FloorCorridors(
    val horizontalY: Float,
    val verticalX: Float,
    val junctionX: Float = verticalX,
    val junctionY: Float = horizontalY,
    /** West-wing hallway axis (`corridors.startX`), floor 4 only. */
    val startX: Float? = null,
    val startY: Float? = null
)

/** Staircase marker on a floor map (`stairs[]` entry). */
data class FloorStairs(
    val id: String,
    val name: String,
    val x: Float,
    val y: Float
) {
    val point: Offset get() = Offset(x, y)
}

/** Parsed contents of one `floor_<n>_map.json` asset. */
data class FloorMapData(
    val floor: Int,
    val width: Float,
    val height: Float,
    val corridors: FloorCorridors? = null,
    val stairs: List<FloorStairs> = emptyList(),
    val rooms: List<FloorRoom> = emptyList()
)

data class FloorRoute(
    val fromRoom: FloorRoom?,
    val toRoom: FloorRoom,
    val points: List<Offset>,
    val description: String = "",
    val isCrossFloor: Boolean = false,
    val fromFloor: Int? = null,
    val toFloor: Int = 4,
    val fromFloorPoints: List<Offset> = emptyList(),
    val toFloorPoints: List<Offset> = emptyList(),
    val transitionStairsName: String = "",
    val fromStairsPoint: Offset? = null,
    val toStairsPoint: Offset? = null
)

enum class FloorLevel(val number: Int, val title: String, val hasMap: Boolean) {
    FLOOR_3(3, "3 этаж", true),
    FLOOR_4(4, "4 этаж", true);

    companion object {
        fun fromNumber(num: Int): FloorLevel? {
            return entries.find { it.number == num }
        }
    }
}
