package omc.boundbyfate.api.feature

import com.google.gson.JsonObject
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.api.effect.EffectDefinition
import omc.boundbyfate.util.codec.CodecUtil
import omc.boundbyfate.util.codec.JsonUtil

/**
 * Грант особенности — что особенность даёт.
 *
 * Sealed class для типобезопасности и exhaustive when.
 *
 * ## Типы грантов:
 * - **Effect** — применяет эффект по ID (StatModifier, DamageResistance, etc.)
 * - **Ability** — даёт способность
 * - **Resource** — даёт ресурс или увеличивает его максимум
 * - **Mechanic** — активирует механику класса
 * - **Proficiency** — даёт владение
 *
 * ## Примеры JSON:
 *
 * ```json
 * {"grant_type": "effect", "id": "boundbyfate-core:darkvision", "data": {"range": 60}}
 * {"grant_type": "ability", "id": "boundbyfate-core:second_wind"}
 * {"grant_type": "resource", "id": "boundbyfate-core:ki_points", "amount": 5}
 * {"grant_type": "mechanic", "id": "boundbyfate-core:spellcasting", "config": {...}}
 * {"grant_type": "proficiency", "id": "boundbyfate-core:martial_weapons"}
 * ```
 */
sealed class FeatureGrant {

    /**
     * Даёт эффект.
     *
     * Хранит inline-определение эффекта (id + data).
     * Применяется через EffectApplier при активации Feature.
     *
     * JSON:
     * ```json
     * {
     *   "grant_type": "effect",
     *   "id": "boundbyfate-core:stat_modifier",
     *   "data": { "stat": "boundbyfate-core:strength", "value": 2 }
     * }
     * ```
     */
    data class Effect(val definition: EffectDefinition) : FeatureGrant()
    
    /**
     * Даёт способность.
     *
     * Способность добавляется в список доступных способностей персонажа.
     */
    data class Ability(val abilityId: Identifier) : FeatureGrant()
    
    /**
     * Даёт ресурс или увеличивает его максимум.
     *
     * Примеры: Ki Points, Rage Uses, Spell Slots.
     */
    data class Resource(
        val resourceId: Identifier,
        val amount: Int
    ) : FeatureGrant()
    
    /**
     * Активирует механику класса.
     *
     * Механика — это сложная система (Spellbook, Metamagic, Rage).
     * Config переопределяет дефолтную конфигурацию из MechanicDefinition.
     */
    data class Mechanic(
        val mechanicId: Identifier,
        val config: JsonObject = JsonObject()
    ) : FeatureGrant()
    
    /**
     * Даёт владение.
     *
     * Примеры: владение оружием, бронёй, инструментами, навыками.
     */
    data class Proficiency(val proficiencyId: Identifier) : FeatureGrant()
    
    companion object {
        val CODEC: Codec<FeatureGrant> = Codec.STRING.dispatch(
            "grant_type",  // Используем grant_type вместо type чтобы не конфликтовать с EffectInstance
            { grant ->
                when (grant) {
                    is Effect -> "effect"
                    is Ability -> "ability"
                    is Resource -> "resource"
                    is Mechanic -> "mechanic"
                    is Proficiency -> "proficiency"
                }
            },
            { type ->
                when (type) {
                    "effect" -> EffectDefinition.CODEC.xmap(
                        { Effect(it) },
                        { it.definition }
                    )
                    
                    "ability" -> RecordCodecBuilder.create<Ability> { instance ->
                        instance.group(
                            CodecUtil.IDENTIFIER.fieldOf("id").forGetter { it.abilityId }
                        ).apply(instance, ::Ability)
                    }
                    
                    "resource" -> RecordCodecBuilder.create<Resource> { instance ->
                        instance.group(
                            CodecUtil.IDENTIFIER.fieldOf("id").forGetter { it.resourceId },
                            Codec.INT.fieldOf("amount").forGetter { it.amount }
                        ).apply(instance, ::Resource)
                    }
                    
                    "mechanic" -> RecordCodecBuilder.create<Mechanic> { instance ->
                        instance.group(
                            CodecUtil.IDENTIFIER.fieldOf("id").forGetter { it.mechanicId },
                            JsonUtil.JSON_OBJECT_CODEC.optionalFieldOf("config", JsonObject())
                                .forGetter { it.config }
                        ).apply(instance, ::Mechanic)
                    }
                    
                    "proficiency" -> RecordCodecBuilder.create<Proficiency> { instance ->
                        instance.group(
                            CodecUtil.IDENTIFIER.fieldOf("id").forGetter { it.proficiencyId }
                        ).apply(instance, ::Proficiency)
                    }
                    
                    else -> throw IllegalArgumentException("Unknown feature grant type: $type")
                }
            }
        )
    }
}
