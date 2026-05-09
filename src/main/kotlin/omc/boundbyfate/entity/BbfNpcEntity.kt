package omc.boundbyfate.entity

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
import omc.boundbyfate.component.components.EntityAppearanceData
import omc.boundbyfate.component.core.getOrCreate
import omc.boundbyfate.system.npc.navigation.NpcMoveControl
import omc.boundbyfate.system.npc.navigation.NpcPathNavigation
import omc.boundbyfate.util.extension.toIdentifier

/**
 * Новая сущность NPC для BoundByFate, чистая от Kool.
 * Автоматически использует EntityAppearanceData для прокси-рендеринга.
 */
class BbfNpcEntity(type: EntityType<out PathAwareEntity>, world: World) : PathAwareEntity(type, world) {

    init {
        moveControl = NpcMoveControl(this)
    }

    override fun createNavigation(world: World): EntityNavigation = NpcPathNavigation(this, world)

    override fun initialize(
        world: net.minecraft.world.ServerWorldAccess,
        difficulty: net.minecraft.world.LocalDifficulty,
        spawnReason: net.minecraft.entity.SpawnReason,
        entityData: net.minecraft.entity.EntityData?,
        entityNbt: net.minecraft.nbt.NbtCompound?
    ): net.minecraft.entity.EntityData? {
        // Автоматически создаем компонент внешности при спавне
        getOrCreate(EntityAppearanceData.TYPE)
        return super.initialize(world, difficulty, spawnReason, entityData, entityNbt)
    }

    override fun interactMob(player: PlayerEntity, hand: Hand): ActionResult {
        if (hand == Hand.MAIN_HAND && world.isClient) {
            // TODO: Открыть меню NPC
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

    // Эти методы обеспечивают сохранение сущности в мире
    override fun canImmediatelyDespawn(distanceSquared: Double) = false
    override fun isPersistent() = true
    override fun cannotDespawn() = true

    override fun writeCustomDataToNbt(nbt: net.minecraft.nbt.NbtCompound) {
        super.writeCustomDataToNbt(nbt)
        // Компоненты сохраняются автоматически через систему BbfComponent
    }

    override fun readCustomDataFromNbt(nbt: net.minecraft.nbt.NbtCompound) {
        super.readCustomDataFromNbt(nbt)
    }

    var npcName: String
        get() = displayName.string
        set(value) {
            customName = Text.literal(value)
            isCustomNameVisible = value.isNotEmpty()
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
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 128.0)
        }
    }
}
