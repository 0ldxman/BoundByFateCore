package omc.boundbyfate.api.proficiency

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.api.core.Definition
import omc.boundbyfate.api.core.Registrable
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Определение владения (Proficiency).
 * 
 * Владение — это умение использовать определённые предметы, инструменты или языки.
 * 
 * В D&D 5e есть владения:
 * - Оружием (Simple Weapons, Martial Weapons, конкретное оружие)
 * - Бронёй (Light, Medium, Heavy, Shields)
 * - Инструментами (Artisan's Tools, Musical Instruments, ...)
 * - Языками (Common, Elvish, Draconic, ...)
 * 
 * Владения могут быть иерархическими:
 * - "Martial Weapons" включает "Swords", "Axes", "Bows"
 * - "Swords" включает конкретные мечи
 * - "Artisan Tools" включает "Smithing Tools", "Carpentry Tools"
 * 
 * ## Новый формат JSON (упрощённый):
 * 
 * ```json
 * {
 *   "id": "dnd:martial_weapons",
 *   "matches": [
 *     "@dnd:swords",
 *     "@dnd:axes",
 *     "@dnd:bows"
 *   ],
 *   "penalty": {
 *     "effects": ["boundbyfate-core:no_weapon_proficiency"]
 *   }
 * }
 * ```
 * 
 * ```json
 * {
 *   "id": "dnd:swords",
 *   "matches": [
 *     "#boundbyfate-core:proficiency/swords",
 *     "minecraft:netherite_sword"
 *   ],
 *   "penalty": {
 *     "effects": ["boundbyfate-core:no_weapon_proficiency"]
 *   }
 * }
 * ```
 * 
 * ```json
 * {
 *   "id": "dnd:common",
 *   "script": "Common"
 * }
 * ```
 * 
 * ## Формат matches:
 * - `#namespace:path` - тег предметов
 * - `@namespace:path` - другое владение (иерархия)
 * - `namespace:path` - конкретный предмет
 */
data class ProficiencyDefinition(
    override val id: Identifier,
    
    /**
     * Список сопоставлений для определения подходящих предметов.
     * 
     * Формат:
     * - `#namespace:path` - тег предметов
     * - `@namespace:path` - другое владение (иерархия)
     * - `namespace:path` - конкретный предмет
     * 
     * Примеры:
     * - `["#boundbyfate-core:proficiency/swords"]` - все предметы с тегом
     * - `["minecraft:diamond_sword", "minecraft:iron_sword"]` - конкретные предметы
     * - `["@dnd:swords", "@dnd:axes"]` - включает другие владения
     * 
     * Если пусто, это владение языком или абстрактная группа.
     */
    val matches: List<String> = emptyList(),
    
    /**
     * Письменность для языков.
     * 
     * Примеры: "Common", "Draconic", "Elvish"
     * Если указано, это владение языком.
     */
    val script: String? = null,
    
    /**
     * Штраф за отсутствие владения.
     * 
     * Если null, штрафа нет (как для языков).
     */
    val penalty: PenaltyConfig? = null,
    
    /**
     * Теги для группировки и фильтрации.
     * 
     * Примеры:
     * - "weapon" — это оружие
     * - "armor" — это броня
     * - "tool" — это инструмент
     * - "language" — это язык
     * - "simple" — простое оружие
     * - "martial" — воинское оружие
     * 
     * Теги полностью настраиваемые через JSON.
     */
    val tags: List<String> = emptyList()
) : Definition, Registrable {
    
    companion object {
        /**
         * Codec для сериализации/десериализации.
         */
        val CODEC: Codec<ProficiencyDefinition> = RecordCodecBuilder.create { instance ->
            instance.group(
                CodecUtil.IDENTIFIER.fieldOf("id").forGetter { it.id },
                Codec.STRING.listOf().optionalFieldOf("matches", emptyList()).forGetter { it.matches },
                Codec.STRING.optionalFieldOf("script").forGetter { 
                    java.util.Optional.ofNullable(it.script) 
                },
                PenaltyConfig.CODEC.optionalFieldOf("penalty").forGetter { 
                    java.util.Optional.ofNullable(it.penalty) 
                },
                Codec.STRING.listOf().optionalFieldOf("tags", emptyList()).forGetter { it.tags }
            ).apply(instance) { id, matches, script, penalty, tags ->
                ProficiencyDefinition(
                    id = id,
                    matches = matches,
                    script = script.orElse(null),
                    penalty = penalty.orElse(null),
                    tags = tags
                )
            }
        }
    }
    
    override fun getTranslationKey(): String = "proficiency.${id.namespace}.${id.path}"
    
    override fun validate() {
        // Владение должно иметь либо matches (для предметов), либо script (для языков)
        if (matches.isEmpty() && script == null && !hasTag("group")) {
            throw IllegalStateException(
                "Proficiency $id must have either 'matches' (for items) or 'script' (for languages) or 'group' tag"
            )
        }
        
        // Если это язык, не должно быть matches
        if (script != null && matches.isNotEmpty()) {
            throw IllegalStateException(
                "Proficiency $id: languages cannot have item matches"
            )
        }
    }
    
    /**
     * Парсит строки matches в типизированные объекты.
     */
    fun parseMatches(): List<ProficiencyMatch> {
        return matches.map { str ->
            when {
                str.startsWith("#") -> {
                    val tagId = str.substring(1)
                    ProficiencyMatch.Tag(Identifier.tryParse(tagId) ?: throw IllegalArgumentException("Invalid tag: $tagId"))
                }
                str.startsWith("@") -> {
                    val profId = str.substring(1)
                    ProficiencyMatch.Proficiency(Identifier.tryParse(profId) ?: throw IllegalArgumentException("Invalid proficiency: $profId"))
                }
                else -> {
                    ProficiencyMatch.Item(Identifier.tryParse(str) ?: throw IllegalArgumentException("Invalid item: $str"))
                }
            }
        }
    }
    
    /**
     * Проверяет, является ли это владение языком.
     */
    fun isLanguage(): Boolean = script != null || hasTag("language")
    
    /**
     * Проверяет, является ли это владение предметом.
     */
    fun isItem(): Boolean = matches.isNotEmpty()
    
    /**
     * Проверяет, является ли это абстрактная группа.
     * 
     * Группа — это владение которое только включает другие владения,
     * но само не сопоставляется с предметами.
     */
    fun isGroup(): Boolean = matches.all { it.startsWith("@") } && hasTag("group")
    
    /**
     * Проверяет наличие тега.
     */
    fun hasTag(tag: String): Boolean = tags.contains(tag)
    
    /**
     * Проверяет наличие любого из тегов.
     */
    fun hasAnyTag(vararg tags: String): Boolean = tags.any { it in this.tags }
    
    /**
     * Проверяет наличие всех тегов.
     */
    fun hasAllTags(vararg tags: String): Boolean = tags.all { it in this.tags }
}

/**
 * Типизированное сопоставление для владения.
 */
sealed class ProficiencyMatch {
    /**
     * Сопоставление по тегу предметов.
     * Формат в JSON: `#namespace:path`
     */
    data class Tag(val tag: Identifier) : ProficiencyMatch()
    
    /**
     * Сопоставление по конкретному предмету.
     * Формат в JSON: `namespace:path`
     */
    data class Item(val item: Identifier) : ProficiencyMatch()
    
    /**
     * Сопоставление по другому владению (иерархия).
     * Формат в JSON: `@namespace:path`
     */
    data class Proficiency(val proficiency: Identifier) : ProficiencyMatch()
}
