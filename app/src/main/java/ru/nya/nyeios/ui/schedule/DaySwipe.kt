package ru.nya.nyeios.ui.schedule

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import kotlin.math.sign

/** Направление горизонтального свайпа по расписанию. */
enum class DaySwipeDirection {
    /** Палец влево либо флик влево: следующий день недели. */
    NEXT,

    /** Палец вправо либо флик вправо: предыдущий день недели. */
    PREVIOUS
}

/**
 * Чистая логика переключения дня свайпом. Вынесена из Compose, чтобы её можно
 * было проверить JVM-тестами без эмулятора.
 *
 * День переключается, если суммарное горизонтальное смещение превысило
 * [DISTANCE_THRESHOLD_DP] ИЛИ жест был достаточно быстрым ([VELOCITY_THRESHOLD_DP_PER_SECOND])
 * и его направление совпадает со смещением. Смещение имеет приоритет: если оно
 * за порогом, день переключается даже при отпускании против движения.
 *
 * Переключение затрагивает только реально показанные в ряду дни: воскресенье
 * заменено ячейкой напоминаний, поэтому в [targetIndex] передаётся список
 * индексов выбираемых дней из [DayRowCells].
 */
object DaySwipeNavigation {

    /**
     * Порог суммарного смещения, после которого свайп считается осознанным.
     *
     * Детектор Compose не учитывает touch slop (~8 dp), поэтому замеренное
     * смещение на столько же меньше хода пальца: 48 dp здесь — это примерно
     * 56 dp реального хода, как у стандартного свайпа Material.
     */
    const val DISTANCE_THRESHOLD_DP = 48f

    /** Порог скорости для быстрого короткого флика (dp в секунду). */
    const val VELOCITY_THRESHOLD_DP_PER_SECOND = 400f

    /**
     * Определяет направление свайпа по суммарному смещению (px) и скорости (px/s).
     *
     * @param totalDragXPx суммарное смещение пальца по горизонтали; отрицательное — палец влево.
     * @param velocityXPxPerSecond скорость в момент отпускания; отрицательная — влево.
     * @param density плотность экрана (px на dp).
     * @return направление переключения либо `null`, если жест не прошёл порог.
     */
    fun directionOf(
        totalDragXPx: Float,
        velocityXPxPerSecond: Float,
        density: Float
    ): DaySwipeDirection? {
        if (!density.isFinite() || density <= 0f) return null
        if (!totalDragXPx.isFinite() || !velocityXPxPerSecond.isFinite()) return null

        val distanceDp = totalDragXPx / density
        val velocityDpPerSecond = velocityXPxPerSecond / density

        val committedByDistance = abs(distanceDp) >= DISTANCE_THRESHOLD_DP
        val committedByVelocity = !committedByDistance &&
            abs(velocityDpPerSecond) >= VELOCITY_THRESHOLD_DP_PER_SECOND &&
            distanceDp != 0f &&
            sign(distanceDp) == sign(velocityDpPerSecond)

        if (!committedByDistance && !committedByVelocity) return null
        return if (distanceDp < 0f) DaySwipeDirection.NEXT else DaySwipeDirection.PREVIOUS
    }

    /**
     * Возвращает исходный индекс соседнего выбираемого дня либо `null`, если
     * свайп упирается в край недели или текущий день не выбираем.
     *
     * @param selectableIndexes исходные индексы дней, показанных в ряду, по порядку.
     * @param currentIndex исходный индекс выбранного дня.
     */
    fun targetIndex(
        selectableIndexes: List<Int>,
        currentIndex: Int,
        direction: DaySwipeDirection
    ): Int? {
        if (selectableIndexes.isEmpty()) return null
        val position = selectableIndexes.indexOf(currentIndex)
        if (position >= 0) {
            val targetPosition = position + when (direction) {
                DaySwipeDirection.NEXT -> 1
                DaySwipeDirection.PREVIOUS -> -1
            }
            return selectableIndexes.getOrNull(targetPosition)
        }
        // Выбранный день не показан в ряду (например, воскресенье заменено ячейкой
        // напоминаний): свайп ведёт на ближайший показанный день в нужном направлении.
        return when (direction) {
            DaySwipeDirection.NEXT -> selectableIndexes.filter { it > currentIndex }.minOrNull()
            DaySwipeDirection.PREVIOUS -> selectableIndexes.filter { it < currentIndex }.maxOrNull()
        }
    }
}

/**
 * Горизонтальный свайп по области расписания переключает выбранный день недели.
 *
 * Жест распознаётся через горизонтальный touch slop, поэтому вертикальная
 * прокрутка списка пар и клики по карточкам не затрагиваются. Вызывать только
 * на контейнере, который показывает дневное содержимое расписания: показ шторки
 * или состояния загрузки заменяет этот контейнер, и жест исчезает вместе с ним.
 *
 * @param selectableIndexes исходные индексы выбираемых дней (см. [DaySelectorRow]).
 * @param currentIndex текущий выбранный день — ключ перезапуска детектора.
 * @param onSelectDay вызывается с исходным индексом целевого дня.
 */
fun Modifier.daySwipeGesture(
    selectableIndexes: List<Int>,
    currentIndex: Int,
    onSelectDay: (Int) -> Unit
): Modifier {
    if (selectableIndexes.size <= 1) return this
    return this.pointerInput(selectableIndexes, currentIndex) {
        val swipeDensity = density
        val velocityTracker = VelocityTracker()
        var totalDragX = 0f

        detectHorizontalDragGestures(
            onDragStart = {
                totalDragX = 0f
                velocityTracker.resetTracking()
            },
            onDragCancel = {
                totalDragX = 0f
                velocityTracker.resetTracking()
            },
            onHorizontalDrag = { change, dragAmount ->
                totalDragX += dragAmount
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                change.consume()
            },
            onDragEnd = {
                val velocityX = velocityTracker.calculateVelocity().x
                val direction = DaySwipeNavigation.directionOf(totalDragX, velocityX, swipeDensity)
                if (direction != null) {
                    DaySwipeNavigation
                        .targetIndex(selectableIndexes, currentIndex, direction)
                        ?.let(onSelectDay)
                }
                totalDragX = 0f
                velocityTracker.resetTracking()
            }
        )
    }
}
