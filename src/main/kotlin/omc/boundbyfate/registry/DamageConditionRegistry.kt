package omc.boundbyfate.registry

import com.google.gson.JsonObject
import net.minecraft.util.Identifier
import omc.boundbyfate.api.combat.DamageCondition
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for bonus damage condition factories.
 *
 * Each condition is identified by a namespaced ID and created via a factory
 * that receives optional parameters from the item's NBT.
 *
 * Register a built-in condition:
 * ```kotlin
 * DamageConditionRegistry.register(Identifier("boundbyfate-core", "undead")) { _ ->
 *     DamageCondition { _, target -> target is UndeadEntity }
 * }
 * ```
 *
 * Register a parameterized condition:
 * ```kotlin
 * DamageConditionRegistry.register(Identifier("boundbyfate-core", "target_has_status")) { params ->
 *     val statusId = Identifier(params.get("statusId").asString)
 *     DamageCondition { _, target ->
 *         target.getAttachedOrElse(BbfAttachments.ENTITY_FEATURES, null)?.hasStatus(statusId) == true
 *     }
 * }
 * ```
 *
 * NBT usage on item:
 * ```
 * BbfBonusDamage: [{
 *   dice: "1d6",
 *   type: "boundbyfate-core:radiant",
 *   condition: "boundbyfate-core:undead"
 * }]
 * ```
 *
 * With parameters:
 * ```
 * BbfBonusDamage: [{
 *   dice: "1d4",
 *   type: "boundbyfate-core:fire",
 *   condition: "boundbyfate-core:target_has_status",
 *   conditionParams: {statusId: "boundbyfate-core:burning"}
 * }]
 * ```
 */
object DamageConditionRegistry {
    private val factories = ConcurrentHashMap<Identifier, (JsonObject) -> DamageCondition>()

    fun register(id: Identifier, factory: (JsonObject) -> DamageCondition) {
        factories[id] = factory
    }

    fun create(id: Identifier, params: JsonObject = JsonObject()): DamageCondition? =
        factories[id]?.invoke(params)

    fun contains(id: Identifier): Boolean = factories.containsKey(id)

    /** Returns display label for a condition ID, used in tooltips. */
    fun getLabel(id: Identifier): String = labelOverrides[id] ?: "vs ${id.path}"

    private val labelOverrides = mutableMapOf<Identifier, String>()

    /** Register a human-readable label for a condition (used in tooltips). */
    fun registerLabel(id: Identifier, label: String) {
        labelOverrides[id] = label
    }
}
