package omc.boundbyfate.api.proficiency

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Владения которые даёт класс, раса, фон или фит.
 * 
 * Используется в:
 * - ClassDefinition — что даёт класс
 * - RaceDefinition — что даёт раса
 * - BackgroundDefinition — что даёт фон
 * - FeatDefinition — что даёт фит
 * 
 * Пример использования:
 * ```kotlin
 * // Fighter даёт владения
 * val fighter = ClassDefinition(
 *     id = Identifier("dnd", "fighter"),
 *     proficiencies = ProficiencyGrants(
 *         proficiencies = listOf(
 *             Identifier("dnd", "simple_weapons"),
 *             Identifier("dnd", "martial_weapons"),
 *             Identifier("dnd", "light_armor"),
 *             Identifier("dnd", "medium_armor"),
 *             Identifier("dnd", "heavy_armor"),
 *             Identifier("dnd", "shields")
 *         ),
 *         skills = SkillChoice(
 *             choose = 2,
 *             from = listOf(
 *                 Identifier("dnd", "athletics"),
 *                 Identifier("dnd", "acrobatics"),
 *                 Identifier("dnd", "intimidation")
 *             )
 *         ),
 *         savingThrows = listOf(
 *             Identifier("dnd", "strength_save"),
 *             Identifier("dnd", "constitution_save")
 *         )
 *     )
 * )
 * ```
 */
data class ProficiencyGrants(
    /**
     * Владения предметами (оружие, броня, инструменты).
     * 
     * Примеры:
     * - "dnd:simple_weapons"
     * - "dnd:light_armor"
     * - "dnd:smithing_tools"
     */
    val proficiencies: List<Identifier> = emptyList(),
    
    /**
     * Владения языками.
     * 
     * Примеры:
     * - "dnd:common"
     * - "dnd:elvish"
     * - "dnd:draconic"
     */
    val languages: List<Identifier> = emptyList(),
    
    /**
     * Выбор языков (например, "выбери 1 из списка").
     */
    val languageChoice: LanguageChoice? = null,
    
    /**
     * Владения навыками.
     * 
     * Обычно классы дают фиксированный список навыков.
     * Примеры:
     * - "dnd:athletics"
     * - "dnd:stealth"
     */
    val skills: List<Identifier> = emptyList(),
    
    /**
     * Выбор навыков (например, "выбери 2 из списка").
     */
    val skillChoice: SkillChoice? = null,
    
    /**
     * Владения спасбросками.
     * 
     * Каждый класс даёт владение 2 спасбросками.
     * Примеры:
     * - "dnd:strength_save"
     * - "dnd:dexterity_save"
     */
    val savingThrows: List<Identifier> = emptyList()
) {
    companion object {
        /**
         * Codec для сериализации/десериализации.
         */
        val CODEC: Codec<ProficiencyGrants> = RecordCodecBuilder.create { instance ->
            instance.group(
                CodecUtil.IDENTIFIER.listOf().optionalFieldOf("proficiencies", emptyList()).forGetter { it.proficiencies },
                CodecUtil.IDENTIFIER.listOf().optionalFieldOf("languages", emptyList()).forGetter { it.languages },
                LanguageChoice.CODEC.optionalFieldOf("language_choice").forGetter { 
                    java.util.Optional.ofNullable(it.languageChoice) 
                },
                CodecUtil.IDENTIFIER.listOf().optionalFieldOf("skills", emptyList()).forGetter { it.skills },
                SkillChoice.CODEC.optionalFieldOf("skill_choice").forGetter { 
                    java.util.Optional.ofNullable(it.skillChoice) 
                },
                CodecUtil.IDENTIFIER.listOf().optionalFieldOf("saving_throws", emptyList()).forGetter { it.savingThrows }
            ).apply(instance) { prof, lang, langChoice, skills, skillChoice, saves ->
                ProficiencyGrants(
                    proficiencies = prof,
                    languages = lang,
                    languageChoice = langChoice.orElse(null),
                    skills = skills,
                    skillChoice = skillChoice.orElse(null),
                    savingThrows = saves
                )
            }
        }
        
        /**
         * Пустые гранты (ничего не даёт).
         */
        val EMPTY = ProficiencyGrants()
    }
    
    /**
     * Проверяет, есть ли какие-либо гранты.
     */
    fun isEmpty(): Boolean {
        return proficiencies.isEmpty() &&
                languages.isEmpty() &&
                languageChoice == null &&
                skills.isEmpty() &&
                skillChoice == null &&
                savingThrows.isEmpty()
    }
    
    /**
     * Проверяет, есть ли какие-либо гранты.
     */
    fun isNotEmpty(): Boolean = !isEmpty()
}

/**
 * Выбор навыков (например, "выбери 2 из 5").
 * 
 * Пример JSON:
 * ```json
 * {
 *   "choose": 2,
 *   "from": [
 *     "dnd:athletics",
 *     "dnd:acrobatics",
 *     "dnd:intimidation",
 *     "dnd:perception",
 *     "dnd:survival"
 *   ]
 * }
 * ```
 */
data class SkillChoice(
    /**
     * Сколько навыков нужно выбрать.
     */
    val choose: Int,
    
    /**
     * Из какого списка выбирать.
     */
    val from: List<Identifier>
) {
    companion object {
        val CODEC: Codec<SkillChoice> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("choose").forGetter { it.choose },
                CodecUtil.IDENTIFIER.listOf().fieldOf("from").forGetter { it.from }
            ).apply(instance, ::SkillChoice)
        }
    }
    
    init {
        require(choose > 0) { "Must choose at least 1 skill" }
        require(choose <= from.size) { "Cannot choose more skills than available" }
    }
}

/**
 * Выбор языков (например, "выбери 1 любой" или "выбери 1 из списка").
 * 
 * Пример JSON (любой язык):
 * ```json
 * {
 *   "choose": 1,
 *   "any": true
 * }
 * ```
 * 
 * Пример JSON (из списка):
 * ```json
 * {
 *   "choose": 1,
 *   "from": [
 *     "dnd:elvish",
 *     "dnd:dwarvish",
 *     "dnd:gnomish"
 *   ]
 * }
 * ```
 */
data class LanguageChoice(
    /**
     * Сколько языков нужно выбрать.
     */
    val choose: Int,
    
    /**
     * Можно выбрать любой язык.
     */
    val any: Boolean = false,
    
    /**
     * Из какого списка выбирать (если не any).
     */
    val from: List<Identifier> = emptyList()
) {
    companion object {
        val CODEC: Codec<LanguageChoice> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("choose").forGetter { it.choose },
                Codec.BOOL.optionalFieldOf("any", false).forGetter { it.any },
                CodecUtil.IDENTIFIER.listOf().optionalFieldOf("from", emptyList()).forGetter { it.from }
            ).apply(instance, ::LanguageChoice)
        }
    }
    
    init {
        require(choose > 0) { "Must choose at least 1 language" }
        if (!any) {
            require(from.isNotEmpty()) { "Must specify languages to choose from if not 'any'" }
            require(choose <= from.size) { "Cannot choose more languages than available" }
        }
    }
}
