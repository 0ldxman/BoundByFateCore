package omc.boundbyfate.api.skill

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.api.core.Definition
import omc.boundbyfate.api.core.Registrable
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Определение навыка (Skill) или спасброска (Saving Throw).
 * 
 * В D&D 5e есть:
 * - 18 навыков (Acrobatics, Athletics, Deception, ...)
 * - 6 спасбросков (Strength Save, Dexterity Save, ...)
 * 
 * Навыки и спасброски имеют одинаковую структуру:
 * - Привязаны к характеристике (linkedStat)
 * - Используют бонус мастерства (proficiency bonus)
 * - Могут иметь преимущество/помеху
 * 
 * Формула проверки навыка:
 * ```
 * d20 + stat_modifier + (proficiency_bonus if proficient)
 * ```
 * 
 * Пример JSON (навык):
 * ```json
 * {
 *   "linked_stat": "boundbyfate-core:strength",
 *   "is_saving_throw": false
 * }
 * ```
 * 
 * Пример JSON (спасбросок):
 * ```json
 * {
 *   "linked_stat": "boundbyfate-core:dexterity",
 *   "is_saving_throw": true
 * }
 * ```
 * 
 * Локализация (en_us.json):
 * ```json
 * {
 *   "skill.boundbyfate-core.athletics": "Athletics",
 *   "skill.boundbyfate-core.athletics.description": "Physical activities requiring strength",
 *   "skill.boundbyfate-core.dexterity_save": "Dexterity Save"
 * }
 * ```
 */
data class SkillDefinition(
    override val id: Identifier,
    
    /**
     * Характеристика к которой привязан навык.
     * 
     * Примеры:
     * - Athletics → Strength
     * - Acrobatics → Dexterity
     * - Arcana → Intelligence
     */
    val linkedStat: Identifier,
    
    /**
     * Является ли это спасброском.
     * 
     * - true: Спасбросок (Strength Save, Dexterity Save, ...)
     * - false: Навык (Athletics, Acrobatics, ...)
     */
    val isSavingThrow: Boolean = false
) : Definition, Registrable {
    
    companion object {
        /**
         * Codec для сериализации/десериализации.
         */
        val CODEC: Codec<SkillDefinition> = RecordCodecBuilder.create { instance ->
            instance.group(
                CodecUtil.IDENTIFIER.fieldOf("id").forGetter { it.id },
                CodecUtil.IDENTIFIER.fieldOf("linked_stat").forGetter { it.linkedStat },
                Codec.BOOL.optionalFieldOf("is_saving_throw", false).forGetter { it.isSavingThrow }
            ).apply(instance, ::SkillDefinition)
        }
    }
    
    override fun getTranslationKey(): String = "skill.${id.namespace}.${id.path}"
    
    override fun validate() {
        // Навык валиден если есть linkedStat
        // Дополнительная валидация будет в регистре (проверка что linkedStat существует)
    }
    
    /**
     * Проверяет, является ли это навыком (не спасброском).
     */
    fun isSkill(): Boolean = !isSavingThrow
}
