package omc.boundbyfate.client.render

import com.mojang.blaze3d.vertex.PoseStack
import de.fabmax.kool.util.Time
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import omc.boundbyfate.client.models.internal.controller.AnimationSystem
import omc.boundbyfate.client.models.internal.rendering.RenderContext
import omc.boundbyfate.client.models.internal.v2.ModelAttachment
import omc.boundbyfate.component.components.NpcModelComponent
import omc.boundbyfate.system.npc.entity.NpcEntity
import org.joml.Quaternionf
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Клиентский рендерер GLTF моделей для НПС.
 *
 * Вызывается из [EntityRendererMixin] при рендере сущности.
 * Если у сущности есть [NpcModelComponent] — рисует GLTF модель
 * через kool пайплайн и возвращает true (отменяет стандартный рендер).
 */
@Environment(EnvType.CLIENT)
object NpcModelRenderer {

    private val logger = LoggerFactory.getLogger(NpcModelRenderer::class.java)

    /**
     * Кеш ModelAttachment для каждой сущности.
     * Ключ — UUID сущности в виде строки.
     */
    private val attachments = ConcurrentHashMap<String, CachedModel>()

    /**
     * Вызывается перед стандартным рендером сущности.
     *
     * @return true если рендер был выполнен и стандартный нужно отменить
     */
    fun onRenderPre(
        entity: Entity,
        entityYaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int
    ): Boolean {
        // Только для наших НПС
        if (entity !is NpcEntity) return false

        // Получаем компонент модели
        val modelComponent = entity.getAttached(NpcModelComponent.TYPE) ?: return false

        // Получаем или создаём ModelAttachment
        val cached = getOrCreateAttachment(entity, modelComponent) ?: return false

        // Обновляем анимации
        if (entity is LivingEntity) {
            cached.animationSystem?.update(Time.deltaT)
        }

        // Рендерим модель
        renderModel(
            attachment = cached.attachment,
            entity = entity,
            entityYaw = entityYaw,
            partialTick = partialTick,
            poseStack = poseStack,
            buffer = buffer,
            packedLight = packedLight,
            scale = modelComponent.scale
        )

        return true
    }

    private fun renderModel(
        attachment: ModelAttachment,
        entity: Entity,
        entityYaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        scale: Float
    ) {
        val overlay = if (entity is LivingEntity) {
            OverlayTexture.pack(
                OverlayTexture.u(0f),
                OverlayTexture.v(entity.hurtTime > 0 || entity.deathTime > 0)
            )
        } else {
            OverlayTexture.NO_OVERLAY
        }

        poseStack.pushPose()

        // Поворачиваем модель по направлению тела сущности
        if (entity is LivingEntity) {
            val bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)
            poseStack.mulPose(
                Quaternionf().rotateY(-bodyYaw * Mth.DEG_TO_RAD)
            )
        }

        // Масштаб
        poseStack.scale(scale, scale, scale)

        // Рендерим через kool пайплайн
        attachment.pipeline.render(
            RenderContext(
                stack = poseStack,
                source = buffer,
                light = packedLight,
                overlay = overlay,
                allowInstancing = false
            )
        )

        poseStack.popPose()
    }

    private fun getOrCreateAttachment(
        entity: NpcEntity,
        component: NpcModelComponent
    ): CachedModel? {
        val key = entity.uuid.toString()
        val existing = attachments[key]

        // Если модель та же — возвращаем кеш
        if (existing != null && existing.modelPath == component.modelPath) {
            return existing
        }

        // Создаём новый attachment
        return try {
            val attachment = ModelAttachment(component.modelPath)
            val animSystem = if (component.animationsEnabled) {
                AnimationSystem(attachment)
            } else null

            val cached = CachedModel(
                modelPath = component.modelPath,
                attachment = attachment,
                animationSystem = animSystem
            )
            attachments[key] = cached
            cached
        } catch (e: Exception) {
            logger.error("Failed to load model '${component.modelPath}' for NPC ${entity.uuid}", e)
            null
        }
    }

    /**
     * Очищает кеш для сущности когда она удаляется из мира.
     */
    fun onEntityRemoved(entity: Entity) {
        attachments.remove(entity.uuid.toString())
    }

    private data class CachedModel(
        val modelPath: String,
        val attachment: ModelAttachment,
        val animationSystem: AnimationSystem?
    )
}
