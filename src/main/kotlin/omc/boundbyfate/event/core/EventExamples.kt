package omc.boundbyfate.event.core

import net.minecraft.entity.LivingEntity
import omc.boundbyfate.util.source.SourceReference
import omc.boundbyfate.util.source.SourceType
import net.minecraft.util.Identifier
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

/**
 * Примеры использования Event системы.
 * 
 * Этот файл содержит примеры для документации.
 * НЕ компилируется в production код.
 */
@Suppress("unused", "UNUSED_PARAMETER")
object EventExamples {
    
    // ========== Пример 1: Простая подписка на событие ==========
    
    fun example1_SimpleSubscription() {
        // Подписываемся на событие изменения стата
        BbfEvents.Character.STAT_CHANGED.register { entity, statId, oldValue, newValue ->
            println("Stat $statId changed from $oldValue to $newValue")
        }
    }
    
    // ========== Пример 2: Подписка с приоритетом ==========
    
    fun example2_PrioritySubscription() {
        // Высокий приоритет — выполнится первым
        BbfEvents.Character.STAT_CHANGED.register(EventPriority.HIGH) { entity, statId, oldValue, newValue ->
            println("HIGH: Validating stat change...")
        }
        
        // Нормальный приоритет — выполнится вторым
        BbfEvents.Character.STAT_CHANGED.register(EventPriority.NORMAL) { entity, statId, oldValue, newValue ->
            println("NORMAL: Processing stat change...")
        }
        
        // Низкий приоритет — выполнится последним
        BbfEvents.Character.STAT_CHANGED.register(EventPriority.LOW) { entity, statId, oldValue, newValue ->
            println("LOW: Logging stat change...")
        }
    }
    
    // ========== Пример 3: Отмена события ==========
    
    fun example3_CancelEvent() {
        // Подписываемся на событие создания персонажа
        BbfEvents.Character.BEFORE_CREATE.register(EventPriority.HIGH) { event ->
            // Проверяем имя персонажа
            if (event.characterName.contains("admin")) {
                event.cancel() // Отменяем создание
                println("Character name contains 'admin' - creation cancelled")
            }
        }
        
        // Публикуем событие
        val event = CharacterCreateEvent(player = mockPlayer(), characterName = "admin123")
        BbfEvents.Character.BEFORE_CREATE.invoke { it.onBeforeCreate(event) }
        
        if (event.isCancelled) {
            println("Character creation was cancelled")
            return
        }
        
        println("Character created successfully")
    }
    
    // ========== Пример 4: Изменение результата ==========
    
    fun example4_ModifyResult() {
        // Подписываемся на событие расчёта урона
        BbfEvents.Combat.BEFORE_DAMAGE.register(EventPriority.HIGH) { event ->
            // Увеличиваем урон на 50%
            event.result = event.result * 1.5f
            println("Damage increased: ${event.originalResult} -> ${event.result}")
        }
        
        BbfEvents.Combat.BEFORE_DAMAGE.register(EventPriority.NORMAL) { event ->
            // Уменьшаем урон на 20%
            event.result = event.result * 0.8f
            println("Damage reduced: ${event.result}")
        }
        
        // Публикуем событие
        val event = DamageEvent(
            target = mockEntity(),
            source = SourceReference.ability(Identifier.of("boundbyfate-core", "fireball")),
            amount = 10f
        )
        
        BbfEvents.Combat.BEFORE_DAMAGE.invoke { it.onBeforeDamage(event) }
        
        println("Final damage: ${event.result}") // 10 * 1.5 * 0.8 = 12
    }
    
    // ========== Пример 5: Отмена с MONITOR ==========
    
    fun example5_CancelWithMonitor() {
        // HIGH приоритет — отменяет событие
        BbfEvents.Combat.BEFORE_DAMAGE.register(EventPriority.HIGH) { event ->
            if (event.target.isInvulnerable) {
                event.cancel()
                println("Target is invulnerable - damage cancelled")
            }
        }
        
        // NORMAL приоритет — НЕ выполнится если отменено
        BbfEvents.Combat.BEFORE_DAMAGE.register(EventPriority.NORMAL) { event ->
            println("This won't execute if cancelled")
        }
        
        // MONITOR приоритет — выполнится ВСЕГДА (даже если отменено)
        BbfEvents.Combat.BEFORE_DAMAGE.register(EventPriority.MONITOR) { event ->
            println("MONITOR: Logging damage attempt (cancelled: ${event.isCancelled})")
        }
        
        // Публикуем событие
        val event = DamageEvent(
            target = mockEntity().apply { isInvulnerable = true },
            source = SourceReference.spell(Identifier.of("boundbyfate-core", "magic_missile")),
            amount = 10f
        )
        
        BbfEvents.Combat.BEFORE_DAMAGE.invokeCancellable(event) { it.onBeforeDamage(event) }
    }
    
    // ========== Пример 6: Цепочка событий ==========
    
    fun example6_EventChain() {
        // Событие 1: Изменение стата
        BbfEvents.Character.STAT_CHANGED.register { entity, statId, oldValue, newValue ->
            if (statId.toString() == "boundbyfate-core:dexterity") {
                // Триггерим пересчёт AC
                val acEvent = ACCalculationEvent(entity, armorClass = 10)
                BbfEvents.Combat.CALCULATE_AC.invoke { it.onCalculateAC(acEvent) }
            }
        }
        
        // Событие 2: Расчёт AC
        BbfEvents.Combat.CALCULATE_AC.register { event ->
            // Добавляем бонус DEX к AC
            val dex = 14 // Получаем из компонента
            event.result += (dex - 10) / 2
        }
    }
    
    // ========== Пример 7: Динамическая отписка ==========
    
    fun example7_DynamicUnsubscribe() {
        // Создаём обработчик
        val handler = StatChanged { entity, statId, oldValue, newValue ->
            println("Stat changed!")
        }
        
        // Подписываемся
        BbfEvents.Character.STAT_CHANGED.register(handler = handler)
        
        // Позже отписываемся
        BbfEvents.Character.STAT_CHANGED.unregister(handler)
    }
    
    // ========== Пример 8: Использование в системе ==========
    
    class ExampleSystem {
        fun initialize() {
            // Подписываемся на события при инициализации системы
            BbfEvents.Character.LEVEL_UP.register(EventPriority.NORMAL) { entity, oldLevel, newLevel ->
                handleLevelUp(entity, oldLevel, newLevel)
            }
            
            BbfEvents.Combat.BEFORE_DAMAGE.register(EventPriority.HIGH) { event ->
                applyDamageReduction(event)
            }
        }
        
        private fun handleLevelUp(entity: LivingEntity, oldLevel: Int, newLevel: Int) {
            println("Entity leveled up: $oldLevel -> $newLevel")
            // Логика обработки левелапа
        }
        
        private fun applyDamageReduction(event: DamageEvent) {
            // Применяем сопротивления
            event.result *= 0.5f
        }
    }
    
    // ========== Mock объекты для примеров ==========
    
    private fun mockPlayer(): ServerPlayerEntity {
        throw NotImplementedError("Mock for examples")
    }
    
    private fun mockEntity(): LivingEntity {
        throw NotImplementedError("Mock for examples")
    }
}
