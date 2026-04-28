package omc.boundbyfate

import net.fabricmc.api.ModInitializer
import omc.boundbyfate.event.core.BbfEvents
import omc.boundbyfate.registry.core.RegistryManager
import omc.boundbyfate.component.core.BbfComponents
import org.slf4j.LoggerFactory

/**
 * Главный класс мода BoundByFate Core.
 * 
 * Отвечает за инициализацию всех систем в правильном порядке:
 * 1. Регистры (Registries)
 * 2. Компоненты (Attachments)
 * 3. События (Events)
 * 4. Системы (Systems)
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
        
        // Этап 1: Инициализация регистров
        initializeRegistries()
        
        // Этап 2: Инициализация компонентов
        initializeComponents()
        
        // Этап 3: Инициализация событий
        initializeEvents()
        
        // Этап 4: Инициализация систем
        initializeSystems()
        
        logger.info("=".repeat(50))
        logger.info("BoundByFate Core initialized successfully!")
        logger.info("=".repeat(50))
        
        // Вывод статистики
        printStatistics()
    }
    
    /**
     * Инициализирует все регистры.
     */
    private fun initializeRegistries() {
        logger.info("Initializing registries...")
        
        // Регистрация регистров
        RegistryManager.registerRegistry(omc.boundbyfate.registry.StatRegistry)
        
        // Регистрация загрузчиков конфигураций
        omc.boundbyfate.config.ConfigManager.registerDatapackLoader(
            omc.boundbyfate.config.loader.StatConfigLoader,
            "bbf_stat"
        )
        omc.boundbyfate.config.ConfigManager.registerDatapackLoader(
            omc.boundbyfate.config.loader.RaceConfigLoader,
            "bbf_race"
        )

        // Регистрация загрузчика состояний
        omc.boundbyfate.config.ConfigManager.registerDatapackLoader(
            omc.boundbyfate.config.loader.StatusConfigLoader,
            "bbf_status"
        )

        // Регистрация загрузчика способностей
        net.fabricmc.fabric.api.resource.ResourceManagerHelper
            .get(net.minecraft.resource.ResourceType.SERVER_DATA)
            .registerReloadListener(omc.boundbyfate.config.loader.AbilityConfigLoader)

        // Регистрация загрузчика конфига мировоззрений
        net.fabricmc.fabric.api.resource.ResourceManagerHelper
            .get(net.minecraft.resource.ResourceType.SERVER_DATA)
            .registerReloadListener(omc.boundbyfate.config.loader.AlignmentConfigLoader)
        
        // Финализация регистрации
        RegistryManager.finalizeRegistration()
        
        // Публикация события
        BbfEvents.Lifecycle.REGISTRIES_INITIALIZED.invoker().onRegistriesInitialized()
        
        logger.info("Registries initialized")
    }
    
    /**
     * Инициализирует все компоненты.
     */
    private fun initializeComponents() {
        logger.info("Initializing components...")
        
        // Публикация события
        BbfEvents.Lifecycle.COMPONENTS_INITIALIZED.invoker().onComponentsInitialized()

        logger.info("Components initialized")
    }
    
    /**
     * Инициализирует систему событий.
     */
    private fun initializeEvents() {
        logger.info("Initializing events...")
        
        // TODO: Регистрация обработчиков событий
        // ServerLifecycleEvents.SERVER_STARTED.register { ... }
        // PlayerEvent.PLAYER_JOIN.register { ... }
        
        logger.info("Events initialized")
    }
    
    /**
     * Инициализирует игровые системы.
     */
    private fun initializeSystems() {
        logger.info("Initializing systems...")

        // Инициализация систем эффектов и условий
        omc.boundbyfate.system.effect.BbfEffects.register()
        omc.boundbyfate.system.condition.BbfConditionTypes
        omc.boundbyfate.registry.EffectRegistry.printStatistics()

        logger.info(
            "${omc.boundbyfate.api.condition.ConditionTypeRegistry.size()} condition types registered"
        )

        // Регистрация встроенных способностей
        omc.boundbyfate.system.ability.BbfAbilities.register()
        omc.boundbyfate.system.ability.AbilityRegistry.printStatistics()

        // Регистрация слушателей событий организаций
        omc.boundbyfate.system.organization.OrganizationSystem.registerEventListeners()

        // Инициализация компонентов — обращение к объектам запускает регистрацию
        omc.boundbyfate.component.BbfBuiltinComponents

        // Регистрация синхронизации компонентов
        omc.boundbyfate.component.sync.ComponentSyncHandler.register()

        // Регистрация синхронизации WorldData секций
        omc.boundbyfate.data.world.sync.WorldDataSyncHandler.register()

        // Инициализация системы визуала
        omc.boundbyfate.system.visual.VisualOrchestrator.register()

        // Инициализация системы передачи файлов
        omc.boundbyfate.system.transfer.FileTransferSystem.register()

        // Инициализация музыкальной системы
        omc.boundbyfate.system.visual.sound.MusicSystem.register()

        // Регистрация НПС сущностей
        omc.boundbyfate.registry.NpcEntityRegistry.register()

        // Инициализация секций WorldData (регистрация через companion object)
        omc.boundbyfate.data.world.sections.CharacterSection.TYPE
        omc.boundbyfate.data.world.sections.RelationSection.TYPE
        omc.boundbyfate.data.world.sections.OrganizationSection.TYPE
        omc.boundbyfate.data.world.sections.WorldSection.TYPE

        omc.boundbyfate.component.core.BbfComponents.printStatistics()

        logger.info("Systems initialized")
    }
    
    /**
     * Выводит статистику инициализации.
     */
    private fun printStatistics() {
        logger.info("")
        logger.info("=== Initialization Statistics ===")
        
        RegistryManager.printStatistics()
        BbfComponents.printStatistics()
        
        logger.info("=================================")
        logger.info("")
    }
}
