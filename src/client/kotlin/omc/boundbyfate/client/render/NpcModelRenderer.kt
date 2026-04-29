package omc.boundbyfate.client.render

import de.fabmax.kool.util.Time
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import omc.boundbyfate.client.models.internal.controller.AnimationSystem
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
 *
 * ## Жизненный цикл
 *
 * - Создание: [getOrCreateAttachment] при первом рендере НПС
 * - Обновление: [onRenderPre] каждый кадр
 * - Удаление: [onEntityRemoved] при выгрузке сущности из мира
 *
 * ## Управление анимациями
 *
 * ```kotlin
 * // Получить AnimationSystem НПС и переключить анимацию:
 * NpcModelRenderer.getAnimationSystem(entity)?.play("attack", duration = 0.2f)
 * ```
 */
@Environment(EnvType.CLIENT)
object NpcModelRenderer {

    private val logger = LoggerFactory.getLogger(NpcModelRenderer::class.java)

    /** Кеш моделей. Ключ — UUID сущности. */
    private val attachments = ConcurrentHashMap<String, CachedModel>()

    // ── Рендер ────────────────────────────────────────────────────────────

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
        if (entity !is NpcEntity) return false

        val modelComponent = entity.getAttached(NpcModelComponent.TYPE) ?: return false
        val cached = getOrCreateAttachment(entity, modelComponent) ?: return false

        // Frustum culling по реальным bounds модели.
        // MC culling использует hitbox сущности (0.6×1.8), модель может быть крупнее.
        if (isCulled(cached.attachment, entity, partialTick)) return true

        // Прокручиваем корутины анимационной системы
        cached.animationSystem?.update(Time.deltaT)

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

    private fun isCulled(attachment: ModelAttachment, entity: Entity, partialTick: Float): Boolean {
        val frustum = MinecraftClient.getInstance().worldRenderer?.let {
            try {
                val field = it.javaClass.getDeclaredField("frustum")
                field.isAccessible = true
                field.get(it) as? net.minecraft.client.render.Frustum
            } catch (_: Exception) { null }
        } ?: return false

        val modelBounds = attachment.calculateBounds() ?: return false
        val (min, max) = modelBounds
        val pos = entity.getLerpedPos(partialTick)
        val worldBox = Box(
            pos.x + min.x, pos.y + min.y, pos.z + min.z,
            pos.x + max.x, pos.y + max.y, pos.z + max.z
        )
        return !frustum.isVisible(worldBox)
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
            OverlayTexture.packUv(OverlayTexture.getU(0f), OverlayTexture.getV(true))
        } else {
            OverlayTexture.DEFAULT_UV
        }

        poseStack.push()

        if (entity is LivingEntity) {
            val bodyYaw = MathHelper.lerpAngleDegrees(partialTick, entity.prevBodyYaw, entity.bodyYaw)
            poseStack.multiply(Quaternionf().rotateY(-bodyYaw * MathHelper.RADIANS_PER_DEGREE))
        }

        poseStack.scale(scale, scale, scale)

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

    // ── Кеш ──────────────────────────────────────────────────────────────

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

        // Если модель сменилась — уничтожаем старую
        if (existing != null) {
            existing.animationSystem?.destroy()
            attachments.remove(key)
        }

        return try {
            val attachment = ModelAttachment(component.modelPath)

            val animSystem = if (component.animationsEnabled) {
                AnimationSystem(attachment).also { sys ->
                    // Запускаем idle когда модель загрузится.
                    // playIdleWhenReady() ждёт загрузки асинхронно — не блокирует рендер,
                    // не создаёт мусор каждый кадр, не падает если анимаций ещё нет.
                    sys.playIdleWhenReady()
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

    // ── Публичный API ─────────────────────────────────────────────────────

    /**
     * Возвращает [AnimationSystem] для сущности или null если модель не загружена.
     *
     * Используй для переключения анимаций из игровой логики:
     * ```kotlin
     * NpcModelRenderer.getAnimationSystem(entity)?.play("attack", duration = 0.2f)
     * ```
     */
    fun getAnimationSystem(entity: Entity): AnimationSystem? =
        attachments[entity.uuid.toString()]?.animationSystem

    /**
     * Очищает кеш и уничтожает [AnimationSystem] при удалении сущности из мира.
     *
     * Вызывается из [NpcRenderEventHandler] при выгрузке сущности.
     * Без этого — утечка корутин на каждый удалённый НПС.
     */
    fun onEntityRemoved(entity: Entity) {
        val cached = attachments.remove(entity.uuid.toString()) ?: return
        cached.animationSystem?.destroy()
    }

    // ── Внутренние типы ───────────────────────────────────────────────────

    private data class CachedModel(
        val modelPath: String,
        val attachment: ModelAttachment,
        val animationSystem: AnimationSystem?
    )
}
