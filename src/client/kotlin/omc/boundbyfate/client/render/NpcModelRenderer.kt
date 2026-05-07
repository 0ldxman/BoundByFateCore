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
 */
@Environment(EnvType.CLIENT)
object NpcModelRenderer {

    private val logger = LoggerFactory.getLogger(NpcModelRenderer::class.java)

    /** Кеш моделей. Ключ — UUID сущности. */
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
        if (entity !is NpcEntity) return false

        val modelComponent = entity.getOrCreate(NpcModelComponent.TYPE)
        val cached = getOrCreateAttachment(entity, modelComponent) ?: return false

        // Frustum culling отключён — используем стандартный MC culling по hitbox.
        // Кастомный culling по bounds модели давал ложные срабатывания.

        val animSystem = cached.animationSystem
        if (animSystem != null) {
            syncAnimationLayers(animSystem, cached, modelComponent.animationLayers)
        }

        animSystem?.update(Time.deltaT)

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

    private fun syncAnimationLayers(
        animSystem: AnimationSystem,
        cached: CachedModel,
        layers: List<AnimLayerState>
    ) {
        val newLayerMap = layers.associateBy { it.key }
        val oldLayerMap = cached.activeLayerStates

        for ((key, oldState) in oldLayerMap) {
            if (key !in newLayerMap) {
                if (key == AnimLayerState.BASE_LAYER_KEY) {
                    animSystem.play("", duration = oldState.blendIn, wrapMode = WrapMode.Once)
                } else {
                    animSystem.stopLayer(key)
                }
            }
        }

        for ((key, newState) in newLayerMap) {
            val oldState = oldLayerMap[key]
            if (oldState != newState) {
                val wrapMode = if (newState.looping) WrapMode.Loop else WrapMode.Once
                if (key == AnimLayerState.BASE_LAYER_KEY) {
                    animSystem.play(newState.animation, duration = newState.blendIn, wrapMode = wrapMode)
                } else {
                    animSystem.playLayer(key, newState.animation, newState.blendIn, wrapMode)
                }
            }
        }

        cached.activeLayerStates = newLayerMap
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

        // Blockbench exports models rotated 180° around Y — apply via pose stack
        if (attachment.flow.value.model.isBlockBench) {
            poseStack.multiply(Quaternionf().rotateY(Math.PI.toFloat()))
        }

        if (skinTexture != null) {
            com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0,
                MinecraftClient.getInstance().textureManager.getTexture(skinTexture).getGlId()
            )
        }

        // Устанавливаем шейдер явно — EmptyEntityRenderer не устанавливает его сам.
        val entityCutoutShader = omc.boundbyfate.client.models.internal.SHADER
        if (entityCutoutShader != null) {
            com.mojang.blaze3d.systems.RenderSystem.setShader { entityCutoutShader }
        } else {
            logger.warn("entityCutoutShader is null — VAO render will be skipped")
        }

        attachment.pipeline.render(
            RenderContext(
                stack = poseStack,
                source = buffer,
                light = packedLight,
                overlay = overlay,
                allowInstancing = false,
                shader = entityCutoutShader
            )
        )

        // Flush batched geometry
        if (buffer is net.minecraft.client.render.VertexConsumerProvider.Immediate) {
            buffer.draw()
        }

        poseStack.pop()
    }

    private fun getOrCreateAttachment(
        entity: NpcEntity,
        component: NpcModelComponent
    ): CachedModel? {
        val key = entity.uuid.toString()
        val existing = attachments[key]

        if (existing != null && existing.modelPath == component.modelPath) {
            return existing
        }

        if (existing != null) {
            existing.animationSystem?.destroy()
            attachments.remove(key)
        }

        return try {
            val attachment = ModelAttachment(component.modelPath)

            val animSystem = if (component.animationsEnabled) {
                AnimationSystem(attachment).also { sys ->
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

    fun getAnimationSystem(entity: Entity): AnimationSystem? =
        attachments[entity.uuid.toString()]?.animationSystem

    fun renderForGui(
        entity: NpcEntity,
        poseStack: MatrixStack,
        buffer: VertexConsumerProvider,
        packedLight: Int,
        entityYaw: Float,
        partialTick: Float
    ): Boolean {
        val modelComponent = entity.getAttached(NpcModelComponent.TYPE) ?: return false
        val cached = getOrCreateAttachment(entity, modelComponent) ?: return false

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

    fun onEntityRemoved(entity: Entity) {
        val cached = attachments.remove(entity.uuid.toString()) ?: return
        cached.animationSystem?.destroy()
    }

    private data class CachedModel(
        val modelPath: String,
        val attachment: ModelAttachment,
        val animationSystem: AnimationSystem?,
        var activeLayerStates: Map<String, AnimLayerState>
    )
}
