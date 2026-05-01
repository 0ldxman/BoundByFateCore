package omc.boundbyfate.api.mechanic

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

/**
 * Базовый интерфейс для механик персонажа.
 *
 * Механика — это сложная система с собственной логикой и состоянием.
 * Может быть получена из любого источника: класса, расы, черты, предмета.
 *
 * Каждая механика — это `object` реализующий [Mechanic].
 *
 * ## Создание механики
 *
 * ```kotlin
 * object SpellcastingMechanic : Mechanic {
 *     override val id = Identifier("boundbyfate-core", "spellcasting")
 *
 *     override fun onActivate(player: ServerPlayerEntity, config: MechanicConfig) {
 *         val stat = config.getString("stat") ?: "intelligence"
 *         val type = config.getString("type") ?: "full"
 *         // Инициализация системы заклинаний
 *     }
 *
 *     override fun onDeactivate(player: ServerPlayerEntity) {
 *         // Очистка
 *     }
 *
 *     override fun onLevelUp(player: ServerPlayerEntity, newLevel: Int) {
 *         // Пересчитать слоты заклинаний для нового уровня персонажа
 *     }
 * }
 * ```
 *
 * ## Жизненный цикл
 *
 * ```
 * onActivate()   — механика активируется (Feature даёт Mechanic grant)
 * onLevelUp()    — персонаж получает уровень (любой источник — класс, раса, черта)
 * onTick()       — каждый тик (опционально, используй осторожно)
 * onDeactivate() — механика деактивируется (смена класса/расы, снятие черты)
 * ```
 *
 * ## Взаимодействие с событиями
 *
 * Механики могут подписываться на события через EventBus:
 * - `AbilityEvents` — модификация способностей
 * - `CombatEvents` — модификация боя
 * - `RestEvents` — восстановление ресурсов
 *
 * ## Примеры механик
 *
 * - **Spellcasting** — базовая система магии (слоты, DC, атака)
 * - **WizardSpellbook** — книга заклинаний волшебника
 * - **Metamagic** — метамагия чародея
 * - **Rage** — ярость варвара
 * - **SneakAttack** — скрытая атака плута
 * - **Ki** — система ки монаха
 */
interface Mechanic {

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
     * Вызывается при повышении уровня персонажа.
     *
     * Опциональный хук для механик которые масштабируются с уровнем.
     * Вызывается при любом лвл-апе независимо от источника механики.
     *
     * Примеры: пересчёт слотов заклинаний, урона Sneak Attack, очков Ki.
     *
     * @param player игрок
     * @param newLevel новый уровень персонажа
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
        for (depId in definition.dependencies) {
            if (!player.hasMechanic(depId)) return false
        }
        return true
    }
}

/**
 * Extension для проверки наличия механики у игрока.
 */
fun ServerPlayerEntity.hasMechanic(mechanicId: Identifier): Boolean =
    omc.boundbyfate.system.mechanic.MechanicManager.hasMechanic(this, mechanicId)
