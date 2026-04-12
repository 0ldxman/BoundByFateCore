package omc.boundbyfate.api.feature

import net.minecraft.entity.LivingEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Vec3d
import omc.boundbyfate.component.EntityStatData

/**
 * Full context passed to every feature effect and condition.
 *
 * Contains all information needed to execute any effect:
 * - Who is casting
 * - Who are the targets
 * - World state
 * - Character data
 * - Trigger-specific data (e.g. damage amount on ON_TAKE_DAMAGE)
 *
 * @property caster The entity using the feature
 * @property targets List of resolved targets (may be empty for SELF)
 * @property targetPos Optional position (for AoE targeting)
 * @property world The server world
 * @property featureId The feature being executed
 * @property casterLevel Character level of the caster
 * @property casterStats Stat data of the caster
 * @property triggerData Extra data from the trigger (damage amount, attacker, etc.)
 */
data class FeatureContext(
    val caster: LivingEntity,
    val targets: List<LivingEntity>,
    val targetPos: Vec3d?,
    val world: ServerWorld,
    val featureId: net.minecraft.util.Identifier,
    val casterLevel: Int = 1,
    val casterStats: EntityStatData? = null,
    val triggerData: Map<String, Any> = emptyMap()
) {
    /** Convenience: first target or null */
    val primaryTarget: LivingEntity? get() = targets.firstOrNull()

    /** Convenience: get trigger data value */
    @Suppress("UNCHECKED_CAST")
    fun <T> getTriggerData(key: String): T? = triggerData[key] as? T
}
