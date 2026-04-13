package omc.boundbyfate.system.ability

import net.minecraft.entity.LivingEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import omc.boundbyfate.api.ability.AbilityDefinition
import omc.boundbyfate.api.ability.component.ActivationComponent
import omc.boundbyfate.api.ability.component.CostComponent
import omc.boundbyfate.component.AbilityActivationState
import omc.boundbyfate.component.ActivationType
import omc.boundbyfate.registry.AbilityRegistry
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.system.resource.ResourceSystem
import org.slf4j.LoggerFactory

/**
 * Система активации способностей.
 * 
 * Управляет процессом активации:
 * - Проверка условий
 * - Создание состояния активации
 * - Отслеживание прогресса
 * - Прерывание
 * - Завершение
 */
object AbilityActivationSystem {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")
    
    /**
     * Начинает активацию способности.
     * 
     * @param caster Кастер способности
     * @param ability Определение способности
     * @param target Цель (опционально)
     * @param targetPos Позиция цели (опционально)
     * @param upcastLevel Уровень апкаста (опционально)
     * @return Результат начала активации
     */
    fun beginActivation(
        caster: ServerPlayerEntity,
        ability: AbilityDefinition,
        target: LivingEntity? = null,
        targetPos: Vec3d? = null,
        upcastLevel: Int? = null
    ): ActivationResult {
        // Проверка, не активирует ли уже другую способность
        val existingState = caster.getAttachedOrElse(BbfAttachments.ABILITY_ACTIVATION, null)
        if (existingState != null) {
            logger.debug("${caster.name.string} is already activating ${existingState.abilityId}")
            return ActivationResult.ALREADY_ACTIVATING
        }
        
        // Проверка глобальных условий
        for (condition in ability.conditions) {
            // TODO: Проверить условия
        }
        
        // Проверка ресурсов (но не тратим пока)
        val costs = calculateCost(ability, upcastLevel)
        if (costs.isNotEmpty()) {
            if (!canAffordCost(caster, costs)) {
                logger.debug("${caster.name.string} cannot afford ${ability.id}")
                return ActivationResult.INSUFFICIENT_RESOURCES
            }
        }
        
        // Создаём состояние активации
        val activationType = when (ability.activation) {
            is ActivationComponent.Instant -> ActivationType.INSTANT
            is ActivationComponent.Channeled -> ActivationType.CHANNELED
            is ActivationComponent.Charged -> ActivationType.CHARGED
            is ActivationComponent.Ritual -> ActivationType.RITUAL
        }
        
        val state = AbilityActivationState(
            abilityId = ability.id,
            caster = caster.uuid,
            target = target?.uuid,
            targetPos = targetPos,
            upcastLevel = upcastLevel,
            startTick = caster.world.time,
            preparationTime = ability.activation.preparationTime,
            activationType = activationType
        )
        
        caster.setAttached(BbfAttachments.ABILITY_ACTIVATION, state)
        
        // Мгновенная активация без preparation time
        if (activationType == ActivationType.INSTANT && ability.activation.preparationTime == 0) {
            return completeActivation(caster, ability, state)
        }
        
        logger.debug("${caster.name.string} began activating ${ability.id}")
        return ActivationResult.PREPARING
    }
    
    /**
     * Тикает активные активации.
     * Вызывается каждый тик для каждого игрока.
     */
    fun tick(player: ServerPlayerEntity) {
        val state = player.getAttachedOrElse(BbfAttachments.ABILITY_ACTIVATION, null)
            ?: return
        
        val ability = AbilityRegistry.get(state.abilityId)
        if (ability == null) {
            logger.error("Unknown ability ${state.abilityId} in activation state")
            cancelActivation(player, "Unknown ability")
            return
        }
        
        val currentTick = player.world.time
        
        // Проверка прерывания
        if (ability.activation.canBeInterrupted) {
            if (shouldInterrupt(player, ability.activation)) {
                cancelActivation(player, "Interrupted")
                return
            }
        }
        
        // Обработка по типу активации
        when (state.activationType) {
            ActivationType.INSTANT -> {
                // Ждём окончания preparation time
                if (state.isComplete(currentTick)) {
                    completeActivation(player, ability, state)
                }
            }
            
            ActivationType.CHANNELED -> {
                // Обновляем визуальные эффекты канала
                // TODO: Визуальные эффекты
                
                // Проверяем, отпустил ли игрок кнопку
                // TODO: Проверка через клиентский пакет
            }
            
            ActivationType.CHARGED -> {
                // Обновляем визуальные эффекты зарядки
                val activation = ability.activation as ActivationComponent.Charged
                val chargeLevel = state.getChargeLevel(
                    currentTick,
                    activation.minChargeTicks,
                    activation.maxChargeTicks
                )
                
                // TODO: Визуальные эффекты зарядки
                
                // Проверяем, отпустил ли игрок кнопку
                // TODO: Проверка через клиентский пакет
            }
            
            ActivationType.RITUAL -> {
                // Обновляем прогресс-бар
                val progress = state.getProgress(currentTick)
                
                // TODO: Отправить прогресс клиенту
                
                if (state.isComplete(currentTick)) {
                    completeActivation(player, ability, state)
                }
            }
        }
    }
    
    /**
     * Завершает активацию способности.
     */
    fun completeActivation(
        player: ServerPlayerEntity,
        ability: AbilityDefinition,
        state: AbilityActivationState
    ): ActivationResult {
        // Расход ресурсов
        val costs = calculateCost(ability, state.upcastLevel)
        if (costs.isNotEmpty()) {
            if (!spendCost(player, costs)) {
                cancelActivation(player, "Failed to spend resources")
                return ActivationResult.INSUFFICIENT_RESOURCES
            }
        }
        
        // Получаем цель
        val target = state.target?.let { uuid ->
            (player.world as net.minecraft.server.world.ServerWorld).getEntity(uuid) as? net.minecraft.entity.LivingEntity
        }
        
        // Выполняем способность через AbilityExecutionSystem
        val success = AbilityExecutionSystem.execute(
            player,
            ability,
            target,
            state.targetPos,
            state.upcastLevel
        )
        
        // Очищаем состояние активации
        player.setAttached(BbfAttachments.ABILITY_ACTIVATION, null)
        
        if (success) {
            logger.debug("${player.name.string} completed ${ability.id}")
            return ActivationResult.SUCCESS
        } else {
            // Возвращаем ресурсы при неудаче
            if (costs.isNotEmpty()) {
                refundCost(player, costs)
            }
            return ActivationResult.FAILED
        }
    }
    
    /**
     * Отменяет активацию способности.
     */
    fun cancelActivation(player: ServerPlayerEntity, reason: String) {
        val state = player.getAttachedOrElse(BbfAttachments.ABILITY_ACTIVATION, null)
            ?: return
        
        player.setAttached(BbfAttachments.ABILITY_ACTIVATION, null)
        logger.debug("${player.name.string} cancelled ${state.abilityId}: $reason")
    }
    
    // ═══ PRIVATE HELPERS ═══
    
    private fun shouldInterrupt(player: ServerPlayerEntity, activation: ActivationComponent): Boolean {
        when (activation) {
            is ActivationComponent.Channeled -> {
                if (activation.interruptOnMove && player.velocity.lengthSquared() > 0.01) {
                    return true
                }
                // TODO: Implement interruptOnDamage when damage tracking is added
            }
            is ActivationComponent.Ritual -> {
                if (activation.requiresStanding && player.velocity.lengthSquared() > 0.01) {
                    return true
                }
            }
            else -> {}
        }
        return false
    }
    
    private fun calculateCost(ability: AbilityDefinition, upcastLevel: Int?): List<AbilityCost> {
        val costs = mutableListOf<AbilityCost>()
        
        for (costComponent in ability.costs) {
            when (costComponent) {
                is CostComponent.Resource -> {
                    costs.add(AbilityCost.ResourceCost(costComponent.resourceId, costComponent.amount))
                }
                is CostComponent.SpellSlot -> {
                    val slotLevel = upcastLevel ?: costComponent.level
                    val slotId = Identifier("boundbyfate-core", "spell_slot_$slotLevel")
                    costs.add(AbilityCost.ResourceCost(slotId, 1))
                }
                is CostComponent.Health -> {
                    costs.add(AbilityCost.HealthCost(costComponent.amount, costComponent.percentage, costComponent.canKill))
                }
                is CostComponent.Cooldown -> {
                    costs.add(AbilityCost.CooldownCost(ability.id, costComponent.ticks))
                }
                is CostComponent.MaterialComponents -> {
                    // Material components checked separately
                    costs.add(AbilityCost.MaterialCost(costComponent.items, costComponent.consumed))
                }
            }
        }
        
        return costs
    }
    
    private fun canAffordCost(player: ServerPlayerEntity, costs: List<AbilityCost>): Boolean {
        for (cost in costs) {
            when (cost) {
                is AbilityCost.ResourceCost -> {
                    if (!ResourceSystem.canSpend(player, cost.resourceId, cost.amount)) {
                        return false
                    }
                }
                is AbilityCost.HealthCost -> {
                    val currentHealth = player.health
                    val maxHealth = player.maxHealth
                    val healthCost = if (cost.percentage > 0) {
                        maxHealth * cost.percentage
                    } else {
                        cost.amount.toFloat()
                    }
                    
                    if (!cost.canKill && currentHealth <= healthCost) {
                        return false
                    }
                    if (currentHealth < healthCost) {
                        return false
                    }
                }
                is AbilityCost.CooldownCost -> {
                    // TODO: Implement cooldown checking if needed for homebrew
                    // Currently not used - resources and preparationTime handle this
                }
                is AbilityCost.MaterialCost -> {
                    if (cost.items.isNotEmpty()) {
                        if (!hasRequiredItems(player, cost.items)) {
                            return false
                        }
                    }
                }
            }
        }
        return true
    }
    
    private fun spendCost(player: ServerPlayerEntity, costs: List<AbilityCost>): Boolean {
        for (cost in costs) {
            when (cost) {
                is AbilityCost.ResourceCost -> {
                    if (!ResourceSystem.spend(player, cost.resourceId, cost.amount)) {
                        return false
                    }
                }
                is AbilityCost.HealthCost -> {
                    val maxHealth = player.maxHealth
                    val healthCost = if (cost.percentage > 0) {
                        maxHealth * cost.percentage
                    } else {
                        cost.amount.toFloat()
                    }
                    player.health = (player.health - healthCost).coerceAtLeast(if (cost.canKill) 0f else 1f)
                }
                is AbilityCost.CooldownCost -> {
                    // TODO: Implement cooldown starting if needed for homebrew
                    // Currently not used - resources and preparationTime handle this
                }
                is AbilityCost.MaterialCost -> {
                    if (cost.consumed && cost.items.isNotEmpty()) {
                        consumeItems(player, cost.items)
                    }
                }
            }
        }
        return true
    }
    
    private fun refundCost(player: ServerPlayerEntity, costs: List<AbilityCost>) {
        for (cost in costs) {
            when (cost) {
                is AbilityCost.ResourceCost -> {
                    ResourceSystem.restore(player, cost.resourceId, cost.amount)
                }
                is AbilityCost.HealthCost -> {
                    val maxHealth = player.maxHealth
                    val healthCost = if (cost.percentage > 0) {
                        maxHealth * cost.percentage
                    } else {
                        cost.amount.toFloat()
                    }
                    player.health = (player.health + healthCost).coerceAtMost(maxHealth)
                }
                is AbilityCost.CooldownCost -> {
                    // Cooldowns are not refunded
                }
                is AbilityCost.MaterialCost -> {
                    // Materials are not refunded
                }
            }
        }
    }
    
    /**
     * Проверяет наличие требуемых предметов в инвентаре.
     */
    private fun hasRequiredItems(player: ServerPlayerEntity, items: List<CostComponent.ItemRequirement>): Boolean {
        for (requirement in items) {
            var found = 0
            
            for (slot in 0 until player.inventory.size()) {
                val stack = player.inventory.getStack(slot)
                if (stack.isEmpty) continue
                
                val matches = if (requirement.itemId != null) {
                    stack.isOf(net.minecraft.registry.Registries.ITEM.get(requirement.itemId))
                } else {
                    stack.isIn(net.minecraft.registry.tag.TagKey.of(
                        net.minecraft.registry.RegistryKeys.ITEM,
                        requirement.itemTag!!
                    ))
                }
                
                if (matches) {
                    found += stack.count
                    if (found >= requirement.count) break
                }
            }
            
            if (found < requirement.count) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * Потребляет требуемые предметы из инвентаря.
     */
    private fun consumeItems(player: ServerPlayerEntity, items: List<CostComponent.ItemRequirement>) {
        for (requirement in items) {
            var remaining = requirement.count
            
            for (slot in 0 until player.inventory.size()) {
                if (remaining <= 0) break
                
                val stack = player.inventory.getStack(slot)
                if (stack.isEmpty) continue
                
                val matches = if (requirement.itemId != null) {
                    stack.isOf(net.minecraft.registry.Registries.ITEM.get(requirement.itemId))
                } else {
                    stack.isIn(net.minecraft.registry.tag.TagKey.of(
                        net.minecraft.registry.RegistryKeys.ITEM,
                        requirement.itemTag!!
                    ))
                }
                
                if (matches) {
                    val toRemove = minOf(remaining, stack.count)
                    stack.decrement(toRemove)
                    remaining -= toRemove
                }
            }
        }
    }
}

/**
 * Результат активации способности.
 */
enum class ActivationResult {
    /** Активация началась, идёт подготовка */
    PREPARING,
    
    /** Активация успешно завершена */
    SUCCESS,
    
    /** Активация провалилась */
    FAILED,
    
    /** Недостаточно ресурсов */
    INSUFFICIENT_RESOURCES,
    
    /** Уже активирует другую способность */
    ALREADY_ACTIVATING,
    
    /** Неизвестная способность */
    UNKNOWN_ABILITY
}

/**
 * Стоимость способности.
 */
sealed class AbilityCost {
    data class ResourceCost(val resourceId: Identifier, val amount: Int) : AbilityCost()
    data class HealthCost(val amount: Int, val percentage: Float, val canKill: Boolean) : AbilityCost()
    data class CooldownCost(val abilityId: Identifier, val ticks: Int) : AbilityCost()
    data class MaterialCost(val items: List<CostComponent.ItemRequirement>, val consumed: Boolean) : AbilityCost()
}
