package omc.boundbyfate.api.mechanic

import com.google.gson.JsonObject
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.api.core.Definition
import omc.boundbyfate.api.core.Registrable
import omc.boundbyfate.util.codec.CodecUtil
import omc.boundbyfate.util.codec.JsonUtil

/**
 * Определение механики класса.
 *
 * Механика — это сложная система которая может влиять на геймплей персонажа.
 * Примеры: Spellcasting, Wizard Spellbook, Metamagic, Rage, Sneak Attack.
 *
 * ## Философия
 *
 * - Механика — это независимая система с собственной логикой
 * - Механики активируются через Feature (Feature → Mechanic grant)
 * - Механики могут зависеть друг от друга (dependencies)
 * - Конфигурация может быть переопределена на уровне Feature
 *
 * ## Примеры JSON
 *
 * ### Spellcasting (базовая механика магии)
 * ```json
 * {
 *   "id": "boundbyfate-core:spellcasting",
 *   "handler": "boundbyfate-core:spellcasting",
 *   "default_config": {
 *     "stat": "intelligence",
 *     "type": "full",
 *     "ritual_casting": false
 *   },
 *   "dependencies": []
 * }
 * ```
 *
 * ### Wizard Spellbook (зависит от Spellcasting)
 * ```json
 * {
 *   "id": "boundbyfate-core:wizard_spellbook",
 *   "handler": "boundbyfate-core:wizard_spellbook",
 *   "default_config": {
 *     "starting_spells": 6,
 *     "spells_per_level": 2
 *   },
 *   "dependencies": ["boundbyfate-core:spellcasting"]
 * }
 * ```
 *
 * ### Metamagic (для Sorcerer)
 * ```json
 * {
 *   "id": "boundbyfate-core:metamagic",
 *   "handler": "boundbyfate-core:metamagic",
 *   "default_config": {
 *     "options_known": 2,
 *     "max_per_spell": 1
 *   },
 *   "dependencies": ["boundbyfate-core:spellcasting"]
 * }
 * ```
 */
data class MechanicDefinition(
    override val id: Identifier,
    
    /**
     * ID хендлера механики.
     * Хендлер — это Kotlin код (ClassMechanic) который реализует логику.
     */
    val handler: Identifier,
    
    /**
     * Дефолтная конфигурация механики.
     * Может быть переопределена на уровне Feature grant.
     */
    val defaultConfig: JsonObject = JsonObject(),
    
    /**
     * Зависимости — другие механики которые должны быть активны.
     * Проверяется при активации механики.
     */
    val dependencies: List<Identifier> = emptyList(),
    
    /**
     * Теги для группировки и фильтрации.
     * Примеры: "spellcasting", "combat", "resource_management"
     */
    val tags: List<String> = emptyList()
) : Definition, Registrable {
    
    /**
     * Проверяет наличие тега.
     */
    fun hasTag(tag: String): Boolean = tag in tags
    
    override fun getTranslationKey(): String = "mechanic.${id.namespace}.${id.path}"
    
    override fun validate() {
        // Проверяем что handler не пустой
        if (handler.toString().isEmpty()) {
            throw IllegalStateException("Mechanic $id has empty handler")
        }
    }
    
    companion object {
        val CODEC: Codec<MechanicDefinition> = RecordCodecBuilder.create { instance ->
            instance.group(
                CodecUtil.IDENTIFIER.fieldOf("id").forGetter { it.id },
                CodecUtil.IDENTIFIER.fieldOf("handler").forGetter { it.handler },
                JsonUtil.JSON_OBJECT_CODEC.optionalFieldOf("default_config", JsonObject())
                    .forGetter { it.defaultConfig },
                CodecUtil.IDENTIFIER.listOf().optionalFieldOf("dependencies", emptyList())
                    .forGetter { it.dependencies },
                Codec.STRING.listOf().optionalFieldOf("tags", emptyList())
                    .forGetter { it.tags }
            ).apply(instance, ::MechanicDefinition)
        }
    }
}
