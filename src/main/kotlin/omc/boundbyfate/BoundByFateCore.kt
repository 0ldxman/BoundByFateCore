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
 * 1. Регистры (Registries) — регистрация реестров и загрузчиков JSON датапаков
 * 2. Компоненты (Attachments) — регистрация entity компонентов
 * 3. Системы (Systems) — через [SystemRegistry] с топологической сортировкой
 *
 * ## Добавление нового реестра
 *
 * Если реестр наследует [omc.boundbyfate.registry.core.BbfRegistry]:
 * - Добавь `RegistryManager.registerRegistry(MyRegistry)` в [initializeRegistries]
 * - Добавь `ConfigManager.registerDatapackLoader(MyLoader, "bbf_my_type")` туда же
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
        initializeCommands()

        logger.info("=".repeat(50))
        logger.info("BoundByFate Core initialized successfully!")
        logger.info("=".repeat(50))

        printStatistics()
    }

    // ── Команды ───────────────────────────────────────────────────────────

    private fun initializeCommands() {
        logger.info("Initializing commands...")
        omc.boundbyfate.command.BbfCommands.register()
        logger.info("Commands initialized")
    }

    // ── Этап 1: Регистры ──────────────────────────────────────────────────

    private fun initializeRegistries() {
        logger.info("Initializing registries...")

        // ── BbfRegistry — регистрируем в RegistryManager для finalizeRegistration() ──

        // StatRegistry — hardcoded stats (strength, dexterity, etc.)
        RegistryManager.registerRegistry(omc.boundbyfate.registry.StatRegistry)

        // RaceRegistry — переопределяет onRegistrationComplete() для валидации подрас
        RegistryManager.registerRegistry(omc.boundbyfate.registry.RaceRegistry)

        // ClassRegistry — классы и подклассы
        RegistryManager.registerRegistry(omc.boundbyfate.registry.ClassRegistry)

        // FeatureRegistry — особенности классов (гранты: эффекты, способности, механики)
        RegistryManager.registerRegistry(omc.boundbyfate.registry.FeatureRegistry)

        // ResourceRegistry — именованные счётчики с правилами восстановления
        RegistryManager.registerRegistry(omc.boundbyfate.registry.ResourceRegistry)

        // ── Загрузчики датапаков (BbfRegistry через DatapackLoader) ──────────────────

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
        omc.boundbyfate.config.ConfigManager.registerDatapackLoader(
            omc.boundbyfate.config.loader.ClassConfigLoader,
            "bbf_class"
        )
        omc.boundbyfate.config.ConfigManager.registerDatapackLoader(
            omc.boundbyfate.config.loader.FeatureConfigLoader,
            "bbf_feature"
        )
        omc.boundbyfate.config.ConfigManager.registerDatapackLoader(
            omc.boundbyfate.config.loader.ResourceConfigLoader,
            "bbf_resource"
        )

        // ── Загрузчики датапаков (BbfDualRegistry через DualDatapackLoader) ──────────

        omc.boundbyfate.config.ConfigManager.registerLoader(
            omc.boundbyfate.config.loader.DualDatapackLoader(
                registry = omc.boundbyfate.registry.AbilityRegistry,
                codec = omc.boundbyfate.api.ability.AbilityDefinition.CODEC,
                directory = "bbf_ability",
                onAfterLoad = { _, _ ->
                    val missing = omc.boundbyfate.registry.AbilityRegistry.getAllHandlers()
                        .filter { !omc.boundbyfate.registry.AbilityRegistry.hasDefinition(it.id) }
                    if (missing.isNotEmpty()) {
                        org.slf4j.LoggerFactory.getLogger("AbilityLoader")
                            .warn("Ability handlers without definitions: ${missing.map { it.id }}")
                    }
                }
            )
        )
        omc.boundbyfate.config.ConfigManager.registerLoader(
            omc.boundbyfate.config.loader.DualDatapackLoader(
                registry = omc.boundbyfate.registry.EffectRegistry,
                codec = omc.boundbyfate.api.effect.EffectDefinition.CODEC,
                directory = "bbf_effect"
            )
        )
        omc.boundbyfate.config.ConfigManager.registerLoader(
            omc.boundbyfate.config.loader.DualDatapackLoader(
                registry = omc.boundbyfate.registry.ItemPropertyRegistry,
                codec = omc.boundbyfate.api.item.ItemDefinition.CODEC,
                directory = "bbf_item"
            )
        )
        omc.boundbyfate.config.ConfigManager.registerLoader(
            omc.boundbyfate.config.loader.DualDatapackLoader(
                registry = omc.boundbyfate.registry.MechanicRegistry,
                codec = omc.boundbyfate.api.mechanic.MechanicDefinition.CODEC,
                directory = "bbf_mechanic"
            )
        )

        // ── Нестандартные загрузчики ──────────────────────────────────────────────────

        omc.boundbyfate.config.ConfigManager.registerLoader(
            omc.boundbyfate.config.loader.AlignmentConfigLoader
        )

        RegistryManager.finalizeRegistration()
        BbfEvents.Lifecycle.REGISTRIES_INITIALIZED.invoker().onRegistriesInitialized()

        logger.info("Registries initialized")
    }

    // ── Этап 2: Компоненты ────────────────────────────────────────────────

    private fun initializeComponents() {
        logger.info("Initializing components...")

        // Регистрируем все встроенные компоненты здесь, на этапе 2,
        // чтобы событие COMPONENTS_INITIALIZED стреляло после реальной регистрации.
        omc.boundbyfate.component.BbfBuiltinComponents

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
