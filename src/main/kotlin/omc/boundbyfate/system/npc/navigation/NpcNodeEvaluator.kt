package omc.boundbyfate.system.npc.navigation

import net.minecraft.entity.ai.pathing.LandPathNodeMaker

/**
 * Custom node evaluator for NPC pathing on Yarn mappings.
 */
class NpcNodeEvaluator : LandPathNodeMaker() {
    init {
        canEnterOpenDoors = true
        canOpenDoors = true
        canSwim = true
    }
}
