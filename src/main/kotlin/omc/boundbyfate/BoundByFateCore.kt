package omc.boundbyfate

import net.fabricmc.api.ModInitializer
import omc.boundbyfate.component.core.BbfComponents
import omc.boundbyfate.event.core.BbfEvents
import omc.boundbyfate.registry.core.RegistryManager
import omc.boundbyfate.system.core.BuiltinSystems
import omc.boundbyfate.system.core.SystemRegistry
import org.slf4j.LoggerFactory

/**
 * Главный класс мода BoundByFate Core.
 *
 * Отвечает за инициализацию всех систем в правильном порядке:
 * 1. Регистры (Registries) — загрузчики JSON датапаков
 * 2. Компоненты (Attachments) — регистрация entity компонентов
 * 3. Системы (Systems) — через [SystemRegistry] с топологической сортировкой
 *
 * ## Добавление новой системы
 *
 * Создай `object MySystem : BbfSystem` и добавь в [BuiltinSystems.registerAll].
 * Порядок инициализации определяется [BbfSystem.dependencies], не позицией в списке.
 */
class BoundByFateCore : ModInitializer {

    companion object {
        const val MOD_ID = "boundbyfate-core"
        private val logger = LoggerFactory.getLogger(BoundByFateCore::class.java)
    }

    override fun onInitialize() {
        logger.info("=".repeat(50))
        logger.info("Initializing BoundByFate Core...")
        logger.info("=".repeat(50))

        initializeRegistries()
        initializeComponents()
        initializeSystems()

        logger.info("=".repeat(50))
        logger.info("BoundByFate Core initialized successfully!")
        logger.info("=".repeat(50))

        printStatistics()
    }

    // ── Этап 1: Регистры ──────────────────────────────────────────────────

    private fun initializeRegistries() {
        logger.info("Initializing registries...")

        RegistryManager.registerRegistry(omc.boundbyfate.registry.StatRegistry)

        omc.boundbyfate.config.ConfigManager.registerDatapackLoader(
            omc.boundbyfate.config.loader.StatConfigLoader,
            "bbf_stat"
        )
        omc.boundbyfate.config.ConfigManager.registerDatapackLoader(
            omc.boundbyfate.config.loader.RaceConfigLoader,
            "bbf_race"
        )
        omc.boundbyfate.config.ConfigManager.registerDatapackLoader(
            omc.boundbyfate.config.loader.StatusConfigLoader,
            "bbf_status"
        )

        net.fabricmc.fabric.api.resource.ResourceManagerHelper
            .get(net.minecraft.resource.ResourceType.SERVER_DATA)
            .registerReloadListener(omc.boundbyfate.config.loader.AbilityConfigLoader)

        net.fabricmc.fabric.api.resource.ResourceManagerHelper
            .get(net.minecraft.resource.ResourceType.SERVER_DATA)
            .registerReloadListener(omc.boundbyfate.config.loader.AlignmentConfigLoader)

        net.fabricmc.fabric.api.resource.ResourceManagerHelper
            .get(net.minecraft.resource.ResourceType.SERVER_DATA)
            .registerReloadListener(omc.boundbyfate.config.loader.ItemConfigLoader)

        RegistryManager.finalizeRegistration()
        BbfEvents.Lifecycle.REGISTRIES_INITIALIZED.invoker().onRegistriesInitialized()

        logger.info("Registries initialized")
    }

    // ── Этап 2: Компоненты ────────────────────────────────────────────────

    private fun initializeComponents() {
        logger.info("Initializing components...")
        BbfEvents.Lifecycle.COMPONENTS_INITIALIZED.invoker().onComponentsInitialized()
        logger.info("Components initialized")
    }

    // ── Этап 3: Системы ───────────────────────────────────────────────────

    private fun initializeSystems() {
        logger.info("Initializing systems...")

        // Регистрируем все встроенные системы
        BuiltinSystems.registerAll()

        // Инициализируем в порядке зависимостей
        SystemRegistry.initialize()

        logger.info("Systems initialized")
    }

    // ── Статистика ────────────────────────────────────────────────────────

    private fun printStatistics() {
        logger.info("")
        logger.info("=== Initialization Statistics ===")
        RegistryManager.printStatistics()
        BbfComponents.printStatistics()
        SystemRegistry.printStatistics()
        logger.info("=================================")
        logger.info("")
    }
}
