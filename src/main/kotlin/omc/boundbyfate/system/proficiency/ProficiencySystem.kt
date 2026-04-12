package omc.boundbyfate.system.proficiency

import net.minecraft.block.Block
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.api.proficiency.PenaltyContext
import omc.boundbyfate.api.proficiency.ProficiencyDefinition
import omc.boundbyfate.component.EntityProficiencyData
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.PenaltyEffectRegistry
import omc.boundbyfate.registry.ProficiencyRegistry
import omc.boundbyfate.system.proficiency.penalty.ActivePenaltyTracker
import org.slf4j.LoggerFactory

/**
 * Core system for proficiency checks and penalty application.
 *
 * Item coverage is determined solely by item tags defined in
 * data/<namespace>/tags/items/proficiency/<name>.json
 * Each leaf ProficiencyDefinition has an [itemTag] pointing to that tag.
 *
 * Hierarchy: parent proficiency includes children.
 * Having "martial_weapons" grants "swords", "axes_weapon", etc.
 */
object ProficiencySystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    // ── Proficiency checks ────────────────────────────────────────────────────

    fun hasProficiency(player: ServerPlayerEntity, proficiencyId: Identifier): Boolean {
        val data = player.getAttachedOrElse(BbfAttachments.ENTITY_PROFICIENCIES, EntityProficiencyData())
        if (data.has(proficiencyId)) return true
        // Check via parent container proficiencies
        return ProficiencyRegistry.getAll().any { parent ->
            data.has(parent.id) && proficiencyId in parent.includes
        }
    }

    fun getEffectiveProficiencies(player: ServerPlayerEntity): Set<Identifier> {
        val data = player.getAttachedOrElse(BbfAttachments.ENTITY_PROFICIENCIES, EntityProficiencyData())
        val result = data.proficiencies.toMutableSet()
        for (profId in data.proficiencies) {
            ProficiencyRegistry.get(profId)?.includes?.let { result.addAll(it) }
        }
        return result
    }

    // ── Item penalties ────────────────────────────────────────────────────────

    /**
     * Finds the leaf proficiency that covers this item via its itemTag.
     * Returns null if no proficiency covers this item.
     */
    fun findItemProficiency(item: ItemStack): ProficiencyDefinition? {
        if (item.isEmpty) return null
        return ProficiencyRegistry.getAll().firstOrNull { prof ->
            prof.itemTag != null && item.isIn(prof.itemTag)
        }
    }

    /**
     * Returns display names of all proficiency categories this item belongs to,
     * derived from ProficiencyRegistry. Includes both leaf and container matches.
     * Filters out containers if a more specific leaf already matched.
     */
    fun getProficiencyCategoriesForItem(item: ItemStack): List<String> {
        if (item.isEmpty) return emptyList()

        // Find all leaf proficiencies that cover this item
        val leafMatches = ProficiencyRegistry.getAll()
            .filter { it.isLeaf && it.itemTag != null && item.isIn(it.itemTag) }
            .map { it.id }
            .toSet()

        if (leafMatches.isEmpty()) return emptyList()

        val result = mutableListOf<String>()

        // Add leaf names
        leafMatches.forEach { leafId ->
            ProficiencyRegistry.get(leafId)?.let { result.add(it.displayName) }
        }

        // Add container names only if ALL their children that cover this item are matched
        ProficiencyRegistry.getAll()
            .filter { it.isContainer }
            .forEach { container ->
                val relevantChildren = container.includes.filter { childId ->
                    ProficiencyRegistry.get(childId)?.let { child ->
                        child.itemTag != null && item.isIn(child.itemTag)
                    } == true
                }
                if (relevantChildren.isNotEmpty() && relevantChildren.all { it in leafMatches }) {
                    result.add(container.displayName)
                }
            }

        return result
    }

    /**
     * Applies penalties for holding an item without proficiency.
     */
    fun applyItemPenalties(player: ServerPlayerEntity, item: ItemStack) {
        val prof = findItemProficiency(item)

        if (prof == null) {
            ActivePenaltyTracker.clearAll(player)
            return
        }

        if (hasProficiency(player, prof.id)) {
            ActivePenaltyTracker.clearAll(player)
            return
        }

        val penalty = prof.penalty ?: return
        val effect = PenaltyEffectRegistry.create(penalty.type, penalty.params) ?: run {
            logger.warn("Unknown penalty type: ${penalty.type}")
            return
        }

        effect.apply(PenaltyContext(player, prof.id, prof.displayName))
    }

    // ── Block interaction ─────────────────────────────────────────────────────

    /**
     * Returns the proficiency definition if this block requires proficiency and player lacks it.
     * Returns null if allowed.
     */
    fun getBlockedProficiency(player: ServerPlayerEntity, block: Block): ProficiencyDefinition? {
        val blockId = Registries.BLOCK.getId(block)

        for (prof in ProficiencyRegistry.getAll()) {
            if (!prof.isLeaf) continue

            val coversBlock = blockId in prof.blocks ||
                prof.blockTags.any { tagId ->
                    block.defaultState.isIn(TagKey.of(RegistryKeys.BLOCK, tagId))
                }

            if (!coversBlock) continue
            if (hasProficiency(player, prof.id)) return null

            if (prof.penalty?.type == Identifier("boundbyfate-core", "block_interaction")) {
                return prof
            }
        }

        return null
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
