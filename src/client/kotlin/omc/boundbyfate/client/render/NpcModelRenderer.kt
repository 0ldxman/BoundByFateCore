package omc.boundbyfate.client.render

import net.minecraft.client.util.math.MatrixStack
import de.fabmax.kool.util.Time
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import omc.boundbyfate.client.models.internal.controller.AnimationSystem
import omc.boundbyfate.client.models.internal.controller.WrapMode
import omc.boundbyfate.client.models.internal.rendering.RenderContext
import omc.boundbyfate.client.models.internal.v2.ModelAttachment
import omc.boundbyfate.client.models.internal.v2.calculateBounds
import omc.boundbyfate.component.components.NpcModelComponent
import omc.boundbyfate.entity.NpcEntity
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
        poseStack: MatrixStack,
        buffer: VertexConsumerProvider,
        packedLight: Int
    ): Boolean {
        // Только для наших НПС
        if (entity !is NpcEntity) return false

        // Получаем компонент модели
        val modelComponent = entity.getAttached(NpcModelComponent.TYPE) ?: return false

        // Получаем или создаём ModelAttachment
        val cached = getOrCreateAttachment(entity, modelComponent) ?: return false

        // Frustum culling по реальным bounds модели.
        // MC culling использует hitbox сущности (0.6×1.8), модель может быть крупнее.
        val frustum = MinecraftClient.getInstance().worldRenderer?.let {
            try {
                val field = it.javaClass.getDeclaredField("frustum")
                field.isAccessible = true
                field.get(it) as? net.minecraft.client.render.Frustum
            } catch (_: Exception) { null }
        }
        if (frustum != null) {
            val modelBounds = cached.attachment.calculateBounds()
            if (modelBounds != null) {
                val (min, max) = modelBounds
                val pos = entity.getLerpedPos(partialTick)
                val worldBox = Box(
                    pos.x + min.x, pos.y + min.y, pos.z + min.z,
                    pos.x + max.x, pos.y + max.y, pos.z + max.z
                )
                if (!frustum.isVisible(worldBox)) return true // отменяем стандартный рендер, но не рисуем
            }
        }

        // Обновляем анимации (NpcEntity всегда LivingEntity)
        cached.animationSystem?.update(Time.deltaT)

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
        poseStack: MatrixStack,
        buffer: VertexConsumerProvider,
        packedLight: Int,
        scale: Float
    ) {
        val overlay = if (entity is LivingEntity && (entity.hurtTime > 0 || entity.deathTime > 0)) {
            OverlayTexture.packUv(
                OverlayTexture.getU(0f),
                OverlayTexture.getV(true)
            )
        } else {
            OverlayTexture.DEFAULT_UV
        }

        poseStack.push()

        // Поворачиваем модель по направлению тела сущности
        if (entity is LivingEntity) {
            val bodyYaw = MathHelper.lerpAngleDegrees(partialTick, entity.prevBodyYaw, entity.bodyYaw)
            poseStack.multiply(
                Quaternionf().rotateY(-bodyYaw * MathHelper.RADIANS_PER_DEGREE)
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

        poseStack.pop()
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
                AnimationSystem(attachment).also { sys ->
                    // Запускаем первую доступную анимацию как idle при создании
                    sys.onUpdate {
                        val idleName = attachment.animations
                            .firstOrNull { it.name.equals("idle", ignoreCase = true) }?.name
                            ?: attachment.animations.firstOrNull()?.name
                        if (idleName != null) {
                            sys.transition(to = idleName, duration = 0f, wrapMode = WrapMode.Loop)
                        }
                        // onUpdate вызывается один раз — запустили анимацию и выходим
                        return@onUpdate
                    }
                }
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
