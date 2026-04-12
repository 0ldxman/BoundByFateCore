package omc.boundbyfate.api.ability.component

import net.minecraft.util.Identifier

/**
 * Компонент, определяющий механику выбора целей.
 */
sealed class TargetingComponent {
    /** Дальность способности в блоках */
    abstract val range: Float
    
    /** Требуется ли прямая видимость до цели */
    abstract val requiresLineOfSight: Boolean
    
    /** Фильтр целей */
    abstract val targetFilter: TargetFilter
    
    /**
     * Таргетинг на себя.
     */
    data class Self(
        override val range: Float = 0f,
        override val requiresLineOfSight: Boolean = false,
        override val targetFilter: TargetFilter = TargetFilter.SELF_ONLY
    ) : TargetingComponent()
    
    /**
     * Одна цель (raycast).
     */
    data class SingleTarget(
        override val range: Float = 60f,
        override val requiresLineOfSight: Boolean = true,
        override val targetFilter: TargetFilter = TargetFilter.ENEMIES
    ) : TargetingComponent()
    
    /**
     * Физический снаряд.
     */
    data class Projectile(
        override val range: Float = 120f,
        override val requiresLineOfSight: Boolean = false,
        override val targetFilter: TargetFilter = TargetFilter.ENEMIES,
        val projectileEntity: Identifier,
        val speed: Float = 1.5f,
        val gravity: Boolean = false,
        val homing: Boolean = false
    ) : TargetingComponent()
    
    /**
     * Область (сфера/конус/линия/цилиндр).
     */
    data class Area(
        override val range: Float = 60f,
        override val requiresLineOfSight: Boolean = false,
        override val targetFilter: TargetFilter = TargetFilter.ENEMIES,
        val shape: AreaShape,
        val radius: Float = 20f,
        val centerOnCaster: Boolean = true
    ) : TargetingComponent()
    
    /**
     * Постоянная зона (entity).
     */
    data class Zone(
        override val range: Float = 60f,
        override val requiresLineOfSight: Boolean = true,
        override val targetFilter: TargetFilter = TargetFilter.ENEMIES,
        val zoneEntity: Identifier,
        val radius: Float = 10f,
        val duration: Int = 600
    ) : TargetingComponent()
}

enum class TargetFilter {
    SELF_ONLY,
    ALLIES,
    ENEMIES,
    ALL_LIVING
}

enum class AreaShape {
    SPHERE,
    CONE,
    LINE,
    CYLINDER
}
