package ru.nya.nyeios.data.floormap

import androidx.compose.ui.geometry.Offset
import com.google.gson.JsonParser

object FloorMapRepository {

    const val MAP_WIDTH_4 = 1005f
    const val MAP_HEIGHT_4 = 605f
    const val MAP_WIDTH_3 = 1024f
    const val MAP_HEIGHT_3 = 603f

    const val MAP_WIDTH = 1005f
    const val MAP_HEIGHT = 605f

    // Floor 4 stairs
    val STAIRS_4_WEST = Offset(212.8f, 461.4f)
    val STAIRS_4_CENTRAL = Offset(602.5f, 450f)
    val STAIRS_4_EAST = Offset(866.1f, 443.9f)

    // Floor 3 stairs
    val STAIRS_3_WEST = Offset(162.2f, 452f)
    val STAIRS_3_CENTRAL = Offset(606.4f, 461.6f)
    val STAIRS_3_EAST = Offset(880.9f, 454.8f)

    // Aliases for floor 4 backward compatibility
    val STAIRS_WEST = STAIRS_4_WEST
    val STAIRS_CENTRAL = STAIRS_4_CENTRAL
    val STAIRS_EAST = STAIRS_4_EAST

    private val defaultFloor4Rooms: List<FloorRoom> by lazy {
        parseRoomsFromJson(DEFAULT_FLOOR_4_JSON, 4)
    }

    private val defaultFloor3Rooms: List<FloorRoom> by lazy {
        parseRoomsFromJson(DEFAULT_FLOOR_3_JSON, 3)
    }

    private var floor4Rooms: List<FloorRoom> = defaultFloor4Rooms
    private var floor3Rooms: List<FloorRoom> = defaultFloor3Rooms

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

    fun loadMapFromJson(jsonString: String): Boolean {
        return try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            val floor = if (root.has("floor")) root.get("floor").asInt else 4
            val parsed = parseRoomsFromJson(jsonString, floor)
            if (parsed.isNotEmpty()) {
                if (floor == 3) {
                    floor3Rooms = parsed
                } else {
                    floor4Rooms = parsed
                }
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun resetToDefaults() {
        floor4Rooms = defaultFloor4Rooms
        floor3Rooms = defaultFloor3Rooms
    }

    fun getRoomsForFloor(floor: Int): List<FloorRoom> {
        return when (floor) {
            3 -> floor3Rooms
            4 -> floor4Rooms
            else -> emptyList()
        }
    }

    fun findRoom(query: String): FloorRoom? {
        val normalized = normalizeRoomQuery(query)
        if (normalized.isEmpty()) return null

        val allRooms = if (normalized.startsWith("3")) {
            floor3Rooms + floor4Rooms
        } else {
            floor4Rooms + floor3Rooms
        }

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

    fun getCorridorY(floor: Int): Float = if (floor == 3) 491f else 492f
    fun getCorridorX(floor: Int): Float = 902f

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
            val stairsOffset = if (toFloor == 3) {
                when {
                    toRoom.wing == RoomWing.VERTICAL || toRoom.nearestStairs.contains("Восточ", true) -> STAIRS_3_EAST
                    toRoom.wing == RoomWing.LEFT || toRoom.nearestStairs.contains("Запад", true) || toRoom.centerX < 350f -> STAIRS_3_WEST
                    else -> STAIRS_3_CENTRAL
                }
            } else {
                when {
                    toRoom.wing == RoomWing.VERTICAL || toRoom.nearestStairs.contains("Восточ", true) -> STAIRS_4_EAST
                    toRoom.wing == RoomWing.LEFT || toRoom.nearestStairs.contains("Запад", true) || toRoom.centerX < 350f -> STAIRS_4_WEST
                    else -> STAIRS_4_CENTRAL
                }
            }
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
        val westX = 115f
        val points = mutableListOf<Offset>()
        points.add(fromRoom.doorPoint)

        val isFromWest = floor == 4 && fromRoom.wing == RoomWing.LEFT && fromRoom.doorY < 480f
        val isToWest = floor == 4 && toRoom.wing == RoomWing.LEFT && toRoom.doorY < 480f

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
                points.add(fromRoom.getCorridorPoint())
            }

            val fromCorrExit = points.last()

            // Destination entry point
            val toCorrEntry = if (isToWest) {
                Offset(westX, corrY)
            } else {
                toRoom.getCorridorPoint()
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
        val westX = 115f
        val points = mutableListOf<Offset>()
        points.add(fromRoom.doorPoint)

        val isFromWest = floor == 4 && fromRoom.wing == RoomWing.LEFT && fromRoom.doorY < 480f

        if (isFromWest) {
            if (kotlin.math.abs(fromRoom.doorX - westX) > 5f) {
                points.add(Offset(westX, fromRoom.doorY))
            }
            points.add(Offset(westX, corrY))
        } else if (fromRoom.accessWaypoints.isNotEmpty()) {
            fromRoom.accessWaypoints.forEach { points.add(it) }
        } else {
            points.add(fromRoom.getCorridorPoint())
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
        val westX = 115f
        val points = mutableListOf<Offset>()
        points.add(stairsPt)

        // Exit stairs into corridor
        points.add(Offset(stairsPt.x, corrY))

        val isToWest = floor == 4 && toRoom.wing == RoomWing.LEFT && toRoom.doorY < 480f

        if (isToWest) {
            points.add(Offset(westX, corrY))
            if (kotlin.math.abs(toRoom.doorX - westX) > 5f) {
                points.add(Offset(westX, toRoom.doorY))
            }
        } else if (toRoom.wing == RoomWing.VERTICAL) {
            points.add(Offset(corrX, corrY))
            points.add(Offset(corrX, toRoom.getCorridorPoint().y))
        } else {
            points.add(toRoom.getCorridorPoint())
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

        data class StairPair(val name: String, val pt4: Offset, val pt3: Offset)
        val stairPairs = listOf(
            StairPair("Западная лестница", STAIRS_4_WEST, STAIRS_3_WEST),
            StairPair("Центральная лестница", STAIRS_4_CENTRAL, STAIRS_3_CENTRAL),
            StairPair("Восточная лестница", STAIRS_4_EAST, STAIRS_3_EAST)
        )

        var bestStair = stairPairs[1] // default central
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


    const val DEFAULT_FLOOR_4_JSON: String = """{
  "floor": 4,
  "width": 1005,
  "height": 605,
  "corridors": {
    "horizontalY": 492,
    "verticalX": 902,
    "junction": {
      "x": 902,
      "y": 492
    }
  },
  "stairs": [
    {
      "id": "west",
      "name": "Западная лестница",
      "x": 212.8,
      "y": 461.4
    },
    {
      "id": "central",
      "name": "Центральная лестница",
      "x": 602.5,
      "y": 450
    },
    {
      "id": "east",
      "name": "Восточная лестница",
      "x": 866.1,
      "y": 443.9
    }
  ],
  "rooms": [
    {
      "room": "401",
      "name": "401",
      "x": 820,
      "y": 276,
      "w": 62,
      "h": 34,
      "wing": "VERTICAL",
      "type": "CLASSROOM",
      "door": { "x": 882, "y": 295 },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "401а",
      "name": "401а",
      "x": 820,
      "y": 240,
      "w": 62,
      "h": 36,
      "wing": "VERTICAL",
      "type": "CLASSROOM",
      "door": { "x": 882, "y": 263 },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "402",
      "name": "402",
      "x": 820,
      "y": 83,
      "w": 102,
      "h": 156,
      "wing": "VERTICAL",
      "type": "CLASSROOM",
      "door": { "x": 882, "y": 238 },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "403",
      "name": "403",
      "x": 922,
      "y": 274,
      "w": 56,
      "h": 32,
      "wing": "VERTICAL",
      "type": "CLASSROOM",
      "door": { "x": 922, "y": 295 },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "404",
      "name": "404",
      "x": 922,
      "y": 83,
      "w": 56,
      "h": 156,
      "wing": "VERTICAL",
      "type": "CLASSROOM",
      "door": { "x": 932.8, "y": 239.3 },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "404а",
      "name": "404а",
      "x": 924,
      "y": 257,
      "w": 54,
      "h": 17,
      "wing": "VERTICAL",
      "type": "CLASSROOM",
      "door": { "x": 921.1, "y": 266 },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "405",
      "name": "405",
      "x": 922,
      "y": 306,
      "w": 56,
      "h": 35,
      "wing": "VERTICAL",
      "type": "CLASSROOM",
      "door": { "x": 922, "y": 323 },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "406",
      "name": "406",
      "x": 820,
      "y": 341,
      "w": 62,
      "h": 35,
      "wing": "VERTICAL",
      "type": "CLASSROOM",
      "door": { "x": 882, "y": 359 },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "407",
      "name": "407",
      "x": 922,
      "y": 341,
      "w": 56,
      "h": 39,
      "wing": "VERTICAL",
      "type": "CLASSROOM",
      "door": { "x": 922, "y": 360 },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "408",
      "name": "408",
      "x": 922,
      "y": 472,
      "w": 56,
      "h": 38,
      "wing": "VERTICAL",
      "type": "CLASSROOM",
      "door": { "x": 922, "y": 491 },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "409",
      "name": "409",
      "x": 922,
      "y": 381,
      "w": 56,
      "h": 58,
      "wing": "VERTICAL",
      "type": "CLASSROOM",
      "door": { "x": 922, "y": 424 },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "411",
      "name": "411",
      "x": 922,
      "y": 440,
      "w": 56,
      "h": 32,
      "wing": "VERTICAL",
      "type": "CLASSROOM",
      "door": { "x": 922, "y": 451 },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "410",
      "name": "410",
      "x": 888,
      "y": 510,
      "w": 59,
      "h": 65,
      "wing": "BOTTOM",
      "type": "CLASSROOM",
      "door": { "x": 899.5, "y": 509 },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "412",
      "name": "412",
      "x": 825,
      "y": 510,
      "w": 63,
      "h": 65,
      "wing": "BOTTOM",
      "type": "CLASSROOM",
      "door": { "x": 864.5, "y": 508 },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "414",
      "name": "414",
      "x": 785,
      "y": 510,
      "w": 40,
      "h": 65,
      "wing": "BOTTOM",
      "type": "CLASSROOM",
      "door": { "x": 804.5, "y": 508 },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "416",
      "name": "416",
      "x": 722,
      "y": 510,
      "w": 63,
      "h": 65,
      "wing": "BOTTOM",
      "type": "CLASSROOM",
      "door": { "x": 763, "y": 508 },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "418",
      "name": "418",
      "x": 687,
      "y": 510,
      "w": 35,
      "h": 65,
      "wing": "BOTTOM",
      "type": "CLASSROOM",
      "door": { "x": 705.5, "y": 508 },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "420",
      "name": "420",
      "x": 653,
      "y": 510,
      "w": 34,
      "h": 65,
      "wing": "BOTTOM",
      "type": "CLASSROOM",
      "door": { "x": 672.5, "y": 508 },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "422",
      "name": "422",
      "x": 579,
      "y": 528,
      "w": 74,
      "h": 47,
      "wing": "BOTTOM",
      "type": "CLASSROOM",
      "door": { "x": 631.5, "y": 528 },
      "waypoints": [],
      "stairs": "Центральная лестница"
    },
    {
      "room": "424",
      "name": "424",
      "x": 534,
      "y": 528,
      "w": 45,
      "h": 47,
      "wing": "BOTTOM",
      "type": "CLASSROOM",
      "door": { "x": 547.5, "y": 528 },
      "waypoints": [],
      "stairs": "Центральная лестница"
    },
    {
      "room": "426",
      "name": "426",
      "x": 497,
      "y": 528,
      "w": 37,
      "h": 47,
      "wing": "BOTTOM",
      "type": "CLASSROOM",
      "door": { "x": 508.5, "y": 528 },
      "waypoints": [],
      "stairs": "Центральная лестница"
    },
    {
      "room": "428",
      "name": "428",
      "x": 450,
      "y": 528,
      "w": 47,
      "h": 47,
      "wing": "BOTTOM",
      "type": "CLASSROOM",
      "door": { "x": 466, "y": 528 },
      "waypoints": [],
      "stairs": "Центральная лестница"
    },
    {
      "room": "430",
      "name": "430",
      "x": 372,
      "y": 528,
      "w": 78,
      "h": 47,
      "wing": "BOTTOM",
      "type": "CLASSROOM",
      "door": { "x": 392, "y": 528 },
      "waypoints": [],
      "stairs": "Центральная лестница"
    },
    {
      "room": "432",
      "name": "432",
      "x": 267,
      "y": 503,
      "w": 105,
      "h": 72,
      "wing": "BOTTOM",
      "type": "CLASSROOM",
      "door": { "x": 298, "y": 503 },
      "waypoints": [],
      "stairs": "Западная лестница"
    },
    {
      "room": "434",
      "name": "434",
      "x": 227,
      "y": 503,
      "w": 40,
      "h": 72,
      "wing": "BOTTOM",
      "type": "CLASSROOM",
      "door": { "x": 249, "y": 503 },
      "waypoints": [],
      "stairs": "Западная лестница"
    },
    {
      "room": "436",
      "name": "436",
      "x": 191,
      "y": 503,
      "w": 36,
      "h": 72,
      "wing": "BOTTOM",
      "type": "CLASSROOM",
      "door": { "x": 211, "y": 503 },
      "waypoints": [],
      "stairs": "Западная лестница"
    },
    {
      "room": "438",
      "name": "438",
      "x": 157,
      "y": 503,
      "w": 34,
      "h": 72,
      "wing": "BOTTOM",
      "type": "CLASSROOM",
      "door": { "x": 170, "y": 503 },
      "waypoints": [],
      "stairs": "Западная лестница"
    },
    {
      "room": "440",
      "name": "440",
      "x": 123,
      "y": 503,
      "w": 34,
      "h": 72,
      "wing": "BOTTOM",
      "type": "CLASSROOM",
      "door": { "x": 142, "y": 503 },
      "waypoints": [],
      "stairs": "Западная лестница"
    },
    {
      "room": "413",
      "name": "413",
      "x": 688,
      "y": 417,
      "w": 154,
      "h": 57,
      "wing": "TOP",
      "type": "CLASSROOM",
      "door": { "x": 715.7, "y": 475 },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "415",
      "name": "415",
      "x": 653,
      "y": 415,
      "w": 35,
      "h": 57,
      "wing": "TOP",
      "type": "CLASSROOM",
      "door": { "x": 672.6, "y": 474.3 },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "417а",
      "name": "417а",
      "x": 548,
      "y": 407,
      "w": 40,
      "h": 44,
      "wing": "TOP",
      "type": "CLASSROOM",
      "door": { "x": 571.8, "y": 451.9 },
      "waypoints": [],
      "stairs": "Центральная лестница"
    },
    {
      "room": "417",
      "name": "417",
      "x": 511,
      "y": 407,
      "w": 37,
      "h": 45,
      "wing": "TOP",
      "type": "CLASSROOM",
      "door": { "x": 531, "y": 451 },
      "waypoints": [],
      "stairs": "Центральная лестница"
    },
    {
      "room": "419",
      "name": "419",
      "x": 470,
      "y": 407,
      "w": 41,
      "h": 43,
      "wing": "TOP",
      "type": "CLASSROOM",
      "door": { "x": 485, "y": 450 },
      "waypoints": [],
      "stairs": "Центральная лестница"
    },
    {
      "room": "421",
      "name": "421",
      "x": 374,
      "y": 407,
      "w": 96,
      "h": 44,
      "wing": "TOP",
      "type": "DEAN_OFFICE",
      "door": { "x": 456, "y": 450 },
      "waypoints": [],
      "stairs": "Центральная лестница"
    },
    {
      "room": "423",
      "name": "423",
      "x": 342,
      "y": 407,
      "w": 31,
      "h": 57,
      "wing": "TOP",
      "type": "CLASSROOM",
      "door": { "x": 361, "y": 463 },
      "waypoints": [],
      "stairs": "Западная лестница"
    },
    {
      "room": "425",
      "name": "425",
      "x": 261,
      "y": 407,
      "w": 81,
      "h": 57,
      "wing": "TOP",
      "type": "CLASSROOM",
      "door": { "x": 298, "y": 463 },
      "waypoints": [],
      "stairs": "Западная лестница"
    },
    {
      "room": "427",
      "name": "427",
      "x": 233,
      "y": 407,
      "w": 28,
      "h": 57,
      "wing": "TOP",
      "type": "CLASSROOM",
      "door": { "x": 243, "y": 463 },
      "waypoints": [],
      "stairs": "Западная лестница"
    },
    {
      "room": "429",
      "name": "429",
      "x": 16,
      "y": 378,
      "w": 75,
      "h": 85,
      "wing": "LEFT",
      "type": "CLASSROOM",
      "door": { "x": 92, "y": 452 },
      "waypoints": [
        { "x": 115, "y": 452 },
        { "x": 115, "y": 492 }
      ],
      "stairs": "Западная лестница"
    },
    {
      "room": "431",
      "name": "431",
      "x": 92,
      "y": 346,
      "w": 44,
      "h": 91,
      "wing": "LEFT",
      "type": "CLASSROOM",
      "door": { "x": 115, "y": 436 },
      "waypoints": [
        { "x": 115, "y": 492 },
        { "x": 142, "y": 492 }
      ],
      "stairs": "Западная лестница"
    },
    {
      "room": "433",
      "name": "433",
      "x": 16,
      "y": 342,
      "w": 75,
      "h": 36,
      "wing": "LEFT",
      "type": "CLASSROOM",
      "door": { "x": 92, "y": 362 },
      "waypoints": [
        { "x": 115, "y": 362 },
        { "x": 115, "y": 436 },
        { "x": 115, "y": 492 },
        { "x": 142, "y": 492 }
      ],
      "stairs": "Западная лестница"
    },
    {
      "room": "442",
      "name": "442",
      "x": 16,
      "y": 464,
      "w": 75,
      "h": 39,
      "wing": "LEFT",
      "type": "CLASSROOM",
      "door": { "x": 92, "y": 492 },
      "waypoints": [
        { "x": 142, "y": 492 }
      ],
      "stairs": "Западная лестница"
    },
    {
      "room": "444",
      "name": "444",
      "x": 12,
      "y": 503,
      "w": 111,
      "h": 72,
      "wing": "LEFT",
      "type": "CLASSROOM",
      "door": { "x": 32, "y": 503 },
      "waypoints": [
        { "x": 32, "y": 492 },
        { "x": 92, "y": 492 },
        { "x": 142, "y": 492 }
      ],
      "stairs": "Западная лестница"
    }
  ]
}"""

    const val DEFAULT_FLOOR_3_JSON: String = """{
  "floor": 3,
  "width": 1024,
  "height": 603,
  "corridors": {
    "horizontalY": 491,
    "verticalX": 902,
    "junction": {
      "x": 902,
      "y": 491
    }
  },
  "stairs": [
    {
      "id": "west",
      "name": "Западная лестница",
      "x": 162.2,
      "y": 452
    },
    {
      "id": "central",
      "name": "Центральная лестница",
      "x": 606.4,
      "y": 461.6
    },
    {
      "id": "east",
      "name": "Восточная лестница",
      "x": 880.9,
      "y": 454.8
    }
  ],
  "rooms": [
    {
      "room": "301",
      "name": "301",
      "x": 835,
      "y": 275,
      "w": 66,
      "h": 34,
      "wing": "VERTICAL",
      "type": "CLASSROOM",
      "door": {
        "x": 901.4,
        "y": 293.1
      },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "302",
      "name": "302",
      "x": 835,
      "y": 221,
      "w": 67,
      "h": 52,
      "wing": "VERTICAL",
      "type": "CLASSROOM",
      "door": {
        "x": 902.2,
        "y": 260
      },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "303",
      "name": "303",
      "x": 835,
      "y": 172,
      "w": 67,
      "h": 46,
      "wing": "VERTICAL",
      "type": "CLASSROOM",
      "door": {
        "x": 902,
        "y": 191
      },
      "waypoints": [],
      "stairs": "Восточная лестница"
    },
    {
      "room": "304",
      "name": "304",
      "x": 835,
      "y": 137,
      "w": 66,
      "h": 34,
      "wing": "VERTICAL",
      "type": "CLASSROOM",
      "door": {
        "x": 902,
        "y": 150.3
      },
      "waypoints": [],
      "stairs": "Восточная лестница"
    }
  ]
}"""
}

