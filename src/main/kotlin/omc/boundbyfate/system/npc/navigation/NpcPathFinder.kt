package omc.boundbyfate.system.npc.navigation

import net.minecraft.entity.ai.pathing.PathNodeNavigator

/**
 * Thin wrapper around Yarn path navigator for NPC tuning.
 */
class NpcPathFinder(nodeMaker: NpcNodeEvaluator, range: Int = 640) : PathNodeNavigator(nodeMaker, range)
