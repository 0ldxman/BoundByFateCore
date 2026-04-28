package omc.boundbyfate.api.relation

/**
 * Статус отношения — вычисляется из числового значения [Relation.value].
 *
 * Пороги настраиваются через конфиг (TODO).
 * Дефолтные пороги:
 *
 * ```
 * value >= 500   → ALLIED
 * value >= 100   → FRIENDLY
 * value >= -100  → NEUTRAL
 * value >= -500  → HOSTILE
 * value <  -500  → WAR
 * ```
 */
enum class RelationStatus {
    /** Союзники — активное сотрудничество. */
    ALLIED,

    /** Дружественные — положительные отношения. */
    FRIENDLY,

    /** Нейтральные — нет явных симпатий или антипатий. */
    NEUTRAL,

    /** Враждебные — напряжённые отношения. */
    HOSTILE,

    /** Война — открытый конфликт. */
    WAR;

    companion object {
        /**
         * Вычисляет статус из числового значения.
         */
        fun fromValue(value: Int): RelationStatus = when {
            value >= 500  -> ALLIED
            value >= 100  -> FRIENDLY
            value >= -100 -> NEUTRAL
            value >= -500 -> HOSTILE
            else          -> WAR
        }
    }
}
