package omc.boundbyfate.api.action

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import omc.boundbyfate.util.time.TimeUtil

/**
 * Компонент ячеек действий сущности.
 *
 * Хранит состояние всех ячеек действий и текущее окно атак (если открыто).
 *
 * ## Архитектура
 *
 * ```
 * EntityActionData (Attachment)
 *   ├── slots: Map<ActionSlotType, ActionSlot>   — ячейки с кулдаунами
 *   └── attackWindow: AttackWindow?              — открытое окно Extra Attack
 * ```
 *
 * ## Жизненный цикл
 *
 * ```
 * Сущность создаётся → EntityActionData.default() — все ячейки свободны
 *
 * Атака без Extra Attack:
 *   tryUseAction(ACTION) → Success → слот на кулдауне
 *
 * Атака с Extra Attack (2 атаки):
 *   tryUseAction(ACTION, isAttack=true, attackCount=2)
 *     → AttackWindowOpen (окно открыто, слот ещё не потрачен)
 *   tryUseAction(ACTION, isAttack=true)
 *     → AttackWindowClosed (все атаки использованы, слот потрачен)
 *
 * Тик:
 *   tick(currentTick) — проверяет истёкшие окна атак
 * ```
 *
 * @property slots ячейки действий по типу
 * @property attackWindow текущее открытое окно атак (null если нет)
 */
data class EntityActionData(
    val slots: Map<ActionSlotType, ActionSlot> = emptyMap(),
    val attackWindow: AttackWindow? = null
) {
    // ── Проверки доступности ──────────────────────────────────────────────

    /**
     * Проверяет, доступна ли ячейка данного типа.
     */
    fun isAvailable(type: ActionSlotType, currentTick: Long): Boolean =
        slots[type]?.isAvailable(currentTick) ?: true

    /**
     * Возвращает оставшийся кулдаун ячейки в тиках.
     */
    fun remainingCooldown(type: ActionSlotType, currentTick: Long): Long =
        slots[type]?.remainingCooldown(currentTick) ?: 0L

    /**
     * Проверяет, открыто ли окно атак в данный тик.
     */
    fun hasOpenAttackWindow(currentTick: Long): Boolean =
        attackWindow?.isOpen(currentTick) == true

    // ── Использование ячеек ───────────────────────────────────────────────

    /**
     * Пытается использовать ячейку действия.
     *
     * @param type тип ячейки
     * @param currentTick текущий тик
     * @param isAttack является ли это атакой (для Extra Attack логики)
     * @param attackCount количество атак в окне (только если isAttack=true и нет открытого окна)
     * @param blockedBy ID состояния блокирующего действие (null = не заблокировано)
     * @return результат попытки
     */
    fun tryUse(
        type: ActionSlotType,
        currentTick: Long,
        isAttack: Boolean = false,
        attackCount: Int = 1,
        blockedBy: String? = null
    ): Pair<EntityActionData, ActionUseResult> {
        // Проверка блокировки состоянием
        if (blockedBy != null) {
            return this to ActionUseResult.Blocked(blockedBy)
        }

        val slot = slots[type] ?: ActionSlot.free(type)

        // Если это атака — проверяем Extra Attack логику
        if (isAttack && type == ActionSlotType.ACTION) {
            return handleAttack(slot, currentTick, attackCount)
        }

        // Обычное использование ячейки
        if (!slot.isAvailable(currentTick)) {
            return this to ActionUseResult.OnCooldown(slot.remainingCooldown(currentTick))
        }

        val consumed = slot.consume(currentTick)
        val updated = copy(slots = slots + (type to consumed))
        return updated to ActionUseResult.Success(consumed)
    }

    /**
     * Обрабатывает атаку с учётом Extra Attack.
     */
    private fun handleAttack(
        slot: ActionSlot,
        currentTick: Long,
        attackCount: Int
    ): Pair<EntityActionData, ActionUseResult> {
        // Есть открытое окно атак
        val window = attackWindow
        if (window != null && window.isOpen(currentTick)) {
            val updatedWindow = window.useAttack()

            return if (updatedWindow.shouldClose(currentTick)) {
                // Все атаки использованы — закрываем окно и тратим ячейку
                val consumed = slot.consume(currentTick)
                val updated = copy(
                    slots = slots + (ActionSlotType.ACTION to consumed),
                    attackWindow = null
                )
                updated to ActionUseResult.AttackWindowClosed(consumed)
            } else {
                // Ещё есть атаки — окно остаётся открытым
                val updated = copy(attackWindow = updatedWindow)
                updated to ActionUseResult.AttackWindowOpen(updatedWindow)
            }
        }

        // Нет открытого окна — проверяем доступность ячейки
        if (!slot.isAvailable(currentTick)) {
            return this to ActionUseResult.OnCooldown(slot.remainingCooldown(currentTick))
        }

        // Одна атака без Extra Attack — тратим ячейку сразу
        if (attackCount <= 1) {
            val consumed = slot.consume(currentTick)
            val updated = copy(slots = slots + (ActionSlotType.ACTION to consumed))
            return updated to ActionUseResult.Success(consumed)
        }

        // Extra Attack — открываем окно, ячейку пока не тратим
        val newWindow = AttackWindow.open(attackCount, currentTick)
        val updatedWindow = newWindow.useAttack() // первая атака уже использована
        val updated = copy(attackWindow = updatedWindow)
        return updated to ActionUseResult.AttackWindowOpen(updatedWindow)
    }

    // ── Тикование ─────────────────────────────────────────────────────────

    /**
     * Обновляет состояние на каждый тик.
     *
     * Проверяет истёкшие окна атак и тратит ячейку если окно закрылось по таймеру.
     *
     * @param currentTick текущий тик
     * @return обновлённые данные
     */
    fun tick(currentTick: Long): EntityActionData {
        val window = attackWindow ?: return this

        // Окно истекло по таймеру — тратим ячейку
        if (window.shouldClose(currentTick)) {
            val slot = slots[ActionSlotType.ACTION] ?: ActionSlot.free(ActionSlotType.ACTION)
            val consumed = slot.consume(currentTick)
            return copy(
                slots = slots + (ActionSlotType.ACTION to consumed),
                attackWindow = null
            )
        }

        return this
    }

    // ── Модификация ───────────────────────────────────────────────────────

    /**
     * Сбрасывает кулдаун ячейки (например, Action Surge).
     */
    fun resetSlot(type: ActionSlotType): EntityActionData {
        val slot = slots[type] ?: return this
        return copy(slots = slots + (type to slot.reset()))
    }

    /**
     * Сбрасывает все ячейки (например, при смерти или телепортации).
     */
    fun resetAll(): EntityActionData = copy(
        slots = slots.mapValues { (_, slot) -> slot.reset() },
        attackWindow = null
    )

    /**
     * Устанавливает кастомный кулдаун для ячейки.
     * Используется эффектами (Haste, Slow и т.д.).
     */
    fun withCustomCooldown(type: ActionSlotType, cooldownTicks: Int): EntityActionData {
        val slot = slots[type] ?: ActionSlot.free(type)
        return copy(slots = slots + (type to slot.withCooldown(cooldownTicks)))
    }

    companion object {
        val CODEC: Codec<EntityActionData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.unboundedMap(ActionSlotType.CODEC, ActionSlot.CODEC)
                    .optionalFieldOf("slots", emptyMap())
                    .forGetter { it.slots },
                AttackWindow.CODEC
                    .optionalFieldOf("attack_window")
                    .forGetter { java.util.Optional.ofNullable(it.attackWindow) }
            ).apply(instance) { slots, window ->
                EntityActionData(slots, window.orElse(null))
            }
        }

        /**
         * Создаёт дефолтные данные — все стандартные ячейки свободны.
         */
        fun default(): EntityActionData = EntityActionData(
            slots = mapOf(
                ActionSlotType.ACTION to ActionSlot.free(ActionSlotType.ACTION),
                ActionSlotType.BONUS_ACTION to ActionSlot.free(ActionSlotType.BONUS_ACTION),
                ActionSlotType.REACTION to ActionSlot.free(ActionSlotType.REACTION)
            )
        )

        /**
         * Создаёт данные для босса/НПС с легендарными действиями.
         */
        fun withLegendary(legendaryCooldownTicks: Int = 60): EntityActionData = EntityActionData(
            slots = mapOf(
                ActionSlotType.ACTION to ActionSlot.free(ActionSlotType.ACTION),
                ActionSlotType.BONUS_ACTION to ActionSlot.free(ActionSlotType.BONUS_ACTION),
                ActionSlotType.REACTION to ActionSlot.free(ActionSlotType.REACTION),
                ActionSlotType.LEGENDARY to ActionSlot.free(ActionSlotType.LEGENDARY, legendaryCooldownTicks)
            )
        )
    }
}
