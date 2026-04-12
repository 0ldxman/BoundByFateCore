package omc.boundbyfate.api.ability.component

import net.minecraft.util.Identifier

/**
 * Компонент метаданных способности.
 */
sealed class MetadataComponent {
    
    /**
     * Метаданные заклинания.
     */
    data class Spell(
        val level: Int,
        val school: SpellSchool,
        val ritual: Boolean = false,
        val concentration: Boolean = false
    ) : MetadataComponent()
    
    /**
     * Доступность способности.
     */
    data class Availability(
        val classes: List<Identifier> = emptyList(),
        val subclasses: List<Identifier> = emptyList(),
        val requiresLevel: Int = 1
    ) : MetadataComponent()
    
    /**
     * Спасбросок.
     */
    data class SavingThrow(
        val ability: Identifier,
        val onSuccess: SavingThrowResult = SavingThrowResult.HALF_DAMAGE
    ) : MetadataComponent()
}

enum class SpellSchool {
    ABJURATION,
    CONJURATION,
    DIVINATION,
    ENCHANTMENT,
    EVOCATION,
    ILLUSION,
    NECROMANCY,
    TRANSMUTATION
}

enum class SavingThrowResult {
    FULL_EFFECT,
    HALF_DAMAGE,
    NO_EFFECT
}
