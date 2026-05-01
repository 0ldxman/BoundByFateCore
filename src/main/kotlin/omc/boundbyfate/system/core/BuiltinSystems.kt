package omc.boundbyfate.system.core

import omc.boundbyfate.component.sync.ComponentSyncHandler
import omc.boundbyfate.data.world.BbfWorldData
import omc.boundbyfate.data.world.sync.WorldDataSyncHandler
import omc.boundbyfate.registry.NpcEntityRegistry
import omc.boundbyfate.system.ability.BbfAbilities
import omc.boundbyfate.system.condition.BbfConditionTypes
import omc.boundbyfate.system.effect.BbfEffects
import omc.boundbyfate.system.organization.OrganizationSystem
import omc.boundbyfate.system.transfer.FileTransferSystem
import omc.boundbyfate.system.visual.VisualOrchestrator
import omc.boundbyfate.system.visual.sound.MusicSystem

/**
 * Встроенные системы BoundByFate Core.
 *
 * Каждая система — объект реализующий [BbfSystem].
 * Все они регистрируются в [SystemRegistry] через [registerAll].
 *
 * ## Добавление новой системы
 *
 * 1. Создай `object MySomethingSystem : BbfSystem { ... }` здесь или в отдельном файле
 * 2. Добавь его в [registerAll]
 * 3. Укажи [BbfSystem.dependencies] если нужен порядок
 */
object BuiltinSystems {

    /**
     * Регистрирует все встроенные системы в [SystemRegistry].
     * Вызывается один раз из [omc.boundbyfate.BoundByFateCore.onInitialize].
     */
    fun registerAll() {
        SystemRegistry.registerAll(
            WorldDataLifecycleSystem,
            WorldDataSectionsSystem,
            EffectsSystem,
            ConditionTypesSystem,
            AbilitiesSystem,
            OrganizationEventSystem,
            ComponentSyncSystem,
            WorldDataSyncSystem,
            VisualOrchestratorSystem,
            FileTransferSystemWrapper,
            MusicSystemWrapper,
            NpcEntitySystem
        )
    }

    // ── Системы ───────────────────────────────────────────────────────────

    object WorldDataLifecycleSystem : BbfSystem {
        override val systemId = "boundbyfate-core:world_data_lifecycle"

        override fun register() {
            BbfWorldData.registerLifecycle()
        }
    }

    object EffectsSystem : BbfSystem {
        override val systemId = "boundbyfate-core:effects"

        override fun register() {
            BbfEffects.register()
            omc.boundbyfate.registry.EffectRegistry.printStatistics()
        }
    }

    object ConditionTypesSystem : BbfSystem {
        override val systemId = "boundbyfate-core:condition_types"

        override fun register() {
            // Обращение к объекту запускает регистрацию всех типов условий
            BbfConditionTypes
            val count = omc.boundbyfate.api.condition.ConditionTypeRegistry.size()
            org.slf4j.LoggerFactory.getLogger("BbfConditions")
                .info("$count condition types registered")
        }
    }

    object AbilitiesSystem : BbfSystem {
        override val systemId = "boundbyfate-core:abilities"
        override val dependencies = listOf("boundbyfate-core:effects")

        override fun register() {
            BbfAbilities.register()
            omc.boundbyfate.registry.AbilityRegistry.printStatistics()
        }
    }

    object OrganizationEventSystem : BbfSystem {
        override val systemId = "boundbyfate-core:organization_events"

        override fun register() {
            OrganizationSystem.registerEventListeners()
        }
    }

    object ComponentSyncSystem : BbfSystem {
        override val systemId = "boundbyfate-core:component_sync"

        override fun register() {
            ComponentSyncHandler.register()
        }
    }

    object WorldDataSyncSystem : BbfSystem {
        override val systemId = "boundbyfate-core:world_data_sync"
        override val dependencies = listOf(
            "boundbyfate-core:world_data_lifecycle",
            "boundbyfate-core:world_data_sections"
        )

        override fun register() {
            WorldDataSyncHandler.register()
        }
    }

    object VisualOrchestratorSystem : BbfSystem {
        override val systemId = "boundbyfate-core:visual_orchestrator"

        override fun register() {
            VisualOrchestrator.register()
        }
    }

    object FileTransferSystemWrapper : BbfSystem {
        override val systemId = "boundbyfate-core:file_transfer"

        override fun register() {
            FileTransferSystem.register()
        }
    }

    object MusicSystemWrapper : BbfSystem {
        override val systemId = "boundbyfate-core:music"
        override val dependencies = listOf("boundbyfate-core:file_transfer")

        override fun register() {
            MusicSystem.register()
        }
    }

    object NpcEntitySystem : BbfSystem {
        override val systemId = "boundbyfate-core:npc_entities"

        override fun register() {
            NpcEntityRegistry.register()
        }
    }

    /**
     * Инициализирует секции WorldData — обращение к TYPE запускает registerSection().
     * Должна быть до WorldDataSyncSystem чтобы секции были зарегистрированы до синхронизации.
     */
    object WorldDataSectionsSystem : BbfSystem {
        override val systemId = "boundbyfate-core:world_data_sections"
        override val dependencies = listOf("boundbyfate-core:world_data_lifecycle")

        override fun register() {
            omc.boundbyfate.data.world.sections.CharacterSection.TYPE
            omc.boundbyfate.data.world.sections.RelationSection.TYPE
            omc.boundbyfate.data.world.sections.OrganizationSection.TYPE
            omc.boundbyfate.data.world.sections.WorldSection.TYPE
        }
    }
}
