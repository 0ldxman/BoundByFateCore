package omc.boundbyfate.api.damage

import net.minecraft.entity.damage.DamageSource
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.world.World
import net.minecraft.entity.damage.DamageType
import omc.boundbyfate.registry.BbfDamageTypes

/**
 * Utility for creating BoundByFate damage sources.
 *
 * Usage:
 * ```kotlin
 * // Simple damage
 * entity.damage(BbfDamage.of(world, BbfDamageTypes.NECROTIC), 5.0f)
 *
 * // Damage with attacker
 * entity.damage(BbfDamage.of(world, BbfDamageTypes.RADIANT, attacker), 8.0f)
 * ```
 */
object BbfDamage {

    /**
     * Creates a DamageSource for the given D&D damage type.
     *
     * @param world The world (needed to access the damage type registry)
     * @param key The damage type registry key (from [BbfDamageTypes])
     * @param attacker Optional entity that caused the damage
     * @return DamageSource ready to use with entity.damage()
     */
    fun of(
        world: World,
        key: RegistryKey<DamageType>,
        attacker: net.minecraft.entity.Entity? = null
    ): DamageSource {
        val entry = world.registryManager
            .get(RegistryKeys.DAMAGE_TYPE)
            .getOrThrow(key)

        return if (attacker != null) {
            DamageSource(entry, attacker)
        } else {
            DamageSource(entry)
        }
    }
}
