package omc.boundbyfate.api.feature

/**
 * Defines how targets are selected for a feature.
 */
sealed class TargetingMode {
    /** Only affects the caster */
    object Self : TargetingMode()

    /** Single entity target (ally or enemy based on TargetFilter) */
    object SingleTarget : TargetingMode()

    /** Sphere around the caster */
    data class Sphere(val radius: Float) : TargetingMode()

    /** Sphere around the selected target position */
    data class TargetedSphere(val radius: Float) : TargetingMode()

    /** Cylinder around the caster */
    data class Cylinder(val radius: Float, val height: Float) : TargetingMode()

    /** Cone in the direction the caster is facing */
    data class Cone(val length: Float, val angleDegrees: Float) : TargetingMode()

    /** Line in the direction the caster is facing */
    data class Line(val length: Float, val width: Float) : TargetingMode()
}

/**
 * Filters which entities are valid targets.
 */
enum class TargetFilter {
    ALL,
    ENEMIES,
    ALLIES,
    SELF_ONLY,
    LIVING
}
