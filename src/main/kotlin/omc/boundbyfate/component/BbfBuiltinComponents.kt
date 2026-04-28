package omc.boundbyfate.component

import omc.boundbyfate.component.components.EntityAbilitiesData
import omc.boundbyfate.component.components.EntityCharacterData
import omc.boundbyfate.component.components.EntityCombatData
import omc.boundbyfate.component.components.EntityEffectsData
import omc.boundbyfate.component.components.EntityParamsData
import omc.boundbyfate.component.components.EntityStatsData
import omc.boundbyfate.component.components.EntityStatusesData
import omc.boundbyfate.component.components.NpcModelComponent
import org.slf4j.LoggerFactory

/**
 * Регистрация всех встроенных компонентов BoundByFate Core.
 */
object BbfBuiltinComponents {

    private val logger = LoggerFactory.getLogger(BbfBuiltinComponents::class.java)

    init {
        logger.info("Registering built-in components...")

        EntityCharacterData.TYPE
        EntityStatsData.TYPE
        EntityParamsData.TYPE
        EntityEffectsData.TYPE
        EntityStatusesData.TYPE
        EntityCombatData.TYPE
        EntityAbilitiesData.TYPE
        NpcModelComponent.TYPE

        logger.info("Built-in components registered")
    }
}
