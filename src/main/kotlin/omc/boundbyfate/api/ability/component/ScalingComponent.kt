package omc.boundbyfate.api.ability.component

/**
 * Компонент масштабирования способности.
 */
sealed class ScalingComponent {
    
    /**
     * Масштабирование при апкасте заклинания.
     */
    data class Upcast(
        val dicePerLevel: Int = 0,
        val targetsPerLevel: Int = 0,
        val radiusPerLevel: Float = 0f
    ) : ScalingComponent()
    
    /**
     * Масштабирование от уровня персонажа.
     */
    data class CharacterLevel(
        val scaleAt: List<Int> = listOf(5, 11, 17),
        val dicePerTier: Int = 1
    ) : ScalingComponent()
}
