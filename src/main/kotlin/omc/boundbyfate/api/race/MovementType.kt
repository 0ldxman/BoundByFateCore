package omc.boundbyfate.api.race

import com.mojang.serialization.Codec

/**
 * Тип передвижения существа.
 *
 * Используется в [RaceDefinition] для задания скоростей.
 * Скорость задаётся в футах D&D — конвертация в блоки Minecraft
 * происходит через [omc.boundbyfate.util.math.DndMath.feetToBlocks].
 *
 * ## Примеры
 *
 * - Дварф: `WALK = 25`
 * - Человек: `WALK = 30`
 * - Ааракокра: `WALK = 25, FLY = 50`
 * - Тритон: `WALK = 30, SWIM = 30`
 */
enum class MovementType {
    /** Наземное передвижение. */
    WALK,
    /** Плавание. */
    SWIM,
    /** Полёт. */
    FLY,
    /** Лазание. */
    CLIMB,
    /** Рытьё. */
    BURROW;

    companion object {
        val CODEC: Codec<MovementType> = Codec.STRING.xmap(
            { str -> valueOf(str.uppercase()) },
            { type -> type.name.lowercase() }
        )
    }
}
