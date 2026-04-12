package omc.boundbyfate.api.ability.component

import net.minecraft.util.Identifier
import omc.boundbyfate.api.ability.AbilityPhase

/**
 * Компонент визуальных эффектов.
 */
sealed class VisualComponent {
    /** Фаза, в которой отображается эффект */
    abstract val phase: AbilityPhase
    
    // TODO: Implement visual components
    // Particles, Sound, Beam, Aura, etc.
}
