package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.skill.SkillDefinition

/**
 * Built-in D&D 5e skills and saving throws.
 */
object BbfSkills {

    // ── Saving Throws ────────────────────────────────────────────────────────

    val SAVE_STRENGTH = SkillDefinition(
        id = Identifier("boundbyfate-core", "save_strength"),
        displayName = "Спасбросок Силы",
        linkedStat = Identifier("boundbyfate-core", "strength"),
        isSavingThrow = true
    )

    val SAVE_DEXTERITY = SkillDefinition(
        id = Identifier("boundbyfate-core", "save_dexterity"),
        displayName = "Спасбросок Ловкости",
        linkedStat = Identifier("boundbyfate-core", "dexterity"),
        isSavingThrow = true
    )

    val SAVE_CONSTITUTION = SkillDefinition(
        id = Identifier("boundbyfate-core", "save_constitution"),
        displayName = "Спасбросок Выносливости",
        linkedStat = Identifier("boundbyfate-core", "constitution"),
        isSavingThrow = true
    )

    val SAVE_INTELLIGENCE = SkillDefinition(
        id = Identifier("boundbyfate-core", "save_intelligence"),
        displayName = "Спасбросок Интеллекта",
        linkedStat = Identifier("boundbyfate-core", "intelligence"),
        isSavingThrow = true
    )

    val SAVE_WISDOM = SkillDefinition(
        id = Identifier("boundbyfate-core", "save_wisdom"),
        displayName = "Спасбросок Мудрости",
        linkedStat = Identifier("boundbyfate-core", "wisdom"),
        isSavingThrow = true
    )

    val SAVE_CHARISMA = SkillDefinition(
        id = Identifier("boundbyfate-core", "save_charisma"),
        displayName = "Спасбросок Харизмы",
        linkedStat = Identifier("boundbyfate-core", "charisma"),
        isSavingThrow = true
    )

    // ── Skills ───────────────────────────────────────────────────────────────

    // Strength
    val ATHLETICS = SkillDefinition(
        id = Identifier("boundbyfate-core", "athletics"),
        displayName = "Атлетика",
        linkedStat = Identifier("boundbyfate-core", "strength")
    )

    // Dexterity
    val ACROBATICS = SkillDefinition(
        id = Identifier("boundbyfate-core", "acrobatics"),
        displayName = "Акробатика",
        linkedStat = Identifier("boundbyfate-core", "dexterity")
    )

    val SLEIGHT_OF_HAND = SkillDefinition(
        id = Identifier("boundbyfate-core", "sleight_of_hand"),
        displayName = "Ловкость рук",
        linkedStat = Identifier("boundbyfate-core", "dexterity")
    )

    val STEALTH = SkillDefinition(
        id = Identifier("boundbyfate-core", "stealth"),
        displayName = "Скрытность",
        linkedStat = Identifier("boundbyfate-core", "dexterity")
    )

    // Intelligence
    val ARCANA = SkillDefinition(
        id = Identifier("boundbyfate-core", "arcana"),
        displayName = "Магия",
        linkedStat = Identifier("boundbyfate-core", "intelligence")
    )

    val HISTORY = SkillDefinition(
        id = Identifier("boundbyfate-core", "history"),
        displayName = "История",
        linkedStat = Identifier("boundbyfate-core", "intelligence")
    )

    val INVESTIGATION = SkillDefinition(
        id = Identifier("boundbyfate-core", "investigation"),
        displayName = "Анализ",
        linkedStat = Identifier("boundbyfate-core", "intelligence")
    )

    val NATURE = SkillDefinition(
        id = Identifier("boundbyfate-core", "nature"),
        displayName = "Природа",
        linkedStat = Identifier("boundbyfate-core", "intelligence")
    )

    val RELIGION = SkillDefinition(
        id = Identifier("boundbyfate-core", "religion"),
        displayName = "Религия",
        linkedStat = Identifier("boundbyfate-core", "intelligence")
    )

    // Wisdom
    val ANIMAL_HANDLING = SkillDefinition(
        id = Identifier("boundbyfate-core", "animal_handling"),
        displayName = "Уход за животными",
        linkedStat = Identifier("boundbyfate-core", "wisdom")
    )

    val INSIGHT = SkillDefinition(
        id = Identifier("boundbyfate-core", "insight"),
        displayName = "Проницательность",
        linkedStat = Identifier("boundbyfate-core", "wisdom")
    )

    val MEDICINE = SkillDefinition(
        id = Identifier("boundbyfate-core", "medicine"),
        displayName = "Медицина",
        linkedStat = Identifier("boundbyfate-core", "wisdom")
    )

    val PERCEPTION = SkillDefinition(
        id = Identifier("boundbyfate-core", "perception"),
        displayName = "Внимательность",
        linkedStat = Identifier("boundbyfate-core", "wisdom")
    )

    val SURVIVAL = SkillDefinition(
        id = Identifier("boundbyfate-core", "survival"),
        displayName = "Выживание",
        linkedStat = Identifier("boundbyfate-core", "wisdom")
    )

    // Charisma
    val DECEPTION = SkillDefinition(
        id = Identifier("boundbyfate-core", "deception"),
        displayName = "Обман",
        linkedStat = Identifier("boundbyfate-core", "charisma")
    )

    val INTIMIDATION = SkillDefinition(
        id = Identifier("boundbyfate-core", "intimidation"),
        displayName = "Запугивание",
        linkedStat = Identifier("boundbyfate-core", "charisma")
    )

    val PERFORMANCE = SkillDefinition(
        id = Identifier("boundbyfate-core", "performance"),
        displayName = "Выступление",
        linkedStat = Identifier("boundbyfate-core", "charisma")
    )

    val PERSUASION = SkillDefinition(
        id = Identifier("boundbyfate-core", "persuasion"),
        displayName = "Убеждение",
        linkedStat = Identifier("boundbyfate-core", "charisma")
    )

    /**
     * Registers all built-in skills and saving throws.
     * Called during mod initialization.
     */
    fun register() {
        // Saving throws
        SkillRegistry.register(SAVE_STRENGTH)
        SkillRegistry.register(SAVE_DEXTERITY)
        SkillRegistry.register(SAVE_CONSTITUTION)
        SkillRegistry.register(SAVE_INTELLIGENCE)
        SkillRegistry.register(SAVE_WISDOM)
        SkillRegistry.register(SAVE_CHARISMA)

        // Skills
        SkillRegistry.register(ATHLETICS)
        SkillRegistry.register(ACROBATICS)
        SkillRegistry.register(SLEIGHT_OF_HAND)
        SkillRegistry.register(STEALTH)
        SkillRegistry.register(ARCANA)
        SkillRegistry.register(HISTORY)
        SkillRegistry.register(INVESTIGATION)
        SkillRegistry.register(NATURE)
        SkillRegistry.register(RELIGION)
        SkillRegistry.register(ANIMAL_HANDLING)
        SkillRegistry.register(INSIGHT)
        SkillRegistry.register(MEDICINE)
        SkillRegistry.register(PERCEPTION)
        SkillRegistry.register(SURVIVAL)
        SkillRegistry.register(DECEPTION)
        SkillRegistry.register(INTIMIDATION)
        SkillRegistry.register(PERFORMANCE)
        SkillRegistry.register(PERSUASION)
    }
}
