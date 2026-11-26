package ru.nya.nyeios.data.floormap

import android.content.Context
import androidx.compose.ui.geometry.Offset
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object FloorMapRepository {

    private const val FLOOR_3_DEFAULT_WIDTH = 1024f
    private const val FLOOR_3_DEFAULT_HEIGHT = 603f
    private const val FLOOR_4_DEFAULT_WIDTH = 1024f
    private const val FLOOR_4_DEFAULT_HEIGHT = 727f

    private const val FLOOR_3_CORRIDOR_Y = 491f
    private const val FLOOR_3_CORRIDOR_X = 902f
    private const val FLOOR_4_CORRIDOR_Y = 535.4f
    private const val FLOOR_4_CORRIDOR_X = 916.6f
    private const val FLOOR_4_HALLWAY_X = 105.2f

    /** Assets are discovered by name, so a new floor only needs `floor_<n>_map.json`. */
    private val FLOOR_MAP_ASSET_REGEX = Regex("^floor_(\\d+)_map\\.json$")

    /** Parsed floor maps keyed by floor number. */
    private val maps = mutableMapOf<Int, FloorMapData>()

    /** Original asset JSON per floor, kept so [resetToDefaults] can drop runtime overrides. */
    private val baselineMapJson = mutableMapOf<Int, String>()

    private var initialized = false

    // Stairs are read from the floor JSON; the constants below are legacy fallbacks.
    val STAIRS_4_WEST: Offset get() = stairsPoint(4, "west", 203.8f, 509.8f)
    val STAIRS_4_CENTRAL: Offset get() = stairsPoint(4, "central", 605.1f, 499f)
    val STAIRS_4_EAST: Offset get() = stairsPoint(4, "east", 878.8f, 491.9f)

    val STAIRS_3_WEST: Offset get() = stairsPoint(3, "west", 162.2f, 452f)
    val STAIRS_3_CENTRAL: Offset get() = stairsPoint(3, "central", 606.4f, 461.6f)
    val STAIRS_3_EAST: Offset get() = stairsPoint(3, "east", 880.9f, 454.8f)

    // Aliases for floor 4 backward compatibility
    val STAIRS_WEST: Offset get() = STAIRS_4_WEST
    val STAIRS_CENTRAL: Offset get() = STAIRS_4_CENTRAL
    val STAIRS_EAST: Offset get() = STAIRS_4_EAST

    /**
     * Loads every `floor_<n>_map.json` from assets. Call once from [android.app.Application].
     * Safe to call multiple times.
     */
    fun initialize(context: Context) {
        if (initialized) return
        val assets = context.applicationContext.assets
        for (name in assets.list("").orEmpty()) {
            if (!FLOOR_MAP_ASSET_REGEX.matches(name)) continue
            val json = runCatching {
                assets.open(name).bufferedReader().use { it.readText() }
            }.getOrNull() ?: continue
            applyBaselineMap(json)
        }
        initialized = true
    }

    /** Test/preview entry point that takes map JSON directly instead of assets. */
    fun initializeForTests(floor3Json: String? = null, floor4Json: String? = null) {
        maps.clear()
        baselineMapJson.clear()
        floor3Json?.let { applyBaselineMap(it) }
        floor4Json?.let { applyBaselineMap(it) }
        initialized = true
    }

    fun isInitialized(): Boolean = initialized

    private fun applyBaselineMap(json: String) {
        val map = parseMapFromJson(json) ?: return
        if (map.rooms.isEmpty()) return
        baselineMapJson[map.floor] = json
        maps[map.floor] = map
    }

    /** Parses one whole floor map: rooms, corridors, stairs and canvas size. */
    fun parseMapFromJson(jsonString: String): FloorMapData? {
        return try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            val floor = if (root.has("floor")) root.get("floor").asInt else 4
            val rooms = parseRoomsFromJson(jsonString, floor)
            val width = if (root.has("width")) root.get("width").asFloat else defaultWidth(floor)
            val height = if (root.has("height")) root.get("height").asFloat else defaultHeight(floor)
            val corridors = if (root.has("corridors") && root.get("corridors").isJsonObject) {
                parseCorridors(root.getAsJsonObject("corridors"))
            } else null
            val stairs = if (root.has("stairs") && root.get("stairs").isJsonArray) {
                parseStairs(root.getAsJsonArray("stairs"))
            } else emptyList()

            FloorMapData(
                floor = floor,
                width = width,
                height = height,
                corridors = corridors,
                stairs = stairs,
                rooms = rooms
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseCorridors(obj: JsonObject): FloorCorridors {
        val verticalX = if (obj.has("verticalX")) obj.get("verticalX").asFloat else 0f
        val horizontalY = if (obj.has("horizontalY")) obj.get("horizontalY").asFloat else 0f
        var junctionX = verticalX
        var junctionY = horizontalY
        if (obj.has("junction") && obj.get("junction").isJsonObject) {
            val junction = obj.getAsJsonObject("junction")
            if (junction.has("x")) junctionX = junction.get("x").asFloat
            if (junction.has("y")) junctionY = junction.get("y").asFloat
        }
        return FloorCorridors(
            horizontalY = horizontalY,
            verticalX = verticalX,
            junctionX = junctionX,
            junctionY = junctionY,
            startX = if (obj.has("startX")) obj.get("startX").asFloat else null,
            startY = if (obj.has("startY")) obj.get("startY").asFloat else null
        )
    }

    private fun parseStairs(array: JsonArray): List<FloorStairs> {
        val result = mutableListOf<FloorStairs>()
        for (elem in array) {
            if (!elem.isJsonObject) continue
            val obj = elem.asJsonObject
            val id = if (obj.has("id")) obj.get("id").asString else continue
            val name = if (obj.has("name")) obj.get("name").asString else id
            val x = if (obj.has("x")) obj.get("x").asFloat else 0f
            val y = if (obj.has("y")) obj.get("y").asFloat else 0f
            result.add(FloorStairs(id = id, name = name, x = x, y = y))
        }
        return result
    }

    fun getMapData(floor: Int): FloorMapData? = maps[floor]

    fun getMapWidth(floor: Int): Float = maps[floor]?.width ?: defaultWidth(floor)

    fun getMapHeight(floor: Int): Float = maps[floor]?.height ?: defaultHeight(floor)

    fun getStairsForFloor(floor: Int): List<FloorStairs> = maps[floor]?.stairs.orEmpty()

    fun getCorridors(floor: Int): FloorCorridors? = maps[floor]?.corridors

    private fun defaultWidth(floor: Int): Float =
        if (floor == 3) FLOOR_3_DEFAULT_WIDTH else FLOOR_4_DEFAULT_WIDTH

    private fun defaultHeight(floor: Int): Float =
        if (floor == 3) FLOOR_3_DEFAULT_HEIGHT else FLOOR_4_DEFAULT_HEIGHT

    private fun stairsPoint(floor: Int, id: String, fallbackX: Float, fallbackY: Float): Offset {
        val stairs = maps[floor]?.stairs?.firstOrNull { it.id.equals(id, ignoreCase = true) }
        return if (stairs != null) Offset(stairs.x, stairs.y) else Offset(fallbackX, fallbackY)
    }

    private fun stairsPointOrFallback(floor: Int, id: String): Offset = when (floor) {
        3 -> when (id) {
            "west" -> STAIRS_3_WEST
            "east" -> STAIRS_3_EAST
            else -> STAIRS_3_CENTRAL
        }
        else -> when (id) {
            "west" -> STAIRS_4_WEST
            "east" -> STAIRS_4_EAST
            else -> STAIRS_4_CENTRAL
        }
    }

    fun parseRoomsFromJson(jsonString: String, defaultFloor: Int = 4): List<FloorRoom> {
        val root = JsonParser.parseString(jsonString).asJsonObject
        val floorNum = if (root.has("floor")) root.get("floor").asInt else defaultFloor
        val roomsArray = root.getAsJsonArray("rooms") ?: return emptyList()
        val result = mutableListOf<FloorRoom>()

        for (elem in roomsArray) {
            val obj = elem.asJsonObject
            val roomName = if (obj.has("room")) obj.get("room").asString else if (obj.has("name")) obj.get("name").asString else ""
            if (roomName.isBlank()) continue

            val x = if (obj.has("x")) obj.get("x").asFloat else 0f
            val y = if (obj.has("y")) obj.get("y").asFloat else 0f
            val w = if (obj.has("w")) obj.get("w").asFloat else if (obj.has("width")) obj.get("width").asFloat else 0f
            val h = if (obj.has("h")) obj.get("h").asFloat else if (obj.has("height")) obj.get("height").asFloat else 0f

            val wingStr = if (obj.has("wing")) obj.get("wing").asString else "BOTTOM"
            val wing = try {
                RoomWing.valueOf(wingStr)
            } catch (_: Exception) {
                when {
                    wingStr.contains("Верх", true) || wingStr.contains("Север", true) || wingStr.equals("TOP", true) -> RoomWing.TOP
                    wingStr.contains("Низ", true) || wingStr.contains("Юж", true) || wingStr.equals("BOTTOM", true) -> RoomWing.BOTTOM
                    wingStr.contains("Прав", true) || wingStr.contains("Восточ", true) || wingStr.equals("VERTICAL", true) -> RoomWing.VERTICAL
                    else -> RoomWing.LEFT
                }
            }

            val typeStr = if (obj.has("type")) obj.get("type").asString else "CLASSROOM"
            val type = try { RoomType.valueOf(typeStr) } catch (_: Exception) { RoomType.CLASSROOM }

            val stairs = if (obj.has("stairs")) obj.get("stairs").asString else if (obj.has("nearestStairs")) obj.get("nearestStairs").asString else ""

            val doorObj = if (obj.has("door") && obj.get("door").isJsonObject) obj.getAsJsonObject("door") else null
            val doorX = if (doorObj != null && doorObj.has("x")) doorObj.get("x").asFloat else (x + w / 2f)
            val doorY = if (doorObj != null && doorObj.has("y")) doorObj.get("y").asFloat else y

            val waypointsList = mutableListOf<Offset>()
            val wpArray = if (obj.has("waypoints") && obj.get("waypoints").isJsonArray) obj.getAsJsonArray("waypoints") else null
            if (wpArray != null) {
                for (wpElem in wpArray) {
                    val wpObj = wpElem.asJsonObject
                    val wx = if (wpObj.has("x")) wpObj.get("x").asFloat else 0f
                    val wy = if (wpObj.has("y")) wpObj.get("y").asFloat else 0f
                    waypointsList.add(Offset(wx, wy))
                }
            }

            val desc = if (roomName.equals("421", ignoreCase = true)) "Деканат" else "Учебная аудитория"

            result.add(
                FloorRoom(
                    room = roomName,
                    x = x,
                    y = y,
                    width = w,
                    height = h,
                    wing = wing,
                    type = type,
                    description = desc,
                    doorX = doorX,
                    doorY = doorY,
                    accessWaypoints = waypointsList,
                    nearestStairs = stairs,
                    floor = floorNum
                )
            )
        }
        return result
    }

    /** Applies a runtime map override (e.g. imported JSON); [resetToDefaults] reverts it. */
    fun loadMapFromJson(jsonString: String): Boolean {
        val map = parseMapFromJson(jsonString) ?: return false
        if (map.rooms.isEmpty()) return false
        maps[map.floor] = map
        return true
    }

    fun resetToDefaults() {
        maps.clear()
        for (json in baselineMapJson.values) {
            parseMapFromJson(json)?.let { maps[it.floor] = it }
        }
    }

    fun getRoomsForFloor(floor: Int): List<FloorRoom> {
        return maps[floor]?.rooms.orEmpty()
    }

    fun findRoom(query: String): FloorRoom? {
        val normalized = normalizeRoomQuery(query)
        if (normalized.isEmpty()) return null

        val orderedFloors = if (normalized.startsWith("3")) maps.keys.sorted() else maps.keys.sortedDescending()
        val allRooms = orderedFloors.flatMap { floor -> maps[floor]?.rooms.orEmpty() }

        allRooms.find { it.room.equals(normalized, ignoreCase = true) }?.let { return it }

        return allRooms.find {
            it.room.startsWith(normalized, ignoreCase = true) ||
            normalized.startsWith(it.room, ignoreCase = true)
        }
    }

    fun getFloorForRoom(query: String): Int? {
        findRoom(query)?.let { return it.floor }
        val normalized = normalizeRoomQuery(query)
        if (normalized.isEmpty()) return null

        val firstDigit = normalized.firstOrNull { it.isDigit() } ?: return null
        return firstDigit.digitToIntOrNull()
    }

    fun normalizeRoomQuery(query: String): String {
        return query
            .trim()
            .lowercase()
            .replace("каб.", "")
            .replace("кабинет", "")
            .replace("ауд.", "")
            .replace("аудитория", "")
            .replace("-", "")
            .replace(" ", "")
            .replace("№", "")
            .trim()
    }

    fun getCorridorY(floor: Int): Float =
        maps[floor]?.corridors?.horizontalY
            ?: if (floor == 3) FLOOR_3_CORRIDOR_Y else FLOOR_4_CORRIDOR_Y

    fun getCorridorX(floor: Int): Float =
        maps[floor]?.corridors?.verticalX
            ?: if (floor == 3) FLOOR_3_CORRIDOR_X else FLOOR_4_CORRIDOR_X

    private fun hallwayX(floor: Int): Float =
        maps[floor]?.corridors?.startX
            ?: if (floor == 3) FLOOR_3_CORRIDOR_X else FLOOR_4_HALLWAY_X

    /** Corridor entry point for the room, resolved from its own floor map geometry. */
    private fun corridorPoint(room: FloorRoom): Offset {
        room.accessWaypoints.lastOrNull()?.let { return it }
        val corridors = maps[room.floor]?.corridors
        val corrY = corridors?.horizontalY
            ?: if (room.floor == 3) FLOOR_3_CORRIDOR_Y else FLOOR_4_CORRIDOR_Y
        val corrX = corridors?.verticalX
            ?: if (room.floor == 3) FLOOR_3_CORRIDOR_X else FLOOR_4_CORRIDOR_X
        return when (room.wing) {
            RoomWing.VERTICAL -> Offset(corrX, room.doorY)
            RoomWing.LEFT -> Offset(room.doorX, corrY)
            RoomWing.TOP, RoomWing.BOTTOM -> Offset(room.doorX, corrY)
        }
    }

    private data class StairPair(val name: String, val pt4: Offset, val pt3: Offset)

    /** Pairs staircases of the two mapped floors by their `id` from map JSON. */
    private fun crossFloorStairPairs(): List<StairPair> {
        val floor3Stairs = getStairsForFloor(3)
        val pairs = getStairsForFloor(4).mapNotNull { stairs4 ->
            val stairs3 = floor3Stairs.firstOrNull {
                it.id.equals(stairs4.id, ignoreCase = true)
            } ?: return@mapNotNull null
            StairPair(stairs4.name, Offset(stairs4.x, stairs4.y), Offset(stairs3.x, stairs3.y))
        }
        if (pairs.isNotEmpty()) return pairs
        return listOf(
            StairPair("Западная лестница", STAIRS_4_WEST, STAIRS_3_WEST),
            StairPair("Центральная лестница", STAIRS_4_CENTRAL, STAIRS_3_CENTRAL),
            StairPair("Восточная лестница", STAIRS_4_EAST, STAIRS_3_EAST)
        )
    }

    fun calculatePathLength(points: List<Offset>): Float {
        var total = 0f
        for (i in 0 until points.size - 1) {
            total += kotlin.math.hypot(points[i + 1].x - points[i].x, points[i + 1].y - points[i].y)
        }
        return total
    }

    fun buildRoute(fromQuery: String?, toQuery: String): FloorRoute? {
        val toRoom = findRoom(toQuery) ?: return null
        val toFloor = toRoom.floor

        if (fromQuery.isNullOrBlank()) {
            return FloorRoute(
                fromRoom = null,
                toRoom = toRoom,
                points = emptyList(),
                description = "Кабинет ${toRoom.room}",
                isCrossFloor = false,
                fromFloor = null,
                toFloor = toFloor
            )
        }

        val fromFloor = getFloorForRoom(fromQuery) ?: toFloor
        val fromRoom = findRoom(fromQuery)

        // Same room
        if (fromRoom != null && fromRoom.room.equals(toRoom.room, ignoreCase = true)) {
            return FloorRoute(
                fromRoom = fromRoom,
                toRoom = toRoom,
                points = emptyList(),
                description = "Обе пары проходят в одной аудитории (${toRoom.room})",
                isCrossFloor = false,
                fromFloor = fromFloor,
                toFloor = toFloor
            )
        }

        // Cross-floor with both rooms identified
        if (fromRoom != null && fromRoom.floor != toRoom.floor) {
            return buildCrossFloorRoute(fromRoom, toRoom)
        }

        // Cross-floor where fromRoom is unknown/unmapped but fromFloor is different
        if (fromFloor != toFloor && fromRoom == null) {
            val stairsId = when {
                toRoom.wing == RoomWing.VERTICAL || toRoom.nearestStairs.contains("Восточ", true) -> "east"
                toRoom.wing == RoomWing.LEFT || toRoom.nearestStairs.contains("Запад", true) || toRoom.centerX < 350f -> "west"
                else -> "central"
            }
            val stairsOffset = stairsPointOrFallback(toFloor, stairsId)
            val legTo = buildPathFromStairs(stairsOffset, toRoom, toFloor)
            val action = if (toFloor > fromFloor) "Поднимитесь" else "Спуститесь"

            return FloorRoute(
                fromRoom = null,
                toRoom = toRoom,
                points = legTo,
                description = "$action с $fromFloor этажа по лестнице к каб. ${toRoom.room}",
                isCrossFloor = true,
                fromFloor = fromFloor,
                toFloor = toFloor,
                toFloorPoints = legTo,
                toStairsPoint = stairsOffset
            )
        }

        // Single-floor route (both rooms on same floor)
        if (fromRoom != null && fromRoom.floor == toRoom.floor) {
            return buildSingleFloorRoute(fromRoom, toRoom, toFloor)
        }

        return FloorRoute(
            fromRoom = null,
            toRoom = toRoom,
            points = emptyList(),
            description = "Кабинет ${toRoom.room}",
            isCrossFloor = false,
            fromFloor = fromFloor,
            toFloor = toFloor
        )
    }

    private fun buildSingleFloorRoute(fromRoom: FloorRoom, toRoom: FloorRoom, floor: Int): FloorRoute {
        val corrY = getCorridorY(floor)
        val corrX = getCorridorX(floor)
        val westX = hallwayX(floor)
        val points = mutableListOf<Offset>()
        points.add(fromRoom.doorPoint)

        val isFromWest = floor == 4 && fromRoom.wing == RoomWing.LEFT && fromRoom.doorY < 515f
        val isToWest = floor == 4 && toRoom.wing == RoomWing.LEFT && toRoom.doorY < 515f

        // Case 1: Both in west hallway (Floor 4: 433, 431, 429)
        if (isFromWest && isToWest) {
            if (kotlin.math.abs(fromRoom.doorX - westX) > 5f) {
                points.add(Offset(westX, fromRoom.doorY))
            }
            if (kotlin.math.abs(toRoom.doorX - westX) > 5f) {
                points.add(Offset(westX, toRoom.doorY))
            }
            points.add(toRoom.doorPoint)
        } else {
            // Exit fromRoom into corridor
            if (isFromWest) {
                if (kotlin.math.abs(fromRoom.doorX - westX) > 5f) {
                    points.add(Offset(westX, fromRoom.doorY))
                }
                points.add(Offset(westX, corrY))
            } else if (fromRoom.accessWaypoints.isNotEmpty()) {
                fromRoom.accessWaypoints.forEach { points.add(it) }
            } else {
                points.add(corridorPoint(fromRoom))
            }

            val fromCorrExit = points.last()

            // Destination entry point
            val toCorrEntry = if (isToWest) {
                Offset(westX, corrY)
            } else {
                corridorPoint(toRoom)
            }

            val isFromVert = fromRoom.wing == RoomWing.VERTICAL
            val isToVert = toRoom.wing == RoomWing.VERTICAL

            if (isFromVert && !isToVert) {
                points.add(Offset(corrX, corrY))
                points.add(Offset(toCorrEntry.x, corrY))
            } else if (!isFromVert && isToVert) {
                points.add(Offset(fromCorrExit.x, corrY))
                points.add(Offset(corrX, corrY))
                points.add(Offset(corrX, toCorrEntry.y))
            } else if (!isFromVert && !isToVert) {
                points.add(Offset(fromCorrExit.x, corrY))
                points.add(Offset(toCorrEntry.x, corrY))
            } else if (isFromVert && isToVert) {
                points.add(Offset(corrX, fromCorrExit.y))
                points.add(Offset(corrX, toCorrEntry.y))
            }

            if (isToWest) {
                points.add(Offset(westX, corrY))
                if (kotlin.math.abs(toRoom.doorX - westX) > 5f) {
                    points.add(Offset(westX, toRoom.doorY))
                }
            } else if (toRoom.accessWaypoints.isNotEmpty()) {
                toRoom.accessWaypoints.asReversed().forEach { points.add(it) }
            }
            points.add(toRoom.doorPoint)
        }

        val simplified = simplifyRoutePoints(points)
        val totalDistancePx = calculatePathLength(simplified)
        val meters = (totalDistancePx * 0.11f).toInt().coerceAtLeast(5)

        return FloorRoute(
            fromRoom = fromRoom,
            toRoom = toRoom,
            points = simplified,
            description = "Маршрут: каб. ${fromRoom.room} → каб. ${toRoom.room} (~$meters м)",
            isCrossFloor = false,
            fromFloor = floor,
            toFloor = floor
        )
    }

    private fun buildPathToStairs(fromRoom: FloorRoom, stairsPt: Offset, floor: Int): List<Offset> {
        val corrY = getCorridorY(floor)
        val corrX = getCorridorX(floor)
        val westX = hallwayX(floor)
        val points = mutableListOf<Offset>()
        points.add(fromRoom.doorPoint)

        val isFromWest = floor == 4 && fromRoom.wing == RoomWing.LEFT && fromRoom.doorY < 515f

        if (isFromWest) {
            if (kotlin.math.abs(fromRoom.doorX - westX) > 5f) {
                points.add(Offset(westX, fromRoom.doorY))
            }
            points.add(Offset(westX, corrY))
        } else if (fromRoom.accessWaypoints.isNotEmpty()) {
            fromRoom.accessWaypoints.forEach { points.add(it) }
        } else {
            points.add(corridorPoint(fromRoom))
        }

        if (fromRoom.wing == RoomWing.VERTICAL) {
            points.add(Offset(corrX, corrY))
        }

        // Corridor travel to stairs position along corridorY
        points.add(Offset(stairsPt.x, corrY))
        // And then directly into stairs door/marker
        points.add(stairsPt)

        return simplifyRoutePoints(points)
    }

    private fun buildPathFromStairs(stairsPt: Offset, toRoom: FloorRoom, floor: Int): List<Offset> {
        val corrY = getCorridorY(floor)
        val corrX = getCorridorX(floor)
        val westX = hallwayX(floor)
        val points = mutableListOf<Offset>()
        points.add(stairsPt)

        // Exit stairs into corridor
        points.add(Offset(stairsPt.x, corrY))

        val isToWest = floor == 4 && toRoom.wing == RoomWing.LEFT && toRoom.doorY < 515f

        if (isToWest) {
            points.add(Offset(westX, corrY))
            if (kotlin.math.abs(toRoom.doorX - westX) > 5f) {
                points.add(Offset(westX, toRoom.doorY))
            }
        } else if (toRoom.wing == RoomWing.VERTICAL) {
            points.add(Offset(corrX, corrY))
            points.add(Offset(corrX, corridorPoint(toRoom).y))
        } else {
            points.add(corridorPoint(toRoom))
        }

        if (toRoom.accessWaypoints.isNotEmpty()) {
            toRoom.accessWaypoints.asReversed().forEach { points.add(it) }
        }

        points.add(toRoom.doorPoint)
        return simplifyRoutePoints(points)
    }

    private fun buildCrossFloorRoute(fromRoom: FloorRoom, toRoom: FloorRoom): FloorRoute {
        val fromFloor = fromRoom.floor
        val toFloor = toRoom.floor

        val stairPairs = crossFloorStairPairs()
        var bestStair = stairPairs.firstOrNull { it.name.contains("Централь", ignoreCase = true) }
            ?: stairPairs.first()
        var bestTotalDist = Float.MAX_VALUE
        var bestLegFrom = emptyList<Offset>()
        var bestLegTo = emptyList<Offset>()

        for (pair in stairPairs) {
            val fromStairs = if (fromFloor == 3) pair.pt3 else pair.pt4
            val toStairs = if (toFloor == 3) pair.pt3 else pair.pt4

            val legFrom = buildPathToStairs(fromRoom, fromStairs, fromFloor)
            val legTo = buildPathFromStairs(toStairs, toRoom, toFloor)

            val totalDist = calculatePathLength(legFrom) + calculatePathLength(legTo)
            if (totalDist < bestTotalDist) {
                bestTotalDist = totalDist
                bestStair = pair
                bestLegFrom = legFrom
                bestLegTo = legTo
            }
        }

        val fromStairsPt = if (fromFloor == 3) bestStair.pt3 else bestStair.pt4
        val toStairsPt = if (toFloor == 3) bestStair.pt3 else bestStair.pt4

        val horizontalMeters = (bestTotalDist * 0.11f).toInt()
        val floorDiff = kotlin.math.abs(toFloor - fromFloor)
        val totalMeters = (horizontalMeters + floorDiff * 10).coerceAtLeast(8)
        val actionVerb = if (toFloor > fromFloor) "Поднимитесь" else "Спуститесь"

        val description = "$actionVerb с $fromFloor на $toFloor этаж по ${bestStair.name} к каб. ${toRoom.room} (~$totalMeters м)"

        return FloorRoute(
            fromRoom = fromRoom,
            toRoom = toRoom,
            points = bestLegTo, // for active single-floor rendering fallback
            description = description,
            isCrossFloor = true,
            fromFloor = fromFloor,
            toFloor = toFloor,
            fromFloorPoints = bestLegFrom,
            toFloorPoints = bestLegTo,
            transitionStairsName = bestStair.name,
            fromStairsPoint = fromStairsPt,
            toStairsPoint = toStairsPt
        )
    }

    private fun simplifyRoutePoints(raw: List<Offset>): List<Offset> {
        if (raw.size < 3) return raw
        val result = mutableListOf(raw.first())
        for (i in 1 until raw.size - 1) {
            val prev = result.last()
            val cur = raw[i]
            val next = raw[i + 1]

            val distPrevCur = kotlin.math.hypot(cur.x - prev.x, cur.y - prev.y)
            if (distPrevCur < 1f) continue

            val area = (cur.x - prev.x) * (next.y - prev.y) - (cur.y - prev.y) * (next.x - prev.x)
            if (kotlin.math.abs(area) < 1.0f) {
                continue
            }
            result.add(cur)
        }
        val distLast = kotlin.math.hypot(raw.last().x - result.last().x, raw.last().y - result.last().y)
        if (distLast >= 1f) {
            result.add(raw.last())
        }
        return result
    }
}
