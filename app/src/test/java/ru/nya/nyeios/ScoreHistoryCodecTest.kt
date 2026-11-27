package ru.nya.nyeios

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.history.ScoreHistoryCodec
import ru.nya.nyeios.data.history.ScoreHistoryPoint

/**
 * JVM-проверки JSON-кодека истории баллов: round-trip, битый JSON, чужая схема,
 * пустые points и юникод в названиях предметов/групп.
 */
class ScoreHistoryCodecTest {

    private val gson = Gson()

    private fun point(
        recordedAtMs: Long = 1_700_000_000_000L,
        group: String = "23АН-о-41",
        termNum: Int = 7,
        subject: String = "Математика",
        controlType: String = "Экзамен",
        scores: Map<String, String> = mapOf(
            "currentScore" to "40",
            "finalRating" to "85",
            "grade" to "5"
        )
    ) = ScoreHistoryPoint(
        recordedAtMs = recordedAtMs,
        group = group,
        termNum = termNum,
        subject = subject,
        controlType = controlType,
        scores = scores
    )

    private fun payload(
        group: String = "23АН-о-41",
        points: List<ScoreHistoryPoint> = listOf(point())
    ) = ScoreHistoryCodec.ExportPayload(
        schema = ScoreHistoryCodec.SCHEMA_VERSION,
        app = ScoreHistoryCodec.APP_NAME,
        exportedAtMs = 1_800_000_000_000L,
        group = group,
        points = points
    )

    // --- round-trip ----------------------------------------------------------

    @Test
    fun roundTripPreservesPayload() {
        val original = payload()
        val decoded = ScoreHistoryCodec.decode(ScoreHistoryCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun roundTripPreservesUnicode() {
        val original = payload(
            group = "23АН-о-41",
            points = listOf(
                point(
                    subject = "Информационные технологии и системы",
                    controlType = "Диф. зачёт",
                    scores = mapOf("finalRating" to "отлично", "currentRating" to "60")
                )
            )
        )

        val decoded = ScoreHistoryCodec.decode(ScoreHistoryCodec.encode(original))

        assertEquals(original, decoded)
    }

    @Test
    fun emptyPointsRoundTrip() {
        val original = payload(points = emptyList())
        val decoded = ScoreHistoryCodec.decode(ScoreHistoryCodec.encode(original))
        assertNotNull(decoded)
        assertEquals(emptyList<ScoreHistoryPoint>(), decoded!!.points)
        assertEquals(original, decoded)
    }

    @Test
    fun encodedJsonContainsContractFieldNames() {
        val json = ScoreHistoryCodec.encode(payload())
        assertTrue(json.contains("\"schema\""))
        assertTrue(json.contains("\"app\""))
        assertTrue(json.contains("\"exportedAt\""))
        assertTrue(json.contains("\"group\""))
        assertTrue(json.contains("\"points\""))
        assertTrue(json.contains("\"recordedAt\""))
        assertTrue(json.contains("\"termNum\""))
        assertTrue(json.contains("\"subject\""))
        assertTrue(json.contains("\"controlType\""))
        assertTrue(json.contains("\"scores\""))
    }

    // --- decode failures -----------------------------------------------------

    @Test
    fun decodeReturnsNullForBrokenJson() {
        assertNull(ScoreHistoryCodec.decode(""))
        assertNull(ScoreHistoryCodec.decode("not a json"))
        assertNull(ScoreHistoryCodec.decode("{"))
        assertNull(ScoreHistoryCodec.decode("null"))
        assertNull(ScoreHistoryCodec.decode("[]"))
    }

    @Test
    fun decodeReturnsNullForUnsupportedSchema() {
        val json = gson.toJson(
            mapOf(
                "schema" to (ScoreHistoryCodec.SCHEMA_VERSION + 1),
                "app" to ScoreHistoryCodec.APP_NAME,
                "exportedAt" to 1L,
                "group" to "",
                "points" to emptyList<Any>()
            )
        )
        assertNull(ScoreHistoryCodec.decode(json))
    }

    @Test
    fun decodeReturnsNullWhenSchemaMissing() {
        val json = gson.toJson(
            mapOf(
                "app" to ScoreHistoryCodec.APP_NAME,
                "exportedAt" to 1L,
                "group" to "",
                "points" to emptyList<Any>()
            )
        )
        assertNull(ScoreHistoryCodec.decode(json))
    }

    @Test
    fun decodeReturnsNullForForeignApp() {
        val json = gson.toJson(
            mapOf(
                "schema" to ScoreHistoryCodec.SCHEMA_VERSION,
                "app" to "SomeOtherApp",
                "exportedAt" to 1L,
                "group" to "",
                "points" to emptyList<Any>()
            )
        )
        assertNull(ScoreHistoryCodec.decode(json))
    }

    @Test
    fun decodeToleratesMissingPointsAsEmptyList() {
        val json = gson.toJson(
            mapOf(
                "schema" to ScoreHistoryCodec.SCHEMA_VERSION,
                "app" to ScoreHistoryCodec.APP_NAME,
                "exportedAt" to 5L,
                "group" to "23АН-о-41"
            )
        )
        val decoded = ScoreHistoryCodec.decode(json)
        assertNotNull(decoded)
        assertEquals(emptyList<ScoreHistoryPoint>(), decoded!!.points)
        assertEquals("23АН-о-41", decoded.group)
        assertEquals(5L, decoded.exportedAtMs)
    }

    @Test
    fun decodeKeepsPointFieldsAsIs() {
        val json = """
            {"schema":1,"app":"NyEIOS","exportedAt":42,"group":"G",
             "points":[{"recordedAt":123,"group":"","termNum":3,"subject":"Физика",
             "controlType":"Зачёт","scores":{"currentScore":"12,5"}}]}
        """.trimIndent()

        val decoded = ScoreHistoryCodec.decode(json)!!

        assertEquals(1, decoded.points.size)
        val p = decoded.points.single()
        assertEquals(123L, p.recordedAtMs)
        assertEquals("", p.group)
        assertEquals(3, p.termNum)
        assertEquals("Физика", p.subject)
        assertEquals("Зачёт", p.controlType)
        assertEquals(mapOf("currentScore" to "12,5"), p.scores)
    }
}
