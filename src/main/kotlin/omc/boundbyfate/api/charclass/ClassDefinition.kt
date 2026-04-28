package omc.boundbyfate.api.charclass

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.api.core.Definition
import omc.boundbyfate.api.core.Registrable
import omc.boundbyfate.api.dice.DiceType
import omc.boundbyfate.api.level.LevelGrant
import omc.boundbyfate.api.proficiency.ProficiencyGrants
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Определение класса персонажа.
 *
 * Класс — это прогрессия грантов с 1 по 20 уровень.
 * Механики активируются через Feature, а не напрямую.
 *
 * ## Философия
 *
 * - Класс НЕ содержит логику — только данные о прогрессии
 * - Механики активируются через Feature (Feature → Mechanic)
 * - Нет мультиклассинга — персонаж имеет ровно 1 класс
 * - HP фиксированный (не рандом) — hp_per_level
 *
 * ## Пример JSON
 *
 * ```json
 * {
 *   "id": "boundbyfate-core:fighter",
 *   "hit_die": "d10",
 *   "hp_per_level": 6,
 *   "starting_proficiencies": {
 *     "armor": ["light", "medium", "heavy", "shields"],
 *     "weapons": ["simple", "martial"],
 *     "saving_throws": ["boundbyfate-core:strength", "boundbyfate-core:constitution"],
 *     "skills": {
 *       "count": 2,
 *       "from": ["boundbyfate-core:athletics", "boundbyfate-core:intimidation"]
 *     }
 *   },
 *   "subclass_level": 3,
 *   "subclasses": ["boundbyfate-core:champion", "boundbyfate-core:battle_master"],
 *   "levels": {
 *     "1": [
 *       {"type": "feature", "id": "boundbyfate-core:fighting_style_choice"},
 *       {"type": "feature", "id": "boundbyfate-core:second_wind"}
 *     ],
 *     "2": [
 *       {"type": "feature", "id": "boundbyfate-core:action_surge"}
 *     ],
 *     "3": [
 *       {"type": "subclass_choice"}
 *     ]
 *   }
 * }
 * ```
 */
data class ClassDefinition(
    override val id: Identifier,
    
    /**
     * Кость хитов (для отображения, не для броска).
     * Примеры: d6, d8, d10, d12
     */
    val hitDie: DiceType,
    
    /**
     * Фиксированное количество HP за уровень.
     * Обычно: среднее значение кости + 1.
     * - d6 → 4
     * - d8 → 5
     * - d10 → 6
     * - d12 → 7
     */
    val hpPerLevel: Int,
    
    /**
     * Начальные владения класса (даются на 1 уровне).
     */
    val startingProficiencies: ClassProficiencies,
    
    /**
     * На каком уровне выбирается подкласс.
     * Обычно 3, но у некоторых классов может быть 1 или 2.
     */
    val subclassLevel: Int = 3,
    
    /**
     * Список доступных подклассов (ID).
     */
    val subclasses: List<Identifier> = emptyList(),
    
    /**
     * Гранты на каждом уровне (1-20).
     * Ключ — уровень, значение — список грантов.
     */
    val levels: Map<Int, List<LevelGrant>> = emptyMap(),
    
    /**
     * Является ли это подклассом.
     * Если true, то это подкласс и у него есть parentClass.
     */
    val isSubclass: Boolean = false,
    
    /**
     * ID родительского класса (только для подклассов).
     */
    val parentClass: Identifier? = null,
    
    /**
     * Теги для группировки и фильтрации.
     * Примеры: "martial", "spellcaster", "full_caster", "half_caster"
     */
    val tags: List<String> = emptyList()
) : Definition, Registrable {
    
    /**
     * Получает все гранты с 1 по targetLevel.
     */
    fun getGrantsUpToLevel(targetLevel: Int): Map<Int, List<LevelGrant>> {
        return levels.filterKeys { it <= targetLevel }
    }
    
    /**
     * Получает гранты конкретного уровня.
     */
    fun getGrantsForLevel(level: Int): List<LevelGrant> {
        return levels[level] ?: emptyList()
    }
    
    /**
     * Проверяет наличие тега.
     */
    fun hasTag(tag: String): Boolean = tag in tags
    
    override fun getTranslationKey(): String = 
        if (isSubclass) "subclass.${id.namespace}.${id.path}"
        else "class.${id.namespace}.${id.path}"
    
    override fun validate() {
        // Подкласс должен иметь parentClass
        if (isSubclass && parentClass == null) {
            throw IllegalStateException("Subclass $id must have parent_class")
        }
        
        // Обычный класс не должен иметь parentClass
        if (!isSubclass && parentClass != null) {
            throw IllegalStateException("Class $id has parent_class but is_subclass is false")
        }
        
        // Уровни должны быть от 1 до 20
        for (level in levels.keys) {
            if (level !in 1..20) {
                throw IllegalStateException("Invalid level $level in class $id (must be 1-20)")
            }
        }
        
        // HP per level должен быть разумным
        if (hpPerLevel !in 1..20) {
            throw IllegalStateException("Invalid hp_per_level $hpPerLevel in class $id (must be 1-20)")
        }
    }
    
    companion object {
        val CODEC: Codec<ClassDefinition> = RecordCodecBuilder.create { instance ->
            instance.group(
                CodecUtil.IDENTIFIER.fieldOf("id").forGetter { it.id },
                DiceType.CODEC.fieldOf("hit_die").forGetter { it.hitDie },
                Codec.INT.fieldOf("hp_per_level").forGetter { it.hpPerLevel },
                ProficiencyGrants.CODEC.fieldOf("starting_proficiencies").forGetter { it.startingProficiencies },
                Codec.INT.optionalFieldOf("subclass_level", 3).forGetter { it.subclassLevel },
                CodecUtil.IDENTIFIER.listOf().optionalFieldOf("subclasses", emptyList()).forGetter { it.subclasses },
                Codec.unboundedMap(
                    Codec.STRING.xmap({ it.toInt() }, { it.toString() }),
                    LevelGrant.CODEC.listOf()
                ).optionalFieldOf("levels", emptyMap()).forGetter { it.levels },
                Codec.BOOL.optionalFieldOf("is_subclass", false).forGetter { it.isSubclass },
                CodecUtil.IDENTIFIER.optionalFieldOf("parent_class")
                    .forGetter { java.util.Optional.ofNullable(it.parentClass) },
                Codec.STRING.listOf().optionalFieldOf("tags", emptyList()).forGetter { it.tags }
            ).apply(instance) { id, hitDie, hpPerLevel, proficiencies, subclassLevel, 
                                 subclasses, levels, isSubclass, parentClass, tags ->
                ClassDefinition(
                    id = id,
                    hitDie = hitDie,
                    hpPerLevel = hpPerLevel,
                    startingProficiencies = proficiencies,
                    subclassLevel = subclassLevel,
                    subclasses = subclasses,
                    levels = levels,
                    isSubclass = isSubclass,
                    parentClass = parentClass.orElse(null),
                    tags = tags
                )
            }
        }
    }
}
