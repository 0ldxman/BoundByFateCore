package omc.boundbyfate.system.npc.navigation

import net.minecraft.block.DoorBlock
import net.minecraft.entity.ai.pathing.MobNavigation
import net.minecraft.entity.ai.pathing.PathNodeNavigator
import net.minecraft.entity.ai.pathing.PathNodeType
import net.minecraft.entity.mob.MobEntity
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

/**
 * Yarn-compatible NPC path navigation with basic door handling.
 */
class NpcPathNavigation(mob: MobEntity, world: World) : MobNavigation(mob, world) {

    override fun createPathNodeNavigator(range: Int): PathNodeNavigator {
        val maker = NpcNodeEvaluator()
        nodeMaker = maker
        return NpcPathFinder(maker, range)
    }

    override fun tick() {
        super.tick()
        val path = currentPath ?: return
        if (path.isFinished) return

        val node = path.currentNode
        if (node.type == PathNodeType.WALKABLE_DOOR) {
            val pos = node.blockPos
            val state = world.getBlockState(pos)
            if (state.block is DoorBlock) {
                world.setBlockState(pos, state.with(DoorBlock.OPEN, true))
            }
        }
    }

    fun setVelocity(velocity: Vec3d) {
        entity.velocity = velocity
    }
}
