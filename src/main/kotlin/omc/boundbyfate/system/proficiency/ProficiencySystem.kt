package omc.boundbyfate.system.proficiency

import net.minecraft.block.Block
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.TagKey
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.api.proficiency.PenaltyContext
import omc.boundbyfate.api.proficiency.ProficiencyEntry
import omc.boundbyfate.component.EntityProficiencyData
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.PenaltyEffectRegistry
import omc.boundbyfate.registry.ProficiencyRegistry
import omc.boundbyfate.system.proficiency.penalty.ActivePenaltyTracker
import org.slf4j.LoggerFactory

/**
 * Core system for proficiency checks and penalty application.
 *
 * Hierarchy resolution:
 * - Player has "martial_weapons" → automatically has "swords", "axes", etc.
 * - Player has "swords" → only has swords, not other martial weapons
 *
 * Usage:
 * ```kotlin
 * // Check proficiency
 * ProficiencySystem.hasProficiency(player, Identifier("boundbyfate-core", "swords"))
 *
 * // Apply penalties for held item
 * ProficiencySystem.applyItemPenalties(player, player.mainHandStack)
 *
 * // Check if block interaction is blocked
 * ProficiencySystem.isBlockBlocked(player, block) // returns entry display name or null
 * ```
 */
object ProficiencySystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    // ── Proficiency checks ────────────────────────────────────────────────────

    /**
     * Returns true if the player has the given proficiency (directly or via parent).
     */
    fun hasProficiency(player: ServerPlayerEntity, proficiencyId: Identifier): Boolean {
        val data = player.getAttachedOrElse(BbfAttachments.ENTITY_PROFICIENCIES, EntityProficiencyData())

        // Direct check
        if (data.has(proficiencyId)) return true

        // Check via parent proficiencies
        return ProficiencyRegistry.getAll().any { parent ->
            data.has(parent.id) && proficiencyId in parent.includes
        }
    }

    /**
     * Returns all effective proficiency IDs for a player (direct + inherited).
     */
    fun getEffectiveProficiencies(player: ServerPlayerEntity): Set<Identifier> {
        val data = player.getAttachedOrElse(BbfAttachments.ENTITY_PROFICIENCIES, EntityProficiencyData())
        val result = data.proficiencies.toMutableSet()

        // Add all children from container proficiencies
        for (profId in data.proficiencies) {
            val prof = ProficiencyRegistry.get(profId) ?: continue
            result.addAll(prof.includes)
        }

        return result
    }

    // ── Item penalties ────────────────────────────────────────────────────────

    /**
     * Finds the proficiency entry that covers this item, if any.
     * Returns null if no proficiency covers this item.
     */
    fun findItemEntry(item: ItemStack): Pair<Identifier, ProficiencyEntry>? {
        if (item.isEmpty) return null
        val itemId = Registries.ITEM.getId(item.item)

        for (prof in ProficiencyRegistry.getAll()) {
            for (entry in prof.entries) {
                if (itemId in entry.items) return prof.id to entry
                if (entry.itemTags.any { tagId ->
                    item.isIn(TagKey.of(net.minecraft.registry.RegistryKeys.ITEM, tagId))
                }) return prof.id to entry
            }
        }
        return null
    }

    /**
     * Applies penalties for holding an item without proficiency.
     * Clears penalties if player has proficiency or item has none.
     */
    fun applyItemPenalties(player: ServerPlayerEntity, item: ItemStack) {
        val found = findItemEntry(item)

        if (found == null) {
            // No proficiency required for this item
            ActivePenaltyTracker.clearAll(player)
            return
        }

        val (profId, entry) = found

        if (hasProficiency(player, profId)) {
            // Player has proficiency - no penalty
            ActivePenaltyTracker.clearAll(player)
            return
        }

        // Apply penalty
        val effect = PenaltyEffectRegistry.create(entry.penalty.type, entry.penalty.params) ?: run {
            logger.warn("Unknown penalty type: ${entry.penalty.type}")
            return
        }

        effect.apply(PenaltyContext(player, profId, entry.displayName))
    }

    // ── Block interaction ─────────────────────────────────────────────────────

    /**
     * Checks if a block interaction should be blocked.
     * Returns the entry display name (for the message) if blocked, null if allowed.
     */
    fun getBlockedEntry(player: ServerPlayerEntity, block: Block): ProficiencyEntry? {
        val blockId = Registries.BLOCK.getId(block)

        for (prof in ProficiencyRegistry.getAll()) {
            for (entry in prof.entries) {
                val coversBlock = blockId in entry.blocks ||
                    entry.blockTags.any { tagId ->
                        block.defaultState.isIn(TagKey.of(net.minecraft.registry.RegistryKeys.BLOCK, tagId))
                    }

                if (!coversBlock) continue

                // Block is covered - check if player has proficiency
                if (hasProficiency(player, prof.id)) return null // allowed

                // Check penalty type - only BLOCK type actually blocks
                if (entry.penalty.type == Identifier("boundbyfate-core", "block_interaction")) {
                    return entry
                }
            }
        }

        return null // not covered or not blocked
    }

    // ── Mutation ──────────────────────────────────────────────────────────────

    fun addProficiency(player: ServerPlayerEntity, proficiencyId: Identifier) {
        val data = player.getAttachedOrElse(BbfAttachments.ENTITY_PROFICIENCIES, EntityProficiencyData())
        player.setAttached(BbfAttachments.ENTITY_PROFICIENCIES, data.with(proficiencyId))
    }

    fun removeProficiency(player: ServerPlayerEntity, proficiencyId: Identifier) {
        val data = player.getAttachedOrElse(BbfAttachments.ENTITY_PROFICIENCIES, null) ?: return
        player.setAttached(BbfAttachments.ENTITY_PROFICIENCIES, data.without(proficiencyId))
    }
}
