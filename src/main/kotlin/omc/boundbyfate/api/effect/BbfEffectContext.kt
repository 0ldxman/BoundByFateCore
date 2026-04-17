package omc.boundbyfate.api.effect

import net.minecraft.entity.LivingEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import omc.boundbyfate.component.EntityStatData

/**
 * Universal context passed to every effect, regardless of source (Feature or Ability).
 *
 * Contains all information needed to execute any effect:
 * - Who is the source (caster/owner)
 * - Who are the targets
 * - World state
 * - Character data
 * - Event-specific data (damage amount on hit, attacker, etc.)
 * - Ability-specific data (upcast level, charge level)
 *
 * @property source The entity that owns/uses the feature or ability
 * @property targets Resolved list of targets (may be empty for self-only)
 * @property targetPos Optional position (for AoE effects)
 * @property world The server world
 * @property sourceId ID of the feature or ability being executed
 * @property sourceLevel Character level of the source entity
 * @property sourceStats Stat data of the source entity
 * @property eventData Extra data from the triggering event (e.g. "damage" -> 10f on ON_HIT)
 * @property upcastLevel Spell upcast level (null if not a spell or not upcast)
 * @property chargeLevel Charge level 0.0–1.0 for charged abilities
 * @property extra Mutable bag for passing data between effects in the same execution
 */
data class BbfEffectContext(
    val source: LivingEntity,
    val targets: List<LivingEntity>,
    val targetPos: Vec3d?,
    val world: ServerWorld,
    val sourceId: Identifier,
    val sourceLevel: Int = 1,
    val sourceStats: EntityStatData? = null,
    val eventData: Map<String, Any> = emptyMap(),
    val upcastLevel: Int? = null,
    val chargeLevel: Float = 1.0f,
    val extra: MutableMap<String, Any> = mutableMapOf()
) {
    /** First target or null */
    val primaryTarget: LivingEntity? get() = targets.firstOrNull()

    /** Whether there are any targets */
    val hasTargets: Boolean get() = targets.isNotEmpty()

    /** Read typed event data */
    @Suppress("UNCHECKED_CAST")
    fun <T> getEvent(key: String): T? = eventData[key] as? T

    /** Read typed extra data */
    @Suppress("UNCHECKED_CAST")
    fun <T> getExtra(key: String): T? = extra[key] as? T

    /** Write extra data (for passing values between effects) */
    fun putExtra(key: String, value: Any) { extra[key] = value }
}
