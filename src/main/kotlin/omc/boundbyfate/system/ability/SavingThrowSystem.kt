package omc.boundbyfate.system.ability

import net.minecraft.entity.LivingEntity
import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.api.stat.Ability
import omc.boundbyfate.component.EntityStatData
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.system.dice.DiceRoller
import org.slf4j.LoggerFactory

/**
 * Система спасбросков.
 * 
 * Обрабатывает спасброски для способностей и заклинаний.
 */
object SavingThrowSystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")
    
    /**
     * Выполняет спасбросок.
     * 
     * @param entity Сущность, выполняющая спасбросок
     * @param ability Характеристика для спасброска
     * @param dc Сложность (Difficulty Class)
     * @return true если спасбросок успешен
     */
    fun makeSave(entity: LivingEntity, ability: Ability, dc: Int): Boolean {
        val stats = entity.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
        
        // Бросок d20
        val roll = DiceRoller.rollD20()
        
        // Модификатор характеристики
        val abilityMod = stats?.getModifier(ability) ?: 0
        
        // Бонус мастерства (если есть)
        val proficiencyBonus = if (entity is ServerPlayerEntity) {
            val level = entity.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, null)?.level ?: 1
            calculateProficiencyBonus(level)
        } else {
            0
        }
        
        // TODO: Проверить, есть ли у сущности proficiency в этом спасброске
        val hasProficiency = false // Временно
        
        val total = roll + abilityMod + (if (hasProficiency) proficiencyBonus else 0)
        
        val success = total >= dc
        
        logger.debug(
            "${entity.name?.string ?: "Entity"} saving throw: " +
            "d20=$roll + mod=$abilityMod + prof=${if (hasProficiency) proficiencyBonus else 0} = $total vs DC $dc -> ${if (success) "SUCCESS" else "FAIL"}"
        )
        
        return success
    }
    
    /**
     * Вычисляет DC заклинания для кастера.
     * 
     * DC = 8 + бонус мастерства + модификатор характеристики заклинателя
     */
    fun calculateSpellSaveDC(caster: LivingEntity, stats: EntityStatData?): Int {
        if (stats == null) return 10
        
        // Получаем характеристику заклинателя
        val spellcastingAbility = getSpellcastingAbility(caster)
        val abilityMod = stats.getModifier(spellcastingAbility)
        
        // Бонус мастерства
        val proficiencyBonus = if (caster is ServerPlayerEntity) {
            val level = caster.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, null)?.level ?: 1
            calculateProficiencyBonus(level)
        } else {
            0
        }
        
        return 8 + proficiencyBonus + abilityMod
    }
    
    /**
     * Вычисляет бонус атаки заклинанием для кастера.
     * 
     * Бонус = бонус мастерства + модификатор характеристики заклинателя
     */
    fun calculateSpellAttackBonus(caster: LivingEntity, stats: EntityStatData?): Int {
        if (stats == null) return 0
        
        val spellcastingAbility = getSpellcastingAbility(caster)
        val abilityMod = stats.getModifier(spellcastingAbility)
        
        val proficiencyBonus = if (caster is ServerPlayerEntity) {
            val level = caster.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, null)?.level ?: 1
            calculateProficiencyBonus(level)
        } else {
            0
        }
        
        return proficiencyBonus + abilityMod
    }
    
    // ═══ PRIVATE HELPERS ═══
    
    /**
     * Получает характеристику заклинателя для класса.
     */
    private fun getSpellcastingAbility(caster: LivingEntity): Ability {
        if (caster !is ServerPlayerEntity) return Ability.INTELLIGENCE
        
        val classData = caster.getAttachedOrElse(BbfAttachments.PLAYER_CLASS, null)
            ?: return Ability.INTELLIGENCE
        
        // TODO: Получить spellcasting ability из определения класса
        // Пока возвращаем INT по умолчанию
        return Ability.INTELLIGENCE
    }
    
    /**
     * Вычисляет бонус мастерства по уровню.
     */
    private fun calculateProficiencyBonus(level: Int): Int {
        return when {
            level >= 17 -> 6
            level >= 13 -> 5
            level >= 9 -> 4
            level >= 5 -> 3
            else -> 2
        }
    }
}
