package omc.boundbyfate.system.npc.entity

import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.ai.goal.MeleeAttackGoal
import net.minecraft.entity.ai.goal.SwimGoal
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttribute
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.ai.pathing.EntityNavigation
import net.minecraft.entity.mob.PathAwareEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.world.World
import omc.boundbyfate.system.npc.navigation.NpcMoveControl
import omc.boundbyfate.system.npc.navigation.NpcPathNavigation
import omc.boundbyfate.util.extension.toIdentifier

/**
 * NPC entity for BoundByFate.
 * Yarn-compatible baseline implementation.
 */
class NpcEntity(type: EntityType<out PathAwareEntity>, world: World) : PathAwareEntity(type, world) {

    init {
        moveControl = NpcMoveControl(this)
    }

    override fun createNavigation(world: World): EntityNavigation = NpcPathNavigation(this, world)

    override fun interactMob(player: PlayerEntity, hand: Hand): ActionResult {
        if (hand == Hand.MAIN_HAND && world.isClient) {
            // TODO: Open NPC menu GUI
            return ActionResult.SUCCESS
        }
        return super.interactMob(player, hand)
    }

    override fun initGoals() {
        goalSelector.add(1, SwimGoal(this))
        goalSelector.add(2, MeleeAttackGoal(this, 1.0, false))
    }

    override fun isInvulnerable() = true
    override fun isDisallowedInPeaceful() = false
    override fun canPickUpLoot() = true
    override fun canGather(stack: ItemStack) = false

    override fun pushAway(entity: Entity) {
        super.pushAway(entity)
    }

    override fun isPushable(): Boolean = super.isPushable()

    override fun isCollidable(): Boolean = false

    override fun canImmediatelyDespawn(distanceSquared: Double) = false
    override fun isPersistent() = true

    var npcName: String
        get() = displayName.string
        set(value) {
            customName = Text.literal(value)
            isCustomNameVisible = value.isNotEmpty()
        }

    fun clearTarget() {
        target = null
    }

    fun setAttributes(attributes: Map<String, Float>) {
        attributes.forEach { (attributeName, value) ->
            val key = attributeName.toIdentifier()
            val attribute: EntityAttribute? = Registries.ATTRIBUTE.get(key)
            if (attribute != null) {
                val instance = getAttributeInstance(attribute)
                if (instance != null) {
                    instance.baseValue = value.toDouble()
                }
            }
        }
    }

    companion object {
        fun createAttributes(): DefaultAttributeContainer.Builder {
            return LivingEntity.createLivingAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2)
                .add(EntityAttributes.GENERIC_ARMOR, 0.0)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 0.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.0)
                .add(EntityAttributes.GENERIC_ATTACK_SPEED, 4.0)
                .add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 0.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 128.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.0)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.4)
                .add(EntityAttributes.GENERIC_LUCK, 0.0)
        }
    }
}
