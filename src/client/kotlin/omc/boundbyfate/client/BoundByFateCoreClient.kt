package omc.boundbyfate.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory

/**
 * Клиентская часть мода BoundByFate Core.
 *
 * Отвечает за инициализацию клиентских систем:
 * - Kool 3D движок (рендеринг моделей НПС)
 * - Рендереры сущностей
 * - GUI
 * - Keybindings
 */
class BoundByFateCoreClient : ClientModInitializer {

    companion object {
        private val logger = LoggerFactory.getLogger(BoundByFateCoreClient::class.java)
    }

    override fun onInitializeClient() {
        logger.info("=".repeat(50))
        logger.info("Initializing BoundByFate Core Client...")
        logger.info("=".repeat(50))

        // Регистрация рендереров сущностей
        omc.boundbyfate.client.render.NpcEntityRendererRegistry.register()
        omc.boundbyfate.client.render.NpcRenderEventHandler.register()

        // Регистрация HollowModelManager как reload listener
        net.fabricmc.fabric.api.resource.ResourceManagerHelper
            .get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
            .registerReloadListener(omc.boundbyfate.client.models.internal.manager.HollowModelManager)

        // KoolManager и HollowModelManager инициализируются при первом тике
        // когда OpenGL контекст Minecraft уже полностью готов
        var koolInitialized = false
        ClientTickEvents.START_CLIENT_TICK.register { client: Minecraft ->
            if (!koolInitialized && client.world != null) {
                koolInitialized = true
                try {
                    // Инициализируем kool контекст
                    omc.boundbyfate.client.kool.KoolManager
                    // Инициализируем менеджер моделей (создаёт GL текстуры и шейдеры)
                    omc.boundbyfate.client.models.internal.manager.HollowModelManager.initialize()
                    logger.info("KoolManager and model system initialized")
                } catch (e: Exception) {
                    logger.error("Failed to initialize KoolManager", e)
                }
            }
        }

        // Система передачи файлов
        omc.boundbyfate.client.transfer.FileTransferClientSystem.register()

        // Система партиклов
        omc.boundbyfate.client.visual.ParticlePacketHandler.register()

        // Система звуков
        omc.boundbyfate.client.visual.SoundPacketHandler.register()

        // Музыкальная система
        omc.boundbyfate.client.visual.music.MusicClientSystem.register()

        logger.info("BoundByFate Core Client initialized successfully!")
        logger.info("=".repeat(50))
    }
}
