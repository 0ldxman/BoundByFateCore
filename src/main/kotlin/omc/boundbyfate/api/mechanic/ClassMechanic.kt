package omc.boundbyfate.api.mechanic

import com.google.gson.JsonObject
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

/**
 * Базовый интерфейс для механик класса.
 *
 * Каждая механика — это `object` реализующий [ClassMechanic].
 * Механика содержит логику которая влияет на геймплей персонажа.
 *
 * ## Создание механики
 *
 * ```kotlin
 * object SpellcastingMechanic : ClassMechanic {
 *     override val id = Identifier("boundbyfate-core", "spellcasting")
 *
 *     override fun onActivate(player: ServerPlayerEntity, config: MechanicConfig) {
 *         val stat = config.getString("stat") ?: "intelligence"
 *         val type = config.getString("type") ?: "full"
 *         
 *         // Инициализация системы заклинаний
 *         val spellcasting = SpellcastingData(stat, type)
 *         player.setSpellcastingData(spellcasting)
 *     }
 *
 *     override fun onDeactivate(player: ServerPlayerEntity) {
 *         player.removeSpellcastingData()
 *     }
 * }
 * ```
 *
 * ## Жизненный цикл
 *
 * ```
 * onActivate()     — механика активируется (Feature даёт Mechanic grant)
 * onLevelUp()      — персонаж получает уровень класса
 * onTick()         — каждый тик (опционально)
 * onDeactivate()   — механика деактивируется (смена класса, смерть)
 * ```
 *
 * ## Взаимодействие с событиями
 *
 * Механики могут подписываться на события через EventBus:
 * - AbilityEvents (для модификации способностей)
 * - CombatEvents (для модификации боя)
 * - RestEvents (для восстановления ресурсов)
 *
 * ## Примеры механик
 *
 * - **Spellcasting** — базовая система магии (слоты, DC, атака)
 * - **Wizard Spellbook** — книга заклинаний волшебника
 * - **Metamagic** — метамагия чародея
 * - **Rage** — ярость варвара
 * - **Sneak Attack** — скрытая атака плута
 * - **Ki** — система ки монаха
 */
interface ClassMechanic {
    
    /**
     * Уникальный идентификатор механики.
     * Должен совпадать с `handler` в MechanicDefinition.
     */
    val id: Identifier
    
    /**
     * Вызывается при активации механики.
     *
     * Здесь происходит инициализация:
     * - Создание данных в WorldData
     * - Подписка на события
     * - Применение начальных эффектов
     *
     * @param player игрок у которого активируется механика
     * @param config конфигурация (из Feature grant или default_config)
     */
    fun onActivate(player: ServerPlayerEntity, config: MechanicConfig)
    
    /**
     * Вызывается при деактивации механики.
     *
     * Здесь происходит очистка:
     * - Удаление данных из WorldData
     * - Отписка от событий
     * - Снятие эффектов
     *
     * @param player игрок у которого деактивируется механика
     */
    fun onDeactivate(player: ServerPlayerEntity)
    
    /**
     * Вызывается при получении уровня класса.
     *
     * Опциональный хук для механик которые масштабируются с уровнем.
     * Примеры: увеличение слотов заклинаний, урона Sneak Attack.
     *
     * @param player игрок
     * @param newLevel новый уровень класса
     */
    fun onLevelUp(player: ServerPlayerEntity, newLevel: Int) {}
    
    /**
     * Вызывается каждый тик.
     *
     * Опциональный хук для механик с постоянной логикой.
     * Используй осторожно — вызывается 20 раз в секунду!
     *
     * @param player игрок
     */
    fun onTick(player: ServerPlayerEntity) {}
    
    /**
     * Проверяет зависимости механики.
     *
     * По умолчанию проверяет что все зависимости из Definition активны.
     * Переопредели для кастомной логики.
     *
     * @param player игрок
     * @param definition определение механики
     * @return true если зависимости выполнены
     */
    fun checkDependencies(player: ServerPlayerEntity, definition: MechanicDefinition): Boolean {
        // Проверяем что все зависимости активны
        for (depId in definition.dependencies) {
            if (!player.hasMechanic(depId)) {
                return false
            }
        }
        return true
    }
}

/**
 * Extension для проверки наличия механики у игрока.
 */
fun ServerPlayerEntity.hasMechanic(mechanicId: Identifier): Boolean {
    return omc.boundbyfate.system.mechanic.ClassMechanicManager.hasMechanic(this, mechanicId)
}

