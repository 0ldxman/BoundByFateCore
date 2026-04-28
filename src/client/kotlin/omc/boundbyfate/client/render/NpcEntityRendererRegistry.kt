package omc.boundbyfate.client.render

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import omc.boundbyfate.client.render.entity.EmptyEntityRenderer
import omc.boundbyfate.registry.NpcEntityRegistry
import org.slf4j.LoggerFactory

/**
 * Регистрация клиентских рендереров для сущностей мода.
 *
 * НПС использует [EmptyEntityRenderer] — стандартный рендер Minecraft отключён.
 * Вместо него модель рендерится через kool/GLTF пайплайн,
 * который перехватывает рендер сущности через Fabric rendering events.
 */
@Environment(EnvType.CLIENT)
object NpcEntityRendererRegistry {

    private val logger = LoggerFactory.getLogger(NpcEntityRendererRegistry::class.java)

    fun register() {
        EntityRendererRegistry.register(NpcEntityRegistry.NPC) { context ->
            EmptyEntityRenderer(context)
        }
        logger.info("NPC entity renderer registered")
    }
}
