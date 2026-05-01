package omc.boundbyfate.api.feature

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.api.core.Definition
import omc.boundbyfate.api.core.Registrable
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Определение особенности класса.
 *
 * Feature — это список грантов (эффекты, способности, механики, ресурсы).
 * Аналогично ClassDefinition, но для особенности.
 *
 * ## Философия
 *
 * Feature НЕ содержит логику — только данные о том, что даётся.
 * Логика живёт в:
 * - EffectApplier (для эффектов)
 * - AbilityHandler (для способностей)
 * - Mechanic (для механик)
 *
 * ## Примеры JSON
 *
 * ### Простая особенность (Second Wind)
 * ```json
 * {
 *   "id": "boundbyfate-core:second_wind",
 *   "grants": [
 *     {"type": "ability", "id": "boundbyfate-core:second_wind"},
 *     {"type": "resource", "id": "boundbyfate-core:second_wind_uses", "amount": 1}
 *   ]
 * }
 * ```
 *
 * ### Особенность с эффектами (Darkvision)
 * ```json
 * {
 *   "id": "boundbyfate-core:darkvision_60",
 *   "grants": [
 *     {"type": "effect", "type": "boundbyfate-core:darkvision", "range": 60}
 *   ]
 * }
 * ```
 *
 * ### Особенность с механикой (Wizard Spellcasting)
 * ```json
 * {
 *   "id": "boundbyfate-core:wizard_spellcasting",
 *   "grants": [
 *     {
 *       "type": "mechanic",
 *       "id": "boundbyfate-core:spellcasting",
 *       "config": {
 *         "stat": "intelligence",
 *         "type": "full",
 *         "ritual_casting": true
 *       }
 *     },
 *     {
 *       "type": "mechanic",
 *       "id": "boundbyfate-core:wizard_spellbook",
 *       "config": {
 *         "starting_spells": 6
 *       }
 *     },
 *     {"type": "ability", "id": "boundbyfate-core:cast_spell"}
 *   ]
 * }
 * ```
 */
data class FeatureDefinition(
    override val id: Identifier,
    
    /**
     * Гранты особенности.
     * Могут быть: эффекты, способности, механики, ресурсы, владения.
     */
    val grants: List<FeatureGrant> = emptyList(),
    
    /**
     * Теги для группировки и фильтрации.
     * Примеры: "combat", "utility", "spellcasting", "passive"
     */
    val tags: List<String> = emptyList()
) : Definition, Registrable {
    
    /**
     * Проверяет наличие тега.
     */
    fun hasTag(tag: String): Boolean = tag in tags
    
    /**
     * Получает все гранты определённого типа.
     */
    inline fun <reified T : FeatureGrant> getGrantsOfType(): List<T> {
        return grants.filterIsInstance<T>()
    }
    
    override fun getTranslationKey(): String = "feature.${id.namespace}.${id.path}"
    
    override fun validate() {
        // Пустые фичи допустимы — могут быть маркерами или плейсхолдерами
    }
    
    companion object {
        val CODEC: Codec<FeatureDefinition> = RecordCodecBuilder.create { instance ->
            instance.group(
                CodecUtil.IDENTIFIER.fieldOf("id").forGetter { it.id },
                FeatureGrant.CODEC.listOf().fieldOf("grants").forGetter { it.grants },
                Codec.STRING.listOf().optionalFieldOf("tags", emptyList()).forGetter { it.tags }
            ).apply(instance, ::FeatureDefinition)
        }
    }
}
