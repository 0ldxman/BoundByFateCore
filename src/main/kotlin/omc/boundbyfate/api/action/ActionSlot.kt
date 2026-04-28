package omc.boundbyfate.api.action

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Ячейка действия — таймер восстановления конкретного типа действия.
 *
 * ## Концепция
 *
 * Ячейка — это не "заряд", а **кулдаун**. Она либо свободна, либо
 * восстанавливается до определённого тика.
 *
 * ```
 * Свободна:       availableAt = 0
 * На кулдауне:    availableAt = currentTick + cooldownTicks
 * ```
 *
 * ## Кулдаун
 *
 * Базовый кулдаун берётся из [ActionSlotType.baseCooldownTicks], но может
 * быть переопределён через [cooldownTicks] (например, Haste уменьшает кулдаун).
 *
 * @property type тип ячейки
 * @property cooldownTicks кулдаун в тиках (0 = использует базовый из типа)
 * @property availableAt тик когда ячейка станет свободной (0 = свободна сейчас)
 */
data class ActionSlot(
    val type: ActionSlotType,
    val cooldownTicks: Int = 0,
    val availableAt: Long = 0L
) {
    /**
     * Эффективный кулдаун: кастомный если задан, иначе базовый из типа.
     */
    val effectiveCooldown: Int
        get() = if (cooldownTicks > 0) cooldownTicks else type.baseCooldownTicks

    /**
     * Проверяет, свободна ли ячейка в данный тик.
     */
    fun isAvailable(currentTick: Long): Boolean = currentTick >= availableAt

    /**
     * Возвращает оставшееся время кулдауна в тиках.
     * 0 если ячейка свободна.
     */
    fun remainingCooldown(currentTick: Long): Long =
        maxOf(0L, availableAt - currentTick)

    /**
     * Использует ячейку — возвращает новую с установленным кулдауном.
     *
     * @param currentTick текущий тик
     * @return новая ячейка на кулдауне
     */
    fun consume(currentTick: Long): ActionSlot =
        copy(availableAt = currentTick + effectiveCooldown)

    /**
     * Сбрасывает кулдаун — ячейка становится свободной немедленно.
     */
    fun reset(): ActionSlot = copy(availableAt = 0L)

    /**
     * Создаёт ячейку с кастомным кулдауном.
     */
    fun withCooldown(ticks: Int): ActionSlot = copy(cooldownTicks = ticks)

    companion object {
        val CODEC: Codec<ActionSlot> = RecordCodecBuilder.create { instance ->
            instance.group(
                ActionSlotType.CODEC.fieldOf("type").forGetter { it.type },
                Codec.INT.optionalFieldOf("cooldown_ticks", 0).forGetter { it.cooldownTicks },
                Codec.LONG.optionalFieldOf("available_at", 0L).forGetter { it.availableAt }
            ).apply(instance, ::ActionSlot)
        }

        /** Создаёт свободную ячейку с базовым кулдауном типа. */
        fun free(type: ActionSlotType): ActionSlot = ActionSlot(type)

        /** Создаёт свободную ячейку с кастомным кулдауном. */
        fun free(type: ActionSlotType, cooldownTicks: Int): ActionSlot =
            ActionSlot(type, cooldownTicks)
    }
}
