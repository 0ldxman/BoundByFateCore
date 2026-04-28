package omc.boundbyfate.system.npc.entity

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import omc.boundbyfate.system.npc.navigation.NpcMoveControl
import omc.boundbyfate.system.npc.navigation.NpcPathNavigation
import omc.boundbyfate.util.rl

/**
 * NPC entity for BoundByFate.
 * Adapted from HollowEngine's NpcEntity.
 * 
 * TODO: Register this entity type in a mod entity registry.
 */
class NpcEntity(type: EntityType<NpcEntity>, world: Level) : PathfinderMob(type, world) {

    init {
        moveControl = NpcMoveControl(this)
        setCanPickUpLoot(true)
    }

    val goals get() = goalSelector

    override fun createNavigation(pLevel: Level) = NpcPathNavigation(pLevel, this)

    override fun mobInteract(pPlayer: Player, pHand: InteractionHand): InteractionResult {
        if (pHand == InteractionHand.MAIN_HAND && level().isClientSide) {
            // TODO: Open NPC menu GUI
            return InteractionResult.SUCCESS
        }
        return super.mobInteract(pPlayer, pHand)
    }

    override fun registerGoals() {
        goalSelector.addGoal(1, FloatGoal(this))
        goalSelector.addGoal(1, MeleeAttackGoal(this, 1.0, false))
    }

    override fun isInvulnerable() = true
    override fun shouldDespawnInPeaceful() = false
    override fun canPickUpLoot() = true
    override fun wantsToPickUp(pStack: ItemStack) = false

    override fun doPush(pEntity: Entity) {
        super.doPush(pEntity)
    }

    override fun isPushable(): Boolean = super.isPushable()

    override fun canBeCollidedWith(): Boolean = false

    override fun aiStep() {
        updateSwingTime()
        super.aiStep()
    }

    override fun removeWhenFarAway(dist: Double) = false
    override fun isPersistenceRequired() = true

    val pickupDistance get() = pickupReach

    var npcName: String
        get() = displayName?.string ?: ""
        set(value) {
            customName = Component.literal(value)
            isCustomNameVisible = value.isNotEmpty()
        }

    fun clearTarget() {
        target = null
    }

    fun setAttributes(attributes: Map<String, Float>) {
        attributes.forEach { (attributeName, value) ->
            BuiltInRegistries.ATTRIBUTE.getHolder(attributeName.rl).orElseThrow()
                ?.let { attribute ->
                    this.attributes.getInstance(attribute)?.let { instance ->
                        instance.baseValue = value.toDouble()
                    }
                }
        }
    }

    companion object {
        fun createAttributes(): AttributeSupplier.Builder {
            return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ARMOR, 0.0)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.ATTACK_SPEED, 4.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0)
                .add(Attributes.FOLLOW_RANGE, 128.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0)
                .add(Attributes.FLYING_SPEED, 0.4)
                .add(Attributes.LUCK, 0.0)
        }
    }
}
