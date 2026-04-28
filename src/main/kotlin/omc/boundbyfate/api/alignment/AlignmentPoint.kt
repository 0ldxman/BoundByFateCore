package omc.boundbyfate.api.alignment

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Точка мировоззрения персонажа на координатной сетке.
 *
 * Хранит конкретные координаты (x, y) а не просто название мировоззрения.
 * Название определяется динамически через [AlignmentSystem.resolve].
 *
 * ## Оси
 *
 * - X отрицательный → Законный
 * - X положительный → Хаотичный
 * - Y отрицательный → Злой
 * - Y положительный → Добрый
 *
 * ## Примеры
 *
 * ```kotlin
 * AlignmentPoint(0, 0)   // Истинно-Нейтральный (центр)
 * AlignmentPoint(-5, 5)  // Законно-Добрый
 * AlignmentPoint(5, -5)  // Хаотично-Злой
 * AlignmentPoint(4, 0)   // Хаотично-Нейтральный
 * ```
 *
 * @property x ось закон-хаос (-gridSize..+gridSize)
 * @property y ось зло-добро (-gridSize..+gridSize)
 */
data class AlignmentPoint(
    val x: Int,
    val y: Int
) {
    companion object {
        /**
         * Центр сетки — Истинно-Нейтральный.
         */
        val NEUTRAL = AlignmentPoint(0, 0)

        val CODEC: Codec<AlignmentPoint> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("x").forGetter { it.x },
                Codec.INT.fieldOf("y").forGetter { it.y }
            ).apply(instance, ::AlignmentPoint)
        }
    }

    /**
     * Сдвигает точку на (dx, dy).
     */
    fun shift(dx: Int, dy: Int): AlignmentPoint =
        AlignmentPoint(x + dx, y + dy)

    /**
     * Расстояние до другой точки (евклидово).
     */
    fun distanceTo(other: AlignmentPoint): Double =
        Math.sqrt(((x - other.x) * (x - other.x) + (y - other.y) * (y - other.y)).toDouble())

    override fun toString(): String = "($x, $y)"
}
