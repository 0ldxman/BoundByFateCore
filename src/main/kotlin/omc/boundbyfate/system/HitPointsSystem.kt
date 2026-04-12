package omc.boundbyfate.system

import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.api.charclass.ClassDefinition
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfStats
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Manages player maximum HP based on class, level and CON modifier.
 *
 * Formula:
 *   HP = hitDie + CON_modifier                          // level 1
 *      + (hpPerLevel + CON_modifier) * (level - 1)     // each subsequent level
 *
 * This completely replaces the default Minecraft 20 HP.
 * The base attribute value is set to 1 (minimum), and we apply
 * a flat ADDITION modifier with the full D&D HP value.
 *
 * Commoner (no class): hitDie = 4, hpPerLevel = 3
 */
object HitPointsSystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")
    private val HP_MODIFIER_UUID = UUID.fromString("bbf00001-0000-0000-0000-000000000001")
    private const val HP_MODIFIER_NAME = "BoundByFate D&D HP"

    // Commoner defaults (no class assigned)
    const val COMMONER_HIT_DIE = 4
    const val COMMONER_HP_PER_LEVEL = 3

    /**
     * Calculates and applies D&D HP to a player.
     *
     * @param player The player to update
     * @param classDef The player's class definition (null = commoner)
     * @param level The player's class level
     */
    fun applyHitPoints(player: ServerPlayerEntity, classDef: ClassDefinition?, level: Int) {
        val hitDie = classDef?.hitDie ?: COMMONER_HIT_DIE
        val hpPerLevel = classDef?.hpPerLevel ?: COMMONER_HP_PER_LEVEL

        // Get CON modifier
        val statsData = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
        val conModifier = statsData?.getStatValue(BbfStats.CONSTITUTION.id)?.dndModifier ?: 0

        val totalHp = calculateHp(hitDie, hpPerLevel, conModifier, level)

        setMaxHp(player, totalHp)

        logger.debug(
            "HP for ${player.name.string}: hitDie=$hitDie hpPerLevel=$hpPerLevel " +
            "CON=$conModifier level=$level → $totalHp HP"
        )
    }

    /**
     * Calculates total HP.
     *
     * Level 1: hitDie + CON_modifier
     * Each level after: hpPerLevel + CON_modifier
     */
    fun calculateHp(hitDie: Int, hpPerLevel: Int, conModifier: Int, level: Int): Int {
        val clampedLevel = level.coerceIn(1, 20)
        val firstLevel = hitDie + conModifier
        val subsequentLevels = (hpPerLevel + conModifier) * (clampedLevel - 1)
        return (firstLevel + subsequentLevels).coerceAtLeast(1)
    }

    /**
     * Sets the player's max HP by overriding the base attribute value.
     * Removes the default 20 HP and replaces with D&D value.
     */
    private fun setMaxHp(player: ServerPlayerEntity, totalHp: Int) {
        val attribute = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH) ?: return

        // Remove old BbF modifier if present
        attribute.getModifier(HP_MODIFIER_UUID)?.let { attribute.removeModifier(it) }

        // Set base value to 1 (minimum allowed), add our modifier on top
        // This effectively makes total HP = 1 + (totalHp - 1) = totalHp
        attribute.baseValue = 1.0

        val modifier = EntityAttributeModifier(
            HP_MODIFIER_UUID,
            HP_MODIFIER_NAME,
            (totalHp - 1).toDouble(),
            EntityAttributeModifier.Operation.ADDITION
        )
        attribute.addPersistentModifier(modifier)

        // Clamp current health to new max
        if (player.health > player.maxHealth) {
            player.health = player.maxHealth
        }
    }
}
