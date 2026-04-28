package omc.boundbyfate.system.mechanic

import com.google.gson.JsonObject
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.api.mechanic.MechanicConfig
import omc.boundbyfate.component.components.EntityStatsData
import omc.boundbyfate.component.core.getOrCreate
import omc.boundbyfate.registry.MechanicRegistry
import org.slf4j.LoggerFactory

/**
 * Менеджер механик классов.
 *
 * Активные механики хранятся в [EntityStatsData.mechanics] на entity.
 * Конфигурация активных механик хранится в памяти (пересоздаётся при загрузке персонажа).
 */
object ClassMechanicManager {

    private val logger = LoggerFactory.getLogger(ClassMechanicManager::class.java)

    /**
     * Конфигурации активных механик — хранятся в памяти, пересоздаются при загрузке.
     * Key: UUID игрока, Value: Map<MechanicId, ActiveMechanic>
     */
    private val activeMechanicConfigs = mutableMapOf<String, MutableMap<Identifier, ActiveMechanic>>()
    
    fun activateMechanic(
        player: ServerPlayerEntity,
        mechanicId: Identifier,
        config: JsonObject,
        sourceFeature: Identifier,
        sourceClass: Identifier
    ) {
        val handler = MechanicRegistry.getHandler(mechanicId)
        if (handler == null) {
            logger.error("Mechanic handler $mechanicId not found")
            return
        }

        val definition = MechanicRegistry.getDefinition(mechanicId)
        if (definition == null) {
            logger.error("Mechanic definition $mechanicId not found")
            return
        }

        if (!handler.checkDependencies(player, definition)) {
            logger.error("Dependencies not met for mechanic $mechanicId")
            return
        }

        if (hasMechanic(player, mechanicId)) {
            logger.warn("Mechanic $mechanicId is already active for ${player.name.string}")
            return
        }

        logger.info("Activating mechanic $mechanicId for ${player.name.string}")

        val defaultConfig = MechanicConfig(definition.defaultConfig)
        val overrideConfig = MechanicConfig(config)
        val finalConfig = defaultConfig.merge(overrideConfig)

        try {
            handler.onActivate(player, finalConfig)
        } catch (e: Exception) {
            logger.error("Error activating mechanic $mechanicId", e)
            return
        }

        // Сохраняем в EntityStatsData.mechanics
        player.getOrCreate(EntityStatsData.TYPE).mechanics.add(mechanicId)

        // Сохраняем конфигурацию в памяти
        val playerUuid = player.uuidAsString
        activeMechanicConfigs.getOrPut(playerUuid) { mutableMapOf() }[mechanicId] = ActiveMechanic(
            mechanicId = mechanicId,
            handler = handler,
            config = finalConfig,
            sourceFeature = sourceFeature,
            sourceClass = sourceClass
        )

        logger.debug("Mechanic $mechanicId activated successfully")
    }

    fun deactivateMechanic(player: ServerPlayerEntity, mechanicId: Identifier) {
        val playerUuid = player.uuidAsString
        val activeMechanic = activeMechanicConfigs[playerUuid]?.get(mechanicId)
        if (activeMechanic == null) {
            logger.warn("Mechanic $mechanicId is not active for ${player.name.string}")
            return
        }

        logger.info("Deactivating mechanic $mechanicId for ${player.name.string}")

        try {
            activeMechanic.handler.onDeactivate(player)
        } catch (e: Exception) {
            logger.error("Error deactivating mechanic $mechanicId", e)
        }

        // Удаляем из EntityStatsData.mechanics
        player.getOrCreate(EntityStatsData.TYPE).mechanics.remove(mechanicId)

        // Удаляем конфигурацию из памяти
        activeMechanicConfigs[playerUuid]?.remove(mechanicId)
        if (activeMechanicConfigs[playerUuid]?.isEmpty() == true) {
            activeMechanicConfigs.remove(playerUuid)
        }

        logger.debug("Mechanic $mechanicId deactivated successfully")
    }

    fun hasMechanic(player: ServerPlayerEntity, mechanicId: Identifier): Boolean =
        player.getOrCreate(EntityStatsData.TYPE).hasMechanic(mechanicId)

    fun getActiveMechanic(player: ServerPlayerEntity, mechanicId: Identifier): ActiveMechanic? =
        activeMechanicConfigs[player.uuidAsString]?.get(mechanicId)

    fun getAllActiveMechanics(player: ServerPlayerEntity): Collection<ActiveMechanic> =
        activeMechanicConfigs[player.uuidAsString]?.values ?: emptyList()

    fun tick(player: ServerPlayerEntity) {
        val playerMechanics = activeMechanicConfigs[player.uuidAsString] ?: return
        for (activeMechanic in playerMechanics.values) {
            try {
                activeMechanic.handler.onTick(player)
            } catch (e: Exception) {
                logger.error("Error in mechanic ${activeMechanic.mechanicId} tick", e)
            }
        }
    }

    fun onLevelUp(player: ServerPlayerEntity, classId: Identifier, newLevel: Int) {
        val playerMechanics = activeMechanicConfigs[player.uuidAsString] ?: return
        for (activeMechanic in playerMechanics.values) {
            if (activeMechanic.sourceClass == classId) {
                try {
                    activeMechanic.handler.onLevelUp(player, newLevel)
                } catch (e: Exception) {
                    logger.error("Error in mechanic ${activeMechanic.mechanicId} onLevelUp", e)
                }
            }
        }
    }

    fun clearAllMechanics(player: ServerPlayerEntity) {
        val playerUuid = player.uuidAsString
        val playerMechanics = activeMechanicConfigs[playerUuid] ?: return

        logger.info("Clearing all mechanics for ${player.name.string}")

        for (mechanicId in playerMechanics.keys.toList()) {
            deactivateMechanic(player, mechanicId)
        }
    }
}

/**
 * Данные активной механики.
 */
data class ActiveMechanic(
    val mechanicId: Identifier,
    val handler: omc.boundbyfate.api.mechanic.ClassMechanic,
    val config: MechanicConfig,
    val sourceFeature: Identifier,
    val sourceClass: Identifier
)
