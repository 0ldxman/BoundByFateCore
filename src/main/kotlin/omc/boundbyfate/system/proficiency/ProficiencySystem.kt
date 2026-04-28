package omc.boundbyfate.system.proficiency

import net.minecraft.entity.LivingEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import omc.boundbyfate.api.effect.EffectContext
import omc.boundbyfate.api.proficiency.PenaltyConfig
import omc.boundbyfate.api.proficiency.ProficiencyDefinition
import omc.boundbyfate.component.components.EntityStatsData
import omc.boundbyfate.component.core.getOrCreate
import omc.boundbyfate.registry.EffectRegistry
import omc.boundbyfate.system.effect.EffectApplier
import omc.boundbyfate.util.source.SourceReference
import org.slf4j.LoggerFactory

/**
 * Система управления владениями персонажей.
 */
object ProficiencySystem {
    private val logger = LoggerFactory.getLogger(ProficiencySystem::class.java)

    // ── Проверка владений ─────────────────────────────────────────────────

    fun hasProficiency(entity: LivingEntity, proficiency: Identifier): Boolean =
        entity.getOrCreate(EntityStatsData.TYPE).hasProficiency(proficiency)

    fun canUseItem(entity: LivingEntity, item: ItemStack): Boolean {
        if (item.isEmpty) return true
        val matchingProficiencies = ProficiencyRegistry.findMatchingProficiencies(item)
        if (matchingProficiencies.isEmpty()) return true
        return matchingProficiencies.any { hasProficiency(entity, it) }
    }

    fun getPenaltyForItem(entity: LivingEntity, item: ItemStack): PenaltyConfig? {
        if (item.isEmpty) return null
        val matchingProficiencies = ProficiencyRegistry.findMatchingProficiencies(item)
        if (matchingProficiencies.isEmpty()) return null
        if (matchingProficiencies.any { hasProficiency(entity, it) }) return null

        for (profId in matchingProficiencies) {
            val definition = ProficiencyRegistry.get(profId) ?: continue
            val penalty = definition.penalty
            if (penalty != null && penalty.isNotEmpty()) return penalty
        }
        return null
    }

    // ── Применение штрафов ────────────────────────────────────────────────

    fun applyPenalty(entity: LivingEntity, penalty: PenaltyConfig, source: SourceReference): Boolean {
        if (penalty.prohibit) {
            if (entity is ServerPlayerEntity) {
                val message = penalty.message ?: "You lack the proficiency to use this item"
                entity.sendMessage(Text.literal(message), true)
            }
            return true
        }

        var applied = false
        for (effectDef in penalty.effects) {
            val handler = EffectRegistry.getHandler(effectDef.id) ?: run {
                logger.warn("Penalty effect handler '${effectDef.id}' not found")
                null
            } ?: continue
            val ctx = EffectContext.passive(entity, effectDef, source)
            if (EffectApplier.apply(handler, ctx)) applied = true
        }
        return applied
    }

    fun removePenalty(entity: LivingEntity, penalty: PenaltyConfig, source: SourceReference) {
        for (effectDef in penalty.effects) {
            val handler = EffectRegistry.getHandler(effectDef.id) ?: run {
                logger.warn("Penalty effect handler '${effectDef.id}' not found")
                null
            } ?: continue
            val ctx = EffectContext.passive(entity, effectDef, source)
            EffectApplier.remove(handler, ctx)
        }
    }

    // ── Управление владениями ─────────────────────────────────────────────

    fun addProficiency(entity: LivingEntity, proficiency: Identifier, temporary: Boolean = false) {
        val stats = entity.getOrCreate(EntityStatsData.TYPE)
        if (!stats.itemProficiencies.contains(proficiency)) {
            stats.itemProficiencies.add(proficiency)
            logger.debug("Added proficiency $proficiency to ${entity.name.string}")
        }
    }

    fun removeProficiency(entity: LivingEntity, proficiency: Identifier) {
        entity.getOrCreate(EntityStatsData.TYPE).itemProficiencies.remove(proficiency)
        logger.debug("Removed proficiency $proficiency from ${entity.name.string}")
    }

    fun getProficiencies(entity: LivingEntity): Set<Identifier> =
        entity.getOrCreate(EntityStatsData.TYPE).itemProficiencies.toSet()

    // ── Удобные геттеры ───────────────────────────────────────────────────

    fun hasLanguageProficiency(entity: LivingEntity, language: Identifier): Boolean =
        hasProficiency(entity, language)

    fun getLanguages(entity: LivingEntity): List<ProficiencyDefinition> =
        getProficiencies(entity).mapNotNull { profId ->
            ProficiencyRegistry.get(profId)?.takeIf { it.isLanguage() }
        }

    fun getItemProficiencies(entity: LivingEntity): List<ProficiencyDefinition> =
        getProficiencies(entity).mapNotNull { profId ->
            ProficiencyRegistry.get(profId)?.takeIf { it.isItem() }
        }

    fun checkEquipmentPenalty(entity: LivingEntity, item: ItemStack): PenaltyConfig? =
        getPenaltyForItem(entity, item)

    fun checkAttackPenalty(entity: LivingEntity, weapon: ItemStack): PenaltyConfig? =
        getPenaltyForItem(entity, weapon)
}
