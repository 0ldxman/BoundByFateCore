package omc.boundbyfate.system.core

import org.slf4j.LoggerFactory

/**
 * Реестр игровых систем BoundByFate.
 *
 * Системы регистрируются через [add], затем инициализируются в правильном
 * порядке (с учётом зависимостей) через [initialize].
 *
 * ## Использование
 *
 * Каждая система добавляет себя при загрузке класса:
 * ```kotlin
 * object VisualOrchestrator : BbfSystem {
 *     init { SystemRegistry.add(this) }
 *     ...
 * }
 * ```
 *
 * Или все системы регистрируются централизованно:
 * ```kotlin
 * SystemRegistry.registerAll(
 *     BbfEffects,
 *     BbfConditionTypes,
 *     BbfAbilities,
 *     OrganizationSystem,
 *     ComponentSyncSystem,
 *     WorldDataSyncSystem,
 *     VisualOrchestrator,
 *     FileTransferSystem,
 *     MusicSystem,
 *     NpcSystem
 * )
 * ```
 *
 * Затем в [omc.boundbyfate.BoundByFateCore]:
 * ```kotlin
 * SystemRegistry.initialize()
 * ```
 *
 * ## Порядок инициализации
 *
 * Системы сортируются топологически по [BbfSystem.dependencies].
 * Если зависимость не зарегистрирована — предупреждение в лог, система
 * инициализируется в порядке добавления.
 *
 * ## Повторная инициализация
 *
 * [initialize] можно вызвать только один раз. Повторный вызов — ошибка.
 */
object SystemRegistry {

    private val logger = LoggerFactory.getLogger(SystemRegistry::class.java)

    private val systems = mutableListOf<BbfSystem>()
    private var initialized = false

    /**
     * Добавляет систему в реестр.
     *
     * Порядок добавления влияет на порядок инициализации при равных зависимостях.
     *
     * @throws IllegalStateException если реестр уже инициализирован
     */
    fun add(system: BbfSystem) {
        check(!initialized) {
            "Cannot add system '${system.systemId}' — SystemRegistry is already initialized"
        }
        if (systems.any { it.systemId == system.systemId }) {
            logger.warn("System '${system.systemId}' is already registered, skipping")
            return
        }
        systems += system
        logger.debug("Registered system: ${system.systemId}")
    }

    /**
     * Добавляет несколько систем сразу.
     */
    fun registerAll(vararg systems: BbfSystem) {
        for (system in systems) add(system)
    }

    /**
     * Инициализирует все зарегистрированные системы в правильном порядке.
     *
     * Порядок определяется топологической сортировкой по [BbfSystem.dependencies].
     *
     * @throws IllegalStateException если уже инициализирован
     */
    fun initialize() {
        check(!initialized) { "SystemRegistry is already initialized" }
        initialized = true

        val sorted = topologicalSort(systems)

        logger.info("Initializing ${sorted.size} systems...")

        for (system in sorted) {
            try {
                logger.debug("  Initializing: ${system.systemId}")
                system.register()
            } catch (e: Exception) {
                logger.error("Failed to initialize system '${system.systemId}'", e)
                throw e
            }
        }

        logger.info("All systems initialized")
    }

    /**
     * Возвращает все зарегистрированные системы (для диагностики).
     */
    fun all(): List<BbfSystem> = systems.toList()

    /**
     * Выводит статистику.
     */
    fun printStatistics() {
        logger.info("=== SystemRegistry ===")
        logger.info("  Total systems: ${systems.size}")
        logger.info("  Initialized: $initialized")
        systems.forEach { sys ->
            val deps = if (sys.dependencies.isEmpty()) "" else " (deps: ${sys.dependencies.joinToString()})"
            logger.info("  - ${sys.systemId}$deps")
        }
        logger.info("======================")
    }

    // ── Топологическая сортировка ─────────────────────────────────────────

    /**
     * Сортирует системы топологически по зависимостям.
     *
     * Алгоритм Кана (BFS). Если есть циклические зависимости — предупреждение,
     * оставшиеся системы добавляются в исходном порядке.
     *
     * Зависимости на незарегистрированные системы — предупреждение, игнорируются.
     */
    private fun topologicalSort(input: List<BbfSystem>): List<BbfSystem> {
        val byId = input.associateBy { it.systemId }

        // Считаем in-degree (количество зависимостей которые ещё не обработаны)
        val inDegree = input.associate { it.systemId to 0 }.toMutableMap()
        val dependents = mutableMapOf<String, MutableList<String>>() // id → кто зависит от него

        for (system in input) {
            for (dep in system.dependencies) {
                if (dep !in byId) {
                    logger.warn(
                        "System '${system.systemId}' depends on '${dep}' which is not registered"
                    )
                    continue
                }
                inDegree[system.systemId] = (inDegree[system.systemId] ?: 0) + 1
                dependents.getOrPut(dep) { mutableListOf() } += system.systemId
            }
        }

        // BFS: начинаем с систем без зависимостей
        val queue = ArrayDeque<BbfSystem>()
        // Сохраняем исходный порядок для детерминизма
        for (system in input) {
            if ((inDegree[system.systemId] ?: 0) == 0) queue += system
        }

        val result = mutableListOf<BbfSystem>()

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            result += current

            for (dependentId in dependents[current.systemId] ?: emptyList()) {
                val newDegree = (inDegree[dependentId] ?: 0) - 1
                inDegree[dependentId] = newDegree
                if (newDegree == 0) {
                    byId[dependentId]?.let { queue += it }
                }
            }
        }

        // Если не все системы попали в результат — есть цикл
        if (result.size < input.size) {
            val remaining = input.filter { it !in result }
            logger.warn(
                "Circular dependency detected among systems: ${remaining.map { it.systemId }}. " +
                "Appending in registration order."
            )
            result += remaining
        }

        return result
    }
}
