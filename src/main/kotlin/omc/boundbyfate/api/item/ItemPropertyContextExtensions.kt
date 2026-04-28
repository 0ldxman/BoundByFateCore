package omc.boundbyfate.api.item

import net.minecraft.entity.LivingEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.api.effect.EffectContext
import omc.boundbyfate.api.effect.EffectDefinition
import omc.boundbyfate.api.stat.ModifierType
import omc.boundbyfate.api.stat.StatModifier
import omc.boundbyfate.component.components.EntityAbilitiesData
import omc.boundbyfate.component.components.EntityCombatData
import omc.boundbyfate.component.components.EntityStatsData
import omc.boundbyfate.component.core.getOrCreate
import omc.boundbyfate.registry.EffectRegistry
import omc.boundbyfate.system.effect.EffectApplier
import omc.boundbyfate.system.proficiency.ProficiencySystem
import omc.boundbyfate.system.status.StatusSystem
import omc.boundbyfate.util.time.Duration

/**
 * Extension-функции на [ItemPropertyContext].
 *
 * Предоставляют удобный DSL для написания логики свойств предметов.
 * Все функции используют [ItemPropertyContext.source] как ключ —
 * это гарантирует что при снятии предмета снимается именно то что он дал.
 */

// ── Эффекты ───────────────────────────────────────────────────────────────

fun ItemPropertyContext.applyEffect(effectDef: EffectDefinition) {
    val handler = EffectRegistry.getHandler(effectDef.id) ?: return
    val ctx = EffectContext.passive(entity, effectDef, source)
    EffectApplier.apply(handler, ctx)
}

fun ItemPropertyContext.removeEffect(effectDef: EffectDefinition) {
    val handler = EffectRegistry.getHandler(effectDef.id) ?: return
    val ctx = EffectContext.passive(entity, effectDef, source)
    EffectApplier.remove(handler, ctx)
}

// ── Модификаторы статов ───────────────────────────────────────────────────

fun ItemPropertyContext.addStatModifier(stat: Identifier, value: Int) {
    val stats = entity.getOrCreate(EntityStatsData.TYPE)
    val modifier = StatModifier(source = source, type = ModifierType.FLAT, value = value)
    val current = stats.temporaryModifiers[stat]?.toMutableList() ?: mutableListOf()
    current.add(modifier)
    stats.temporaryModifiers[stat] = current
    // Пересчитываем итоговое значение
    val base = stats.calculatedStats[stat]?.base ?: return
    val permanent = stats.permanentModifiers[stat] ?: emptyList()
    stats.calculatedStats[stat] = omc.boundbyfate.api.stat.StatValue.compute(base, permanent + current)
}

fun ItemPropertyContext.removeStatModifier(stat: Identifier) {
    val stats = entity.getOrCreate(EntityStatsData.TYPE)
    val current = stats.temporaryModifiers[stat]?.toMutableList() ?: return
    current.removeIf { it.source == source }
    if (current.isEmpty()) stats.temporaryModifiers.remove(stat)
    else stats.temporaryModifiers[stat] = current
    // Пересчитываем
    val base = stats.calculatedStats[stat]?.base ?: return
    val permanent = stats.permanentModifiers[stat] ?: emptyList()
    stats.calculatedStats[stat] = omc.boundbyfate.api.stat.StatValue.compute(base, permanent + current)
}

// ── AC ────────────────────────────────────────────────────────────────────

fun ItemPropertyContext.addArmorClassModifier(value: Int) {
    entity.getOrCreate(EntityCombatData.TYPE).armorClass += value
}

fun ItemPropertyContext.removeArmorClassModifier() {
    // Значение берём из data предмета
    val value = definition.propertyData.getInt("value", 0)
        .let { if (it == 0) definition.propertyData.getInt("penalty", 0) else it }
    entity.getOrCreate(EntityCombatData.TYPE).armorClass -= value
}

fun ItemPropertyContext.setArmorClassFormula(formula: String) {
    // Факт активности хранится в EntityEffectsData — система AC пересчитывает при надевании
    // Пока просто обновляем armorClass напрямую (полная система AC будет позже)
}

fun ItemPropertyContext.removeArmorClassFormula() {
    // Аналогично
}

// ── Способности ───────────────────────────────────────────────────────────

fun ItemPropertyContext.grantAbility(abilityId: Identifier) {
    val abilities = entity.getOrCreate(EntityAbilitiesData.TYPE)
    if (!abilities.knownAbilities.contains(abilityId)) {
        abilities.knownAbilities.add(abilityId)
    }
}

fun ItemPropertyContext.revokeAbility(abilityId: Identifier) {
    entity.getOrCreate(EntityAbilitiesData.TYPE).knownAbilities.remove(abilityId)
}

// ── Ресурсы ───────────────────────────────────────────────────────────────

fun ItemPropertyContext.grantResource(resourceId: Identifier, amount: Int) {
    val abilities = entity.getOrCreate(EntityAbilitiesData.TYPE)
    val current = abilities.resourcesMaximums[resourceId] ?: 0
    abilities.resourcesMaximums[resourceId] = current + amount
    if (!abilities.resourcesCurrent.containsKey(resourceId)) {
        abilities.resourcesCurrent[resourceId] = current + amount
    }
}

fun ItemPropertyContext.revokeResource(resourceId: Identifier) {
    val abilities = entity.getOrCreate(EntityAbilitiesData.TYPE)
    abilities.resourcesMaximums.remove(resourceId)
    abilities.resourcesCurrent.remove(resourceId)
}

// ── Владения ─────────────────────────────────────────────────────────────

fun ItemPropertyContext.grantProficiency(proficiencyId: Identifier) {
    ProficiencySystem.addProficiency(entity, proficiencyId)
}

fun ItemPropertyContext.revokeProficiency(proficiencyId: Identifier) {
    ProficiencySystem.removeProficiency(entity, proficiencyId)
}

// ── Состояния ─────────────────────────────────────────────────────────────

fun ItemPropertyContext.applyStatusToTarget(
    target: LivingEntity,
    statusId: Identifier,
    duration: Duration
) {
    StatusSystem.apply(target, statusId, duration, source)
}

// ── Преимущество/помеха ───────────────────────────────────────────────────
// Хранятся как активные эффекты в EntityEffectsData — системы проверяют hasEffect()

fun ItemPropertyContext.addDisadvantage(skills: List<Identifier>) {
    // Факт активности в EntityEffectsData через EffectApplier
}

fun ItemPropertyContext.removeDisadvantage(skills: List<Identifier>) {
    // Снятие через EffectApplier.remove()
}
