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
import net.minecraft.util.Identifier
import omc.boundbyfate.client.models.internal.controller.AnimationSystem
import omc.boundbyfate.client.models.internal.controller.WrapMode
import omc.boundbyfate.client.models.internal.rendering.RenderContext
import omc.boundbyfate.client.models.internal.v2.ModelAttachment
import omc.boundbyfate.client.models.internal.v2.calculateBounds
import omc.boundbyfate.component.components.AnimLayerState
import omc.boundbyfate.component.components.NpcModelComponent
import omc.boundbyfate.component.core.getOrCreate
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
 * Анимации управляются через [NpcModelComponent.animationLayers] на сервере.
 * Компонент синхронизируется с клиентом автоматически через ComponentSyncHandler.
 * Рендерер читает слои из компонента и применяет их к [AnimationSystem].
 *
 * ```kotlin
 * // На сервере:
 * npcEntity.getAttached(NpcModelComponent.TYPE)?.playAnimation("body", "walk")
 * npcEntity.getAttached(NpcModelComponent.TYPE)?.playAnimation("emotion", "happy")
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
    private var renderLogCount = 0

    fun onRenderPre(
        entity: Entity,
        entityYaw: Float,
        partialTick: Float,
        poseStack: MatrixStack,
        buffer: VertexConsumerProvider,
        packedLight: Int
    ): Boolean {
        if (entity !is NpcEntity) return false

        if (renderLogCount < 3) {
            renderLogCount++
            logger.info("[onRenderPre] NPC {} render called (count={})", entity.uuid, renderLogCount)
        }

        // Получаем или создаём компонент на клиенте.
        // getOrCreate нужен потому что на клиенте компонент может ещё не прийти по сети,
        // но дефолтные значения (modelPath = "boundbyfate-core:models/entity/classic.gltf") уже корректны.
        val modelComponent = entity.getOrCreate(NpcModelComponent.TYPE)
        val cached = getOrCreateAttachment(entity, modelComponent)
        if (cached == null) {
            if (renderLogCount <= 3) logger.info("[onRenderPre] getOrCreateAttachment returned null for {}", entity.uuid)
            return false
        }

        // Frustum culling отключён — используем стандартный MC culling по hitbox.
        // Кастомный culling по bounds модели давал ложные срабатывания из-за
        // несоответствия координатных систем модели и мира.
        // if (isCulled(cached.attachment, entity, partialTick)) return true

        // Синхронизируем анимационные слои из компонента
        val animSystem = cached.animationSystem
        if (animSystem != null) {
            syncAnimationLayers(animSystem, cached, modelComponent.animationLayers)
        }

        // Прокручиваем корутины анимационной системы
        animSystem?.update(Time.deltaT)

        // Получаем текстуру скина если назначена
        val skinTexture = if (modelComponent.skinId.isNotEmpty()) {
            omc.boundbyfate.client.skin.ClientSkinManager.also {
                it.ensureLoaded(modelComponent.skinId)
            }.getTexture(modelComponent.skinId)
        } else null

        renderModel(
            attachment = cached.attachment,
            entity = entity,
            entityYaw = entityYaw,
            partialTick = partialTick,
            poseStack = poseStack,
            buffer = buffer,
            packedLight = packedLight,
            scale = modelComponent.scale,
            skinTexture = skinTexture
        )

        return true
    }

    /**
     * Синхронизирует анимационные слои из компонента с [AnimationSystem].
     *
     * Основной слой (`key = "__base__"`) — через transition (плавная замена).
     * Именованные слои — аддитивно через playLayer/stopLayer.
     */
    private fun syncAnimationLayers(
        animSystem: AnimationSystem,
        cached: CachedModel,
        layers: List<AnimLayerState>
    ) {
        val newLayerMap = layers.associateBy { it.key }
        val oldLayerMap = cached.activeLayerStates

        // Слои которые были удалены — останавливаем
        for ((key, oldState) in oldLayerMap) {
            if (key !in newLayerMap) {
                if (key == AnimLayerState.BASE_LAYER_KEY) {
                    // Основной слой остановлен — transition к пустоте
                    animSystem.play("", duration = oldState.blendIn, wrapMode = WrapMode.Once)
                } else {
                    animSystem.stopLayer(key)
                }
            }
        }

        // Слои которые добавились или изменились — запускаем
        for ((key, newState) in newLayerMap) {
            val oldState = oldLayerMap[key]
            if (oldState != newState) {
                val wrapMode = if (newState.looping) WrapMode.Loop else WrapMode.Once
                if (key == AnimLayerState.BASE_LAYER_KEY) {
                    // Основной слой — transition
                    animSystem.play(newState.animation, duration = newState.blendIn, wrapMode = wrapMode)
                } else {
                    // Именованный слой — аддитивно
                    animSystem.playLayer(key, newState.animation, newState.blendIn, wrapMode)
                }
            }
        }

        cached.activeLayerStates = newLayerMap
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
        scale: Float,
        skinTexture: Identifier? = null
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

        // Биндим кастомную текстуру скина если назначена.
        // Пайплайн использует GL_TEXTURE0 для основной текстуры модели.
        if (skinTexture != null) {
            com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0,
                MinecraftClient.getInstance().textureManager.getTexture(skinTexture).getGlId()
            )
        }

        // Устанавливаем шейдер явно перед рендером VAO/Instanced примитивов.
        // PipelineRenderer.renderVAO() использует RenderSystem.getShader() — он должен быть не null.
        // EmptyEntityRenderer не устанавливает шейдер сам, поэтому делаем это здесь.
        val entityCutoutShader = omc.boundbyfate.client.models.internal.SHADER
        if (entityCutoutShader != null) {
            com.mojang.blaze3d.systems.RenderSystem.setShader { entityCutoutShader }
        } else {
            logger.warn("[renderModel] entityCutoutShader is null — VAO render will be skipped")
        }

        val pipeline = attachment.pipeline
        val pipelineClass = pipeline.javaClass.simpleName
        logger.info("[renderModel] pipeline=$pipelineClass, shader=${entityCutoutShader != null}, entity=${entity.uuid}")

        // Log pipeline command counts for debugging
        if (pipeline is omc.boundbyfate.client.models.internal.rendering.ListRenderPipeline && renderLogCount <= 5) {
            logger.info("[renderModel] nodes=${attachment.nodes.size}, triangles=${attachment.triangles}, model=${attachment.model.javaClass.simpleName}")
        }

        pipeline.render(
            RenderContext(
                stack = poseStack,
                source = buffer,
                light = packedLight,
                overlay = overlay,
                allowInstancing = false,
                shader = entityCutoutShader
            )
        )

        // Flush batched geometry — VertexConsumerProvider.Immediate buffers must be drawn explicitly
        if (buffer is net.minecraft.client.render.VertexConsumerProvider.Immediate) {
            buffer.draw()
        }

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
                    // Если слоёв нет — запускаем idle как fallback
                    if (component.animationLayers.isEmpty()) {
                        sys.playIdleWhenReady()
                    }
                }
            } else null

            val cached = CachedModel(
                modelPath = component.modelPath,
                attachment = attachment,
                animationSystem = animSystem,
                activeLayerStates = emptyMap()
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
     */
    fun getAnimationSystem(entity: Entity): AnimationSystem? =
        attachments[entity.uuid.toString()]?.animationSystem

    /**
     * Рендерит модель НПС напрямую без frustum culling.
     * Используется для GUI превью где entity не находится в мире.
     *
     * @return true если модель загружена и была нарисована
     */
    fun renderForGui(
        entity: NpcEntity,
        poseStack: MatrixStack,
        buffer: VertexConsumerProvider,
        packedLight: Int,
        entityYaw: Float,
        partialTick: Float
    ): Boolean {
        val modelComponent = entity.getAttached(NpcModelComponent.TYPE)
        if (modelComponent == null) {
            logger.warn("[renderForGui] NpcModelComponent is null for ${entity.uuid}")
            return false
        }
        val cached = getOrCreateAttachment(entity, modelComponent)
        if (cached == null) {
            logger.warn("[renderForGui] getOrCreateAttachment returned null for ${entity.uuid}")
            return false
        }
        logger.info("[renderForGui] modelPath=${cached.modelPath}, animSystem=${cached.animationSystem != null}")

        val animSystem = cached.animationSystem
        if (animSystem != null) {
            syncAnimationLayers(animSystem, cached, modelComponent.animationLayers)
            animSystem.update(Time.deltaT)
        }

        val skinTexture = if (modelComponent.skinId.isNotEmpty()) {
            omc.boundbyfate.client.skin.ClientSkinManager.also {
                it.ensureLoaded(modelComponent.skinId)
            }.getTexture(modelComponent.skinId)
        } else null

        renderModel(
            attachment  = cached.attachment,
            entity      = entity,
            entityYaw   = entityYaw,
            partialTick = partialTick,
            poseStack   = poseStack,
            buffer      = buffer,
            packedLight = packedLight,
            scale       = modelComponent.scale,
            skinTexture = skinTexture
        )
        return true
    }

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
        val animationSystem: AnimationSystem?,
        /** Снимок слоёв из компонента на прошлом кадре — для diff-сравнения. Ключ = AnimLayerState.key */
        var activeLayerStates: Map<String, AnimLayerState>
    )
}
