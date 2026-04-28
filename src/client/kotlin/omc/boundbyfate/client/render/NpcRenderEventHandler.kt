package omc.boundbyfate.client.render

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import omc.boundbyfate.system.npc.entity.NpcEntity

/**
 * Обработчик событий сущностей для системы рендеринга НПС.
 *
 * Очищает кеш [NpcModelRenderer] когда НПС удаляется из мира,
 * чтобы не держать в памяти неиспользуемые ModelAttachment.
 */
@Environment(EnvType.CLIENT)
object NpcRenderEventHandler {

    fun register() {
        ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
            if (entity is NpcEntity) {
                NpcModelRenderer.onEntityRemoved(entity)
            }
        }
    }
}
