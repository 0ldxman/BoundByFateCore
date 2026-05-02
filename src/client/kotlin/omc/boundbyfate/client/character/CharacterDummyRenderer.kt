package omc.boundbyfate.client.character

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderDispatcher
import net.minecraft.client.util.math.MatrixStack
import org.slf4j.LoggerFactory

/**
 * Рендерер для [CharacterDummy].
 *
 * Поскольку Dummy не добавляются в `ClientWorld.entityList`,
 * их нужно рендерить вручную каждый кадр.
 *
 * Использует стандартный [EntityRenderDispatcher] Minecraft —
 * он автоматически выберет `PlayerEntityRenderer` для
 * `AbstractClientPlayerEntity` и применит скин, анимации и т.д.
 *
 * Регистрируется через [WorldRenderEvents.AFTER_ENTITIES].
 */
@Environment(EnvType.CLIENT)
object CharacterDummyRenderer {

    private val logger = LoggerFactory.getLogger(CharacterDummyRenderer::class.java)

    fun register() {
        WorldRenderEvents.AFTER_ENTITIES.register { context ->
            renderDummies(context)
        }
        logger.info("CharacterDummyRenderer registered")
    }

    private fun renderDummies(context: WorldRenderContext) {
        val dummies = CharacterDummyManager.getAllDummies()
        if (dummies.isEmpty()) return

        val client = MinecraftClient.getInstance()
        val dispatcher = client.entityRenderDispatcher
        val camera = context.camera()
        val tickDelta = context.tickDelta()

        val matrices = context.matrixStack() ?: return
        val consumers = context.consumers() ?: return

        val camPos = camera.pos

        for (dummy in dummies) {
            try {
                // Обновляем тик Dummy (минимальный — только возраст)
                dummy.tick()

                val x = dummy.x - camPos.x
                val y = dummy.y - camPos.y
                val z = dummy.z - camPos.z

                matrices.push()
                matrices.translate(x, y, z)

                dispatcher.render(
                    dummy,
                    0.0, 0.0, 0.0,
                    dummy.yaw,
                    tickDelta,
                    matrices,
                    consumers,
                    dispatcher.getLight(dummy, tickDelta)
                )

                matrices.pop()
            } catch (e: Exception) {
                logger.error("Failed to render CharacterDummy ${dummy.characterId}", e)
            }
        }
    }
}
