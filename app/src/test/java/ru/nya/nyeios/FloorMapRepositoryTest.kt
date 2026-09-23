package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.floormap.FloorMapRepository
import ru.nya.nyeios.data.floormap.RoomType

class FloorMapRepositoryTest {

    @Test
    fun `floor 4 has exactly 42 rooms mapped`() {
        val rooms = FloorMapRepository.getRoomsForFloor(4)
        assertEquals(42, rooms.size)
    }

    @Test
    fun `all rooms have coordinates within map bounds`() {
        val rooms = FloorMapRepository.getRoomsForFloor(4)
        for (room in rooms) {
            assertTrue("Room ${room.room} x out of bounds: ${room.x}", room.x >= 0f && room.x < FloorMapRepository.MAP_WIDTH)
            assertTrue("Room ${room.room} y out of bounds: ${room.y}", room.y >= 0f && room.y < FloorMapRepository.MAP_HEIGHT)
            assertTrue("Room ${room.room} width <= 0: ${room.width}", room.width > 0f)
            assertTrue("Room ${room.room} height <= 0: ${room.height}", room.height > 0f)
            assertTrue("Room ${room.room} right edge exceeds map: ${room.x + room.width}", room.x + room.width <= FloorMapRepository.MAP_WIDTH)
            assertTrue("Room ${room.room} bottom edge exceeds map: ${room.y + room.height}", room.y + room.height <= FloorMapRepository.MAP_HEIGHT)
        }
    }

    @Test
    fun `room 421 is dean office`() {
        val room421 = FloorMapRepository.findRoom("421")
        assertNotNull(room421)
        assertEquals(RoomType.DEAN_OFFICE, room421?.type)
        assertEquals(374.0f, room421?.x)
        assertEquals(407.0f, room421?.y)
        assertEquals(96.0f, room421?.width)
        assertEquals(44.0f, room421?.height)
    }

    @Test
    fun `query normalization handles messy input strings`() {
        val room417a = FloorMapRepository.findRoom("ауд. 417а")
        assertNotNull(room417a)
        assertEquals("417а", room417a?.room)

        val room401a = FloorMapRepository.findRoom("каб. 401-А")
        assertNotNull(room401a)
        assertEquals("401а", room401a?.room)

        val room444 = FloorMapRepository.findRoom(" 444 ")
        assertNotNull(room444)
        assertEquals("444", room444?.room)
    }

    @Test
    fun `floor detection identifies correct floor numbers`() {
        assertEquals(4, FloorMapRepository.getFloorForRoom("421"))
        assertEquals(4, FloorMapRepository.getFloorForRoom("ауд. 417а"))
        assertEquals(3, FloorMapRepository.getFloorForRoom("305"))
        assertEquals(3, FloorMapRepository.getFloorForRoom("каб. 312"))
        assertEquals(2, FloorMapRepository.getFloorForRoom("220"))
        assertNull(FloorMapRepository.getFloorForRoom(""))
    }

    @Test
    fun `buildRoute generates valid corridor waypoints between horizontal rooms`() {
        val route = FloorMapRepository.buildRoute("412", "421")
        assertNotNull(route)
        assertEquals("412", route?.fromRoom?.room)
        assertEquals("421", route?.toRoom?.room)
        assertTrue(route!!.points.size >= 4)
        // All corridor points between horizontal rooms should be on y = 492
        val corridorPoints = route.points.subList(1, route.points.size - 1)
        for (pt in corridorPoints) {
            assertEquals(492f, pt.y, 1f)
        }
    }

    @Test
    fun `buildRoute passes through corner junction when moving to vertical wing`() {
        val route = FloorMapRepository.buildRoute("416", "407")
        assertNotNull(route)
        assertEquals("416", route?.fromRoom?.room)
        assertEquals("407", route?.toRoom?.room)
        // Should contain corner junction (902, 492)
        val hasJunction = route!!.points.any { it.x == 902f && it.y == 492f }
        assertTrue("Route should pass through junction (902, 492)", hasJunction)
    }

    @Test
    fun `buildRoute handles same room gracefully`() {
        val route = FloorMapRepository.buildRoute("421", "421")
        assertNotNull(route)
        assertEquals(0, route?.points?.size)
        assertTrue(route!!.description.contains("Обе пары"))
    }

    @Test
    fun `buildRoute handles cross floor navigation from floor 3`() {
        val route = FloorMapRepository.buildRoute("305", "421")
        assertNotNull(route)
        assertTrue(route!!.isCrossFloor)
        assertEquals(3, route.fromFloor)
        assertEquals(4, route.toFloor)
        assertTrue(route.points.isNotEmpty())
    }

    @Test
    fun `all rooms have valid door coordinates within room or corridor proximity`() {
        val rooms = FloorMapRepository.getRoomsForFloor(4)
        for (room in rooms) {
            assertTrue("Room ${room.room} doorX out of bounds: ${room.doorX}", room.doorX >= 0f && room.doorX <= FloorMapRepository.MAP_WIDTH)
            assertTrue("Room ${room.room} doorY out of bounds: ${room.doorY}", room.doorY >= 0f && room.doorY <= FloorMapRepository.MAP_HEIGHT)
            assertEquals(room.doorX, room.doorPoint.x, 0.01f)
            assertEquals(room.doorY, room.doorPoint.y, 0.01f)
        }
    }

    @Test
    fun `nested room 444 routes through room 442 door to destination 436`() {
        val route = FloorMapRepository.buildRoute("444", "436")
        assertNotNull(route)
        assertEquals("444", route?.fromRoom?.room)
        assertEquals("436", route?.toRoom?.room)
        assertTrue(route!!.points.size >= 4)

        // Starts at 444 door (32, 503)
        assertEquals(32f, route.points.first().x, 1f)
        assertEquals(503f, route.points.first().y, 1f)

        // Finishes at 436 door (211, 503)
        assertEquals(211f, route.points.last().x, 1f)
        assertEquals(503f, route.points.last().y, 1f)

        // Enters main corridor at (32, 492) and travels along y = 492
        val corridorPoints = route.points.filter { kotlin.math.abs(it.y - 492f) < 1f }
        assertTrue("Route must travel along corridor y = 492", corridorPoints.size >= 2)
    }

    @Test
    fun `nested room 433 routes through room 431 door to destination 436`() {
        val route = FloorMapRepository.buildRoute("433", "436")
        assertNotNull(route)
        assertEquals("433", route?.fromRoom?.room)
        assertEquals("436", route?.toRoom?.room)

        // Starts at 433 door (92, 362)
        assertEquals(92f, route!!.points.first().x, 1f)
        assertEquals(362f, route.points.first().y, 1f)

        // Finishes at 436 door (211, 503)
        assertEquals(211f, route.points.last().x, 1f)
        assertEquals(503f, route.points.last().y, 1f)

        // Passes through hallway waypoint (115, 362)
        val passesHallway = route.points.any { kotlin.math.abs(it.x - 115f) < 1f && kotlin.math.abs(it.y - 362f) < 1f }
        assertTrue("Route from 433 must pass through 431 hallway corner at (115, 362)", passesHallway)
    }

    @Test
    fun `navigation between west wing rooms 433 and 429 stays inside hallway without looping down to main corridor`() {
        val route = FloorMapRepository.buildRoute("433", "429")
        assertNotNull(route)
        assertEquals("433", route?.fromRoom?.room)
        assertEquals("429", route?.toRoom?.room)

        // Starts at 433 door (92, 362)
        assertEquals(92f, route!!.points.first().x, 1f)
        assertEquals(362f, route.points.first().y, 1f)

        // Finishes at 429 door (92, 452)
        assertEquals(92f, route.points.last().x, 1f)
        assertEquals(452f, route.points.last().y, 1f)

        // All points must stay above y = 480 (never drop down to y = 492)
        for (pt in route.points) {
            assertTrue("Point $pt should not loop down to main corridor", pt.y < 480f)
        }

        // Must pass through west hallway x = 115
        val hallwayPoints = route.points.filter { kotlin.math.abs(it.x - 115f) < 1f }
        assertTrue("Must travel along west hallway x = 115", hallwayPoints.size >= 2)
    }

    @Test
    fun `room 438 routes from door at 170 503 into corridor`() {
        val route = FloorMapRepository.buildRoute("438", "421")
        assertNotNull(route)
        assertEquals(170f, route!!.points.first().x, 1f)
        assertEquals(503f, route.points.first().y, 1f)
    }

    @Test
    fun `loadMapFromJson imports custom room and door coordinates`() {
        val testJson = """
            {
              "floor": 4,
              "rooms": [
                {
                  "room": "499_TEST",
                  "x": 100.0,
                  "y": 200.0,
                  "w": 50.0,
                  "h": 60.0,
                  "wing": "BOTTOM",
                  "type": "CLASSROOM",
                  "door": { "x": 125.0, "y": 200.0 }
                }
              ]
            }
        """.trimIndent()

        val success = FloorMapRepository.loadMapFromJson(testJson)
        assertTrue(success)

        val room = FloorMapRepository.findRoom("499_TEST")
        assertNotNull(room)
        assertEquals(125f, room!!.doorX, 0.1f)
        assertEquals(200f, room.doorY, 0.1f)

        // Reset back to defaults for subsequent tests
        FloorMapRepository.resetToDefaults()
        assertEquals(42, FloorMapRepository.getRoomsForFloor(4).size)
    }

    @Test
    fun `floor 3 loads initial rooms with correct coordinates and doors`() {
        val rooms3 = FloorMapRepository.getRoomsForFloor(3)
        assertEquals(4, rooms3.size)

        val room301 = FloorMapRepository.findRoom("301")
        assertNotNull(room301)
        assertEquals(3, room301!!.floor)
        assertEquals(835f, room301.x, 0.1f)
        assertEquals(275f, room301.y, 0.1f)
        assertEquals(901.4f, room301.doorX, 0.1f)
        assertEquals(293.1f, room301.doorY, 0.1f)
    }

    @Test
    fun `navigation within floor 3 connects vertical wing rooms directly`() {
        val route = FloorMapRepository.buildRoute("301", "304")
        assertNotNull(route)
        assertEquals(3, route!!.fromFloor)
        assertEquals(3, route.toFloor)
        assertFalse(route.isCrossFloor)
        assertTrue(route.points.size >= 2)
        assertEquals(901.4f, route.points.first().x, 0.1f)
        assertEquals(902f, route.points.last().x, 0.1f)
    }

    @Test
    fun `cross-floor route from 421 on floor 4 to 301 on floor 3 chooses central stairs`() {
        val route = FloorMapRepository.buildRoute("421", "301")
        assertNotNull(route)
        assertTrue(route!!.isCrossFloor)
        assertEquals(4, route.fromFloor)
        assertEquals(3, route.toFloor)
        assertEquals("Центральная лестница", route.transitionStairsName)
        assertTrue(route.fromFloorPoints.isNotEmpty())
        assertTrue(route.toFloorPoints.isNotEmpty())

        // Floor 4 leg starts at 421 door (456, 450) and ends at Central stairs (602.5, 450)
        assertEquals(456f, route.fromFloorPoints.first().x, 1f)
        assertEquals(450f, route.fromFloorPoints.first().y, 1f)
        assertEquals(FloorMapRepository.STAIRS_4_CENTRAL.x, route.fromFloorPoints.last().x, 1f)
        assertEquals(FloorMapRepository.STAIRS_4_CENTRAL.y, route.fromFloorPoints.last().y, 1f)

        // Floor 3 leg starts at Central stairs (606.4, 461.6) and ends at 301 door (901.4, 293.1)
        assertEquals(FloorMapRepository.STAIRS_3_CENTRAL.x, route.toFloorPoints.first().x, 1f)
        assertEquals(FloorMapRepository.STAIRS_3_CENTRAL.y, route.toFloorPoints.first().y, 1f)
        assertEquals(901.4f, route.toFloorPoints.last().x, 1f)
        assertEquals(293.1f, route.toFloorPoints.last().y, 1f)
    }

    @Test
    fun `cross-floor route from 301 on floor 3 to 421 on floor 4 ascends correctly`() {
        val route = FloorMapRepository.buildRoute("301", "421")
        assertNotNull(route)
        assertTrue(route!!.isCrossFloor)
        assertEquals(3, route.fromFloor)
        assertEquals(4, route.toFloor)
        assertEquals("Центральная лестница", route.transitionStairsName)
        assertTrue(route.description.contains("Поднимитесь"))
    }

    @Test
    fun `cross-floor route between east wing rooms selects east stairs`() {
        val route = FloorMapRepository.buildRoute("401", "301")
        assertNotNull(route)
        assertTrue(route!!.isCrossFloor)
        assertEquals("Восточная лестница", route.transitionStairsName)
    }
}

