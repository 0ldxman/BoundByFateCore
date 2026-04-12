package omc.boundbyfate.system.ability.effect

import net.minecraft.block.Blocks
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import omc.boundbyfate.api.ability.AbilityContext
import omc.boundbyfate.api.ability.AbilityEffect
import org.slf4j.LoggerFactory
import kotlin.math.ceil

/**
 * Эффект поджигания блоков.
 * 
 * Поджигает блоки в радиусе от точки применения.
 * Используется для Fireball, Wall of Fire и подобных заклинаний.
 * 
 * JSON параметры:
 * ```json
 * {
 *   "type": "boundbyfate-core:set_blocks_on_fire",
 *   "radius": 4,
 *   "duration": 100,
 *   "onlyFlammable": true,
 *   "spreadFire": false
 * }
 * ```
 */
class SetBlocksOnFireEffect(
    /** Радиус поджигания в блоках */
    val radius: Float = 5f,
    
    /** Длительность горения в тиках */
    val duration: Int = 100,
    
    /** Поджигать только горючие блоки */
    val onlyFlammable: Boolean = true,
    
    /** Может ли огонь распространяться */
    val spreadFire: Boolean = false
) : AbilityEffect {
    
    override val type = Identifier("boundbyfate-core", "set_blocks_on_fire")
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    override fun apply(context: AbilityContext): Boolean {
        // Определяем центр поджигания
        val center = context.targetPos ?: context.getTarget()?.pos ?: context.caster.pos
        val centerBlockPos = BlockPos.ofFloored(center)
        
        val world = context.world
        val radiusInt = ceil(radius).toInt()
        var blocksSet = 0
        
        // Проходим по всем блокам в радиусе
        for (x in -radiusInt..radiusInt) {
            for (y in -radiusInt..radiusInt) {
                for (z in -radiusInt..radiusInt) {
                    val pos = centerBlockPos.add(x, y, z)
                    
                    // Проверяем дистанцию
                    val distance = center.distanceTo(pos.toCenterPos())
                    if (distance > radius) continue
                    
                    val blockState = world.getBlockState(pos)
                    val abovePos = pos.up()
                    val aboveState = world.getBlockState(abovePos)
                    
                    // Проверяем, можно ли поставить огонь сверху
                    if (!aboveState.isAir) continue
                    
                    // Проверяем, горючий ли блок
                    if (onlyFlammable) {
                        val flammable = blockState.isBurnable
                        if (!flammable) continue
                    }
                    
                    // Ставим огонь
                    world.setBlockState(abovePos, Blocks.FIRE.defaultState)
                    blocksSet++
                    
                    // TODO: Добавить таймер для автоматического тушения через duration тиков
                }
            }
        }
        
        if (blocksSet > 0) {
            logger.debug("SetBlocksOnFireEffect: set $blocksSet blocks on fire")
            return true
        }
        
        return false
    }
}
