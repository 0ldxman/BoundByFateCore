package omc.boundbyfate.api.effect

import net.minecraft.entity.LivingEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.Box
import omc.boundbyfate.api.stat.ModifierType
import omc.boundbyfate.api.stat.StatModifier
import omc.boundbyfate.component.components.EntityAbilitiesData
import omc.boundbyfate.component.components.EntityCombatData
import omc.boundbyfate.component.components.EntityEffectsData
import omc.boundbyfate.component.components.EntityStatsData
import omc.boundbyfate.component.core.getOrCreate
import omc.boundbyfate.system.proficiency.ProficiencySystem
import omc.boundbyfate.system.status.StatusSystem
import omc.boundbyfate.util.time.Duration

/**
 * Extension-функции на [EffectContext].
 *
 * Предоставляют удобный DSL для написания логики эффектов.
 */

// ── Урон ──────────────────────────────────────────────────────────────────

fun EffectContext.dealDamage(target: LivingEntity, amount: Int, damageTypeId: Identifier) {
    // Применяем сопротивления цели
    val resistance = target.getOrCreate(EntityCombatData.TYPE).getResistance(damageTypeId)
    val finalAmount = (amount * resistance.multiplier).toInt()
    if (finalAmount > 0) {
        target.damage(target.damageSources.magic(), finalAmount.toFloat())
    }
}

fun EffectContext.dealDamageToSelf(amount: Int, damageTypeId: Identifier) =
    dealDamage(entity, amount, damageTypeId)

// ── Исцеление ─────────────────────────────────────────────────────────────

fun EffectContext.heal(target: LivingEntity, amount: Int) {
    target.heal(amount.toFloat())
}

fun EffectContext.healSelf(amount: Int) = heal(entity, amount)

// ── Статусы ───────────────────────────────────────────────────────────────

fun EffectContext.applyStatus(target: LivingEntity, statusId: Identifier, duration: Duration) {
    StatusSystem.apply(target, statusId, duration, source)
}

fun EffectContext.removeStatus(target: LivingEntity, statusId: Identifier) {
    StatusSystem.remove(target, statusId)
}

// ── Модификаторы навыков (через EntityEffectsData) ────────────────────────
// Преимущество/помеха хранятся как активные эффекты — системы проверяют
// наличие эффекта через EntityEffectsData.hasEffect(id)

fun EffectContext.addDisadvantage(skills: List<Identifier>) {
    // Эффект уже применяется через EffectApplier — факт активности
    // хранится в EntityEffectsData. Системы проверяют hasEffect().
}

fun EffectContext.removeDisadvantage(skills: List<Identifier>) {
    // Снятие происходит через EffectApplier.remove() — удаляет из EntityEffectsData
}

fun EffectContext.addAdvantage(skills: List<Identifier>) {
    // Аналогично — факт активности в EntityEffectsData
}

fun EffectContext.removeAdvantage(skills: List<Identifier>) {
    // Аналогично
}

// ── Модификаторы статов ───────────────────────────────────────────────────

fun EffectContext.addStatModifier(stat: Identifier, value: Int) {
    val stats = entity.getOrCreate(EntityStatsData.TYPE)
    val modifier = StatModifier(source = source, type = ModifierType.FLAT, value = value)
    val current = stats.temporaryModifiers[stat]?.toMutableList() ?: mutableListOf()
    current.add(modifier)
    stats.temporaryModifiers[stat] = current
    // Пересчитываем итоговое значение
    recalculateStat(stats, stat)
}

fun EffectContext.removeStatModifier(stat: Identifier) {
    val stats = entity.getOrCreate(EntityStatsData.TYPE)
    val current = stats.temporaryModifiers[stat]?.toMutableList() ?: return
    current.removeIf { it.source == source }
    if (current.isEmpty()) {
        stats.temporaryModifiers.remove(stat)
    } else {
        stats.temporaryModifiers[stat] = current
    }
    recalculateStat(stats, stat)
}

private fun EffectContext.recalculateStat(stats: EntityStatsData, statId: Identifier) {
    val base = stats.calculatedStats[statId]?.base ?: return
    val permanent = stats.permanentModifiers[statId] ?: emptyList()
    val temporary = stats.temporaryModifiers[statId] ?: emptyList()
    val newValue = omc.boundbyfate.api.stat.StatValue.compute(base, permanent + temporary)
    stats.calculatedStats[statId] = newValue
}

// ── Модификаторы атаки ────────────────────────────────────────────────────
// Хранятся как активные эффекты в EntityEffectsData.
// Системы атаки проверяют наличие эффектов attack_penalty/attack_bonus.

fun EffectContext.addAttackModifier(value: Int) {
    // Факт активности хранится в EntityEffectsData через EffectApplier
}

fun EffectContext.removeAttackModifier() {
    // Снятие через EffectApplier.remove()
}

fun EffectContext.addDamageModifier(value: Int) {
    // Аналогично
}

fun EffectContext.removeDamageModifier() {
    // Аналогично
}

// ── AC ────────────────────────────────────────────────────────────────────

fun EffectContext.addArmorClassModifier(value: Int) {
    val combat = entity.getOrCreate(EntityCombatData.TYPE)
    combat.armorClass += value
}

fun EffectContext.removeArmorClassModifier() {
    // При снятии нужно пересчитать AC — пока просто убираем значение из data
    val value = data.getInt("penalty", 0).let { if (it == 0) data.getInt("value", 0) else it }
    val combat = entity.getOrCreate(EntityCombatData.TYPE)
    combat.armorClass -= value
}

// ── Владения ─────────────────────────────────────────────────────────────

fun EffectContext.grantProficiency(proficiencyId: Identifier) {
    ProficiencySystem.addProficiency(entity, proficiencyId)
}

fun EffectContext.revokeProficiency(proficiencyId: Identifier) {
    ProficiencySystem.removeProficiency(entity, proficiencyId)
}

// ── Способности ───────────────────────────────────────────────────────────

fun EffectContext.grantAbility(abilityId: Identifier) {
    val abilities = entity.getOrCreate(EntityAbilitiesData.TYPE)
    if (!abilities.knownAbilities.contains(abilityId)) {
        abilities.knownAbilities.add(abilityId)
    }
}

fun EffectContext.revokeAbility(abilityId: Identifier) {
    entity.getOrCreate(EntityAbilitiesData.TYPE).knownAbilities.remove(abilityId)
}

// ── Таргетинг ─────────────────────────────────────────────────────────────

fun EffectContext.findEntitiesInRadius(
    radius: Float,
    filter: EffectTargetFilter = EffectTargetFilter.ALL_LIVING
): List<LivingEntity> {
    val center = entity.pos
    val box = Box(
        center.x - radius, center.y - radius, center.z - radius,
        center.x + radius, center.y + radius, center.z + radius
    )
    val radiusSq = radius * radius

    return entity.world.getEntitiesByClass(LivingEntity::class.java, box) { e ->
        if (e.isDead) return@getEntitiesByClass false
        if (e.squaredDistanceTo(center) > radiusSq) return@getEntitiesByClass false
        when (filter) {
            EffectTargetFilter.ENEMIES -> e != entity && !e.isTeammate(entity)
            EffectTargetFilter.ALLIES -> e != entity && e.isTeammate(entity)
            EffectTargetFilter.ALL_LIVING -> e != entity
            EffectTargetFilter.ALL_INCLUDING_SELF -> true
        }
    }
}

enum class EffectTargetFilter {
    ENEMIES, ALLIES, ALL_LIVING, ALL_INCLUDING_SELF
}
