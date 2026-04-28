package omc.boundbyfate.api.ability

import net.minecraft.entity.LivingEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/**
 * Результат попытки использовать способность.
 *
 * Возвращается [omc.boundbyfate.system.ability.AbilityExecutor] после
 * попытки выполнить способность.
 */
sealed class AbilityUseResult {

    /** Способность успешно выполнена. */
    data object Success : AbilityUseResult()

    /**
     * Способность не может быть использована.
     * [reason] показывается игроку если не null.
     */
    data class CannotUse(val canUseResult: CanUseResult) : AbilityUseResult()

    /**
     * Способность заблокирована внешней системой (через BEFORE_CHECK).
     * [reason] — причина блокировки для отображения игроку.
     */
    data class Blocked(val reason: Text? = null) : AbilityUseResult()

    /**
     * Способность отменена в BEFORE_EXECUTE.
     */
    data object Cancelled : AbilityUseResult()

    /**
     * Способность прервана во время подготовки.
     * [forced] — true если прервана насильственно (урон, оглушение).
     */
    data class Interrupted(val forced: Boolean = false) : AbilityUseResult()

    /**
     * Хендлер для способности не найден в реестре.
     */
    data class HandlerNotFound(val abilityId: Identifier) : AbilityUseResult()

    // ── Удобные проверки ──────────────────────────────────────────────────

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = !isSuccess
}

/**
 * Результат проверки [AbilityHandler.canUse].
 */
sealed class CanUseResult {

    /** Способность можно использовать. */
    data object Yes : CanUseResult()

    /**
     * Способность нельзя использовать.
     * [reason] показывается игроку в UI.
     */
    data class No(val reason: Text) : CanUseResult()

    /**
     * Способность нельзя использовать, но без сообщения игроку.
     * Используется для тихих проверок.
     */
    data object NoSilent : CanUseResult()

    val isYes: Boolean get() = this is Yes
    val isNo: Boolean get() = !isYes
}

/**
 * Запись о том что произошло во время выполнения способности.
 *
 * Накапливается в [AbilityContext.results] через extension-функции.
 * Читается в AFTER_EXECUTE для цепных реакций, lifesteal, логирования и т.д.
 * Также используется для dry-run превью.
 */
sealed class AbilityExecutionResult {

    /**
     * Урон нанесён цели.
     *
     * @property target цель
     * @property amount итоговый урон
     * @property damageType тип урона
     * @property wasCritical был ли критический удар
     */
    data class DamageDealt(
        val target: LivingEntity,
        val amount: Int,
        val damageType: Identifier,
        val wasCritical: Boolean = false
    ) : AbilityExecutionResult()

    /**
     * Исцеление применено к цели.
     *
     * @property target цель
     * @property amount количество восстановленного HP
     */
    data class HealingApplied(
        val target: LivingEntity,
        val amount: Int
    ) : AbilityExecutionResult()

    /**
     * Статус применён к цели.
     *
     * @property target цель
     * @property statusId идентификатор статуса
     */
    data class StatusApplied(
        val target: LivingEntity,
        val statusId: Identifier
    ) : AbilityExecutionResult()

    /**
     * Статус снят с цели.
     */
    data class StatusRemoved(
        val target: LivingEntity,
        val statusId: Identifier
    ) : AbilityExecutionResult()

    /**
     * Ресурс потрачен.
     *
     * @property resourceId идентификатор ресурса
     * @property amount количество
     */
    data class ResourceConsumed(
        val resourceId: Identifier,
        val amount: Int
    ) : AbilityExecutionResult()

    /**
     * Кастомный результат — для нестандартных эффектов.
     *
     * @property key ключ для идентификации
     * @property data произвольные данные
     */
    data class Custom(
        val key: String,
        val data: Map<String, Any> = emptyMap()
    ) : AbilityExecutionResult()
}
