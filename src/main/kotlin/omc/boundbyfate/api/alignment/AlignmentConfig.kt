package omc.boundbyfate.api.alignment

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Конфигурация системы мировоззрений.
 *
 * Загружается из одного JSON файла и определяет:
 * - Размер сетки координат
 * - Список всех мировоззрений с их областями
 *
 * ## Формат JSON (`alignment_config.json`)
 *
 * ```json
 * {
 *   "grid_size": 6,
 *   "alignments": [
 *     {"id": "boundbyfate-core:true_neutral",    "x_range": [-2,  2], "y_range": [-2,  2]},
 *     {"id": "boundbyfate-core:lawful_good",     "x_range": [-6, -3], "y_range": [ 3,  6]},
 *     {"id": "boundbyfate-core:neutral_good",    "x_range": [-2,  2], "y_range": [ 3,  6]},
 *     {"id": "boundbyfate-core:chaotic_good",    "x_range": [ 3,  6], "y_range": [ 3,  6]},
 *     {"id": "boundbyfate-core:lawful_neutral",  "x_range": [-6, -3], "y_range": [-2,  2]},
 *     {"id": "boundbyfate-core:chaotic_neutral", "x_range": [ 3,  6], "y_range": [-2,  2]},
 *     {"id": "boundbyfate-core:lawful_evil",     "x_range": [-6, -3], "y_range": [-6, -3]},
 *     {"id": "boundbyfate-core:neutral_evil",    "x_range": [-2,  2], "y_range": [-6, -3]},
 *     {"id": "boundbyfate-core:chaotic_evil",    "x_range": [ 3,  6], "y_range": [-6, -3]}
 *   ]
 * }
 * ```
 *
 * ## Оси координат
 *
 * ```
 *        +Y (Добро)
 *         │
 *  LG  NG │ CG
 *  ───────┼───────  +X (Хаос)
 *  LN  TN │ CN
 *  ───────┼───────
 *  LE  NE │ CE
 *         │
 *        -Y (Зло)
 *  -X (Закон)
 * ```
 *
 * @property gridSize половина размера сетки (координаты от -gridSize до +gridSize)
 * @property alignments список определений мировоззрений
 */
data class AlignmentConfig(
    val gridSize: Int,
    val alignments: List<AlignmentDefinition>
) {
    /**
     * Минимальное значение координаты.
     */
    val min: Int get() = -gridSize

    /**
     * Максимальное значение координаты.
     */
    val max: Int get() = gridSize

    /**
     * Проверяет, находятся ли координаты в пределах сетки.
     */
    fun isInBounds(x: Int, y: Int): Boolean =
        x in min..max && y in min..max

    /**
     * Зажимает координаты в пределах сетки.
     */
    fun clamp(x: Int, y: Int): Pair<Int, Int> =
        x.coerceIn(min, max) to y.coerceIn(min, max)

    companion object {
        val CODEC: Codec<AlignmentConfig> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT
                    .fieldOf("grid_size")
                    .forGetter { it.gridSize },
                AlignmentDefinition.CODEC.listOf()
                    .fieldOf("alignments")
                    .forGetter { it.alignments }
            ).apply(instance, ::AlignmentConfig)
        }

        /**
         * Дефолтный конфиг — стандартная D&D сетка 6x6.
         */
        val DEFAULT = AlignmentConfig(
            gridSize = 6,
            alignments = emptyList() // загружается из JSON
        )
    }
}
