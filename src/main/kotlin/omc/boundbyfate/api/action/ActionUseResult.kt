package omc.boundbyfate.api.action

/**
 * Результат попытки использовать ячейку действия.
 *
 * Возвращается системой действий при попытке совершить действие.
 * Позволяет коду понять почему действие не удалось.
 *
 * ## Использование
 *
 * ```kotlin
 * val result = ActionSystem.tryUse(entity, ActionSlotType.ACTION)
 *
 * when (result) {
 *     is ActionUseResult.Success -> {
 *         // Действие разрешено, ячейка потрачена
 *         performAction()
 *     }
 *     is ActionUseResult.OnCooldown -> {
 *         // Ячейка на кулдауне
 *         showCooldownFeedback(result.remainingTicks)
 *     }
 *     is ActionUseResult.Blocked -> {
 *         // Действие заблокировано состоянием
 *         showBlockedFeedback(result.reason)
 *     }
 *     is ActionUseResult.AttackWindowOpen -> {
 *         // Атака в рамках Extra Attack окна
 *         performAttack()
 *     }
 * }
 * ```
 */
sealed class ActionUseResult {

    /**
     * Действие разрешено, ячейка потрачена.
     *
     * @property slot ячейка после потребления (с установленным кулдауном)
     */
    data class Success(val slot: ActionSlot) : ActionUseResult()

    /**
     * Ячейка на кулдауне — действие недоступно.
     *
     * @property remainingTicks сколько тиков осталось до восстановления
     */
    data class OnCooldown(val remainingTicks: Long) : ActionUseResult()

    /**
     * Действие заблокировано состоянием сущности.
     *
     * Примеры причин:
     * - "incapacitated" — существо недееспособно
     * - "paralyzed" — существо парализовано
     * - "stunned" — существо ошеломлено
     *
     * @property reason причина блокировки (ID состояния или описание)
     */
    data class Blocked(val reason: String) : ActionUseResult()

    /**
     * Атака выполнена в рамках открытого окна Extra Attack.
     *
     * Ячейка ACTION ещё не потрачена — окно продолжает работать.
     *
     * @property window текущее состояние окна атак после использования атаки
     */
    data class AttackWindowOpen(val window: AttackWindow) : ActionUseResult()

    /**
     * Атака закрыла окно Extra Attack — ячейка ACTION потрачена.
     *
     * @property slot ячейка ACTION после потребления
     */
    data class AttackWindowClosed(val slot: ActionSlot) : ActionUseResult()

    // ── Удобные проверки ──────────────────────────────────────────────────

    /** Действие было разрешено (Success, AttackWindowOpen, AttackWindowClosed). */
    val isAllowed: Boolean
        get() = this is Success || this is AttackWindowOpen || this is AttackWindowClosed

    /** Действие было заблокировано. */
    val isBlocked: Boolean get() = !isAllowed
}
