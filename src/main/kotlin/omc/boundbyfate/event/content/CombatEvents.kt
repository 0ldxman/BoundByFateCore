package omc.boundbyfate.event.content

import net.minecraft.entity.LivingEntity
import omc.boundbyfate.event.core.CancellableResultEvent
import omc.boundbyfate.event.core.BaseResultEvent
import omc.boundbyfate.event.core.EventBus
import omc.boundbyfate.event.core.eventBus
import omc.boundbyfate.util.source.SourceReference

/**
 * Боевые события.
 *
 * Покрывают расчёт урона, броски атаки и вычисление AC.
 * Используют EventBus для поддержки приоритетов и изменения результатов.
 */
object CombatEvents {

    /**
     * Вызывается перед расчётом урона.
     * Можно изменить количество урона или отменить.
     */
    val BEFORE_DAMAGE: EventBus<BeforeDamage> =
        eventBus("combat.before_damage")

    /** Вызывается после нанесения урона. */
    val AFTER_DAMAGE: EventBus<AfterDamage> =
        eventBus("combat.after_damage")

    /**
     * Вызывается при расчёте броска атаки.
     * Можно изменить результат броска.
     */
    val ATTACK_ROLL: EventBus<AttackRoll> =
        eventBus("combat.attack_roll")

    /**
     * Вызывается при расчёте AC.
     * Можно изменить итоговый AC.
     */
    val CALCULATE_AC: EventBus<CalculateAC> =
        eventBus("combat.calculate_ac")
}

// ── Callbacks ─────────────────────────────────────────────────────────────

fun interface BeforeDamage {
    fun onBeforeDamage(event: DamageEvent)
}

fun interface AfterDamage {
    fun onAfterDamage(target: LivingEntity, amount: Float, source: SourceReference?)
}

fun interface AttackRoll {
    fun onAttackRoll(event: AttackRollEvent)
}

fun interface CalculateAC {
    fun onCalculateAC(event: ACCalculationEvent)
}

// ── Event data ────────────────────────────────────────────────────────────

data class DamageEvent(
    val target: LivingEntity,
    val source: SourceReference?,
    val amount: Float
) : CancellableResultEvent<Float>(amount)

data class AttackRollEvent(
    val attacker: LivingEntity,
    val target: LivingEntity,
    val rollResult: Int
) : BaseResultEvent<Int>(rollResult)

data class ACCalculationEvent(
    val entity: LivingEntity,
    val armorClass: Int
) : BaseResultEvent<Int>(armorClass)
