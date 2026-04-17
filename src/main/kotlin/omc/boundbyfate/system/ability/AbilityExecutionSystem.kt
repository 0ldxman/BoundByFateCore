package omc.boundbyfate.system.ability

import net.minecraft.entity.LivingEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import omc.boundbyfate.api.ability.AbilityDefinition
import omc.boundbyfate.api.ability.AbilityPhase
import omc.boundbyfate.api.ability.component.MetadataComponent
import omc.boundbyfate.api.ability.component.ScalingComponent
import omc.boundbyfate.api.ability.component.TargetingComponent
import omc.boundbyfate.api.effect.BbfEffectContext
import omc.boundbyfate.network.ServerPacketHandler
import omc.boundbyfate.registry.BbfAttachments
import org.slf4j.LoggerFactory

/**
 * Executes abilities using BbfEffectContext.
 */
object AbilityExecutionSystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    fun execute(
        caster: LivingEntity,
        ability: AbilityDefinition,
        target: LivingEntity? = null,
        targetPos: Vec3d? = null,
        upcastLevel: Int? = null
    ): Boolean {
        if (caster.world !is ServerWorld) return false
        val world = caster.world as ServerWorld

        val casterStats = caster.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
        val casterLevel = if (caster is ServerPlayerEntity) {
            caster.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, null)?.level ?: 1
        } else 1

        val targets = resolveTargets(caster, ability.targeting, target, targetPos, world)

        val context = BbfEffectContext(
            source = caster,
            targets = targets,
            targetPos = targetPos,
            world = world,
            sourceId = ability.id,
            sourceLevel = casterLevel,
            sourceStats = casterStats,
            upcastLevel = upcastLevel
        )

        applyScaling(context, ability)
        performSavingThrows(context, ability)

        // Execute all phases
        for (phase in AbilityPhase.entries) {
            if (!ability.executePhase(context)) {
                logger.debug("Ability ${ability.id} failed at phase $phase")
                return false
            }
        }

        handleConcentration(caster, ability)

        if (caster is ServerPlayerEntity) {
            ServerPacketHandler.broadcastAbilityCast(caster, ability.id, world)
        }

        logger.debug("${caster.name?.string} executed ${ability.id}")
        return true
    }

    private fun resolveTargets(
        caster: LivingEntity,
        targeting: TargetingComponent,
        primaryTarget: LivingEntity?,
        targetPos: Vec3d?,
        world: ServerWorld
    ): List<LivingEntity> {
        return when (targeting) {
            is TargetingComponent.Self -> listOf(caster)
            is TargetingComponent.SingleTarget -> if (primaryTarget != null) listOf(primaryTarget) else emptyList()
            is TargetingComponent.Projectile -> emptyList()
            is TargetingComponent.Area -> {
                if (targetPos == null) emptyList()
                else {
                    val r = targeting.radius.toDouble()
                    val box = Box.of(targetPos, r * 2, r * 2, r * 2)
                    world.getEntitiesByClass(LivingEntity::class.java, box) { it != caster && it.pos.distanceTo(targetPos) <= targeting.radius }
                }
            }
            is TargetingComponent.Zone -> emptyList()
        }
    }

    private fun applyScaling(context: BbfEffectContext, ability: AbilityDefinition) {
        for (scaling in ability.scaling) {
            when (scaling) {
                is ScalingComponent.Upcast -> {
                    val upcastLevel = context.upcastLevel ?: continue
                    val baseLevel = ability.getMetadata<MetadataComponent.Spell>()?.level ?: 1
                    val levelsAbove = upcastLevel - baseLevel
                    if (levelsAbove > 0) {
                        context.putExtra("upcast_levels", levelsAbove)
                        context.putExtra("upcast_dice_per_level", scaling.dicePerLevel)
                    }
                }
                is ScalingComponent.CharacterLevel -> {
                    var tier = 0
                    for (threshold in scaling.scaleAt) { if (context.sourceLevel >= threshold) tier++ }
                    context.putExtra("character_level_scaling", tier * scaling.dicePerTier)
                }
            }
        }
    }

    private fun performSavingThrows(context: BbfEffectContext, ability: AbilityDefinition) {
        val savingThrow = ability.getMetadata<MetadataComponent.SavingThrow>() ?: return
        val dc = SavingThrowSystem.calculateSpellSaveDC(context.source, context.sourceStats)
        for (target in context.targets) {
            val success = SavingThrowSystem.makeSave(target, savingThrow.ability, dc)
            context.putExtra("save_${target.uuid}", success)
        }
    }

    private fun handleConcentration(caster: LivingEntity, ability: AbilityDefinition) {
        val spell = ability.getMetadata<MetadataComponent.Spell>() ?: return
        if (spell.concentration && caster is ServerPlayerEntity) {
            ConcentrationSystem.startConcentration(caster, ability.id)
        }
    }
}
