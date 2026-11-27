package ru.nya.nyeios.data.history

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * JSON-экспорт/импорт истории баллов (чистый Kotlin + Gson, JVM-тестируемый).
 *
 * Формат: `{"schema":1,"app":"NyEIOS","exportedAt":<ms>,"group":"...","points":[...]}`.
 */
object ScoreHistoryCodec {

    const val SCHEMA_VERSION = 1

    const val APP_NAME = "NyEIOS"

    private val gson = Gson()

    data class ExportPayload(
        val schema: Int,
        val app: String,            // "NyEIOS"
        val exportedAtMs: Long,
        val group: String,          // текущая группа на момент экспорта
        val points: List<ScoreHistoryPoint>
    )

    fun encode(payload: ExportPayload): String {
        val wire = WirePayload(
            schema = payload.schema,
            app = payload.app,
            exportedAt = payload.exportedAtMs,
            group = payload.group,
            points = payload.points.map { it.toWire() }
        )
        return gson.toJson(wire)
    }

    /** null, если JSON не наш формат/битый/неподдерживаемая схема. */
    fun decode(json: String): ExportPayload? {
        if (json.isBlank()) return null
        val wire = runCatching { gson.fromJson(json, WirePayload::class.java) }.getOrNull() ?: return null
        if (wire.schema != SCHEMA_VERSION) return null
        if (wire.app != APP_NAME) return null
        return ExportPayload(
            schema = SCHEMA_VERSION,
            app = APP_NAME,
            exportedAtMs = wire.exportedAt ?: 0L,
            group = wire.group.orEmpty(),
            points = wire.points.orEmpty().map { it.toPoint() }
        )
    }

    // --- wire format ---------------------------------------------------------

    /**
     * DTO ровно по контрактным именам полей. Все поля nullable: битые/неполные объекты
     * декодируются без исключений, а валидность точки проверяет импортёр
     * (он умеет посчитать `invalid`).
     */
    private data class WirePayload(
        @SerializedName("schema") val schema: Int?,
        @SerializedName("app") val app: String?,
        @SerializedName("exportedAt") val exportedAt: Long?,
        @SerializedName("group") val group: String?,
        @SerializedName("points") val points: List<WirePoint>?
    )

    private data class WirePoint(
        @SerializedName("recordedAt") val recordedAt: Long?,
        @SerializedName("group") val group: String?,
        @SerializedName("termNum") val termNum: Int?,
        @SerializedName("subject") val subject: String?,
        @SerializedName("controlType") val controlType: String?,
        @SerializedName("scores") val scores: Map<String, String?>?
    )

    private fun ScoreHistoryPoint.toWire(): WirePoint = WirePoint(
        recordedAt = recordedAtMs,
        group = group,
        termNum = termNum,
        subject = subject,
        controlType = controlType,
        scores = scores
    )

    private fun WirePoint.toPoint(): ScoreHistoryPoint = ScoreHistoryPoint(
        recordedAtMs = recordedAt ?: 0L,
        group = group.orEmpty(),
        termNum = termNum ?: 0,
        subject = subject.orEmpty(),
        controlType = controlType.orEmpty(),
        scores = scores.orEmpty().mapValues { it.value.orEmpty() }
    )
}
