package omc.boundbyfate.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.minecraft.client.MinecraftClient
import net.minecraft.resource.ResourceType
import org.slf4j.LoggerFactory

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

        // Синхронизация компонентов с сервера
        omc.boundbyfate.client.component.ClientComponentHandler.register()

        // Кейбиндинги — инициализируем объект, чтобы зарегистрировать все кейбиндинги
        omc.boundbyfate.client.keybind.BbfKeybinds.let { }

        // HUD система
        omc.boundbyfate.client.hud.HudSystem.register()

        // Регистрация HollowModelManager как reload listener
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
            .registerReloadListener(omc.boundbyfate.client.models.internal.manager.HollowModelManager)

        // KoolManager инициализируется при первом тике когда OpenGL контекст уже готов
        var koolInitialized = false
        ClientTickEvents.START_CLIENT_TICK.register { client: MinecraftClient ->
            if (!koolInitialized && client.world != null) {
                koolInitialized = true
                try {
                    omc.boundbyfate.client.kool.KoolManager
                    omc.boundbyfate.client.models.internal.manager.HollowModelManager.initialize()
                    logger.info("KoolManager and model system initialized")
                } catch (e: Exception) {
                    logger.error("Failed to initialize KoolManager", e)
                }
            }
        }

        // Обновляем Kool-контекст каждый кадр рендера.
        // Это нужно для: обновления матриц трансформации нод, тика анимаций через AnimationDispatcher,
        // и прокрутки корутин KoolManager.
        // Без этого вызова анимации не тикают и матрицы нод не обновляются.
        // Используем BEFORE_ENTITIES чтобы матрицы были актуальны до рендера сущностей.
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.BEFORE_ENTITIES.register { context ->
            if (koolInitialized) {
                try {
                    omc.boundbyfate.client.kool.gl.tickKool()
                } catch (e: Exception) {
                    logger.error("Error in Kool tickKool", e)
                }
            }
        }

        // Система передачи файлов
        omc.boundbyfate.client.transfer.FileTransferClientSystem.register()

        // Скины — подписываемся на получение файлов категории SKIN
        omc.boundbyfate.client.transfer.FileTransferClientSystem.onFileReceived(
            omc.boundbyfate.system.transfer.FileCategory.SKIN
        ) { skinId, bytes, _ ->
            omc.boundbyfate.client.skin.ClientSkinManager.onSkinFileReceived(skinId, bytes)
        }

        // Очищаем скины при отключении от сервера
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            omc.boundbyfate.client.skin.ClientSkinManager.clearAll()
        }

        // Система партиклов
        omc.boundbyfate.client.visual.ParticlePacketHandler.register()

        // Система звуков
        omc.boundbyfate.client.visual.SoundPacketHandler.register()

        // Музыкальная система
        omc.boundbyfate.client.visual.music.MusicClientSystem.register()

        // Система анимаций игрока
        omc.boundbyfate.client.animation.PlayerAnimPacketHandler.register()
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
            if (entity is net.minecraft.entity.player.PlayerEntity) {
                omc.boundbyfate.client.animation.PlayerAnimSystem.onPlayerRemoved(entity)
            }
        }

        // Система манекенов персонажей
        omc.boundbyfate.client.character.CharacterDummyManager.register()
        omc.boundbyfate.client.character.CharacterDummyRenderer.register()
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            omc.boundbyfate.client.character.CharacterDummyManager.clearAll()
        }

        // Обработчик клиентских пакетов
        omc.boundbyfate.client.network.ClientPacketHandler.register()

        logger.info("BoundByFate Core Client initialized successfully!")
        logger.info("=".repeat(50))
    }
}
