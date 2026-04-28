package omc.boundbyfate.api.action

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Окно атак — механика Extra Attack.
 *
 * ## Концепция
 *
 * Когда персонаж с Extra Attack начинает атаку, открывается окно атак.
 * Ячейка ACTION **не тратится** пока окно открыто.
 *
 * Окно закрывается когда:
 * 1. Использованы все доступные атаки ([attacksRemaining] == 0)
 * 2. Истёк таймер ([expiresAt] <= currentTick)
 *
 * При закрытии окна ячейка ACTION тратится.
 *
 * ## Пример
 *
 * Fighter 5 уровня (Extra Attack, 2 атаки):
 * ```
 * // Первая атака — открывается окно
 * window = AttackWindow(attacksTotal=2, attacksRemaining=2, expiresAt=currentTick+40)
 *
 * // Первая атака наносится
 * window = window.useAttack()  // attacksRemaining=1
 *
 * // Вторая атака наносится
 * window = window.useAttack()  // attacksRemaining=0 → окно закрывается
 *
 * // ACTION слот тратится
 * ```
 *
 * @property attacksTotal общее количество атак в окне
 * @property attacksRemaining оставшееся количество атак
 * @property expiresAt тик когда окно закрывается принудительно
 */
data class AttackWindow(
    val attacksTotal: Int,
    val attacksRemaining: Int,
    val expiresAt: Long
) {
    /**
     * Проверяет, открыто ли окно в данный тик.
     *
     * Окно открыто если:
     * - Есть оставшиеся атаки
     * - Таймер не истёк
     */
    fun isOpen(currentTick: Long): Boolean =
        attacksRemaining > 0 && currentTick < expiresAt

    /**
     * Проверяет, должно ли окно закрыться (и ячейка потрачена).
     */
    fun shouldClose(currentTick: Long): Boolean =
        attacksRemaining <= 0 || currentTick >= expiresAt

    /**
     * Использует одну атаку из окна.
     * Возвращает новое окно с уменьшенным счётчиком.
     */
    fun useAttack(): AttackWindow = copy(attacksRemaining = attacksRemaining - 1)

    /**
     * Количество уже использованных атак.
     */
    val attacksUsed: Int get() = attacksTotal - attacksRemaining

    companion object {
        val CODEC: Codec<AttackWindow> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("attacks_total").forGetter { it.attacksTotal },
                Codec.INT.fieldOf("attacks_remaining").forGetter { it.attacksRemaining },
                Codec.LONG.fieldOf("expires_at").forGetter { it.expiresAt }
            ).apply(instance, ::AttackWindow)
        }

        /**
         * Создаёт новое окно атак.
         *
         * @param attackCount количество атак (1 = обычная атака без Extra Attack)
         * @param currentTick текущий тик
         * @param windowTicks длительность окна в тиках (по умолчанию 2 секунды)
         */
        fun open(
            attackCount: Int,
            currentTick: Long,
            windowTicks: Int = 40
        ): AttackWindow = AttackWindow(
            attacksTotal = attackCount,
            attacksRemaining = attackCount,
            expiresAt = currentTick + windowTicks
        )
    }
}
