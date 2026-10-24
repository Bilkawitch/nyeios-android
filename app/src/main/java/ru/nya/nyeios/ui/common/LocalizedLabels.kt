package ru.nya.nyeios.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.nya.nyeios.R
import ru.nya.nyeios.data.floormap.FloorLevel
import ru.nya.nyeios.data.floormap.RoomType
import ru.nya.nyeios.data.floormap.RoomWing
import ru.nya.nyeios.data.model.LessonType
import ru.nya.nyeios.data.model.DaySchedule

/**
 * Domain enums keep Russian titles because the data layer has no localized Context available.
 * UI resolves display titles through string resources here.
 */
@Composable
fun localizedLessonType(type: LessonType): String = stringResource(
    when (type) {
        LessonType.LECTURE -> R.string.lesson_type_lecture
        LessonType.SEMINAR -> R.string.lesson_type_seminar
        LessonType.PRACTICE -> R.string.lesson_type_practice
        LessonType.LAB -> R.string.lesson_type_lab
        LessonType.EXAM -> R.string.lesson_type_exam
        LessonType.OTHER -> R.string.lesson_type_other
    }
)

@Composable
fun localizedRoomType(type: RoomType): String = stringResource(
    when (type) {
        RoomType.CLASSROOM -> R.string.room_type_classroom
        RoomType.DEAN_OFFICE -> R.string.room_type_dean_office
        RoomType.STAIRS -> R.string.room_type_stairs
        RoomType.RESTROOM -> R.string.room_type_restroom
        RoomType.OTHER -> R.string.room_type_other
    }
)

@Composable
fun localizedRoomWing(wing: RoomWing): String = stringResource(
    when (wing) {
        RoomWing.TOP -> R.string.room_wing_top
        RoomWing.BOTTOM -> R.string.room_wing_bottom
        RoomWing.VERTICAL -> R.string.room_wing_vertical
        RoomWing.LEFT -> R.string.room_wing_left
    }
)

@Composable
fun localizedFloorLevel(level: FloorLevel): String = stringResource(R.string.floor_level_fmt, level.number)

@Composable
fun localizedDayTitle(day: DaySchedule): String {
    val full = when (day.dayName.trim().uppercase()) {
        "ПН" -> stringResource(R.string.day_full_mon)
        "ВТ" -> stringResource(R.string.day_full_tue)
        "СР" -> stringResource(R.string.day_full_wed)
        "ЧТ" -> stringResource(R.string.day_full_thu)
        "ПТ" -> stringResource(R.string.day_full_fri)
        "СБ" -> stringResource(R.string.day_full_sat)
        "ВС" -> stringResource(R.string.day_full_sun)
        else -> day.dayTitle.substringBefore(" - ").uppercase()
    }
    val date = day.dayTitle.substringAfter(" - ", "").ifBlank { day.dateString }
    return if (date.isBlank()) full else "$full - $date"
}

/**
 * Staircase names arrive from the floor map JSON in Russian. Only the three known staircases
 * are translated; anything unknown is passed through untouched.
 */
@Composable
fun localizedStairsName(name: String): String {
    val value = name.trim()
    return when {
        value.isEmpty() -> name
        value.contains("Запад", ignoreCase = true) -> stringResource(R.string.stairs_west)
        value.contains("Централь", ignoreCase = true) -> stringResource(R.string.stairs_central)
        value.contains("Восточ", ignoreCase = true) -> stringResource(R.string.stairs_east)
        else -> name
    }
}
