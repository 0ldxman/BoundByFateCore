package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.stat.StatDefinition

/**
 * Built-in D&D 5e stats for BoundByFate.
 *
 * These six core stats are automatically registered during mod initialization.
 * All stats use standard D&D ranges: min=1, max=30, default=10.
 */
object BbfStats {
    /**
     * Strength (STR) - Physical power, melee damage, carrying capacity.
     */
    val STRENGTH: StatDefinition = StatDefinition(
        id = Identifier("boundbyfate-core", "strength"),
        shortName = "STR",
        displayName = "Сила",
        minValue = 1,
        maxValue = 30,
        defaultValue = 10
    )
    
    /**
     * Constitution (CON) - Endurance, hit points, stamina.
     */
    val CONSTITUTION: StatDefinition = StatDefinition(
        id = Identifier("boundbyfate-core", "constitution"),
        shortName = "CON",
        displayName = "Выносливость",
        minValue = 1,
        maxValue = 30,
        defaultValue = 10
    )
    
    /**
     * Dexterity (DEX) - Agility, reflexes, speed, ranged attacks.
     */
    val DEXTERITY: StatDefinition = StatDefinition(
        id = Identifier("boundbyfate-core", "dexterity"),
        shortName = "DEX",
        displayName = "Ловкость",
        minValue = 1,
        maxValue = 30,
        defaultValue = 10
    )
    
    /**
     * Intelligence (INT) - Reasoning, memory, arcane magic.
     */
    val INTELLIGENCE: StatDefinition = StatDefinition(
        id = Identifier("boundbyfate-core", "intelligence"),
        shortName = "INT",
        displayName = "Интеллект",
        minValue = 1,
        maxValue = 30,
        defaultValue = 10
    )
    
    /**
     * Wisdom (WIS) - Awareness, intuition, divine magic.
     */
    val WISDOM: StatDefinition = StatDefinition(
        id = Identifier("boundbyfate-core", "wisdom"),
        shortName = "WIS",
        displayName = "Мудрость",
        minValue = 1,
        maxValue = 30,
        defaultValue = 10
    )
    
    /**
     * Charisma (CHA) - Force of personality, leadership, social skills.
     */
    val CHARISMA: StatDefinition = StatDefinition(
        id = Identifier("boundbyfate-core", "charisma"),
        shortName = "CHA",
        displayName = "Харизма",
        minValue = 1,
        maxValue = 30,
        defaultValue = 10
    )
    
    /**
     * Registers all built-in stats.
     * Called during mod initialization.
     */
    fun register() {
        StatRegistry.register(STRENGTH)
        StatRegistry.register(CONSTITUTION)
        StatRegistry.register(DEXTERITY)
        StatRegistry.register(INTELLIGENCE)
        StatRegistry.register(WISDOM)
        StatRegistry.register(CHARISMA)
    }
}
