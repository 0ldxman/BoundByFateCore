package omc.boundbyfate.system.core

/**
 * Интерфейс игровой системы BoundByFate.
 *
 * Каждая система реализует этот интерфейс и регистрирует себя
 * в [SystemRegistry] через `companion object` или `init`.
 *
 * ## Создание системы
 *
 * ```kotlin
 * object VisualOrchestrator : BbfSystem {
 *
 *     override val systemId = "boundbyfate-core:visual_orchestrator"
 *
 *     // Опционально — зависимости которые должны быть инициализированы раньше
 *     override val dependencies = listOf("boundbyfate-core:effects")
 *
 *     override fun register() {
 *         ServerTickEvents.END_SERVER_TICK.register { server -> tick(server) }
 *         logger.info("VisualOrchestrator registered")
 *     }
 * }
 * ```
 *
 * ## Регистрация в SystemRegistry
 *
 * ```kotlin
 * // В companion object системы:
 * init {
 *     SystemRegistry.add(VisualOrchestrator)
 * }
 * ```
 *
 * Или явно в [SystemRegistry.registerAll]:
 * ```kotlin
 * SystemRegistry.registerAll(
 *     EffectSystem,
 *     VisualOrchestrator,
 *     MusicSystem
 * )
 * ```
 */
interface BbfSystem {

    /**
     * Уникальный идентификатор системы.
     * Формат: "namespace:system_name"
     */
    val systemId: String

    /**
     * Список ID систем которые должны быть инициализированы раньше этой.
     * Используется [SystemRegistry] для сортировки порядка инициализации.
     */
    val dependencies: List<String> get() = emptyList()

    /**
     * Инициализирует систему: регистрирует события, загрузчики, обработчики.
     *
     * Вызывается ровно один раз из [SystemRegistry.initialize].
     * Не вызывай напрямую.
     */
    fun register()
}
