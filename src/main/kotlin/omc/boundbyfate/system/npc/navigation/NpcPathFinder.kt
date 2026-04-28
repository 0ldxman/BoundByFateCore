package omc.boundbyfate.system.npc.navigation

import net.minecraft.world.level.pathfinder.Node
import net.minecraft.world.level.pathfinder.PathFinder

class NpcPathFinder(evaluator: NpcNodeEvaluator) : PathFinder(evaluator, 640) {
    init {
        neighbors = arrayOfNulls<Node>(72)
    }
}
