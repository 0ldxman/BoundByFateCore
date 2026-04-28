package omc.boundbyfate.api.alignment

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.api.core.Definition
import omc.boundbyfate.api.core.Registrable
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Определение мировоззрения.
 *
 * Мировоззрение — это именованная область на двумерной сетке координат.
 * Ось X: закон (отрицательные) ↔ хаос (положительные)
 * Ось Y: зло (отрицательные) ↔ добро (положительные)
 *
 * ## Пример JSON
 *
 * ```json
 * {
 *   "id": "boundbyfate-core:true_neutral",
 *   "x_range": [-2, 2],
 *   "y_range": [-2, 2]
 * }
 * ```
 *
 * ```json
 * {
 *   "id": "boundbyfate-core:chaotic_good",
 *   "x_range": [3, 6],
 *   "y_range": [3, 6]
 * }
 * ```
 *
 * @property id уникальный идентификатор
 * @property xRange диапазон по оси X [min, max] включительно
 * @property yRange диапазон по оси Y [min, max] включительно
 */
data class AlignmentDefinition(
    override val id: Identifier,
    val xRange: IntRange,
    val yRange: IntRange
) : Definition, Registrable {

    /**
     * Проверяет, попадают ли координаты в область этого мировоззрения.
     */
    fun contains(x: Int, y: Int): Boolean =
        x in xRange && y in yRange

    override fun getTranslationKey(): String =
        "alignment.${id.namespace}.${id.path}"

    override fun validate() {
        require(xRange.first <= xRange.last) {
            "Alignment $id: x_range min must be <= max"
        }
        require(yRange.first <= yRange.last) {
            "Alignment $id: y_range min must be <= max"
        }
    }

    companion object {
        val CODEC: Codec<AlignmentDefinition> = RecordCodecBuilder.create { instance ->
            instance.group(
                CodecUtil.IDENTIFIER
                    .fieldOf("id")
                    .forGetter { it.id },
                // [min, max] как список из двух чисел
                Codec.INT.listOf()
                    .fieldOf("x_range")
                    .forGetter { listOf(it.xRange.first, it.xRange.last) },
                Codec.INT.listOf()
                    .fieldOf("y_range")
                    .forGetter { listOf(it.yRange.first, it.yRange.last) }
            ).apply(instance) { id, xList, yList ->
                require(xList.size == 2) { "x_range must have exactly 2 values: [min, max]" }
                require(yList.size == 2) { "y_range must have exactly 2 values: [min, max]" }
                AlignmentDefinition(
                    id = id,
                    xRange = xList[0]..xList[1],
                    yRange = yList[0]..yList[1]
                )
            }
        }
    }
}
